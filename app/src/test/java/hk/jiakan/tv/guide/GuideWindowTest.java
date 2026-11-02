package hk.jiakan.tv.guide;

import hk.jiakan.core.FutureFeatures.Programme;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuideWindowTest {
    private final long day=Instant.parse("2026-09-11T16:00:00Z").toEpochMilli();
    private final long hour=3600000L;

    @Test public void partialQueryUsesFullHongKongDay() throws Exception {
        GuideWindow window=new GuideWindow(day+10*hour,day+12*hour);
        assertEquals(day,window.dayStart);assertEquals(day+24*hour,window.dayEnd);
    }
    @Test public void fullDayAndEndAtMidnightAllowed() throws Exception {
        assertEquals(day+24*hour,new GuideWindow(day,day+24*hour).dayEnd);
        assertEquals(day,new GuideWindow(day+23*hour,day+24*hour).dayStart);
    }
    @Test public void shortQueryCrossingMidnightRejected() throws Exception {
        try { new GuideWindow(day+23*hour,day+25*hour);fail(); } catch(IOException expected) {}
    }
    @Test public void emptyAndReversedQueryRejected() throws Exception {
        for(long end:new long[]{day,day-1})
            try { new GuideWindow(day,end);fail(); } catch(IOException expected) {}
    }
    @Test public void filtersIntersectionWithoutChangingCacheOrMetadata() throws Exception {
        Programme before=new Programme("CCTV1.cn","before",day,day+10*hour);
        Programme overlap=new Programme("CCTV1.cn","overlap",day+9*hour,day+11*hour);
        Programme after=new Programme("CCTV1.cn","after",day+12*hour,day+13*hour);
        GuideService.Result all=new GuideService.Result(Arrays.asList(before,overlap,after),true,"fixture source");
        GuideService.Result part=new GuideWindow(day+10*hour,day+12*hour).filter(all);
        assertEquals(Arrays.asList(overlap),part.programmes);
        assertTrue(part.stale);assertEquals(all.source,part.source);assertEquals(3,all.programmes.size());
    }
    @Test public void emptyFilteredResultRetainsSourceAndFreshness() throws Exception {
        GuideService.Result all=new GuideService.Result(Arrays.asList(new Programme("c","late",day+20*hour,day+21*hour)),false,"source");
        GuideService.Result part=new GuideWindow(day,day+hour).filter(all);
        assertTrue(part.programmes.isEmpty());assertFalse(part.stale);assertEquals("source",part.source);
    }
}
