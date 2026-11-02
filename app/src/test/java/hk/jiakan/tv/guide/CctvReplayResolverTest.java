package hk.jiakan.tv.guide;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class CctvReplayResolverTest {
    private final String id="db5c01aab2a1433fae88d45ca1c5c6fb";
    private JSONObject valid() throws Exception { return new JSONObject("{\"ack\":\"yes\",\"play\":\"1\",\"public\":\"1\",\"is_preview\":\"0\",\"is_invalid_copyright\":\"0\",\"is_protected\":\"0\",\"hls_url\":\"https://example.org/program.m3u8\"}"); }
    @Test public void publicReplayResolvesOnlyReturnedUrl() throws Exception { assertEquals("https://example.org/program.m3u8",new CctvReplayResolver().parse(valid().toString(),id).get(0).url); }
    @Test public void previewAndRestrictedMediaAreNotOffered() throws Exception {
        for(String flag:new String[]{"is_preview","is_invalid_copyright","is_protected"}) assertTrue(new CctvReplayResolver().parse(valid().put(flag,"1").toString(),id).isEmpty());
        assertTrue(new CctvReplayResolver().parse(valid().put("play","0").toString(),id).isEmpty());
    }
    @Test public void nonHttpsAndEmbeddedCredentialsAreRejected() throws Exception {
        for(String url:new String[]{"http://example.org/a.m3u8","https://user:secret@example.org/a.m3u8","file:///tmp/a"}) assertTrue(new CctvReplayResolver().parse(valid().put("hls_url",url).toString(),id).isEmpty());
    }
}
