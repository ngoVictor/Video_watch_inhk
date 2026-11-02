package hk.jiakan.tv.guide;

import hk.jiakan.core.FutureFeatures.Programme;
import hk.jiakan.tv.data.JsonFields;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.*;
import org.json.*;

/** Source-specific validation; all cached documents retain the original CCTV row contract. */
final class CctvGuideDocuments {
    private CctvGuideDocuments() {}
    static JSONObject object(String raw) throws JSONException {
        if(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>CctvGuideParser.MAX_BYTES)
            throw new JSONException("节目单过大");
        return new JSONObject(raw);
    }
    static String web(String raw,String id) throws JSONException {
        String code=CctvChannels.code(id);
        JSONArray input=object(raw).getJSONObject("data").getJSONObject(code).getJSONArray("list");
        if(input.length()>500) throw new JSONException("节目单过大");
        JSONArray rows=new JSONArray();
        for(int i=0;i<input.length();i++) {
            try {
                JSONObject row=input.getJSONObject(i);
                rows.put(new JSONObject().put("t",JsonFields.text(row,"title",300))
                        .put("st",JsonFields.integer(row,"startTime",1,4102444800L))
                        .put("et",JsonFields.integer(row,"endTime",1,4102444800L)));
            } catch(JSONException ignored) { /* A malformed row cannot erase valid neighbours. */ }
        }
        return new JSONObject().put(code,new JSONObject().put("program",rows)).toString();
    }
    static String backup(String raw,String id,String queryDay,long from,long until) throws JSONException {
        JSONObject root=object(raw);String number=CctvChannels.backupId(id);
        String isoDay=queryDay.substring(0,4)+"-"+queryDay.substring(4,6)+"-"+queryDay.substring(6,8);
        if(number.isEmpty() || !isoDay.equals(root.optString("start_date")) || !root.optString("error_message","").isEmpty())
            throw new JSONException("备用节目单日期或状态无效");
        try {
            URI source=URI.create(root.getString("source_url"));
            Set<String> query=new HashSet<>(Arrays.asList(source.getRawQuery().split("&")));
            if(!"https".equals(source.getScheme()) || !"epg.pw".equals(source.getHost())
                    || !"/api/epg.json".equals(source.getPath()) || !query.contains("channel_id="+number)
                    || !query.contains("date="+queryDay)) throw new IllegalArgumentException();
        } catch(RuntimeException e) { throw new JSONException("备用节目单频道不匹配"); }
        JSONArray input=root.getJSONArray("epg_list");
        if(input.length()>1000) throw new JSONException("两日节目单过大");
        // Keep valid time boundaries even if a title is malformed, avoiding stretched neighbours.
        TreeMap<Long,String> starts=new TreeMap<>();
        for(int i=0;i<input.length();i++) {
            try {
                JSONObject row=input.getJSONObject(i);
                long start=OffsetDateTime.parse(row.getString("start_date")).toInstant().getEpochSecond();
                if(start<1 || start>4102444800L) continue;
                String title="";try { title=JsonFields.text(row,"title",300); } catch(JSONException ignored) {}
                starts.putIfAbsent(start,title);
            } catch(JSONException | java.time.DateTimeException ignored) { /* Invalid timestamps supply no boundary. */ }
        }
        JSONArray rows=new JSONArray();Map.Entry<Long,String> before=null;
        for(Map.Entry<Long,String> next:starts.entrySet()) {
            if(before!=null && !before.getValue().isEmpty() && next.getKey()-before.getKey()<=86400
                    && before.getKey()*1000L<until && next.getKey()*1000L>from)
                rows.put(new JSONObject().put("t",before.getValue()).put("st",before.getKey()).put("et",next.getKey()));
            before=next;
        }
        return new JSONObject().put(CctvChannels.code(id),new JSONObject().put("program",rows)).toString();
    }
    static String canonical(List<Programme> programmes,String id,String source) throws JSONException {
        JSONArray rows=new JSONArray();
        for(Programme p:programmes) rows.put(new JSONObject().put("t",p.title).put("st",p.startUtcMillis/1000)
                .put("et",p.endUtcMillis/1000).put("vid",p.replayId));
        String raw=new JSONObject().put("_guideSource",source)
                .put(CctvChannels.code(id),new JSONObject().put("program",rows)).toString();
        object(raw);return raw;
    }
    static String source(String raw) throws JSONException {
        switch(object(raw).optString("_guideSource","official")) {
            case "official":return "央视官方节目单";
            case "web":return "央视官网节目单 · 备用入口";
            case "epg-pw":return "EPG.pw 备用节目单 · 编排以电视台为准";
            default:throw new JSONException("节目单来源无效");
        }
    }
}
