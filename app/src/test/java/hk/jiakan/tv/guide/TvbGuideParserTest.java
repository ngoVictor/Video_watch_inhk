package hk.jiakan.tv.guide;

import hk.jiakan.core.FutureFeatures.Programme;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TvbGuideParserTest {
    private JSONObject row(long at,String title) throws Exception {
        return new JSONObject().put("network_code","J").put("event_time",at).put("programme_title",title)
                .put("play_available",1).put("mytv_super_url","https://www.mytvsuper.com/tc/programme/show_123/节目 [粤]");
    }
    private JSONObject day(JSONObject... rows) throws Exception {
        return new JSONObject().put("code",0).put("data",new JSONObject().put("list",new JSONArray().put(
                new JSONObject().put("network_code","j").put("schedules",new JSONArray(Arrays.asList(rows))))));
    }
    @Test public void midnightUsesPreviousBroadcastDayAndDoesNotInventMedia() throws Exception {
        String raw=new JSONObject().put("previous",day(row(90,"跨午夜"),row(110,"凌晨")))
                .put("current",day(row(130,"早间"),row(200,"下一天"))).toString();
        List<Programme> rows=new TvbGuideParser().parse(raw,"tvb-jade",100000,180000);
        assertEquals(3,rows.size());assertEquals(90000,rows.get(0).startUtcMillis);assertEquals(110000,rows.get(0).endUtcMillis);
        assertEquals(200000,rows.get(2).endUtcMillis);assertEquals("",rows.get(0).replayId);
        assertEquals("https://www.mytvsuper.com/tc/programme/show_123/",rows.get(0).officialReplayUrl);
    }
    @Test public void malformedRowAndOtherNetworkNeverMislabelChannel() throws Exception {
        JSONObject alien=row(115,"wrong").put("network_code","P");
        String raw=new JSONObject().put("previous",day(row(90,"valid"),alien,row(120,"").put("en_programme_title","English")))
                .put("current",day(row(150,"last"))).toString();
        List<Programme> rows=new TvbGuideParser().parse(raw,"tvb-jade",100000,140000);
        assertEquals(2,rows.size());assertEquals("English",rows.get(1).title);
        assertEquals("",TvbGuideParser.code("CCTV1.cn"));assertEquals("P",TvbGuideParser.code("TVBPearl.hk"));
    }
    @Test public void restrictedOrUntrustedPagesAreNotReplayLinks() throws Exception {
        for(String url:Arrays.asList("http://www.mytvsuper.com/tc/programme/x/","https://www.mytvsuper.com.evil/tc/programme/x/","https://evil.com/x","https://www.mytvsuper.com/tc/live/81/")) assertEquals("",TvbGuideParser.officialPage(url));
        String raw=new JSONObject().put("previous",day(row(90,"no replay").put("play_available",0)))
                .put("current",day(row(150,"next"))).toString();
        assertEquals("",new TvbGuideParser().parse(raw,"tvb-jade",100000,140000).get(0).officialReplayUrl);
    }
    @Test(expected=JSONException.class) public void missingChannelIsAnError() throws Exception {
        new TvbGuideParser().parse(new JSONObject().put("previous",day(row(90,"x"))).put("current",day(row(150,"x"))).toString(),"TVBPearl.hk",100000,140000);
    }
}
