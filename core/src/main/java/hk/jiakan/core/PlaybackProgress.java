package hk.jiakan.core;

/** Monotonic observation token; a moving live window is not rendered media progress. */
public final class PlaybackProgress {
    private long token,video=-1,audio=-1,position=-1;
    public void reset() { token=0;video=audio=position=-1; }
    public long sample(boolean videoSelected,long videoFrames,boolean audioSelected,long audioBuffers,long timelinePosition) {
        boolean advanced;
        if(videoSelected) advanced=videoFrames>0 && videoFrames>video;
        else if(audioSelected) advanced=audioBuffers>0 && audioBuffers>audio;
        else advanced=timelinePosition>0 && timelinePosition>position;
        video=videoFrames;audio=audioBuffers;position=timelinePosition;
        if(advanced) token++;
        return token;
    }
}
