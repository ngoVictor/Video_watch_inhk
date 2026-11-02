package hk.jiakan.tv.guide;

import hk.jiakan.core.Channel;
import hk.jiakan.core.FutureFeatures.*;
import hk.jiakan.tv.net.SafeHttp;
import java.util.*;
import org.json.*;

public final class CctvReplayResolver implements ReplayResolver {
    @Override public List<Channel.Source> resolve(Programme programme) throws Exception { return resolve(programme,new SafeHttp.Request()); }
    public List<Channel.Source> resolve(Programme programme,SafeHttp.Request request) throws Exception {
        request.check();
        if(CctvChannels.code(programme.channelId).isEmpty() || !programme.replayId.matches("[a-fA-F0-9]{32}")
                || programme.endUtcMillis>System.currentTimeMillis()) return Collections.emptyList();
        return parse(SafeHttp.text("https://vdn.apps.cntv.cn/api/getHttpVideoInfo.do?pid="+programme.replayId,256*1024,SafeHttp.NO_AUTH,request),programme.replayId);
    }
    public List<Channel.Source> parse(String json,String id) throws JSONException {
        if(json.length()>256*1024 || !id.matches("[a-fA-F0-9]{32}")) throw new JSONException("回看数据无效");
        JSONObject root=new JSONObject(json);
        if(!"yes".equals(root.optString("ack")) || !"1".equals(root.optString("play"))
                || !"1".equals(root.optString("public")) || !"0".equals(root.optString("is_preview"))
                || !"0".equals(root.optString("is_invalid_copyright")) || !"0".equals(root.optString("is_protected"))) return Collections.emptyList();
        String url=root.optString("hls_url","");
        if(url.length()>2048) return Collections.emptyList();
        try { SafeHttp.validate(url,true); } catch(IllegalArgumentException e) { return Collections.emptyList(); }
        return Collections.singletonList(new Channel.Source("replay-"+id,url,"cctv","application/x-mpegURL",80));
    }
}
