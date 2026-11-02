package hk.jiakan.tv.playback;

import android.content.Context;
import androidx.media3.common.*;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import hk.jiakan.core.PlaybackMode;
import java.util.ArrayList;
import java.util.List;

/** Shared live/replay configuration; audio recovery never changes video constraints. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class PlaybackProfiles {
    private PlaybackProfiles() {}
    public static boolean isAudioError(PlaybackException error) {
        if(!(error instanceof androidx.media3.exoplayer.ExoPlaybackException)) return false;
        Format format=((androidx.media3.exoplayer.ExoPlaybackException)error).rendererFormat;
        return format!=null && MimeTypes.isAudio(format.sampleMimeType);
    }
    public static DefaultRenderersFactory renderers(Context context, boolean preferSoftwareAudio) {
        return new DefaultRenderersFactory(context) {
            @Override protected AudioSink buildAudioSink(Context context, boolean floating, boolean playbackParams) {
                return new PcmAudioSink(new DefaultAudioSink.Builder(context).build());
            }
            @Override protected void buildAudioRenderers(Context context,int extensionMode,
                    androidx.media3.exoplayer.mediacodec.MediaCodecSelector selector,boolean fallback,AudioSink sink,
                    android.os.Handler handler,androidx.media3.exoplayer.audio.AudioRendererEventListener listener,
                    ArrayList<androidx.media3.exoplayer.Renderer> out) {
                super.buildAudioRenderers(context,preferSoftwareAudio ? EXTENSION_RENDERER_MODE_PREFER : EXTENSION_RENDERER_MODE_ON,
                        selector,fallback,sink,handler,listener,out);
            }
        }.setEnableDecoderFallback(true);
    }
    public static DefaultTrackSelector selector(Context context) {
        DefaultTrackSelector selector=new DefaultTrackSelector(context);
        selector.setParameters(selector.buildUponParameters().setExceedRendererCapabilitiesIfNecessary(false)
                .setPreferredAudioMimeTypes(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2)
                .setMaxAudioChannelCount(2).setForceHighestSupportedBitrate(false).setForceLowestBitrate(false));
        return selector;
    }
    public static TrackSelectionParameters parameters(TrackSelectionParameters current, Tracks tracks, PlaybackMode mode, boolean lowered) {
        TrackSelectionParameters.Builder builder=current.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .clearViewportSizeConstraints().setMinVideoSize(0,720)
                .setMaxVideoSize(mode.maxHeight(lowered)==720 ? 1280 : 1920,mode.maxHeight(lowered))
                .setMaxVideoBitrate(mode.maxBitrate(lowered)).setForceHighestSupportedBitrate(false).setForceLowestBitrate(false);
        // A bitrate cap alone can cause selection of SD despite an available HD rendition.
        // Restrict the adaptive set explicitly; when a cap is impossible keep the least costly HD track.
        Tracks.Group best=null; List<Integer> bestIndices=null; int bestHeight=0;
        for(Tracks.Group group:tracks.getGroups()) {
            if(group.getType()!=C.TRACK_TYPE_VIDEO) continue;
            List<Integer> hd=new ArrayList<>(),within=new ArrayList<>(); int cheapest=-1;
            for(int i=0;i<group.length;i++) {
                Format f=group.getTrackFormat(i);
                if(!group.isTrackSupported(i) || (f.roleFlags&C.ROLE_FLAG_TRICK_PLAY)!=0 || f.height<720 || f.height>1080) continue;
                hd.add(i);
                if(cheapest<0 || cost(f)<cost(group.getTrackFormat(cheapest))) cheapest=i;
                if(f.height<=mode.maxHeight(lowered) && (f.bitrate==Format.NO_VALUE || f.bitrate<=mode.maxBitrate(lowered))) within.add(i);
            }
            if(hd.isEmpty()) continue;
            if(within.isEmpty()) within.add(cheapest);
            int height=0; for(int i:within) height=Math.max(height,group.getTrackFormat(i).height);
            if(best==null || height>bestHeight) { best=group; bestIndices=within; bestHeight=height; }
        }
        if(best!=null) builder.setOverrideForType(new TrackSelectionOverride(best.getMediaTrackGroup(),bestIndices));
        return builder.build();
    }
    private static long cost(Format format) { return format.bitrate>0 ? format.bitrate : (long)format.height*12000; }
    public static void apply(ExoPlayer player, PlaybackMode mode, boolean lowered) {
        TrackSelectionParameters next=parameters(player.getTrackSelectionParameters(),player.getCurrentTracks(),mode,lowered);
        if(!next.equals(player.getTrackSelectionParameters())) player.setTrackSelectionParameters(next);
    }
    public static boolean hasAudio(Tracks tracks) { return tracks.isTypeSelected(C.TRACK_TYPE_AUDIO); }
    public static String qualityLabel(Tracks tracks, VideoSize size) {
        int maximum=0;
        for(Tracks.Group group:tracks.getGroups()) if(group.getType()==C.TRACK_TYPE_VIDEO)
            for(int i=0;i<group.length;i++) if(group.isTrackSupported(i)) maximum=Math.max(maximum,group.getTrackFormat(i).height);
        return size.height>0 && size.height<720 && maximum>0 && maximum<720 ? "源为标清 · "+size.height+"p" : "";
    }
}
