package hk.jiakan.tv.guide;
import hk.jiakan.core.FutureFeatures.Programme;
import org.json.JSONException;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class CctvGuideParserTest {
    private String row(String title,long start,long end,String vid) { return "{\"t\":\""+title+"\",\"st\":"+start+",\"et\":"+end+",\"vid\":\""+vid+"\"}"; }
    private String document(String rows) { return "{\"cctv5\":{\"program\":["+rows+"]}}"; }
    @Test public void sortsFiltersAndDoesNotGuessReplayIds() throws Exception {
        String a=row("后",200,300,""),b=row("前",100,200,"db5c01aab2a1433fae88d45ca1c5c6fb");
        List<Programme> rows=new CctvGuideParser().parse(document(a+","+b+","+b),"CCTV5.cn",150000,250000);
        assertEquals(2,rows.size()); assertEquals(100000,rows.get(0).startUtcMillis); assertEquals(32,rows.get(0).replayId.length()); assertEquals("",rows.get(1).replayId);
        assertTrue(new CctvGuideParser().parse(document(a),"CCTV5.cn",300000,400000).isEmpty());
    }
    @Test public void zeroAndInvertedRowsDoNotEraseValidProgrammes() throws Exception {
        List<Programme> rows=new CctvGuideParser().parse(document(row("坏",200,100,"")+","+row("零时长",200,200,"")+","+row("正常",300,400,"")),"CCTV5.cn",1,1000000);
        assertEquals(1,rows.size());assertEquals("正常",rows.get(0).title);
    }
    @Test(expected=JSONException.class) public void wrongRootIsNotAnEmptyGuide() throws Exception { new CctvGuideParser().parse("{}","CCTV5.cn",1,1000000); }
    @Test public void unknownChannelDoesNotReuseCctvGuide() throws Exception { assertTrue(new CctvGuideParser().parse(document(row("一",100,200,"")),"rthk31",1,1000000).isEmpty()); }
    @Test public void channelMappingDistinguishesSportsAndRegionalEditions() {
        assertEquals("cctv5plus",CctvChannels.code("CCTV5Plus.cn")); assertEquals("cctveurope",CctvChannels.code("CCTV4Europe.cn")); assertEquals("cctv4k",CctvChannels.code("CCTV4K.cn"));
        assertEquals("cctv8k",CctvChannels.code("CCTV8K.cn"));
        assertEquals("cctvjilu",CctvChannels.code("CCTV9.cn")); assertEquals("cctvchild",CctvChannels.code("CCTV14.cn"));
    }
}
