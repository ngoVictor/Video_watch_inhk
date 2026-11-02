package hk.jiakan.core;

/** Detect missing selected audio, never silence, device volume or programme content. */
public final class AudioHealth {
    private long missingSince=-1;
    public void reset() { missingSince=-1; }
    public boolean failed(long now, boolean playingReady, boolean tracksKnown, boolean selectedAudio) {
        if(!playingReady || !tracksKnown || selectedAudio) { reset(); return false; }
        if(missingSince<0) missingSince=now;
        return now-missingSince>=3000;
    }
}
