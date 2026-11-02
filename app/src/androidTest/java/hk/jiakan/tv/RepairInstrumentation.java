package hk.jiakan.tv;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.net.Uri;
import android.widget.TextView;
import androidx.media3.common.*;
import androidx.media3.datasource.*;
import androidx.media3.exoplayer.ExoPlayer;
import hk.jiakan.core.*;
import hk.jiakan.tv.catalog.*;
import hk.jiakan.tv.favorites.*;
import hk.jiakan.tv.guide.*;
import hk.jiakan.tv.playback.*;
import hk.jiakan.tv.update.*;
import hk.jiakan.tv.net.SafeHttp;
import hk.jiakan.tv.ui.MainActivity;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.*;

/** Device checks against a local finite media fixture, never a household device. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class RepairInstrumentation extends Instrumentation {
    private MainActivity activity;
    private int checks;
    private boolean publicStreams;
    private String suite="";
    private final String base="http://10.0.2.2:18765";
    private interface Checked {void run() throws Exception;}
    private interface Condition {boolean ready() throws Exception;}
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);suite=arguments.getString("suite","");publicStreams="public".equals(suite);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            getTargetContext().getSharedPreferences("settings",0).edit().putString("catalogUrl","").putString("updateUrl","").putString("lastChannel","CCTV4K.cn").commit();
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(()->field(activity,"catalog")!=null,10000);
            if(publicStreams) testPublicStreams();
            else if("keyboard".equals(suite)) testMaintenanceKeyboard();
            else if("recovery".equals(suite)) {testPlaybackNotices();testStableSourceStorage();}
            else if("liveprogress".equals(suite)) testSlidingLiveProgress();
            else if("cctv".equals(suite)) checks+=CctvGuideChecks.run(getTargetContext());
            else if("navigation".equals(suite) || "guides".equals(suite)) checks+=new NavigationGuideChecks(this,activity).run("guides".equals(suite));
            else if("layout".equals(suite)) checks+=new NavigationGuideChecks(this,activity).layout();
            else if("consistency".equals(suite)) {checks+=GuideCacheChecks.run(getTargetContext());checks+=CatalogRepositoryChecks.run(getTargetContext());testPartialGuideCache();}
            else {testFavoritesAndCache();testPartialGuideCache();checks+=GuideCacheChecks.run(getTargetContext());checks+=CatalogRepositoryChecks.run(getTargetContext());testHlsHeaders();testLiveLifecycle();testDownloadGate();testReplay();testAudioAndModes();}
            result.putString("stream","\nPASS: "+checks+" device assertions; suite="+suite+" completed.\n");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            if(publicStreams && activity!=null) try { ui(()->savePublicEvents()); } catch(Exception ignored) {}
            StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));result.putString("stream",trace.toString());finish(Activity.RESULT_CANCELED,result);
        } finally {if(activity!=null)runOnMainSync(()->activity.finish());}
    }
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private void testPlaybackNotices()throws Exception {
        ui(()->{activity.connected();activity.notice("已恢复自动画质");
            check(((TextView)field(activity,"status")).isShown(),"recovery notice not shown");});
        await(()->!((TextView)field(activity,"status")).isShown(),7500);
        ui(()->check(!((TextView)field(activity,"status")).isShown(),"recovery notice did not expire without another READY"));
        ui(()->{activity.notice("旧恢复提示");activity.status("网络已断开");});
        Thread.sleep(6500);
        ui(()->{
            TextView view=(TextView)field(activity,"status");
            check(view.isShown() && "网络已断开".contentEquals(view.getText()),"old notice timer hid a new persistent error");
            activity.connected();check(!view.isShown(),"connected did not clear persistent state");
            activity.notice("旧频道恢复提示");
            callActivityOnPause(activity);callActivityOnStop(activity);
            check(!view.isShown(),"background retained playback notice");
            callActivityOnStart(activity);callActivityOnResume(activity);
        });
    }
    private void testStableSourceStorage()throws Exception {
        android.content.Context context=getTargetContext();context.getSharedPreferences("stable-sources",0).edit().clear().commit();
        Channel.Source a=new Channel.Source("a","https://example.org/a?token=secret","one","",100);
        Channel.Source b=new Channel.Source("b","https://example.org/b","two","",40);
        Channel channel=new Channel("test","测试","央视","",Arrays.asList(a,b));
        StableSourceStore store=new StableSourceStore(context);store.update(channel,b,100000,100000);
        FailoverPolicy restored=new FailoverPolicy();new StableSourceStore(context).restore(channel,restored,0,100001);
        check(restored.tune(channel,0,true).source.id.equals("b"),"process recreation lost stable source preference");
        String raw=context.getSharedPreferences("stable-sources",0).getString("test","");
        check(!raw.contains("https")&&!raw.contains("secret"),"stable source storage contains a media URL");
        restored.error(1000,true);store.update(channel,restored.preferred(),100002,100002);
        FailoverPolicy failed=new FailoverPolicy();new StableSourceStore(context).restore(channel,failed,0,100003);
        check(failed.tune(channel,0,true).source.id.equals("a"),"failed source preference survived removal");
        store.update(channel,b,100004,100004);
        Channel changed=new Channel("test","测试","央视","",Arrays.asList(a,new Channel.Source("b","https://example.org/replaced","two","",40)));
        FailoverPolicy replaced=new FailoverPolicy();store.restore(changed,replaced,0,100005);
        check(replaced.tune(changed,0,true).source.id.equals("a"),"old URL preference followed a replacement source");
        for(int i=0;i<140;i++) {
            Channel item=new Channel("c"+i,"测试","央视","",Collections.singletonList(a));store.update(item,a,200000+i,200000+i);
        }
        check(context.getSharedPreferences("stable-sources",0).getAll().size()==128,"stable source storage is not bounded");
        context.getSharedPreferences("stable-sources",0).edit().clear().commit();
    }
    private void testSlidingLiveProgress()throws Exception {
        Channel local=channel("sliding-fixture","/live.m3u8","application/x-mpegURL");
        ui(()->{catalog(local);invoke("tune",new Class<?>[]{Channel.class},local);});ready();
        await(()->{ExoPlayer p=(ExoPlayer)field(live(),"player");return p!=null&&p.getVideoDecoderCounters()!=null&&p.getVideoDecoderCounters().renderedOutputBufferCount>25;},12000);
        AtomicReference<ExoPlayer> original=new AtomicReference<>();AtomicReference<Long> previous=new AtomicReference<>(-1L);
        java.util.concurrent.atomic.AtomicInteger rebases=new java.util.concurrent.atomic.AtomicInteger();
        ui(()->original.set((ExoPlayer)field(live(),"player")));
        for(int i=0;i<36;i++) {
            Thread.sleep(1000);
            ui(()->{
                ExoPlayer current=(ExoPlayer)field(live(),"player");
                check(current==original.get(),"healthy sliding live source was reconnected");
                long at=current.getCurrentPosition();if(previous.get()>at)rebases.incrementAndGet();previous.set(at);
            });
        }
        ui(()->{
            ExoPlayer current=(ExoPlayer)field(live(),"player");
            check(current.getVideoDecoderCounters().renderedOutputBufferCount>300,"video output did not advance");
            check(((FailoverPolicy)field(live(),"policy")).preferred()!=null,"rendered stable source was not remembered");
            check(rebases.get()>0,"fixture did not exercise live-window rebasing");
        });
    }
    private void testMaintenanceKeyboard()throws Exception {
        ui(()->invoke("showPanel",new Class<?>[0]));waitForIdleSync();
        ui(()->check(((android.view.View)field(activity,"panel")).hasFocus(),"channel panel must have focus"));
        for(int code:new int[]{android.view.KeyEvent.KEYCODE_M,android.view.KeyEvent.KEYCODE_F10,
                android.view.KeyEvent.KEYCODE_MENU,android.view.KeyEvent.KEYCODE_SETTINGS}) {
            ui(()->activity.onKeyDown(code,new android.view.KeyEvent(0,0,android.view.KeyEvent.ACTION_DOWN,code,1)));
            ui(()->check(!(Boolean)field(activity,"maintenance"),"repeat opened maintenance"));
            sendKeyDownUpSync(code);waitForIdleSync();
            await(()->field(activity,"maintenanceDialog")!=null,3000);
            AtomicReference<AlertDialog> opened=new AtomicReference<>();
            ui(()->{
                opened.set((AlertDialog)field(activity,"maintenanceDialog"));
                check(opened.get().isShowing(),"shortcut did not show maintenance");
                check((Boolean)field(activity,"maintenance"),"maintenance did not suspend the session");
                activity.onKeyDown(code,new android.view.KeyEvent(0,0,android.view.KeyEvent.ACTION_DOWN,code,1));
                check(field(activity,"maintenanceDialog")==opened.get(),"repeat recreated maintenance");
            });
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);waitForIdleSync();
            await(()->!(Boolean)field(activity,"maintenance"),3000);
            ui(()->check(field(activity,"maintenanceDialog")==null,"back left maintenance open"));
        }
        AtomicReference<AlertDialog> editor=new AtomicReference<>();
        AtomicReference<android.widget.EditText> input=new AtomicReference<>();
        ui(()->{
            android.widget.EditText field=new android.widget.EditText(activity);
            field.setSingleLine(true);input.set(field);
            editor.set(new AlertDialog.Builder(activity).setView(field).setNegativeButton("close",null).create());
            editor.get().show();field.requestFocus();
        });waitForIdleSync();
        try {
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_M);waitForIdleSync();
            ui(()->{
                check("m".equals(input.get().getText().toString()),"M was swallowed in an input field");
                check(editor.get().isShowing(),"shortcut replaced the input dialog");
                check(!(Boolean)field(activity,"maintenance"),"M in input opened maintenance");
            });
        } finally {ui(()->editor.get().dismiss());}
    }
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void set(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private void invoke(String name,Class<?>[] types,Object... args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}
    private void ui(Checked work)throws Exception{
        AtomicReference<Throwable> failure=new AtomicReference<>();runOnMainSync(()->{try{work.run();}catch(Throwable e){failure.set(e);}});
        if(failure.get()!=null)throw new Exception(failure.get());
    }
    private void await(Condition condition,long timeout)throws Exception{
        long until=System.nanoTime()+timeout*1000000;
        while(System.nanoTime()<until){AtomicReference<Boolean> ready=new AtomicReference<>(false);ui(()->ready.set(condition.ready()));if(ready.get())return;Thread.sleep(100);}
        throw new AssertionError("Condition timed out after "+timeout+" ms");
    }
    private Channel channel(String id,String path,String mime){return new Channel(id,id,"央视","",Collections.singletonList(new Channel.Source(id,base+path,"fixture",mime,80)));}
    private PlaybackController live()throws Exception{return (PlaybackController)field(activity,"playback");}
    private ReplayPlayer replay()throws Exception{return (ReplayPlayer)field(activity,"replay");}
    private PlaybackSession session()throws Exception{return (PlaybackSession)field(activity,"session");}
    private void catalog(Channel c)throws Exception{Catalog next=new Catalog(2026090802,Collections.singletonList(c));set(activity,"catalog",next);session().catalog(next.channels,c.id);}
    private void ready()throws Exception{await(()->{ExoPlayer p=(ExoPlayer)field(live(),"player");return p!=null && p.getPlaybackState()==Player.STATE_READY;},15000);}
    private void testFavoritesAndCache()throws Exception{
        long bundled=new CatalogRepository(getTargetContext()).load().revision;
        LocalFavoritesRepository favorites=new LocalFavoritesRepository(getTargetContext());favorites.saveOrder(Arrays.asList("CCTV1.cn","rthk31"));favorites.moveToTop("rthk31");
        check(new LocalFavoritesRepository(getTargetContext()).orderedChannelIds().equals(Arrays.asList("rthk31","CCTV1.cn")),"favorites did not persist in order");
        File cache=new File(getTargetContext().getFilesDir(),"channels.json");
        try(FileOutputStream out=new FileOutputStream(cache)){out.write("invalid".getBytes(StandardCharsets.UTF_8));}
        check(new CatalogRepository(getTargetContext()).load().revision==bundled,"bad cache must fall back to bundled catalog");
        cache.delete();
        String original;try(InputStream in=getTargetContext().getAssets().open("channels.json")){original=read(in);}
        JSONObject newer=new JSONObject(original).put("revision",bundled+1);
        try(FileOutputStream out=new FileOutputStream(cache)){out.write(newer.toString().getBytes(StandardCharsets.UTF_8));}
        check(new CatalogRepository(getTargetContext()).load().revision==bundled+1,"newer cache was not selected");
        android.util.AtomicFile atomic=new android.util.AtomicFile(cache);FileOutputStream incomplete=atomic.startWrite();incomplete.write("partial".getBytes(StandardCharsets.UTF_8));atomic.failWrite(incomplete);
        check(new CatalogRepository(getTargetContext()).load().revision==bundled+1,"failed atomic write replaced valid cache");
        try(FileOutputStream out=new FileOutputStream(cache)){out.write(newer.put("revision",1).toString().getBytes(StandardCharsets.UTF_8));}
        check(new CatalogRepository(getTargetContext()).load().revision==bundled,"old cache replaced bundled catalog");cache.delete();
        Calendar day=Calendar.getInstance(CctvProgramGuide.ZONE);day.set(Calendar.HOUR_OF_DAY,0);day.set(Calendar.MINUTE,0);day.set(Calendar.SECOND,0);day.set(Calendar.MILLISECOND,0);
        long from=day.getTimeInMillis();java.text.SimpleDateFormat date=new java.text.SimpleDateFormat("yyyyMMdd",Locale.ROOT);date.setTimeZone(CctvProgramGuide.ZONE);
        File guide=new File(getTargetContext().getCacheDir(),"guide");guide.mkdirs();File target=new File(guide,"cctv1-"+date.format(day.getTime())+".json");
        String data="{\"cctv1\":{\"program\":[{\"t\":\"fixture\",\"st\":"+(from/1000)+",\"et\":"+(from/1000+3600)+"}]}}";
        try(FileOutputStream out=new FileOutputStream(target)){out.write(data.getBytes(StandardCharsets.UTF_8));}
        check(new CctvProgramGuide(getTargetContext()).load("CCTV1.cn",from,from+86400000).programmes.size()==1,"fresh same-day guide cache failed");
        target.setLastModified(System.currentTimeMillis()-3*60*60*1000L);
        CctvProgramGuide unavailable=new CctvProgramGuide(getTargetContext(),(url,request)->{throw new IOException("fixture network failure");});
        check(unavailable.load("CCTV1.cn",from,from+86400000).stale,"same-day stale cache was not marked");
        boolean failed=false;try{unavailable.load("CCTV1.cn",from+86400000,from+2*86400000);}catch(IOException expected){failed=true;}check(failed,"another day incorrectly borrowed cached guide");
        try(FileOutputStream out=new FileOutputStream(target)){out.write("invalid".getBytes(StandardCharsets.UTF_8));}
        failed=false;try{unavailable.load("CCTV1.cn",from,from+86400000);}catch(Exception expected){failed=true;}check(failed,"corrupt guide cache became valid result");target.delete();
    }
    private void testPartialGuideCache()throws Exception{
        Calendar day=Calendar.getInstance(GuideTime.ZONE);day.set(2026,Calendar.SEPTEMBER,1,0,0,0);day.set(Calendar.MILLISECOND,0);
        long from=day.getTimeInMillis(),hour=3600000L;
        File target=new File(getTargetContext().getCacheDir(),"guide/cctv1-20260901.json");
        new android.util.AtomicFile(target).delete();
        java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        String raw=new JSONObject().put("cctv1",new JSONObject().put("program",new JSONArray()
                .put(new JSONObject().put("t","morning fixture").put("st",from/1000+3600).put("et",from/1000+7200))
                .put(new JSONObject().put("t","evening fixture").put("st",from/1000+72000).put("et",from/1000+75600)))).toString();
        CctvProgramGuide provider=new CctvProgramGuide(getTargetContext(),(url,r)->{calls.incrementAndGet();return raw;});
        try {
            GuideService.Result part=provider.load("CCTV1.cn",from,from+12*hour);
            check(part.programmes.size()==1,"partial guide result not filtered");
            GuideService.Result all=provider.load("CCTV1.cn",from,from+24*hour);
            check(all.programmes.size()==2,"partial query truncated full-day cache");
            check(calls.get()==1,"full-day cache was not reused");
            check(!all.stale && all.source.equals(part.source),"cache source or freshness changed");
            check(provider.load("CCTV1.cn",from+3*hour,from+4*hour).programmes.isEmpty(),"empty range returned unrelated programmes");
            check(calls.get()==1,"empty range needlessly fetched full day again");
            boolean rejected=false;try {provider.load("CCTV1.cn",from+23*hour,from+25*hour);}catch(IOException expected){rejected=true;}
            check(rejected && calls.get()==1,"cross-day range fetched or accepted");
            TvbProgramGuide tvb=new TvbProgramGuide(getTargetContext(),(url,r)->{throw new AssertionError("invalid range reached TVB network");});
            rejected=false;try {tvb.load("tvb-jade",from+23*hour,from+25*hour,new SafeHttp.Request(),false);}catch(IOException expected){rejected=true;}
            check(rejected,"TVB cross-day range accepted");
            target.setLastModified(System.currentTimeMillis()-3600000L);
            CctvProgramGuide offline=new CctvProgramGuide(getTargetContext(),(url,r)->{throw new IOException("fixture offline");});
            GuideService.Result evening=offline.load("CCTV1.cn",from+19*hour,from+22*hour);
            check(evening.stale && evening.programmes.size()==1 && evening.programmes.get(0).title.equals("evening fixture"),"stale full-day cache did not serve partial interval");
        } finally {new android.util.AtomicFile(target).delete();}
    }
    private void testHlsHeaders()throws Exception{
        int previous=new JSONArray(read(new URL(base+"/requests").openStream())).length();
        HlsDataSources factory=new HlsDataSources(new DefaultHttpDataSource.Factory());
        for(String path:new String[]{"/master","/playlist.m3u8","/playlist.m3u","/master?fixture=1","/redirect","/variant"}) {
            DataSource ds=factory.createDataSource(C.DATA_TYPE_MANIFEST);
            try{ds.open(new DataSpec(Uri.parse(base+path)));ds.read(new byte[32],0,32);}finally{ds.close();}
        }
        DataSource segment=factory.createDataSource(C.DATA_TYPE_MEDIA);
        try{segment.open(new DataSpec(Uri.parse(base+"/chunk000.ts")));segment.read(new byte[32],0,32);}finally{segment.close();}
        JSONArray rows=new JSONArray(read(new URL(base+"/requests").openStream()));int manifests=0,segments=0;
        for(int i=previous;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String path=row.getString("path");
            if(path.startsWith("/chunk")){segments++;check(!"no-cache".equals(row.optString("cache")),"segment cache changed");}
            else if(!path.equals("/requests")){manifests++;check("no-cache".equals(row.optString("cache")),"manifest missing no-cache: "+path);}
            check(row.optString("authorization").isEmpty(),"media request contained authentication");
        }
        check(manifests>=7 && segments>=1,"missing redirect or segment evidence");
    }
    private void testLiveLifecycle()throws Exception{
        Channel a=channel("fixture","/master","application/x-mpegURL");
        ui(()->{catalog(a);invoke("tune",new Class<?>[]{Channel.class},a);});ready();Thread.sleep(1200);
        ui(()->check(((ExoPlayer)field(live(),"player")).getCurrentPosition()>0,"HLS did not progress"));
        Channel fresh=channel("fixture","/master?new=1","application/x-mpegURL");
        ui(()->{callActivityOnPause(activity);callActivityOnStop(activity);catalog(fresh);check(field(live(),"player")==null,"background did not release live player");callActivityOnStart(activity);callActivityOnResume(activity);});ready();
        ui(()->check(live().channel().sources.get(0).url.equals(fresh.sources.get(0).url),"resume used stale source"));
        ui(()->{callActivityOnPause(activity);callActivityOnStop(activity);catalog(channel("other","/master","application/x-mpegURL"));callActivityOnStart(activity);callActivityOnResume(activity);check(field(live(),"player")==null,"removed channel played substitute");check(((TextView)field(activity,"status")).getText().toString().contains("不在目录"),"missing channel not explained");});
        for(String[] fixture:new String[][]{{"/sample.mpd","application/dash+xml"},{"/sample.mp4","video/mp4"}}){
            Channel c=channel("fixture",fixture[0],fixture[1]);ui(()->{catalog(c);invoke("tune",new Class<?>[]{Channel.class},c);});ready();ui(()->check(field(live(),"player")!=null,"non-HLS player missing"));
        }
    }
    private void testDownloadGate()throws Exception{
        DownloadCoordinator downloads=(DownloadCoordinator)field(activity,"downloads");ExecutorService worker=Executors.newSingleThreadExecutor();CountDownLatch release=new CountDownLatch(1),started=new CountDownLatch(1);AtomicReference<SafeHttp.Request> request=new AtomicReference<>();
        ui(()->{invoke("maintenance",new Class<?>[]{});request.set(downloads.start(worker,command->runOnMainSync(command),r->{try{started.countDown();release.await();r.check();return null;}finally{}},r->{}));set(activity,"download",request.get());});
        check(started.await(2,TimeUnit.SECONDS),"download fixture did not start");
        ui(()->((AlertDialog)field(activity,"maintenanceDialog")).getButton(AlertDialog.BUTTON_NEGATIVE).performClick());waitForIdleSync();
        ui(()->{check(!Boolean.TRUE.equals(field(activity,"maintenance")),"maintenance dialog did not close");check(field(live(),"player")==null,"playback resumed before download ended");check(downloads.busy(),"worker stopped before cleanup was released");});
        release.countDown();await(()->!downloads.busy(),3000);ui(()->invoke("restorePlayback",new Class<?>[]{}));ready();worker.shutdown();
    }
    private void testReplay()throws Exception{
        ui(()->{live().suspend();session().suspend();replay().start(new Channel.Source("replay-fixture",base+"/sample.mp4","fixture","video/mp4",80));});
        await(()->{ExoPlayer p=(ExoPlayer)field(replay(),"player");return p!=null&&p.getPlaybackState()==Player.STATE_READY;},10000);Thread.sleep(700);
        AtomicReference<Long> position=new AtomicReference<>();
        ui(()->{replay().togglePause();position.set(((ExoPlayer)field(replay(),"player")).getCurrentPosition());callActivityOnPause(activity);callActivityOnStop(activity);check(field(replay(),"player")==null,"background did not release replay");callActivityOnStart(activity);callActivityOnResume(activity);});
        await(()->{ExoPlayer p=(ExoPlayer)field(replay(),"player");return p!=null&&p.getPlaybackState()==Player.STATE_READY;},10000);
        ui(()->{ExoPlayer p=(ExoPlayer)field(replay(),"player");check(!p.getPlayWhenReady(),"paused replay resumed playing");check(Math.abs(p.getCurrentPosition()-position.get())<1500,"replay position lost");replay().seek(30000);replay().togglePause();});
        await(()->Boolean.TRUE.equals(field(replay(),"ended")),10000);
        ui(()->{callActivityOnPause(activity);callActivityOnStop(activity);callActivityOnStart(activity);callActivityOnResume(activity);check(field(replay(),"player")==null,"ended replay restarted");invoke("returnLive",new Class<?>[]{});});ready();
    }
    private Tracks videoTracks(int... heights) {
        Format[] formats=new Format[heights.length];int[] support=new int[heights.length];boolean[] selected=new boolean[heights.length];
        for(int i=0;i<heights.length;i++) {formats[i]=new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264).setWidth(heights[i]*16/9).setHeight(heights[i]).setAverageBitrate(heights[i]*7000).build();support[i]=C.FORMAT_HANDLED;}
        return new Tracks(com.google.common.collect.ImmutableList.of(new Tracks.Group(new TrackGroup(formats),true,support,selected)));
    }
    private void testPublicStreams()throws Exception {
        File evidence=new File(getTargetContext().getExternalFilesDir(null),"av-public");evidence.mkdirs();
        for(String id:new String[]{"CCTV5.cn","CCTV6.cn","CCTV8.cn"}) {
            ui(()->{
                Channel selected=null;for(Channel c:((Catalog)field(activity,"catalog")).channels)if(c.id.equals(id))selected=c;
                check(selected!=null&&!selected.sources.isEmpty(),id+" no configured source");
                invoke("tune",new Class<?>[]{Channel.class},selected);
            });ready();
            await(()->{ExoPlayer p=(ExoPlayer)field(live(),"player");return p!=null&&p.getAudioDecoderCounters()!=null&&p.getAudioDecoderCounters().renderedOutputBufferCount>10&&p.getVideoSize().height>=720;},15000);
            AtomicReference<Integer> frames=new AtomicReference<>();AtomicReference<Integer> buffers=new AtomicReference<>();AtomicReference<ExoPlayer> playing=new AtomicReference<>();
            ui(()->{ExoPlayer p=(ExoPlayer)field(live(),"player");playing.set(p);frames.set(p.getVideoDecoderCounters().renderedOutputBufferCount);buffers.set(p.getAudioDecoderCounters().renderedOutputBufferCount);});
            Thread.sleep(12000);
            ui(()->{
                ExoPlayer p=(ExoPlayer)field(live(),"player");
                check(p==playing.get(),id+" changed player during 12-second sample");
                int rendered=p.getVideoDecoderCounters().renderedOutputBufferCount-frames.get();
                String metrics="renderedFrames="+rendered+" audioBuffers="+(p.getAudioDecoderCounters().renderedOutputBufferCount-buffers.get())
                        +" droppedFramesTotal="+p.getVideoDecoderCounters().droppedBufferCount+" video="+p.getVideoSize().width+"x"+p.getVideoSize().height+" state="+p.getPlaybackState();
                try(FileOutputStream out=new FileOutputStream(new File(evidence,id+"-metrics.txt"))) {out.write(metrics.getBytes(StandardCharsets.UTF_8));}
                // A sliding HLS window rebases getCurrentPosition; decoded output counters do not.
                check(rendered>=200,id+" rendered only "+rendered+" video frames in 12 seconds");
                check(p.getAudioDecoderCounters().renderedOutputBufferCount>buffers.get()+50,id+" audio did not advance");
                check(p.getVideoSize().height>=720,id+" below HD");
            });
            android.graphics.Bitmap screenshot=getUiAutomation().takeScreenshot();
            try(FileOutputStream out=new FileOutputStream(new File(evidence,id+".png"))) {screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();
        }
        ui(()->savePublicEvents());
    }
    private void savePublicEvents()throws Exception {
        File evidence=new File(getTargetContext().getExternalFilesDir(null),"av-public");evidence.mkdirs();
        String events=((hk.jiakan.tv.diagnostics.EventLog)field(activity,"log")).snapshot();
        try(FileOutputStream out=new FileOutputStream(new File(evidence,"events.txt"))) {out.write(events.getBytes(StandardCharsets.UTF_8));}
    }
    private void testAudioAndModes()throws Exception {
        setInTouchMode(false);
        check(androidx.media3.decoder.ffmpeg.FfmpegLibrary.isAvailable(),"FFmpeg native audio library unavailable");
        check(androidx.media3.decoder.ffmpeg.FfmpegLibrary.supportsFormat(MimeTypes.AUDIO_MPEG_L2),"MP2 unsupported");
        ui(()->{
            Tracks ladder=videoTracks(360,480,720,1080);
            for(PlaybackMode mode:PlaybackMode.values()) for(boolean low:new boolean[]{false,true}) {
                TrackSelectionParameters p=PlaybackProfiles.parameters(TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT,ladder,mode,low);
                TrackSelectionOverride o=p.overrides.values().iterator().next();
                check(!o.trackIndices.contains(0)&&!o.trackIndices.contains(1)&&o.trackIndices.contains(2),"HD ladder admitted SD");
                check(!p.forceLowestBitrate&&!p.forceHighestSupportedBitrate,"mode disabled adaptive selection");
            }
            TrackSelectionParameters low=PlaybackProfiles.parameters(TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT,ladder,PlaybackMode.SMOOTH,true);
            check(low.overrides.values().iterator().next().trackIndices.equals(Arrays.asList(2)),"recovery violated 720p floor");
            TrackSelectionParameters normal=PlaybackProfiles.parameters(low,ladder,PlaybackMode.QUALITY,false);
            check(normal.overrides.values().iterator().next().trackIndices.equals(Arrays.asList(2,3)),"restore did not restore HD ladder");
            Tracks sd=videoTracks(360,576);
            check(PlaybackProfiles.parameters(low,sd,PlaybackMode.SMOOTH,true).overrides.isEmpty(),"SD-only channel blocked");
            check(PlaybackProfiles.qualityLabel(sd,new VideoSize(720,576)).contains("源为标清"),"SD label missing");
            check(PlaybackProfiles.qualityLabel(ladder,new VideoSize(720,576)).isEmpty(),"HD source mislabeled as SD-only");
            PcmAudioSink sink=new PcmAudioSink(new androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(getTargetContext()).build());
            check(!sink.supportsFormat(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).setChannelCount(6).setSampleRate(48000).build()),"compressed passthrough enabled");
            check(sink.supportsFormat(new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(C.ENCODING_PCM_16BIT).setChannelCount(2).setSampleRate(48000).build()),"PCM unsupported");
            sink.release();
            replay().clear(); live().tune(channel("mp2-hd","/hd-mp2.ts","video/mp2t"));
        });ready();
        await(()->{ExoPlayer p=(ExoPlayer)field(live(),"player");return p.getAudioDecoderCounters()!=null&&p.getAudioDecoderCounters().renderedOutputBufferCount>10&&p.getVideoSize().height==1080;},10000);
        ui(()->{
            ExoPlayer p=(ExoPlayer)field(live(),"player");
            check(p.getVideoSize().height==1080,"MP2 audio recovery lowered video");
            check(PlaybackProfiles.hasAudio(p.getCurrentTracks()),"MP2 audio not selected");
            PlaybackMode before=(PlaybackMode)field(activity,"mode");invoke("toggleMode",new Class<?>[]{});
            check(field(activity,"mode")==before.toggle(),"mode button did not toggle");
            check(getTargetContext().getSharedPreferences("settings",0).getString("playbackMode","").equals(before.toggle().name()),"mode not persisted");
            check(field(live(),"player")==p,"mode change restarted player");
            check(p.getVideoSize().height==1080,"mode transcoded single-rendition HD source");
            Channel.Source missing=new Channel.Source("missing-audio",base+"/hd-silent.ts","fixture","video/mp2t",100);
            Channel.Source backup=new Channel.Source("audible-backup",base+"/hd-mp2.ts","fixture","video/mp2t",90);
            live().tune(new Channel("audio-failover","audio-failover","央视","",Arrays.asList(missing,backup)));
        });
        await(()->{FailoverPolicy p=(FailoverPolicy)field(live(),"policy");return p.current()!=null&&p.current().id.equals("audible-backup");},15000);ready();
        await(()->{ExoPlayer p=(ExoPlayer)field(live(),"player");return p.getVideoSize().height==1080&&p.getAudioDecoderCounters()!=null&&p.getAudioDecoderCounters().renderedOutputBufferCount>10;},10000);
        ui(()->check(!((FailoverPolicy)field(live(),"policy")).isLowered(),"missing audio triggered quality downgrade"));
    }
    private String read(InputStream input)throws IOException{try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
}
