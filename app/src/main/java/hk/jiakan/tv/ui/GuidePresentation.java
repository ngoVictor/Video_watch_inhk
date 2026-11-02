package hk.jiakan.tv.ui;

import hk.jiakan.core.FutureFeatures.Programme;
import hk.jiakan.tv.guide.GuideTime;
import java.text.SimpleDateFormat;
import java.util.*;

/** Date and programme labels only; no Android, network or playback state. */
public final class GuidePresentation {
    private GuidePresentation() {}
    public static final long DAY=86400000L;
    public enum Period {
        ALL("全天",0,24), NIGHT("凌晨",0,6), MORNING("上午",6,12), AFTERNOON("下午",12,18), EVENING("晚间",18,24);
        public final String label;
        public final int startHour,endHour;
        Period(String label,int start,int end) { this.label=label;startHour=start;endHour=end; }
        public String range() { return String.format(Locale.ROOT,"%02d:00–%02d:00",startHour,endHour); }
    }
    public static long dayStart(long instant) {
        Calendar c=Calendar.getInstance(GuideTime.ZONE);c.setTimeInMillis(instant);
        c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
    }
    private static String format(String pattern,long value) {
        SimpleDateFormat f=new SimpleDateFormat(pattern,Locale.SIMPLIFIED_CHINESE);f.setTimeZone(GuideTime.ZONE);
        return f.format(new Date(value));
    }
    public static String relativeDay(long day,long now) {
        int difference=(int)((day-dayStart(now))/DAY);
        switch(difference) { case -2:return "前天";case -1:return "昨天";case 0:return "今天";case 1:return "明天";case 2:return "后天";default:return format("EEEE",day); }
    }
    public static String dateTitle(long day,long now) { return relativeDay(day,now)+" · "+format("MM月dd日 E",day); }
    public static String railDate(long day) { return format("MM/dd E",day); }
    public static String time(long instant,long day) {
        long shift=(dayStart(instant)-day)/DAY;
        String prefix=shift==-1 ? "前一天 " : shift==1 ? "次日 " : shift==0 ? "" : format("MM/dd ",instant);
        return prefix+format("HH:mm",instant);
    }
    public static String timeRange(Programme p,long day) { return time(p.startUtcMillis,day)+"\n至 "+time(p.endUtcMillis,day); }
    public static boolean live(Programme p,long now) { return p.startUtcMillis<=now && p.endUtcMillis>now; }
    public static String state(Programme p,long now) {
        if(live(p,now)) return "正在播出 · 确定看直播";
        if(p.startUtcMillis>now) return "未播出 · 预告";
        return !p.replayId.isEmpty() ? "已播 · 查询回看" : !p.officialReplayUrl.isEmpty() ? "已播 · 官方重温页面" : "已播 · 暂无回看";
    }
    public static List<Programme> filter(List<Programme> all,long day,Period period) {
        long from=day+period.startHour*3600000L,until=day+period.endHour*3600000L;
        List<Programme> result=new ArrayList<>();
        for(Programme p:all) if(p.startUtcMillis<until && p.endUtcMillis>from) result.add(p);
        return result;
    }
    public static int initialSelection(List<Programme> rows,long now) {
        for(int i=0;i<rows.size();i++) if(live(rows.get(i),now))return i;
        return 0;
    }
}
