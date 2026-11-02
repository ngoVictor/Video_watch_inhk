package hk.jiakan.tv.guide;

import hk.jiakan.core.FutureFeatures.Programme;
import hk.jiakan.tv.net.SafeHttp;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/** Bounded fallback chain. No player state, Android context, or persistent retry machinery. */
final class CctvGuideSources {
    private final GuideLoader loader;
    private final CctvGuideParser parser=new CctvGuideParser();
    CctvGuideSources(GuideLoader loader) { this.loader=loader; }
    String load(String id,long from,long until,SafeHttp.Request request) throws Exception {
        String code=CctvChannels.code(id);
        SimpleDateFormat date=new SimpleDateFormat("yyyyMMdd",Locale.ROOT);date.setTimeZone(GuideTime.ZONE);
        String day=date.format(new Date(from));
        // EPG.pw supplies two UTC days. The preceding UTC date includes local midnight and its predecessor.
        String previous=date.format(new Date(from-86400000L));
        String[] urls={"https://api.cntv.cn/epg/epginfo?serviceId=cbox&c="+code+"&d="+day,
                "https://api.cntv.cn/epg/getEpgInfoByChannelNew?c="+code+"&serviceId=tvcctv&d="+day,
                "https://epg.pw/api/epg.json?lang=zh-hans&date="+previous+"&channel_id="+CctvChannels.backupId(id)};
        String[] names={"official","web","epg-pw"};String empty=null;
        List<String> failures=new ArrayList<>();
        for(int i=0;i<urls.length;i++) {
            request.check();
            try {
                String raw=loader.load(urls[i],request);request.check();
                if(i==1) raw=CctvGuideDocuments.web(raw,id);
                if(i==2) raw=CctvGuideDocuments.backup(raw,id,previous,from,until);
                List<Programme> rows=parser.parse(raw,id,from,until);
                String canonical=CctvGuideDocuments.canonical(rows,id,names[i]);request.check();
                if(!rows.isEmpty()) return canonical;
                if(empty==null) empty=canonical;
            } catch(Exception e) {
                request.check();failures.add(names[i]+":"+e.getClass().getSimpleName());
            }
        }
        request.check();if(empty!=null) return empty;
        throw new IOException("央视节目单各入口均失败 ("+String.join(", ",failures)+")");
    }
}
