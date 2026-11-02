package hk.jiakan.tv;

import android.content.Context;
import hk.jiakan.core.*;
import hk.jiakan.core.FutureFeatures.Programme;
import hk.jiakan.tv.catalog.CatalogRepository;
import hk.jiakan.tv.guide.*;
import hk.jiakan.tv.net.SafeHttp;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Real HTTPS and parser checks, with an injected outage of the whole official host. */
final class CctvGuideChecks {
    static int run(Context context) throws Exception {
        Calendar calendar=Calendar.getInstance(GuideTime.ZONE);calendar.set(Calendar.HOUR_OF_DAY,0);calendar.set(Calendar.MINUTE,0);calendar.set(Calendar.SECOND,0);calendar.set(Calendar.MILLISECOND,0);
        long today=calendar.getTimeInMillis();int checks=0;JSONArray evidence=new JSONArray();
        CctvProgramGuide regular=new CctvProgramGuide(context);
        CctvProgramGuide outage=new CctvProgramGuide(context,(url,request)->{
            if(url.startsWith("https://api.cntv.cn/")) throw new java.net.UnknownHostException("injected official host outage");
            return SafeHttp.text(url,CctvGuideParser.MAX_BYTES,SafeHttp.NO_AUTH,request,8000);
        });
        for(Channel channel:new CatalogRepository(context).load().channels) {
            if(!"央视".equals(channel.group))continue;
            for(int offset=0;offset<3;offset++) {
                long from=today+offset*86400000L;
                GuideService.Result result=regular.load(channel.id,from,from+86400000L,new SafeHttp.Request(),true);
                require(!result.stale,"unexpected stale regular result: "+channel.id);checks++;
                if(offset==0) {require(!result.programmes.isEmpty(),"no current guide: "+channel.id);checks++;}
                for(Programme p:result.programmes) require(p.channelId.equals(channel.id) && p.startUtcMillis<from+86400000L && p.endUtcMillis>from,"wrong channel/day");
                evidence.put(entry(channel.id,offset,"regular",result));
            }
            GuideService.Result result=outage.load(channel.id,today,today+86400000L,new SafeHttp.Request(),true);
            require(!result.stale && !result.programmes.isEmpty() && result.source.contains("EPG.pw"),"outage did not recover with live backup: "+channel.id);checks++;
            for(Programme p:result.programmes) require(p.replayId.isEmpty(),"backup fabricated replay");checks++;
            GuideService.Result cached=outage.load(channel.id,today,today+86400000L);
            require(!cached.stale && cached.source.contains("EPG.pw"),"cached provenance lost");checks++;
            evidence.put(entry(channel.id,0,"forced-official-host-outage",result));
            File target=new File(context.getFilesDir(),"cctv-guide-evidence.json");
            try(FileOutputStream out=new FileOutputStream(target)) {out.write(new JSONObject().put("checkedAtUtcMillis",System.currentTimeMillis()).put("device","project jiakan_tv temporary emulator; not household device").put("rows",evidence).toString(2).getBytes(StandardCharsets.UTF_8));}
        }
        require(evidence.length()==84,"catalog CCTV coverage changed");return checks+1;
    }
    private static JSONObject entry(String id,int day,String mode,GuideService.Result result) throws JSONException {
        return new JSONObject().put("id",id).put("dayOffset",day).put("mode",mode).put("programmes",result.programmes.size()).put("source",result.source).put("stale",result.stale);
    }
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
