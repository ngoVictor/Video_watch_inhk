package hk.jiakan.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackProgressTest {
    @Test public void liveWindowRebasesCannotCauseFalseStallWhenVideoAdvances() {
        PlaybackProgress progress=new PlaybackProgress();FailoverPolicy policy=new FailoverPolicy();
        Channel.Source source=new Channel.Source("s","https://example.org/live","","",80);
        policy.tune(new Channel("c","C","央视","",java.util.Collections.singletonList(source)),0,true);
        for(int second=1;second<=180;second++) {
            long token=progress.sample(true,second*25L,true,second*40L,second%4*1000L);
            assertEquals(FailoverPolicy.Kind.NONE,policy.sample(second*1000L,true,true,false,token).kind);
        }
    }
    @Test public void advancingAudioOrTimelineCannotMaskFrozenVideo() {
        PlaybackProgress progress=new PlaybackProgress();
        assertEquals(1,progress.sample(true,25,true,30,1000));
        for(int i=2;i<15;i++) assertEquals(1,progress.sample(true,25,true,i*30,i*1000));
    }
    @Test public void missingVideoCountersDoNotFallBackToAnAdvancingClock() {
        PlaybackProgress progress=new PlaybackProgress();
        assertEquals(0,progress.sample(true,-1,true,30,1000));
        assertEquals(0,progress.sample(true,0,true,60,2000));
        assertEquals(1,progress.sample(true,1,true,90,3000));
    }
    @Test public void rendererCounterResetIsNotProgressAndFutureFramesStillCount() {
        PlaybackProgress progress=new PlaybackProgress();
        assertEquals(1,progress.sample(true,100,false,-1,1000));
        assertEquals(1,progress.sample(true,0,false,-1,2000));
        assertEquals(2,progress.sample(true,1,false,-1,3000));
        progress.reset();assertEquals(0,progress.sample(true,0,false,-1,0));
        assertEquals(1,progress.sample(false,-1,true,20,0));
    }
}
