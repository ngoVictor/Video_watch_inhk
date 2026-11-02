package hk.jiakan.tv.data;

import org.json.JSONObject;
import org.json.JSONException;

public final class JsonFields {
    private JsonFields() {}
    public static long integer(JSONObject o,String key,long min,long max) throws JSONException {
        Object n=o.get(key);
        if (!(n instanceof Integer || n instanceof Long)) throw new JSONException(key+" 必须为整数");
        long value=((Number)n).longValue();
        if(value<min || value>max) throw new JSONException(key+" 超出范围");
        return value;
    }
    public static String text(JSONObject o,String key,int max) throws JSONException {
        Object raw=o.get(key);
        if(!(raw instanceof String)) throw new JSONException(key+" 必须为文字");
        String s=((String)raw).trim();
        if(s.isEmpty() || s.length()>max) throw new JSONException(key+" 为空或过长");
        for(int i=0;i<s.length();i++) if(s.charAt(i)<32) throw new JSONException(key+" 含控制字符");
        return s;
    }
}
