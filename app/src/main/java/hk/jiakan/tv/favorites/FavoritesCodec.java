package hk.jiakan.tv.favorites;

import java.util.*;
import org.json.*;

public final class FavoritesCodec {
    private FavoritesCodec() {}
    public static List<String> decode(String json) throws JSONException {
        if(json.length()>256*1024) throw new JSONException("收藏数据过大");
        JSONArray array=new JSONArray(json);
        if(array.length()>2000) throw new JSONException("收藏数量过多");
        LinkedHashSet<String> ids=new LinkedHashSet<>();
        for(int i=0;i<array.length();i++) {
            Object id=array.get(i);
            if(!(id instanceof String) || !((String)id).matches("[A-Za-z0-9._-]{1,100}")) throw new JSONException("收藏 ID 无效");
            ids.add((String)id);
        }
        return new ArrayList<>(ids);
    }
    public static String encode(List<String> ids) {
        try { return new JSONArray(decode(new JSONArray(ids).toString())).toString(); }
        catch(JSONException e) { throw new IllegalArgumentException("收藏数据无效",e); }
    }
}
