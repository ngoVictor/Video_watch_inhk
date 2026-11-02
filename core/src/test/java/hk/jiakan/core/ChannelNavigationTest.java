package hk.jiakan.core;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChannelNavigationTest {
    private Channel c(String id,String group,boolean playable) {
        return new Channel(id,id,group,"",playable ? Collections.singletonList(new Channel.Source(id,"https://example.org/live","","",80)) : Collections.emptyList());
    }
    @Test public void everyGroupWrapsAndPreviewCannotChangePlayingScope() {
        for(String group:Arrays.asList("央视","卫视","香港","国际")) {
            Channel a=c("a",group,true),b=c("b",group,true);
            List<Channel> all=Arrays.asList(a,c("missing",group,false),b,c("other","outside",true));
            ChannelNavigation n=new ChannelNavigation();n.restore(a,"");n.browse("outside");
            assertEquals("a",n.next(all,"b",1,Collections.emptyList()).id);
            assertEquals("b",n.next(all,"a",-1,Collections.emptyList()).id);
            assertEquals(group,n.playingGroup());
        }
    }
    @Test public void favoritesStartOnlyAfterConfirmationAndKeepTheirOrder() {
        Channel a=c("a","央视",true),b=c("b","香港",true);
        List<Channel> all=Arrays.asList(a,b);List<String> favorites=Arrays.asList("b","a");
        ChannelNavigation n=new ChannelNavigation();n.restore(a,"");n.browse(ChannelSelection.FAVORITES);
        assertEquals("a",n.next(all,"a",1,favorites).id);
        n.confirm(a,favorites);assertEquals("b",n.next(all,"a",1,favorites).id);
        ChannelNavigation restored=new ChannelNavigation();restored.restore(a,n.playingGroup());
        assertEquals(ChannelSelection.FAVORITES,restored.playingGroup());
        assertNull(n.next(all,"a",1,Collections.emptyList()));
    }
    @Test public void selectionMemoryAndLegacyGroupUseStableIdentity() {
        Channel a=c("local","地方",true);ChannelNavigation n=new ChannelNavigation();n.restore(a,"invalid");
        assertEquals("卫视",n.playingGroup());n.remember("local");n.browse("香港");n.remember("jade");
        n.showPlaying();assertEquals("local",n.focusedId("other"));
        assertNull(n.next(Collections.emptyList(),"local",1,Collections.emptyList()));
        assertEquals("local",n.next(Collections.singletonList(a),"gone",-1,Collections.emptyList()).id);
    }
    @Test public void catalogRegroupingUpdatesNormalScopeButKeepsFavoriteScope() {
        ChannelNavigation n=new ChannelNavigation();Channel before=c("a","央视",true),after=c("a","香港",true);
        n.restore(before,"");n.reconcile(after);assertEquals("香港",n.playingGroup());assertEquals("香港",n.browsingGroup());
        n.restore(before,ChannelSelection.FAVORITES);n.reconcile(after);assertEquals(ChannelSelection.FAVORITES,n.playingGroup());
    }
}
