package hk.jiakan.tv.guide;

import android.content.Context;
import hk.jiakan.core.Channel;
import hk.jiakan.core.FutureFeatures.*;
import hk.jiakan.tv.net.SafeHttp;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** CCTV adapter; cache policy and view state are owned elsewhere. */
public final class CctvProgramGuide implements ProgramGuideProvider, GuideService {
    public static final TimeZone ZONE=GuideTime.ZONE;
    public interface Loader extends GuideLoader {}
    private final GuideCache cache;
    private final CctvGuideSources sources;
    private final CctvGuideParser parser=new CctvGuideParser();
    public CctvProgramGuide(Context context) { this(context,(url,request) -> SafeHttp.text(url,CctvGuideParser.MAX_BYTES,SafeHttp.NO_AUTH,request,8000)); }
    public CctvProgramGuide(Context context,Loader loader) { cache=new GuideCache(new File(context.getCacheDir(),"guide"));sources=new CctvGuideSources(loader); }
    @Override public boolean supports(String id) { return !CctvChannels.code(id).isEmpty(); }
    @Override public List<Programme> query(String id,long from,long until) throws Exception { return load(id,from,until).programmes; }
    public Result load(String id,long from,long until) throws Exception { return load(id,from,until,new SafeHttp.Request(),false); }
    public Result load(String id,long from,long until,SafeHttp.Request request) throws Exception { return load(id,from,until,request,false); }
    @Override public Result load(String id,long from,long until,SafeHttp.Request request,boolean refresh) throws Exception {
        request.check();String code=CctvChannels.code(id);
        if(code.isEmpty()) return new Result(Collections.emptyList(),false,"");
        GuideWindow window=new GuideWindow(from,until);
        SimpleDateFormat date=new SimpleDateFormat("yyyyMMdd",Locale.ROOT);date.setTimeZone(ZONE);
        String day=date.format(new Date(window.dayStart));
        return window.filter(cache.load(code+"-"+day,CctvGuideParser.MAX_BYTES,CctvGuideDocuments::source,request,refresh,false,
                ()->{try {return sources.load(id,window.dayStart,window.dayEnd,request);}
                    catch(IOException e) {if(!request.isCancelled()) android.util.Log.w("CctvGuide",e.getMessage());throw e;}},
                raw->parser.parse(raw,id,window.dayStart,window.dayEnd)));
    }
    @Override public List<Channel.Source> resolve(Programme p,SafeHttp.Request request) throws Exception { return new CctvReplayResolver().resolve(p,request); }
}
