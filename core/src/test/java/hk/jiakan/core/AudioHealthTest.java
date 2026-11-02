package hk.jiakan.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class AudioHealthTest {
    @Test public void missingTrackRequiresReadyGraceAndResetsAfterRecovery() {
        AudioHealth h=new AudioHealth();
        assertFalse(h.failed(0,true,true,false));
        assertFalse(h.failed(2999,true,true,false));
        assertTrue(h.failed(3000,true,true,false));
        assertFalse(h.failed(3001,true,true,true));
        assertFalse(h.failed(4000,true,true,false));
        assertTrue(h.failed(7000,true,true,false));
    }
    @Test public void pauseBufferingUnknownTracksDoNotTriggerFailure() {
        AudioHealth h=new AudioHealth();
        for(int i=0;i<10;i++) { assertFalse(h.failed(i*10000,false,true,false)); assertFalse(h.failed(i*10000,true,false,false)); }
        assertFalse(h.failed(100000,true,true,false));
        h.reset(); assertFalse(h.failed(200000,true,true,false));
    }
}
