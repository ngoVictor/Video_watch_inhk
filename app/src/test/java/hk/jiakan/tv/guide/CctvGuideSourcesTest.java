package hk.jiakan.tv.guide;

import hk.jiakan.core.FutureFeatures.Programme;
import hk.jiakan.tv.net.SafeHttp;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class CctvGuideSourcesTest {
    private final long from=OffsetDateTime.parse("2026-09-11T00:00:00+08:00").toInstant().toEpochMilli();
    private final long until=from+86400000L;
    private String official() throws JSONException {
        return new JSONObject().put("cctv1",new JSONObject().put("program",new JSONArray().put(new JSONObject()
                .put("t","官方节目").put("st",from/1000).put("et",until/1000).put("vid","1234567890abcdef1234567890abcdef")))).toString();
    }
    private String web() throws JSONException {
        return new JSONObject().put("data",new JSONObject().put("cctv1",new JSONObject().put("list",new JSONArray().put(new JSONObject()
                .put("title","官网节目").put("startTime",from/1000).put("endTime",until/1000).put("column_url","https://tv.cctv.com/"))))).toString();
    }
    private JSONObject backup() throws JSONException {
        JSONArray rows=new JSONArray();
        for(String[] pair:new String[][]{{"2026-09-10T15:30:00+00:00","跨午夜"},{"2026-09-11T01:00:00+08:00","凌晨"},
                {"2026-09-11T00:00:00+00:00","上午"},{"2026-09-11T16:00:00+00:00","次日"}})
            rows.put(new JSONObject().put("start_date",pair[0]).put("title",pair[1]));
        return new JSONObject().put("start_date","2026-09-10").put("error_message","")
                .put("source_url","https://epg.pw/api/epg.json?channel_id=545932&date=20260910&timezone=None").put("epg_list",rows);
    }
    private String load(GuideLoader loader,SafeHttp.Request request) throws Exception { return new CctvGuideSources(loader).load("CCTV1.cn",from,until,request); }
    @Test public void officialSuccessPreservesReplayAndSkipsBackup() throws Exception {
        String officialFixture=official(),webFixture=web(),backupFixture=backup().toString();
        AtomicInteger calls=new AtomicInteger();String raw=load((u,r)->{calls.incrementAndGet();return officialFixture;},new SafeHttp.Request());
        assertEquals(1,calls.get());assertEquals("央视官方节目单",CctvGuideDocuments.source(raw));
        assertEquals(32,new CctvGuideParser().parse(raw,"CCTV1.cn",from,until).get(0).replayId.length());
    }
    @Test public void transportFailureUsesWebsiteWithoutInventingReplay() throws Exception {
        String officialFixture=official(),webFixture=web(),backupFixture=backup().toString();
        AtomicInteger calls=new AtomicInteger();String raw=load((u,r)->{calls.incrementAndGet();if(u.contains("/epginfo?"))throw new javax.net.ssl.SSLHandshakeException("fixture");return webFixture;},new SafeHttp.Request());
        assertEquals(2,calls.get());assertTrue(CctvGuideDocuments.source(raw).contains("官网"));
        assertEquals("",new CctvGuideParser().parse(raw,"CCTV1.cn",from,until).get(0).replayId);
    }
    @Test public void EntireOfficialHostFailureUsesIndependentDomain() throws Exception {
        String officialFixture=official(),webFixture=web(),backupFixture=backup().toString();
        AtomicInteger calls=new AtomicInteger();String raw=load((u,r)->{calls.incrementAndGet();if(u.contains("api.cntv.cn"))throw new java.net.UnknownHostException();
            assertTrue(u.contains("date=20260910&channel_id=545932"));return backupFixture;},new SafeHttp.Request());
        assertEquals(3,calls.get());assertTrue(CctvGuideDocuments.source(raw).contains("EPG.pw"));
        assertEquals(3,new CctvGuideParser().parse(raw,"CCTV1.cn",from,until).size());
    }
    @Test public void badOfficialStructureAndEmptyWebsiteUseBackup() throws Exception {
        String officialFixture=official(),webFixture=web(),backupFixture=backup().toString();
        String raw=load((u,r)->u.contains("/epginfo?") ? "{}" : u.contains("api.cntv.cn") ? "{\"data\":{\"cctv1\":{\"list\":[]}}}" : backupFixture,new SafeHttp.Request());
        assertTrue(CctvGuideDocuments.source(raw).contains("EPG.pw"));
    }
    @Test public void legitimateEmptyDayRemainsEmptyWhenBackupsFail() throws Exception {
        String raw=load((u,r)->{if(u.contains("/epginfo?"))return "{\"cctv1\":{\"program\":[]}}";throw new IOException();},new SafeHttp.Request());
        assertTrue(new CctvGuideParser().parse(raw,"CCTV1.cn",from,until).isEmpty());
    }
    @Test public void allFailuresAreBoundedAndDoNotLeakErrorMessages() throws Exception {
        AtomicInteger calls=new AtomicInteger();try {load((u,r)->{calls.incrementAndGet();throw new IOException("secret://sensitive");},new SafeHttp.Request());fail();}
        catch(IOException e){assertEquals(3,calls.get());assertFalse(e.getMessage().contains("sensitive"));assertTrue(e.getMessage().contains("IOException"));}
    }
    @Test public void cancellationStopsFallbackEvenWhenLoaderReturns() throws Exception {
        String officialFixture=official(),webFixture=web(),backupFixture=backup().toString();
        AtomicInteger calls=new AtomicInteger();SafeHttp.Request request=new SafeHttp.Request();
        try {load((u,r)->{calls.incrementAndGet();r.cancel();return officialFixture;},request);fail();}catch(InterruptedIOException expected){assertEquals(1,calls.get());}
    }
    @Test public void backupUsesOffsetsAndDoesNotInventLastDuration() throws Exception {
        String raw=CctvGuideDocuments.backup(backup().toString(),"CCTV1.cn","20260910",from,until);
        List<Programme> rows=new CctvGuideParser().parse(raw,"CCTV1.cn",from,until);
        assertEquals(3,rows.size());assertEquals(from-1800000L,rows.get(0).startUtcMillis);
        assertEquals(from+3600000L,rows.get(0).endUtcMillis);assertEquals(until,rows.get(2).endUtcMillis);
        assertTrue(new CctvGuideParser().parse(raw,"CCTV1.cn",until,until+86400000).isEmpty());
    }
    @Test public void wrongChannelAndWrongDateAreRejected() throws Exception {
        for(JSONObject bad:Arrays.asList(backup().put("start_date","2026-09-09"),backup().put("source_url","https://epg.pw/api/epg.json?channel_id=545935&date=20260910"))) {
            try {CctvGuideDocuments.backup(bad.toString(),"CCTV1.cn","20260910",from,until);fail();}catch(JSONException expected){}
        }
    }
    @Test public void malformedTitleDoesNotStretchPreviousProgramme() throws Exception {
        JSONObject raw=backup();raw.getJSONArray("epg_list").getJSONObject(1).put("title","");
        List<Programme> rows=new CctvGuideParser().parse(CctvGuideDocuments.backup(raw.toString(),"CCTV1.cn","20260910",from,until),"CCTV1.cn",from,until);
        assertEquals(2,rows.size());assertEquals(from+3600000,rows.get(0).endUtcMillis);
    }
    @Test public void twoDaysOfShortProgrammesAreFilteredBeforeDailyLimit() throws Exception {
        JSONObject document=backup();JSONArray input=new JSONArray();
        for(int i=0;i<577;i++) input.put(new JSONObject().put("title","短节目")
                .put("start_date",java.time.Instant.ofEpochMilli(from-86400000L+i*300000L).toString()));
        document.put("epg_list",input);
        String raw=CctvGuideDocuments.backup(document.toString(),"CCTV1.cn","20260910",from,until);
        assertEquals(288,new CctvGuideParser().parse(raw,"CCTV1.cn",from,until).size());
        while(input.length()<=1000)input.put(input.get(0));
        try {CctvGuideDocuments.backup(document.toString(),"CCTV1.cn","20260910",from,until);fail();}catch(JSONException expected){}
    }
    @Test public void sourceLabelsSurviveSerializationAndOldCacheIsCompatible() throws Exception {
        String officialFixture=official(),webFixture=web(),backupFixture=backup().toString();
        assertEquals("央视官方节目单",CctvGuideDocuments.source(official()));
        String raw=load((u,r)->{if(u.contains("api.cntv.cn"))throw new IOException();return backupFixture;},new SafeHttp.Request());
        assertTrue(CctvGuideDocuments.source(new JSONObject(raw).toString()).contains("EPG.pw"));
        try {CctvGuideDocuments.source(new JSONObject(raw).put("_guideSource","arbitrary").toString());fail();}catch(JSONException expected){}
    }
    @Test public void numberedCctvChannelsHaveDistinctMappingsAndUnmappedServicesStayUnsupported() throws Exception {
        Set<String> official=new HashSet<>(),backup=new HashSet<>();int count=0,unmapped=0;
        Set<String> expected=new HashSet<>();
        for(int number=1;number<=17;number++)expected.add("CCTV"+number+".cn");
        expected.addAll(Arrays.asList("CCTV5Plus.cn","CCTV4K.cn","CCTV8K.cn","CCTV4Europe.cn"));
        JSONArray channels=new JSONArray();
        for(String id:expected) channels.put(new JSONObject().put("id",id).put("group","央视"));
        channels.put(new JSONObject().put("id","unmapped-service").put("group","央视"));
        for(int i=0;i<channels.length();i++) {JSONObject c=channels.getJSONObject(i);if(!"央视".equals(c.getString("group")))continue;
            String id=c.getString("id");
            if(!expected.contains(id)) {
                assertEquals("",CctvChannels.code(id));assertEquals("",CctvChannels.backupId(id));unmapped++;continue;
            }
            assertFalse(CctvChannels.code(id).isEmpty());assertFalse(CctvChannels.backupId(id).isEmpty());
            assertTrue(official.add(CctvChannels.code(id)));assertTrue(backup.add(CctvChannels.backupId(id)));count++;
        }
        assertEquals(expected.size(),count);assertTrue(unmapped>=1);assertEquals("",CctvChannels.backupId("tvb-jade"));
    }
}
