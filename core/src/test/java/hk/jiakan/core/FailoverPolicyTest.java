package hk.jiakan.core;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;
import static hk.jiakan.core.FailoverPolicy.Kind.*;

public class FailoverPolicyTest {
    private void stable(FailoverPolicy policy,long from) {
        for(int i=1;i<=31;i++) policy.sample(from+i*1000,true,true,false,i);
    }
    @Test public void returningToChannelPrefersLastStableSourceEvenAfterFailedFirstSourceCoolsDown() {
        FailoverPolicy p=new FailoverPolicy();Channel original=channel(2);
        Channel.Source second=original.sources.get(1);
        Channel a=new Channel("tv","电视","央视","",Arrays.asList(original.sources.get(0),
                new Channel.Source(second.id,second.url,second.upstream,second.mimeType,40)));
        p.tune(a,0,true);assertEquals("b",p.error(1000,true).source.id);stable(p,1000);
        assertEquals("b",p.preferred().id);
        Channel other=new Channel("other","Other","央视","",Collections.singletonList(new Channel.Source("other","https://other/live","","",90)));
        p.tune(other,33000,true);
        assertEquals("b",p.tune(a,180000,true).source.id);
        assertEquals("a",p.error(181000,true).source.id);
        assertNull(p.preferred());
    }
    @Test public void connectionOrReadyWithoutProgressIsNotAStablePreference() {
        FailoverPolicy p=new FailoverPolicy();p.tune(channel(2),0,true);
        assertNull(p.preferred());
        p.sample(1000,true,true,false,0);p.sample(7000,true,true,false,0);
        assertNull(p.preferred());
        p.tune(channel(2),8000,true);p.sample(9000,true,true,false,1);
        p.sample(10000,true,true,true,1);p.sample(11000,true,true,false,2);
        for(int i=12;i<=40;i++)p.sample(i*1000,true,true,false,i);
        assertNull(p.preferred());p.sample(41000,true,true,false,41);assertEquals("a",p.preferred().id);
    }
    @Test public void oldUrlHealthAndPreferenceCannotAttachToAReplacementWithTheSameId() {
        FailoverPolicy p=new FailoverPolicy();Channel old=channel(1);p.tune(old,0,true);stable(p,0);
        Channel changed=new Channel("tv","电视","央视","",Collections.singletonList(new Channel.Source("a","https://changed/live","one","",100)));
        assertEquals("https://changed/live",p.tune(changed,32000,true).source.url);assertNull(p.preferred());
        p.error(33000,true);
        assertEquals("https://a/live.m3u8",p.tune(old,34000,true).source.url);
    }
    @Test public void rememberedPreferenceIsBoundToItsChannelAndCurrentCatalog() {
        FailoverPolicy p=new FailoverPolicy();Channel a=channel(2);
        p.restorePreferred(a,a.sources.get(1),0);assertEquals("b",p.tune(a,1000,true).source.id);
        p.restorePreferred(a,new Channel.Source("foreign","https://foreign/live","","",100),2000);
        assertEquals("b",p.tune(a,3000,true).source.id);
        assertEquals("a",p.tune(channel(1),4000,true).source.id);assertNull(p.preferred());
        p.restorePreferred(a,a.sources.get(1),5000);
        assertEquals("a",p.tune(a,86_405_001L,true).source.id);
    }
    @Test public void stalePersistedHintCannotResurrectAFailedPreferenceOrBypassCooldown() {
        FailoverPolicy p=new FailoverPolicy();Channel c=channel(2);p.tune(c,0,true);p.error(1000,true);
        p.restorePreferred(c,c.sources.get(0),2000);
        assertEquals("b",p.tune(c,3000,true).source.id);assertNull(p.preferred());
    }
    @Test public void restoringAnOldPreferenceDoesNotRenewItsLifetime() {
        FailoverPolicy p=new FailoverPolicy();Channel c=channel(2);
        p.restorePreferred(c,c.sources.get(1),-23*3_600_000L);
        assertEquals("b",p.tune(c,0,true).source.id);
        assertEquals("a",p.tune(c,2*3_600_000L,true).source.id);
    }
    @Test public void smoothRecoversEarlierButModeChangesPreserveCooldown() {
        FailoverPolicy p=new FailoverPolicy(); p.setMode(PlaybackMode.SMOOTH); p.tune(channel(1),0,true);
        p.sample(1000,true,true,false,1000); p.sample(2000,true,true,true,1000);
        assertEquals(LOWER_QUALITY,p.sample(8000,true,true,true,1000).kind);
        assertEquals(WAIT_RETRY,p.error(9000,true).kind);
        p.setMode(PlaybackMode.QUALITY);
        assertEquals(24000,p.sample(10000,true,true,true,1000).retryAt);
    }
    @Test public void audioRecoveryDoesNotLowerVideoAndGetsFreshStartupWindow() {
        FailoverPolicy p=new FailoverPolicy(); p.tune(channel(2),0,true);
        p.sample(1000,true,true,false,1000); p.retryAudio(120000);
        assertFalse(p.isLowered());
        assertEquals(NONE,p.sample(121000,true,true,true,0).kind);
        assertEquals(SWITCH,p.error(122000,true).kind);
        assertFalse(p.isLowered());
    }
    @Test public void profileStorageAndBoundsAreConservative() {
        assertEquals(PlaybackMode.QUALITY,PlaybackMode.fromStored(null));
        assertEquals(PlaybackMode.QUALITY,PlaybackMode.fromStored("bad"));
        assertEquals(PlaybackMode.SMOOTH,PlaybackMode.fromStored("SMOOTH"));
        for(PlaybackMode mode:PlaybackMode.values()) {
            assertEquals(1080,mode.maxHeight(false)); assertEquals(720,mode.maxHeight(true));
            assertTrue(mode.maxBitrate(true)<mode.maxBitrate(false));
        }
    }
    private Channel channel(int count) {
        Channel.Source a = new Channel.Source("a", "https://a/live.m3u8", "one", "", 100);
        Channel.Source b = new Channel.Source("b", "https://b/live.m3u8", "two", "", 90);
        return new Channel("tv", "电视", "央视", "", count == 0 ? Collections.emptyList()
                : count == 1 ? Collections.singletonList(a) : Arrays.asList(a, b));
    }
    @Test public void hardErrorSwitchesAndAllFailedSourcesWait() {
        FailoverPolicy p = new FailoverPolicy();
        assertEquals("a", p.tune(channel(2), 0, true).source.id);
        assertEquals("b", p.error(1000, true).source.id);
        assertEquals(WAIT_RETRY, p.error(2000, true).kind);
        assertEquals(WAIT_RETRY, p.sample(15000, true, true, true, 0).kind);
        assertEquals("a", p.sample(16000, true, true, true, 0).source.id);
    }
    @Test public void offlineDoesNotPenalizeSources() {
        FailoverPolicy p = new FailoverPolicy(); p.tune(channel(2), 0, true);
        assertEquals(WAIT_NETWORK, p.error(1, false).kind);
        assertEquals(NONE, p.sample(999999, false, true, true, 0).kind);
        assertEquals("a", p.sample(1000000, true, true, true, 0).source.id);
    }
    @Test public void repeatedBufferingLowersThenSwitchesAfterGrace() {
        FailoverPolicy p = new FailoverPolicy(); p.tune(channel(2), 0, true);
        p.sample(1000, true, true, false, 1000);
        p.sample(2000, true, true, true, 1000); p.sample(3000, true, true, false, 2000);
        p.sample(4000, true, true, true, 2000); p.sample(5000, true, true, false, 3000);
        assertEquals(LOWER_QUALITY, p.sample(6000, true, true, true, 3000).kind);
        assertEquals(NONE, p.sample(20000, true, true, true, 3000).kind);
        assertEquals(SWITCH, p.sample(21000, true, true, true, 3000).kind);
    }
    @Test public void startupTimeoutAndSingleSourceRetryAreBounded() {
        FailoverPolicy p = new FailoverPolicy(); p.tune(channel(1), 0, true);
        p.sample(0, true, true, true, 0);
        assertEquals(WAIT_RETRY, p.sample(12000, true, true, true, 0).kind);
        assertEquals(WAIT_RETRY, p.sample(26000, true, true, true, 0).kind);
        assertEquals(SWITCH, p.sample(27000, true, true, true, 0).kind);
    }
    @Test public void pauseDoesNotTriggerStallAndResumeGetsFreshWindow() {
        FailoverPolicy p = new FailoverPolicy(); p.tune(channel(2), 0, true);
        assertEquals(NONE, p.sample(999999, true, false, false, 0).kind);
        assertEquals(NONE, p.sample(1000000, true, true, false, 0).kind);
        assertEquals(SWITCH, p.sample(1008000, true, true, false, 0).kind);
    }
    @Test public void stablePlaybackNeverSwitches() {
        FailoverPolicy p = new FailoverPolicy(); p.tune(channel(2), 0, true);
        for (int i=1; i<=3600; i++) assertEquals(NONE, p.sample(i*1000L, true, true, false, i*1000L).kind);
    }
    @Test public void emptyChannelCannotPlayDifferentChannel() {
        assertEquals(NO_SOURCE, new FailoverPolicy().tune(channel(0), 0, true).kind);
    }
    @Test public void sustainedBufferingRestoresQualityAfterTwoStableMinutes() {
        FailoverPolicy p = new FailoverPolicy(); p.tune(channel(1), 0, true);
        p.sample(1000,true,true,false,1000); p.sample(2000,true,true,true,1000);
        assertEquals(LOWER_QUALITY, p.sample(10000,true,true,true,1000).kind);
        p.sample(11000,true,true,false,2000);
        for (int i=12;i<131;i++) assertEquals(NONE,p.sample(i*1000L,true,true,false,i*1000L).kind);
        assertEquals(RESTORE_QUALITY,p.sample(131000,true,true,false,131000).kind);
    }
    @Test public void networkFlapsDoNotBypassFailedSourceCooldown() {
        FailoverPolicy p=new FailoverPolicy();p.tune(channel(1),0,true);
        assertEquals(16000,p.error(1000,true).retryAt);
        for(int now=2000;now<16000;now+=2000) {
            assertEquals(WAIT_NETWORK,p.sample(now,false,true,true,0).kind);
            assertEquals(WAIT_RETRY,p.sample(now+1000,true,true,true,0).kind);
        }
        assertEquals(SWITCH,p.sample(16000,true,true,true,0).kind);
    }
    @Test public void allFailedSourcesKeepEarliestRetryAcrossNetworkRecovery() {
        FailoverPolicy p=new FailoverPolicy();p.tune(channel(2),0,true);p.error(1000,true);p.error(2000,true);
        p.sample(3000,false,true,true,0);
        assertEquals(16000,p.sample(4000,true,true,true,0).retryAt);
        assertEquals("a",p.sample(16000,true,true,true,0).source.id);
    }
}
