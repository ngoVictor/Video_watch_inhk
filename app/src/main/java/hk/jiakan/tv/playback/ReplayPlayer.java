package hk.jiakan.tv.playback;

import android.content.Context;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.*;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import hk.jiakan.core.Channel;
import hk.jiakan.core.PlaybackMode;
import hk.jiakan.tv.diagnostics.EventLog;

/** Finite on-demand playback; reaching the end must never enter live failover. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class ReplayPlayer {
    private final Context context;
    private final PlayerView view;
    private final PlaybackController.Listener listener;
    private final EventLog log;
    private ExoPlayer player;
    private Channel.Source source;
    private long position;
    private boolean paused,ended;
    private boolean softwareAudio;
    private boolean audioUnavailable;
    private PlaybackMode mode=PlaybackMode.QUALITY;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    public void setMode(PlaybackMode mode) { this.mode=mode; if(player!=null) PlaybackProfiles.apply(player,mode,false); }
    public ReplayPlayer(Context context,PlayerView view,PlaybackController.Listener listener,EventLog log) {
        this.context=context.getApplicationContext(); this.view=view; this.listener=listener; this.log=log;
    }
    public boolean active() { return source!=null; }
    public void start(Channel.Source source) { clear(); this.source=source; resume(); }
    public void resume() {
        if(source==null) return;
        if(ended) { listener.status("回看已结束 · 按返回看直播"); return; }
        if(player!=null) return;
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setConnectTimeoutMs(6000).setReadTimeoutMs(6000).setUserAgent("JiaKanTV/0.2");
        player=new ExoPlayer.Builder(context).setRenderersFactory(PlaybackProfiles.renderers(context,softwareAudio))
                .setTrackSelector(PlaybackProfiles.selector(context))
                .setMediaSourceFactory(new DefaultMediaSourceFactory(http)).build();
        final ExoPlayer session=player;
        view.setPlayer(player);
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
        player.setHandleAudioBecomingNoisy(true);
        PlaybackProfiles.apply(player,mode,false);
        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if(player!=session) return;
                log.add("replay_state="+state+" positionMs="+session.getCurrentPosition());
                if(state==Player.STATE_READY) {
                    if(paused) listener.status("回看已暂停 · 按确定继续"); else listener.connected();
                    handler.postDelayed(() -> {
                        if(player==session && session.getPlaybackState()==Player.STATE_READY && !session.getCurrentTracks().isEmpty()
                                && !PlaybackProfiles.hasAudio(session.getCurrentTracks())) {
                            audioUnavailable=true; session.pause(); listener.status("回看缺少可播放音轨 · 按返回看直播");
                        }
                    },3000);
                }
                if(state==Player.STATE_ENDED) { ended=true; listener.status("回看已结束 · 按返回看直播"); }
            }
            @Override public void onPlayerError(PlaybackException error) {
                if(player!=session) return;
                log.add("replay_error="+error.errorCode);
                if(!softwareAudio && PlaybackProfiles.isAudioError(error)) {
                    handler.post(() -> { if(player==session) { softwareAudio=true; suspend(); resume(); } });
                } else listener.status("回看暂时无法播放 · 按返回看直播");
            }
            @Override public void onTracksChanged(Tracks tracks) {
                if(player==session) { PlaybackProfiles.apply(session,mode,false); listener.qualityLabel(PlaybackProfiles.qualityLabel(tracks,session.getVideoSize())); }
            }
            @Override public void onVideoSizeChanged(VideoSize size) {
                if(player==session) listener.qualityLabel(PlaybackProfiles.qualityLabel(session.getCurrentTracks(),size));
            }
        });
        listener.status("正在加载回看…");
        player.setMediaItem(new MediaItem.Builder().setUri(source.url).setMimeType(source.mimeType).build(),position);
        player.prepare(); player.setPlayWhenReady(!paused);
    }
    public void togglePause() {
        if(audioUnavailable) { listener.status("回看缺少可播放音轨 · 按返回看直播"); return; }
        if(player==null || ended) return;
        paused=player.getPlayWhenReady(); player.setPlayWhenReady(!paused);
        if(paused) listener.status("回看已暂停 · 按确定继续"); else listener.connected();
    }
    public void seek(long delta) {
        if(player==null || !player.isCurrentMediaItemSeekable()) return;
        long target=Math.max(0,player.getCurrentPosition()+delta),duration=player.getDuration();
        if(duration!=C.TIME_UNSET) target=Math.min(target,Math.max(0,duration-1000));
        ended=false; player.seekTo(target);
    }
    public void suspend() {
        handler.removeCallbacksAndMessages(null); listener.qualityLabel("");
        if(player==null) return;
        position=player.getCurrentPosition(); paused=!player.getPlayWhenReady();
        ExoPlayer old=player; player=null; view.setPlayer(null); old.release();
    }
    public void clear() { suspend(); source=null; position=0; paused=false; ended=false; softwareAudio=false; audioUnavailable=false; }
}
