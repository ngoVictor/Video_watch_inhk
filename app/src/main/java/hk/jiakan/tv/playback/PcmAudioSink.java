package hk.jiakan.tv.playback;

import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.exoplayer.audio.AudioOffloadSupport;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.ForwardingAudioSink;

/** Decode compressed audio before HDMI output, regardless of reported passthrough support. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class PcmAudioSink extends ForwardingAudioSink {
    public PcmAudioSink(AudioSink sink) { super(sink); }
    @Override public boolean supportsFormat(Format format) { return getFormatSupport(format)!=SINK_FORMAT_UNSUPPORTED; }
    @Override public int getFormatSupport(Format format) {
        return MimeTypes.AUDIO_RAW.equals(format.sampleMimeType) ? super.getFormatSupport(format) : SINK_FORMAT_UNSUPPORTED;
    }
    @Override public AudioOffloadSupport getFormatOffloadSupport(Format format) { return AudioOffloadSupport.DEFAULT_UNSUPPORTED; }
}
