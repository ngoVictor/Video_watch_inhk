package hk.jiakan.tv.ui;

import hk.jiakan.core.FutureFeatures.Programme;
import hk.jiakan.tv.guide.GuideTime;
import java.text.SimpleDateFormat;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuidePresentationTest {
    private long at(String value) throws Exception {SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.ROOT);f.setTimeZone(GuideTime.ZONE);return f.parse(value).getTime();}
    private Programme p(long start,long end) {return new Programme("tvb-jade","节目",start,end);}
    @Test public void dayLabelsUseHongKongAndKeepAbsoluteDateAcrossMidnight() throws Exception {
        long day=at("2026-09-11 00:00"),now=at("2026-09-11 23:59");
        assertEquals(day,GuidePresentation.dayStart(now));assertEquals("今天",GuidePresentation.relativeDay(day,now));
        assertEquals("昨天",GuidePresentation.relativeDay(day,now+60000));
        assertEquals("后天",GuidePresentation.relativeDay(day+2*GuidePresentation.DAY,now));
        assertTrue(GuidePresentation.dateTitle(day,now).contains("09月11日"));
    }
    @Test public void crossingMidnightHasExplicitDayMarkers() throws Exception {
        long day=at("2026-09-11 00:00");
        assertEquals("前一天 23:30\n至 00:30",GuidePresentation.timeRange(p(day-1800000,day+1800000),day));
        assertEquals("23:30\n至 次日 00:30",GuidePresentation.timeRange(p(day+84600000,day+88200000),day));
    }
    @Test public void timeSlotsUseOverlapWithoutBorrowingAnotherDay() throws Exception {
        long day=at("2026-09-11 00:00");Programme cross=p(day+5*3600000L,day+7*3600000L),later=p(day+12*3600000L,day+13*3600000L);
        List<Programme> all=Arrays.asList(p(day-7200000,day),cross,later,p(day+GuidePresentation.DAY,day+2*GuidePresentation.DAY));
        assertEquals(Collections.singletonList(cross),GuidePresentation.filter(all,day,GuidePresentation.Period.NIGHT));
        assertEquals(Collections.singletonList(cross),GuidePresentation.filter(all,day,GuidePresentation.Period.MORNING));
        assertEquals(Arrays.asList(cross,later),GuidePresentation.filter(all,day,GuidePresentation.Period.ALL));
    }
    @Test public void liveAndUpcomingNeverAdvertiseReplay() {
        Programme p=new Programme("CCTV1.cn","节目",100,200,"vid","https://www.mytvsuper.com/tc/programme/show/");
        assertEquals("未播出 · 预告",GuidePresentation.state(p,99));assertTrue(GuidePresentation.state(p,100).contains("正在播出"));
        assertEquals("已播 · 查询回看",GuidePresentation.state(p,200));
    }
    @Test public void officialWebpageAndUnavailableAreDifferentPastStates() {
        Programme web=new Programme("tvb-jade","节目",100,200,"","https://www.mytvsuper.com/tc/programme/show/");
        assertEquals("已播 · 官方重温页面",GuidePresentation.state(web,201));
        assertEquals("已播 · 暂无回看",GuidePresentation.state(p(100,200),201));
    }
    @Test public void initialFocusUsesCurrentProgrammeOnlyWhenPresent() {
        List<Programme> rows=Arrays.asList(p(100,200),p(200,300),p(300,400));
        assertEquals(1,GuidePresentation.initialSelection(rows,250));assertEquals(0,GuidePresentation.initialSelection(rows,50));
        assertEquals(0,GuidePresentation.initialSelection(Collections.emptyList(),250));
    }
}
