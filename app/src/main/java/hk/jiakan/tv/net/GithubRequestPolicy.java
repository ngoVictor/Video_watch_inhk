package hk.jiakan.tv.net;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** A catalog credential is bound to one explicitly configured contents URI. */
public final class GithubRequestPolicy {
    public static final String CATALOG_URL="";
    public static final String UPDATE_URL="https://raw.githubusercontent.com/ngoVictor/Video_watch_inhk/main/publishing/update.json";
    private GithubRequestPolicy() {}
    public static boolean accepts(URI uri,String endpoint) {
        if(endpoint==null || endpoint.isEmpty() || !uri.toString().equals(endpoint)) return false;
        if(!"https".equals(uri.getScheme()) || !"api.github.com".equals(uri.getHost())
                || uri.getPort()!=-1 || uri.getUserInfo()!=null || uri.getFragment()!=null) return false;
        String path=uri.getRawPath();
        return path!=null && path.matches("/repos/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/contents/[A-Za-z0-9_./-]+")
                && !path.contains("/../") && !path.contains("/./") && !path.endsWith("/..") && !path.endsWith("/.");
    }
    public static Map<String,String> headers(URI uri,String endpoint,String token) {
        if(!accepts(uri,endpoint)) return Collections.emptyMap();
        Map<String,String> headers=new HashMap<>();
        headers.put("Accept","application/vnd.github.raw+json");
        headers.put("X-GitHub-Api-Version","2022-11-28");
        if(token!=null && !token.isEmpty()) {
            if(!token.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("频道凭据格式无效");
            headers.put("Authorization","Bearer "+token);
        }
        return headers;
    }
}
