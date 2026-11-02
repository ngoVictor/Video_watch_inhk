package hk.jiakan.tv.playback;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import hk.jiakan.core.Channel;
import hk.jiakan.core.FailoverPolicy;
import hk.jiakan.core.PlaybackMode;
import hk.jiakan.core.AudioHealth;
import hk.jiakan.core.PlaybackProgress;
import androidx.media3.common.Tracks;
import androidx.media3.common.Format;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import hk.jiakan.tv.diagnostics.EventLog;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlaybackController {
    public interface Listener {
        void status(String text); void connected();
        default void notice(String text) { status(text); }
        default void qualityLabel(String text) {}
    }
    private final Context context;
    private final PlayerView view;
    private final Listener listener;
    private final EventLog log;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final FailoverPolicy policy=new FailoverPolicy();
    private final PlaybackProgress progress=new PlaybackProgress();
    private final StableSourceStore stableSources;
    private ExoPlayer player;
    private PlaybackMode mode=PlaybackMode.QUALITY;
    private final AudioHealth audioHealth=new AudioHealth();
    private Channel channel;
    private boolean active;
    private long generation;
    private String lastStatus="";
    private FailoverPolicy.Kind lastAction=FailoverPolicy.Kind.NONE;
    private final Runnable tick=new Runnable() {
        @Override public void run() {
            if(!active) return;
            boolean intended=player==null || (player.getPlayWhenReady() && player.getPlaybackSuppressionReason()==Player.PLAYBACK_SUPPRESSION_REASON_NONE);
            if(player!=null && audioHealth.failed(SystemClock.elapsedRealtime(),intended && player.getPlaybackState()==Player.STATE_READY,
                    !player.getCurrentTracks().isEmpty(),PlaybackProfiles.hasAudio(player.getCurrentTracks()))) {
                log.add("audio_missing_or_unsupported source="+policy.current().id);
                execute(policy.error(SystemClock.elapsedRealtime(),online()));
                handler.postDelayed(this,1000); return;
            }
            execute(policy.sample(SystemClock.elapsedRealtime(),online(),intended,
                    player==null || player.getPlaybackState()==Player.STATE_BUFFERING || player.getPlaybackState()==Player.STATE_IDLE,
                    renderedProgress()));
            handler.postDelayed(this,1000);
        }
    };
    public PlaybackController(Context context,PlayerView view,Listener listener,EventLog log) {
        this.context=context.getApplicationContext(); this.view=view; this.listener=listener; this.log=log;
        stableSources=new StableSourceStore(this.context);
    }
    public void tune(Channel channel) {
        this.channel=channel; active=true; releasePlayer();
        stableSources.restore(channel,policy,SystemClock.elapsedRealtime(),System.currentTimeMillis());
        handler.removeCallbacks(tick); execute(policy.tune(channel,SystemClock.elapsedRealtime(),online())); handler.post(tick);
    }
    public void suspend() { active=false; handler.removeCallbacks(tick); releasePlayer(); }
    public Channel channel() { return channel; }
    public void setMode(PlaybackMode mode) { this.mode=mode; policy.setMode(mode); quality(policy.isLowered()); }
    public boolean online() {
        ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities caps=manager.getNetworkCapabilities(manager.getActiveNetwork());
        return caps!=null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
    private void execute(FailoverPolicy.Action action) {
        if(channel!=null) {
            long wallNow=System.currentTimeMillis();
            long confirmedAt=wallNow-Math.max(0,SystemClock.elapsedRealtime()-policy.preferredConfirmedAt());
            stableSources.update(channel,policy.preferred(),wallNow,confirmedAt);
        }
        if(action.kind!=FailoverPolicy.Kind.NONE) {
            if(action.kind!=FailoverPolicy.Kind.WAIT_RETRY || lastAction!=action.kind)
                log.add(action.kind+" channel="+(channel==null ? "" : channel.id)+" reason="+action.reason);
            lastAction=action.kind;
        }
        switch(action.kind) {
            case NONE: return;
            case SWITCH:
                showStatus(action.reason); start(action.source,false); return;
            case LOWER_QUALITY: quality(true); listener.notice(action.reason); return;
            case RESTORE_QUALITY: quality(false); listener.notice(action.reason); return;
            case WAIT_NETWORK: case WAIT_RETRY: case NO_SOURCE: releasePlayer(); break;
        }
        String message=action.reason;
        if(action.kind==FailoverPolicy.Kind.WAIT_RETRY) message="线路暂不可用，"+Math.max(1,(action.retryAt-SystemClock.elapsedRealtime()+999)/1000)+"秒后自动重试";
        showStatus(message);
    }
    private void showStatus(String message) {
        if(!message.equals(lastStatus)) { lastStatus=message; listener.status(message); }
    }
    private long renderedProgress() {
        if(player==null) return 0;
        androidx.media3.exoplayer.DecoderCounters video=player.getVideoDecoderCounters(),audio=player.getAudioDecoderCounters();
        if(video!=null) video.ensureUpdated(); if(audio!=null) audio.ensureUpdated();
        Tracks tracks=player.getCurrentTracks();
        return progress.sample(tracks.isTypeSelected(C.TRACK_TYPE_VIDEO),video==null ? -1 : video.renderedOutputBufferCount,
                tracks.isTypeSelected(C.TRACK_TYPE_AUDIO),audio==null ? -1 : audio.renderedOutputBufferCount,player.getCurrentPosition());
    }
    private void start(Channel.Source source,boolean softwareAudio) {
        releasePlayer(); final long token=generation;
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
                .setConnectTimeoutMs(6000).setReadTimeoutMs(6000).setAllowCrossProtocolRedirects(false)
                .setUserAgent("JiaKanTV/0.1");
        player=new ExoPlayer.Builder(context)
                .setRenderersFactory(PlaybackProfiles.renderers(context,softwareAudio))
                .setTrackSelector(PlaybackProfiles.selector(context))
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http))
                .setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(15000,45000,1500,4000).setTargetBufferBytes(32*1024*1024).setPrioritizeTimeOverSizeThresholds(false).build())
                .build();
        view.setPlayer(player);
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        quality(policy.isLowered());
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException error) {
                if(token!=generation || !active) return;
                log.add("error="+error.errorCode+" source="+source.id);
                handler.post(() -> { if(token==generation && active) {
                    if(!softwareAudio && PlaybackProfiles.isAudioError(error)) {
                        log.add("audio_software_retry source="+source.id); policy.retryAudio(SystemClock.elapsedRealtime()); start(source,true);
                    } else execute(policy.error(SystemClock.elapsedRealtime(),online()));
                } });
            }
            @Override public void onPlaybackStateChanged(int state) {
                if(token!=generation || !active) return;
                log.add("state="+state+" positionMs="+player.getCurrentPosition()+" bufferedMs="+player.getTotalBufferedDuration()+" source="+source.id);
                if(state==Player.STATE_READY) { lastStatus=""; listener.connected(); }
                if(state==Player.STATE_ENDED) handler.post(() -> { if(token==generation && active) execute(policy.error(SystemClock.elapsedRealtime(),online())); });
            }
            @Override public void onRenderedFirstFrame() { if(token==generation) log.add("first_frame source="+source.id); }
            @Override public void onVideoSizeChanged(VideoSize size) {
                if(token==generation) { log.add("video="+size.width+"x"+size.height+" source="+source.id); listener.qualityLabel(PlaybackProfiles.qualityLabel(player.getCurrentTracks(),size)); }
            }
            @Override public void onTracksChanged(Tracks tracks) {
                if(token!=generation) return;
                quality(policy.isLowered());
                listener.qualityLabel(PlaybackProfiles.qualityLabel(tracks,player.getVideoSize()));
                log.add("audio_selected="+PlaybackProfiles.hasAudio(tracks)+" source="+source.id);
            }
        });
        player.addAnalyticsListener(new AnalyticsListener() {
            @Override public void onDroppedVideoFrames(EventTime time,int frames,long elapsed) {
                if(token==generation) log.add("dropped="+frames+" intervalMs="+elapsed+" source="+source.id);
            }
            @Override public void onAudioDecoderInitialized(EventTime time,String name,long at,long duration) {
                if(token==generation) log.add("audio_decoder="+name+" source="+source.id);
            }
            @Override public void onAudioInputFormatChanged(EventTime time,Format format,DecoderReuseEvaluation evaluation) {
                if(token==generation) log.add("audio="+format.sampleMimeType+" channels="+format.channelCount+" source="+source.id);
            }
            @Override public void onAudioSinkError(EventTime time,Exception error) {
                if(token==generation) log.add("audio_sink_error="+error.getClass().getSimpleName()+" source="+source.id);
            }
            @Override public void onAudioPositionAdvancing(EventTime time,long playoutStartSystemTimeMs) {
                if(token==generation) log.add("audio_output_started source="+source.id);
            }
        });
        MediaItem.Builder media=new MediaItem.Builder().setUri(source.url).setMediaId(channel.id);
        if(!source.mimeType.isEmpty()) media.setMimeType(source.mimeType);
        MediaItem item=media.build();
        if(Util.inferContentType(android.net.Uri.parse(source.url),source.mimeType.isEmpty()?null:source.mimeType)==C.CONTENT_TYPE_HLS)
            player.setMediaSource(new HlsMediaSource.Factory(new HlsDataSources(http)).createMediaSource(item));
        else player.setMediaItem(item);
        player.prepare(); player.play();
        log.add("connect source="+source.id);
    }
    private void quality(boolean low) {
        if(player!=null) PlaybackProfiles.apply(player,mode,low);
    }
    private void releasePlayer() {
        generation++; audioHealth.reset(); progress.reset(); listener.qualityLabel("");
        if(player!=null) { view.setPlayer(null); player.release(); player=null; }
    }
}
