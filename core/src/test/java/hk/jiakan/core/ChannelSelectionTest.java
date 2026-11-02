package hk.jiakan.core;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class ChannelSelectionTest {
    private Channel c(String id,String group,boolean playable) { return new Channel(id,id,group,"",playable ? Collections.singletonList(new Channel.Source(id,"https://example.org/live.m3u8","","",80)) : Collections.emptyList()); }
    @Test public void favoritesKeepOrderWhenCatalogChangesAndSkipMissingIds() {
        List<Channel> all=Arrays.asList(c("a","央视",true),c("b","香港",true));
        List<Channel> result=ChannelSelection.filter(all,ChannelSelection.FAVORITES,Arrays.asList("gone","b","a","b"));
        assertEquals(2,result.size()); assertEquals("b",result.get(0).id); assertEquals("a",result.get(1).id);
    }
    @Test public void favoriteStepNeverEscapesScopeAndSkipsUnavailable() {
        List<Channel> scope=Arrays.asList(c("a","央视",true),c("pending","央视",false),c("b","香港",true));
        assertEquals("b",ChannelSelection.next(scope,"a",-1).id); assertEquals("b",ChannelSelection.next(scope,"a",1).id);
        assertNull(ChannelSelection.next(Collections.singletonList(c("pending","央视",false)),"",1));
    }
    @Test public void legacyLocalChannelsAreInSatelliteGroup() {
        assertEquals(1,ChannelSelection.filter(Collections.singletonList(c("gz","地方",true)),"卫视",Collections.emptyList()).size());
    }
}
