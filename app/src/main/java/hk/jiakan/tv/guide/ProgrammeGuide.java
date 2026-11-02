package hk.jiakan.tv.guide;

import android.content.Context;
import hk.jiakan.core.Channel;
import hk.jiakan.core.FutureFeatures.Programme;
import hk.jiakan.tv.net.SafeHttp;
import java.util.*;

/** Explicit provider routing; neither the UI nor core knows endpoint or replay ID formats. */
public final class ProgrammeGuide implements GuideService {
    private final List<GuideService> providers;
    public ProgrammeGuide(Context context) { this(new CctvProgramGuide(context),new TvbProgramGuide(context)); }
    public ProgrammeGuide(GuideService... providers) { this.providers=Arrays.asList(providers); }
    private GuideService provider(String id) { for(GuideService p:providers) if(p.supports(id)) return p;return null; }
    @Override public boolean supports(String id) { return provider(id)!=null; }
    @Override public Result load(String id,long from,long until,SafeHttp.Request request,boolean refresh) throws Exception {
        request.check();GuideService p=provider(id);
        return p==null ? new Result(Collections.emptyList(),false,"") : p.load(id,from,until,request,refresh);
    }
    @Override public List<Channel.Source> resolve(Programme programme,SafeHttp.Request request) throws Exception {
        request.check();GuideService p=provider(programme.channelId);
        return p==null ? Collections.emptyList() : p.resolve(programme,request);
    }
    @Override public String officialReplayPage(Programme programme) {
        GuideService p=provider(programme.channelId);return p==null ? "" : p.officialReplayPage(programme);
    }
}
