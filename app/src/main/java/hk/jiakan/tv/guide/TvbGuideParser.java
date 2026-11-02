package hk.jiakan.tv.guide;

import hk.jiakan.core.FutureFeatures.Programme;
import hk.jiakan.tv.data.JsonFields;
import org.json.*;
import java.util.*;
import java.util.regex.*;

public final class TvbGuideParser {
    public static final int MAX_BYTES=1024*1024;
    public static String code(String id) {
        switch(id) {
            case "tvb-jade": return "J";
            case "TVBPearl.hk": return "P";
            case "tvb-plus": return "B";
            case "tvb-news": return "C";
            default: return "";
        }
    }
    public static String officialPage(String url) {
        if(url==null || url.length()>2048) return "";
        Matcher m=Pattern.compile("^https://www\\.mytvsuper\\.com/tc/programme/([A-Za-z0-9_-]+)(?:/.*)?$").matcher(url);
        return m.matches() ? "https://www.mytvsuper.com/tc/programme/"+m.group(1)+"/" : "";
    }
    public List<Programme> parse(String raw,String id,long from,long until) throws JSONException {
        if(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES || until<=from) throw new JSONException("节目单大小或范围无效");
        String code=code(id);if(code.isEmpty()) return Collections.emptyList();
        JSONObject combined=new JSONObject(raw);TreeMap<Long,JSONObject> timed=new TreeMap<>();
        for(String day:new String[]{"previous","current"}) {
            JSONObject root=combined.getJSONObject(day);
            if(root.getInt("code")!=0) throw new JSONException("TVB 节目服务返回错误");
            JSONArray channels=root.getJSONObject("data").getJSONArray("list");boolean found=false;
            for(int c=0;c<channels.length();c++) {
                JSONObject channel=channels.getJSONObject(c);
                if(!code.equalsIgnoreCase(channel.getString("network_code"))) continue;
                found=true;JSONArray rows=channel.getJSONArray("schedules");
                if(rows.length()>500) throw new JSONException("节目单过大");
                for(int i=0;i<rows.length();i++) try {
                    JSONObject row=rows.getJSONObject(i);
                    if(!code.equalsIgnoreCase(row.getString("network_code"))) continue;
                    long start=JsonFields.integer(row,"event_time",1,4102444800L)*1000L;
                    timed.put(start,row);
                } catch(JSONException invalidRow) { /* Keep other valid programme boundaries. */ }
            }
            if(!found) throw new JSONException("节目单频道不匹配");
        }
        List<Programme> result=new ArrayList<>();
        for(Map.Entry<Long,JSONObject> entry:timed.entrySet()) {
            long start=entry.getKey();Long end=timed.higherKey(start);
            // TVB's broadcast day runs from 06:00. Adjacent starts give actual midnight boundaries.
            if(end==null || start>=until || end<=from) continue;
            try {
                JSONObject row=entry.getValue();String title=row.optString("programme_title","").trim();
                if(title.isEmpty()) title=row.optString("en_programme_title","").trim();
                title=JsonFields.text(new JSONObject().put("title",title),"title",300);
                String page=row.optInt("play_available",0)==1 ? officialPage(row.optString("mytv_super_url","")) : "";
                result.add(new Programme(id,title,start,end,"",page));
            } catch(JSONException invalidRow) { /* Bad labels do not erase the remaining schedule. */ }
        }
        return result;
    }
}
