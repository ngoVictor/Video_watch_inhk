package hk.jiakan.tv.guide;

import hk.jiakan.core.Channel;
import hk.jiakan.core.FutureFeatures.Programme;
import hk.jiakan.tv.net.SafeHttp;
import java.util.*;

/** UI-facing contract. A programme page is deliberately not a playable media source. */
public interface GuideService {
    final class Result {
        public final List<Programme> programmes;
        public final boolean stale;
        public final String source;
        public Result(List<Programme> programmes,boolean stale,String source) {
            this.programmes=Collections.unmodifiableList(new ArrayList<>(programmes)); this.stale=stale; this.source=source;
        }
    }
    boolean supports(String channelId);
    Result load(String id,long from,long until,SafeHttp.Request request,boolean refresh) throws Exception;
    List<Channel.Source> resolve(Programme programme,SafeHttp.Request request) throws Exception;
    default String officialReplayPage(Programme programme) { return ""; }
}
