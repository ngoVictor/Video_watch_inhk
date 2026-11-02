package hk.jiakan.core;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Pure, single-threaded state machine. All time inputs are monotonic milliseconds. */
public final class FailoverPolicy {
    public enum Kind { NONE, SWITCH, LOWER_QUALITY, RESTORE_QUALITY, WAIT_NETWORK, WAIT_RETRY, NO_SOURCE }
    public static final class Action {
        public final Kind kind;
        public final Channel.Source source;
        public final String reason;
        public final long retryAt;
        private Action(Kind kind, Channel.Source source, String reason, long retryAt) {
            this.kind = kind; this.source = source; this.reason = reason; this.retryAt = retryAt;
        }
    }
    private static final Action NONE = new Action(Kind.NONE, null, "", 0);
    private static final class Health { int failures; long cooldown; }
    private static final class Preferred {
        final Channel.Source source; long confirmedAt;
        Preferred(Channel.Source source,long now) { this.source=source; confirmedAt=now; }
    }
    private final Map<String, Health> health = new LinkedHashMap<String,Health>(64,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Health> entry) { return size()>1024; }
    };
    private final Map<String,Preferred> preferred = new LinkedHashMap<String,Preferred>(16,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Preferred> entry) { return size()>128; }
    };
    private final ArrayDeque<Long> buffers = new ArrayDeque<>();
    private List<Channel.Source> sources = Collections.emptyList();
    private Channel.Source current;
    private String channelId="";
    private PlaybackMode mode=PlaybackMode.QUALITY;
    private boolean waiting, offline, started, wasBuffering, lowered;
    private long attemptAt, bufferAt, progressAt, position = -1, stableAt, qualityGrace;

    public Action tune(Channel channel, long now, boolean online) {
        channelId=channel.id;
        sources = channel.sources; current = null; lowered = false; waiting = false;
        Preferred remembered=preferred.get(channelId);
        if(remembered!=null && (now<remembered.confirmedAt || now-remembered.confirmedAt>86_400_000L
                || sources.stream().noneMatch(s -> same(s,remembered.source)))) preferred.remove(channelId);
        offline = !online; resetObservation(now);
        if (sources.isEmpty()) return action(Kind.NO_SOURCE, "暂无可用线路");
        if (!online) return action(Kind.WAIT_NETWORK, "网络已断开，正在等待恢复");
        return choose(now);
    }
    public boolean isLowered() { return lowered; }
    public void setMode(PlaybackMode mode) { this.mode=java.util.Objects.requireNonNull(mode); }
    public Channel.Source current() { return current; }
    public Channel.Source preferred() {
        Preferred value=preferred.get(channelId); return value==null ? null : value.source;
    }
    public long preferredConfirmedAt() {
        Preferred value=preferred.get(channelId); return value==null ? 0 : value.confirmedAt;
    }
    /** Restored data must match a current source exactly; it is never a media URL to play directly. */
    public void restorePreferred(Channel channel,Channel.Source source,long now) {
        Health known=source==null ? null : health.get(identity(source));
        if(known!=null && known.failures>0) return;
        if(source!=null && channel.sources.stream().anyMatch(s -> same(s,source)))
            preferred.put(channel.id,new Preferred(source,now));
    }
    public void retryAudio(long now) { resetObservation(now); }

    public Action sample(long now, boolean online, boolean intended, boolean buffering, long newPosition) {
        if (sources.isEmpty()) return NONE;
        if (!online) {
            if (!offline) { offline = true; return action(Kind.WAIT_NETWORK, "网络已断开，正在等待恢复"); }
            return NONE;
        }
        if (offline) { offline = false; return waiting || current == null ? choose(now) : activate(current, now); }
        if (waiting) return choose(now);
        if (!intended) { resetObservation(now); return NONE; }
        if (current == null) return NONE;
        while (!buffers.isEmpty() && now - buffers.peekFirst() > 60_000) buffers.removeFirst();
        if (buffering) {
            stableAt = now;
            if (!wasBuffering) { bufferAt = now; if (started) buffers.addLast(now); }
            wasBuffering = true;
            if (!started && now - attemptAt >= 12_000) return fail(now, "启动播放超时");
            if (started && now >= qualityGrace && (now - bufferAt >= mode.bufferTimeoutMs || buffers.size() >= 3)) {
                if (!lowered) {
                    lowered = true; qualityGrace = now + 15_000; buffers.clear();
                    return action(Kind.LOWER_QUALITY, "正在适度调整画质，高清源最低保持720p");
                }
                return fail(now, "持续缓冲");
            }
        } else {
            if (wasBuffering) { progressAt = now; stableAt = now; }
            wasBuffering = false;
            boolean progressed=newPosition>0 && newPosition!=position;
            if (newPosition != position) {
                position = newPosition; progressAt = now; if(newPosition>0) started = true;
            } else if (now - progressAt >= 8_000) {
                return fail(now, "播放进度停滞");
            }
            if(started && progressed && now-stableAt>=30_000) {
                Preferred remembered=preferred.get(channelId);
                if(remembered==null || !same(remembered.source,current)) preferred.put(channelId,new Preferred(current,now));
                else remembered.confirmedAt=now;
            }
            if (started && now - stableAt >= 120_000) {
                Health h = health.get(identity(current));
                if (h != null) { h.failures = 0; h.cooldown = 0; }
                if (lowered) { lowered = false; return action(Kind.RESTORE_QUALITY, "已恢复自动画质"); }
            }
        }
        return NONE;
    }
    public Action error(long now, boolean online) {
        if (!online) { offline = true; return action(Kind.WAIT_NETWORK, "网络已断开，正在等待恢复"); }
        return waiting || current == null ? NONE : fail(now, "当前线路播放失败");
    }
    private Action fail(long now, String reason) {
        Preferred remembered=preferred.get(channelId);
        if(remembered!=null && same(remembered.source,current)) preferred.remove(channelId);
        Health h = health.computeIfAbsent(identity(current), id -> new Health());
        h.failures = Math.min(8, h.failures + 1);
        h.cooldown = now + Math.min(120_000L, 15_000L << Math.min(3, h.failures - 1));
        waiting = true;
        Action next = choose(now);
        return new Action(next.kind, next.source, reason + "；" + next.reason, next.retryAt);
    }
    private Action choose(long now) {
        Channel.Source best = null; int bestScore = Integer.MIN_VALUE; long earliest = Long.MAX_VALUE;
        Preferred remembered=preferred.get(channelId);
        for (Channel.Source source : sources) {
            Health h = health.get(identity(source));
            long cooldown = h == null ? 0 : h.cooldown;
            earliest = Math.min(earliest, cooldown);
            if (cooldown > now) continue;
            if(remembered!=null && same(remembered.source,source)) { best=source; break; }
            int score = source.priority - (h == null ? 0 : h.failures * 25);
            if (current != null && !source.id.equals(current.id) && !source.upstream.isEmpty()
                    && !source.upstream.equals(current.upstream)) score += 10;
            if (score > bestScore) { best = source; bestScore = score; }
        }
        if (best != null) return activate(best, now);
        waiting = true;
        return new Action(Kind.WAIT_RETRY, null, "线路暂不可用，稍后自动重试", earliest);
    }
    private Action activate(Channel.Source source, long now) {
        current = source; waiting = false; resetObservation(now);
        return new Action(Kind.SWITCH, source, "正在连接线路", 0);
    }
    private void resetObservation(long now) {
        buffers.clear(); started = false; wasBuffering = false; position = -1;
        attemptAt = bufferAt = progressAt = stableAt = now; qualityGrace = 0;
    }
    private Action action(Kind kind, String reason) { return new Action(kind, null, reason, 0); }
    private static String identity(Channel.Source source) { return source.id+"\n"+source.url; }
    private static boolean same(Channel.Source left,Channel.Source right) {
        return left.id.equals(right.id) && left.url.equals(right.url);
    }
}
