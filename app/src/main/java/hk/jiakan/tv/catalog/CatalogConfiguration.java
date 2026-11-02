package hk.jiakan.tv.catalog;

import android.content.Context;
import android.content.SharedPreferences;
import hk.jiakan.tv.net.SafeHttp;
import java.io.*;
import org.json.JSONObject;

/** Optional private build input. Public builds have no configuration asset. */
public final class CatalogConfiguration {
    private CatalogConfiguration() {}
    public static String bundledUrl(Context context) {
        try(InputStream in=context.getAssets().open("catalog-config.json")) {
            ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[1024]; int n;
            while((n=in.read(buf))!=-1) { if(out.size()+n>8192) return ""; out.write(buf,0,n); }
            String url=new JSONObject(out.toString("UTF-8")).getString("catalogUrl");
            SafeHttp.validate(url,true); return url;
        } catch(Exception e) { return ""; }
    }
    public static void initialize(Context context,SharedPreferences prefs) {
        String bundled=bundledUrl(context);
        if(!bundled.isEmpty() && !prefs.getBoolean("splitUpdateMigrated",false)) {
            SharedPreferences.Editor edit=prefs.edit().putBoolean("splitUpdateMigrated",true);
            String legacy=bundled.replace("/channels.json?", "/update.json?");
            if(!legacy.equals(bundled) && legacy.equals(prefs.getString("updateUrl",""))) edit.remove("updateUrl");
            edit.apply();
        }
        if(!prefs.contains("catalogUrl")) {
            String url=bundled;
            if(!url.isEmpty()) prefs.edit().putString("catalogUrl",url).apply();
        }
    }
}
