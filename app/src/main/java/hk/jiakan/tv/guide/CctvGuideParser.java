package hk.jiakan.tv.guide;

import hk.jiakan.core.FutureFeatures.Programme;
import hk.jiakan.tv.data.JsonFields;
import org.json.*;
import java.util.*;

public final class CctvGuideParser {
    public static final int MAX_BYTES=256*1024;
    public List<Programme> parse(String json,String channelId,long from,long until) throws JSONException {
        if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES || until<=from) throw new JSONException("节目单大小或范围无效");
        String code=CctvChannels.code(channelId);
        if(code.isEmpty()) return Collections.emptyList();
        JSONArray rows=new JSONObject(json).getJSONObject(code).getJSONArray("program");
        if(rows.length()>500) throw new JSONException("节目单过大");
        Map<Long,Programme> sorted=new TreeMap<>();
        for(int i=0;i<rows.length();i++) {
            try {
            JSONObject row=rows.getJSONObject(i);
            String title=JsonFields.text(row,"t",300);
            long start=JsonFields.integer(row,"st",1,4102444800L)*1000L;
            long end=JsonFields.integer(row,"et",1,4102444800L)*1000L;
            if(end<=start) throw new JSONException("节目时间无效");
            String vid=row.optString("vid","");
            if(!vid.matches("[a-fA-F0-9]{32}")) vid="";
            if(start<until && end>from) sorted.putIfAbsent(start,new Programme(channelId,title,start,end,vid));
            } catch(JSONException invalidRow) { /* One malformed upstream row must not erase the whole day. */ }
        }
        return new ArrayList<>(sorted.values());
    }
}
