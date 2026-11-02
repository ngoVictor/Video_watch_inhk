package hk.jiakan.tv.update;

import hk.jiakan.tv.data.JsonFields;
import hk.jiakan.tv.net.SafeHttp;
import org.json.JSONObject;
import org.json.JSONException;

public final class UpdateManifest {
    public final long versionCode;
    public final int minSdk;
    public final String versionName, apkUrl, sha256, notes;
    public UpdateManifest(String json) throws JSONException {
        JSONObject o=new JSONObject(json);
        JsonFields.integer(o,"schemaVersion",1,1);
        versionCode=JsonFields.integer(o,"versionCode",1,Integer.MAX_VALUE);
        minSdk=(int)JsonFields.integer(o,"minSdk",23,100);
        versionName=JsonFields.text(o,"versionName",80);
        apkUrl=JsonFields.text(o,"apkUrl",2048);
        try { SafeHttp.validate(apkUrl,true); } catch(IllegalArgumentException e) { throw new JSONException("APK 地址必须为 HTTPS"); }
        sha256=JsonFields.text(o,"sha256",64).toLowerCase(java.util.Locale.ROOT);
        if(!sha256.matches("[0-9a-f]{64}")) throw new JSONException("更新校验值无效");
        notes=o.optString("notes","");
        if(notes.length()>4000) throw new JSONException("更新说明过长");
    }
}
