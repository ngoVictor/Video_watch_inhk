package hk.jiakan.tv.catalog;

import org.junit.Test;
import org.json.JSONException;
import static org.junit.Assert.*;

public class CatalogParserTest {
    @Test public void publicBootstrapContainsNoChannels() throws Exception {
        String json=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/assets/channels.json")),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(new CatalogParser().parseBundled(json).channels.isEmpty());
    }
    private String channel(String id,String sources) { return "{\"id\":\""+id+"\",\"name\":\"央视\",\"group\":\"央视\",\"sources\":"+sources+"}"; }
    private String document(String channels) { return "{\"schemaVersion\":1,\"revision\":2,\"channels\":["+channels+"]}"; }
    private String source(String id,String url) { return "{\"id\":\""+id+"\",\"url\":\""+url+"\",\"priority\":80}"; }
    @Test public void acceptsUnavailableCandidateAndHttpMedia() throws Exception {
        Catalog c=new CatalogParser().parse(document(channel("cctv5","[]")+","+channel("other","["+source("s","http://media.example/live.m3u8")+"]")));
        assertEquals(2,c.channels.size()); assertTrue(c.channels.get(0).sources.isEmpty());
    }
    @Test(expected=JSONException.class) public void rejectsDuplicateChannels() throws Exception { new CatalogParser().parse(document(channel("tv","[]")+","+channel("tv","[]"))); }
    @Test(expected=JSONException.class) public void rejectsDuplicateUrls() throws Exception { new CatalogParser().parse(document(channel("tv","["+source("a","https://tv.example/a")+","+source("b","https://tv.example/a")+"]"))); }
    @Test(expected=JSONException.class) public void rejectsSameUrlAcrossDifferentChannels() throws Exception {
        new CatalogParser().parse(document(channel("one","["+source("a","https://tv.example/a")+"]")+","+channel("two","["+source("b","https://tv.example/a")+"]")));
    }
    @Test(expected=JSONException.class) public void rejectsSourceIdAcrossChannels() throws Exception {
        new CatalogParser().parse(document(channel("one","["+source("a","https://tv.example/a")+"]")+","+channel("two","["+source("a","https://tv.example/b")+"]")));
    }
    @Test public void preservesExactUrlQueriesAndLegacyGroup() throws Exception {
        String raw=document(channel("one","["+source("a","https://tv.example/a?channel=1")+"]")+","+channel("two","["+source("b","https://tv.example/a?channel=2")+"]"));
        Catalog c=new CatalogParser().parse(raw.replace("央视","地方"));
        assertEquals(2,c.channels.size());assertEquals("地方",c.channels.get(0).group);
    }
    @Test(expected=JSONException.class) public void rejectsCredentials() throws Exception { new CatalogParser().parse(document(channel("tv","["+source("a","https://user:secret@tv.example/a")+"]"))); }
    @Test(expected=JSONException.class) public void rejectsFileUrl() throws Exception { new CatalogParser().parse(document(channel("tv","["+source("a","file:///etc/passwd")+"]"))); }
    @Test(expected=JSONException.class) public void rejectsFractionalVersion() throws Exception { new CatalogParser().parse(document(channel("tv","[]")).replace("\"revision\":2","\"revision\":2.5")); }
    @Test(expected=JSONException.class) public void rejectsEmptyCatalog() throws Exception { new CatalogParser().parse(document("")); }
    @Test(expected=JSONException.class) public void rejectsUnknownSchema() throws Exception { new CatalogParser().parse(document(channel("tv","[]")).replace("\"schemaVersion\":1","\"schemaVersion\":2")); }
}
