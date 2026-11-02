package hk.jiakan.tv.update;

import org.json.JSONException;
import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateManifestTest {
    private String sample() { return "{\"schemaVersion\":1,\"versionCode\":2,\"versionName\":\"0.2\",\"minSdk\":23,\"apkUrl\":\"https://example.com/app.apk\",\"sha256\":\""+String.join("",java.util.Collections.nCopies(64,"a"))+"\"}"; }
    @Test public void acceptsHttpsAndValidHash() throws Exception { assertEquals(2,new UpdateManifest(sample()).versionCode); }
    @Test(expected=JSONException.class) public void rejectsInsecureDownload() throws Exception { new UpdateManifest(sample().replace("https://","http://")); }
    @Test(expected=JSONException.class) public void rejectsMalformedHash() throws Exception { new UpdateManifest(sample().replace("aaaaaaaa","invalid!")); }
    @Test(expected=JSONException.class) public void rejectsFractionalVersion() throws Exception { new UpdateManifest(sample().replace("\"versionCode\":2","\"versionCode\":2.2")); }
}
