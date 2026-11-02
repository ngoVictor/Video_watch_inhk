package hk.jiakan.tv.guide;

import android.content.Context;
import hk.jiakan.core.Channel;
import hk.jiakan.core.FutureFeatures.Programme;
import hk.jiakan.tv.net.SafeHttp;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import org.json.JSONObject;

public final class TvbProgramGuide implements GuideService {
    private final GuideCache cache;
    private final GuideLoader loader;
    private final TvbGuideParser parser=new TvbGuideParser();
    public TvbProgramGuide(Context context) { this(context,(url,r)->SafeHttp.text(url,TvbGuideParser.MAX_BYTES/2,SafeHttp.NO_AUTH,r)); }
    public TvbProgramGuide(Context context,GuideLoader loader) { cache=new GuideCache(new File(context.getCacheDir(),"guide"));this.loader=loader; }
    @Override public boolean supports(String id) { return !TvbGuideParser.code(id).isEmpty(); }
    @Override public Result load(String id,long from,long until,SafeHttp.Request request,boolean refresh) throws Exception {
        request.check();String code=TvbGuideParser.code(id);
        if(code.isEmpty()) return new Result(Collections.emptyList(),false,"");
        GuideWindow window=new GuideWindow(from,until);
        SimpleDateFormat format=new SimpleDateFormat("yyyyMMdd",Locale.ROOT);format.setTimeZone(GuideTime.ZONE);
        String day=format.format(new Date(window.dayStart)),previous=format.format(new Date(window.dayStart-86400000L));
        return window.filter(cache.load("tvb-"+code+"-"+day,TvbGuideParser.MAX_BYTES,"TVB 官方节目单 · 标有重温的节目可打开官方页面",request,refresh,
                ()->{
                    String before=loader.load(url(code,previous),request);request.check();
                    String current=loader.load(url(code,day),request);
                    return new JSONObject().put("previous",new JSONObject(before)).put("current",new JSONObject(current)).toString();
                },raw->parser.parse(raw,id,window.dayStart,window.dayEnd)));
    }
    private String url(String code,String day) { return "https://programme.tvb.com/api/schedule?input_date="+day+"&network_code="+code; }
    @Override public String officialReplayPage(Programme p) { return supports(p.channelId) ? TvbGuideParser.officialPage(p.officialReplayUrl) : ""; }
    @Override public List<Channel.Source> resolve(Programme p,SafeHttp.Request request) throws Exception {
        request.check();return Collections.emptyList(); // Official programme pages are not media playlists.
    }
}
