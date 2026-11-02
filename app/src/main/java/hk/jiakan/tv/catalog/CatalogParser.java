package hk.jiakan.tv.catalog;

import hk.jiakan.core.Channel;
import static hk.jiakan.tv.data.JsonFields.*;
import hk.jiakan.tv.net.SafeHttp;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CatalogParser {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public Catalog parse(String json) throws JSONException { return parse(json,false); }
    public Catalog parseBundled(String json) throws JSONException { return parse(json,true); }
    private Catalog parse(String json,boolean bundled) throws JSONException {
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) throw new JSONException("目录过大");
        JSONObject root = new JSONObject(json);
        if (integer(root,"schemaVersion",1,1) != 1) throw new JSONException("目录版本不兼容");
        long revision = integer(root,"revision",1,Long.MAX_VALUE);
        JSONArray input = root.getJSONArray("channels");
        if ((!bundled && input.length() == 0) || input.length() > 2000) throw new JSONException("频道数须为 1..2000");
        Set<String> ids = new HashSet<>(), sourceIds = new HashSet<>(), urls = new HashSet<>();
        List<Channel> channels = new ArrayList<>();
        for (int i=0; i<input.length(); i++) {
            JSONObject item = input.getJSONObject(i);
            String id = identifier(item,"id");
            if (!ids.add(id)) throw new JSONException("频道 ID 重复");
            String name = text(item,"name",80), group = text(item,"group",16);
            if (!Arrays.asList("央视","卫视","地方","香港","国际").contains(group)) throw new JSONException("未知分类");
            String region = item.has("region") ? text(item,"region",80) : "";
            JSONArray lines = item.getJSONArray("sources");
            if (lines.length()>20) throw new JSONException("单频道线路过多");
            List<Channel.Source> sources = new ArrayList<>();
            for (int j=0; j<lines.length(); j++) {
                JSONObject line = lines.getJSONObject(j);
                String sid = identifier(line,"id"), url = text(line,"url",2048);
                if (!sourceIds.add(sid) || !urls.add(url)) throw new JSONException("线路重复");
                try { SafeHttp.validate(url,false); } catch (IllegalArgumentException e) { throw new JSONException("线路地址无效"); }
                String mime = line.optString("mimeType", "");
                if (!Arrays.asList("","application/x-mpegURL","application/vnd.apple.mpegurl","application/dash+xml","video/mp2t","video/mp4").contains(mime)) throw new JSONException("不支持的媒体类型");
                String upstream = line.has("upstream") ? text(line,"upstream",120) : "";
                sources.add(new Channel.Source(sid,url,upstream,mime,(int)integer(line,"priority",0,100)));
            }
            channels.add(new Channel(id,name,group,region,sources));
        }
        return new Catalog(revision,channels);
    }
    private String identifier(JSONObject o,String key) throws JSONException {
        String id=text(o,key,100);
        if(!id.matches("[A-Za-z0-9._-]+")) throw new JSONException("ID 格式无效");
        return id;
    }
}
