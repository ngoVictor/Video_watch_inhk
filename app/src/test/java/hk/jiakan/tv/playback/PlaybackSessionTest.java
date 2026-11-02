package hk.jiakan.tv.playback;
import hk.jiakan.core.Channel;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class PlaybackSessionTest {
    private Channel channel(String id,String url){return new Channel(id,id,"央视","",url.isEmpty()?Collections.emptyList():Collections.singletonList(new Channel.Source(id,url,"","",80)));}
    @Test public void firstDownloadedCatalogSelectsAfterEmptyBootstrap(){
        PlaybackSession s=new PlaybackSession();s.catalog(Collections.emptyList(),"");
        assertEquals(PlaybackSession.Kind.MISSING,s.resume(true,false,false).kind);
        s.catalog(Collections.singletonList(channel("a","new")),"");
        assertEquals("a",s.resume(true,false,false).channel.id);
    }
    @Test public void initialBackgroundLoadPreservesSelectionAndConsumesOnce(){
        PlaybackSession s=new PlaybackSession();s.catalog(Collections.singletonList(channel("a","old")),"a");
        assertEquals(PlaybackSession.Kind.NONE,s.resume(false,false,false).kind);
        assertEquals("a",s.resume(true,false,false).channel.id);
        assertEquals(PlaybackSession.Kind.NONE,s.resume(true,false,false).kind);
    }
    @Test public void initialMaintenanceAndDownloadBlockWithoutLosingIntent(){
        PlaybackSession s=new PlaybackSession();s.catalog(Collections.singletonList(channel("a","old")),"a");
        assertEquals(PlaybackSession.Kind.NONE,s.resume(true,true,false).kind);
        assertEquals(PlaybackSession.Kind.NONE,s.resume(true,false,true).kind);
        assertEquals("a",s.resume(true,false,false).channel.id);
    }
    @Test public void startBeforeCatalogDoesNotConsumePending(){
        PlaybackSession s=new PlaybackSession();assertEquals(PlaybackSession.Kind.NONE,s.resume(true,false,false).kind);
        s.catalog(Collections.singletonList(channel("a","old")),"a");assertEquals("a",s.resume(true,false,false).channel.id);
    }
    @Test public void catalogRefreshDoesNotInterruptButResumeUsesLatestObject(){
        PlaybackSession s=new PlaybackSession();s.catalog(Collections.singletonList(channel("a","old")),"a");s.resume(true,false,false);
        s.catalog(Collections.singletonList(channel("a","new")),"a");assertEquals(PlaybackSession.Kind.NONE,s.resume(true,false,false).kind);
        s.suspend();assertEquals("new",s.resume(true,false,false).channel.sources.get(0).url);
    }
    @Test public void removedChannelNeverPlaysReplacement(){
        PlaybackSession s=new PlaybackSession();s.catalog(Collections.singletonList(channel("a","old")),"a");s.resume(true,false,false);
        s.catalog(Collections.singletonList(channel("b","new")),"a");s.suspend();assertEquals(PlaybackSession.Kind.MISSING,s.resume(true,false,false).kind);
        assertEquals("a",s.selectedId());s.select("b");assertEquals("b",s.resume(true,false,false).channel.id);
    }
    @Test public void sameChannelWithoutSourcesKeepsItsIdentity(){
        PlaybackSession s=new PlaybackSession();s.catalog(Collections.singletonList(channel("a","old")),"a");s.resume(true,false,false);
        s.catalog(Collections.singletonList(channel("a","")),"a");s.suspend();assertTrue(s.resume(true,false,false).channel.sources.isEmpty());
    }
}
