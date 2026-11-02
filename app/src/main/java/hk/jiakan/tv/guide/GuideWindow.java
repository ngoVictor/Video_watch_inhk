package hk.jiakan.tv.guide;

import hk.jiakan.core.FutureFeatures.Programme;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Cache a complete local day; only the returned view may be narrowed by the caller. */
final class GuideWindow {
    final long dayStart, dayEnd;
    private final long from, until;

    GuideWindow(long from, long until) throws IOException {
        Calendar day=Calendar.getInstance(GuideTime.ZONE);
        day.setTimeInMillis(from);
        day.set(Calendar.HOUR_OF_DAY,0);day.set(Calendar.MINUTE,0);
        day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);
        dayStart=day.getTimeInMillis();day.add(Calendar.DATE,1);dayEnd=day.getTimeInMillis();
        if(until<=from || from<dayStart || until>dayEnd) throw new IOException("请按香港自然日查询节目单");
        this.from=from;this.until=until;
    }

    GuideService.Result filter(GuideService.Result result) {
        List<Programme> rows=new ArrayList<>();
        for(Programme p:result.programmes)
            if(p.startUtcMillis<until && p.endUtcMillis>from) rows.add(p);
        return new GuideService.Result(rows,result.stale,result.source);
    }
}
