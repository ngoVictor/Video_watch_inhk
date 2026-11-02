package hk.jiakan.tv.net;
import java.net.URI;
import org.junit.Test;
import static org.junit.Assert.*;
public class GithubRequestPolicyTest {
    private static final String ENDPOINT="https://api.github.com/repos/example/catalog/contents/channels.json?ref=main";
    @Test public void exactCatalogGetsRawAndCredential() {
        assertEquals("Bearer test_token",GithubRequestPolicy.headers(URI.create(ENDPOINT),ENDPOINT,"test_token").get("Authorization"));
        assertEquals("application/vnd.github.raw+json",GithubRequestPolicy.headers(URI.create(ENDPOINT),ENDPOINT,"").get("Accept"));
    }
    @Test public void credentialsStayOffUpdatesRedirectsOtherPathsAndQueries() {
        for(String url:new String[]{GithubRequestPolicy.UPDATE_URL,"https://example.org/live",ENDPOINT+"x",ENDPOINT.replace("channels.json","update.json"),ENDPOINT.replace("catalog/","other/"),ENDPOINT.replace("https:","http:")})
            assertTrue(url,GithubRequestPolicy.headers(URI.create(url),ENDPOINT,"test_token").isEmpty());
    }
    @Test public void invalidConfiguredTargetsNeverReceiveCredentials() {
        for(String url:new String[]{"https://api.github.com:444/repos/a/b/contents/c","https://api.github.com/repos/a/b/contents/../c","https://api.github.com/repos/a/b/contents/%2e%2e/c","https://api.github.com.evil.example/repos/a/b/contents/c","https://user@api.github.com/repos/a/b/contents/c","https://api.github.com/repos/a/b/releases/assets/123"})
            assertTrue(GithubRequestPolicy.headers(URI.create(url),url,"test_token").isEmpty());
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsHeaderInjection() {
        GithubRequestPolicy.headers(URI.create(ENDPOINT),ENDPOINT,"x\r\nInjected: y");
    }
}
