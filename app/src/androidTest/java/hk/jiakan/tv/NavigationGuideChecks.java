package hk.jiakan.tv;

import android.app.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import hk.jiakan.core.*;
import hk.jiakan.core.FutureFeatures.Programme;
import hk.jiakan.tv.catalog.Catalog;
import hk.jiakan.tv.favorites.LocalFavoritesRepository;
import hk.jiakan.tv.guide.*;
import hk.jiakan.tv.net.SafeHttp;
import hk.jiakan.tv.playback.*;
import hk.jiakan.tv.ui.MainActivity;
import hk.jiakan.tv.ui.GuidePresentation;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Bounded native navigation and guide checks, separate from media recovery fixtures. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
final class NavigationGuideChecks {
    interface Checked { void run() throws Exception; }
    interface Condition { boolean get() throws Exception; }
    private final Instrumentation runner;
    private final MainActivity activity;
    private int checks;
    NavigationGuideChecks(Instrumentation runner,MainActivity activity) { this.runner=runner;this.activity=activity; }
    int run(boolean publicData) throws Exception {
        if(publicData) publicGuides();else { navigation();cache();dialog();officialPage(); }
        return checks;
    }
    int layout() throws Exception {
        runner.setInTouchMode(false);
        long day=today();AtomicInteger calls=new AtomicInteger();AtomicBoolean failure=new AtomicBoolean(),sparse=new AtomicBoolean();
        AtomicReference<SafeHttp.Request> last=new AtomicReference<>();
        GuideService fixture=new GuideService() {
            @Override public boolean supports(String id){return true;}
            @Override public Result load(String id,long from,long until,SafeHttp.Request request,boolean refresh) throws Exception {
                calls.incrementAndGet();last.set(request);Thread.sleep(300);request.check();if(failure.get())throw new IOException("fixture network failure");
                if(from==day+2*GuidePresentation.DAY)return new Result(Collections.emptyList(),false,"合成节目单 · 尚未公布");
                List<Programme> rows=new ArrayList<>();
                rows.add(new Programme(id,"跨午夜节目",from-1800000,from+1800000));
                rows.add(new Programme(id,"清晨新闻",from+6*3600000L,from+7*3600000L));
                rows.add(new Programme(id,"上午节目",from+11*3600000L,from+12*3600000L));
                if(!sparse.get()) {
                    rows.add(new Programme(id,"特别节目：一起认识我们的城市与生活——长节目名称的两行排版和遥控阅读检查",from+12*3600000L,from+13*3600000L));
                    rows.add(new Programme(id,"晚间剧场",from+18*3600000L,from+20*3600000L,"vid"));
                    rows.add(new Programme(id,"跨至次日的节目",from+84600000L,from+88200000L,"","https://www.mytvsuper.com/tc/programme/show/"));
                }
                if(from==day && !sparse.get()) {long now=System.currentTimeMillis();rows.add(new Programme(id,"正在播出定位检查",now-60000,now+300000));rows.sort(Comparator.comparingLong(p->p.startUtcMillis));}
                return new Result(rows,false,"合成节目单");
            }
            @Override public List<Channel.Source> resolve(Programme p,SafeHttp.Request r){throw new AssertionError("upcoming programme resolved replay");}
        };
        ui(()->{set(activity,"guide",fixture);call("showGuide",new Class<?>[]{Channel.class},channel("tvb-jade","香港"));});
        await(()->programmes().size()==7,5000);
        screenshot("fullscreen-first-layout");
        await(()->((ListView)field(programmeDialog(),"list")).getSelectedItemPosition()>=0,3000);
        ui(()->{
            AlertDialog d=(AlertDialog)field(programmeDialog(),"dialog");
            check(d.getWindow().getDecorView().getWidth()==activity.getWindow().getDecorView().getWidth(),"guide is not full width");
            check(((ListView)field(programmeDialog(),"dates")).getCount()==10,"date range must include day after tomorrow");
            ListView rows=(ListView)field(programmeDialog(),"list");check(rows.hasFocus(),"initial focus not in programmes");
            check(GuidePresentation.live(programmes().get(rows.getSelectedItemPosition()),System.currentTimeMillis()),"initial focus is not current programme");
        });screenshot("fullscreen-today");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);ui(()->check(periodButtons().get(0).hasFocus(),"right key cannot reach time slots"));key(KeyEvent.KEYCODE_DPAD_DOWN);
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        ui(()->check(((ListView)field(programmeDialog(),"dates")).getSelectedItemPosition()==7,"left focus lost today"));
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        await(()->!Boolean.TRUE.equals(field(programmeDialog(),"loading")) && programmes().size()==6,5000);
        ui(()->{
            check(((TextView)field(programmeDialog(),"date")).getText().toString().contains("明天"),"tomorrow title unclear");
            check(((ListView)field(programmeDialog(),"dates")).hasFocus(),"loading stole date focus");
            for(Programme p:programmes()) {
                long now=System.currentTimeMillis();
                check(p.startUtcMillis>now ? GuidePresentation.state(p,now).contains("预告") : GuidePresentation.live(p,now),"future date mislabels a preview or crossing-midnight live programme");
            }
        });screenshot("fullscreen-tomorrow");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_UP);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_CENTER);
        ui(()->{check(field(programmeDialog(),"period")==GuidePresentation.Period.MORNING,"remote cannot choose morning");check(programmes().size()==2,"morning overlap filter incorrect");});
        screenshot("fullscreen-morning");key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_CENTER);
        ui(()->check(!replay().active(),"future preview started replay"));key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_DOWN);
        await(()->!Boolean.TRUE.equals(field(programmeDialog(),"loading")) && programmes().isEmpty(),5000);
        ui(()->{check(((TextView)field(programmeDialog(),"date")).getText().toString().contains("后天"),"day-after title missing");check(((TextView)field(programmeDialog(),"empty")).getText().toString().contains("这一天尚未"),"empty date confused with no broadcasts");});
        screenshot("fullscreen-unpublished");key(KeyEvent.KEYCODE_DPAD_RIGHT);
        ui(()->check(((Button)field(programmeDialog(),"retry")).hasFocus(),"empty date has no retry focus"));
        int before=calls.get();key(KeyEvent.KEYCODE_DPAD_CENTER);await(()->calls.get()>before,3000);
        ui(()->((Button)field(programmeDialog(),"today")).performClick());await(()->programmes().size()==7,5000);
        ui(()->{check(field(programmeDialog(),"period")==GuidePresentation.Period.ALL,"today did not reset all-day view");check(((TextView)field(programmeDialog(),"date")).getText().toString().contains("今天"),"today action failed");});
        ui(()->{periodButtons().get(3).performClick();});screenshot("fullscreen-long-title");
        ui(()->{periodButtons().get(4).performClick();});screenshot("fullscreen-cross-midnight");
        sparse.set(true);ui(()->((Button)field(programmeDialog(),"retry")).performClick());
        await(()->!Boolean.TRUE.equals(field(programmeDialog(),"loading")) && programmes().isEmpty(),5000);
        ui(()->check(((TextView)field(programmeDialog(),"empty")).getText().toString().contains("该时段"),"empty time slot confused with empty day"));
        failure.set(true);ui(()->((Button)field(programmeDialog(),"retry")).performClick());
        await(()->((TextView)field(programmeDialog(),"empty")).getText().toString().contains("加载失败"),5000);screenshot("fullscreen-failure");
        failure.set(false);sparse.set(false);ui(()->((Button)field(programmeDialog(),"today")).performClick());await(()->programmes().size()==7,5000);
        key(KeyEvent.KEYCODE_BACK);await(()->last.get().isCancelled(),3000);check(last.get().isCancelled(),"fullscreen close did not cancel request");
        return checks;
    }
    @SuppressWarnings("unchecked") private List<Button> periodButtons() throws Exception {return (List<Button>)field(programmeDialog(),"periods");}
    private Object field(Object owner,String name) throws Exception { Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner); }
    private void set(Object owner,String name,Object value) throws Exception { Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value); }
    private void call(String name,Class<?>[] types,Object... args) throws Exception { Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args); }
    private void ui(Checked work) throws Exception {
        AtomicReference<Throwable> error=new AtomicReference<>();runner.runOnMainSync(()->{try {work.run();}catch(Throwable e){error.set(e);}});
        if(error.get()!=null) throw new Exception(error.get());
    }
    private void await(Condition condition,long timeout) throws Exception {
        long end=System.nanoTime()+timeout*1000000;
        while(System.nanoTime()<end) { AtomicBoolean ready=new AtomicBoolean();ui(()->ready.set(condition.get()));if(ready.get())return;Thread.sleep(100); }
        throw new AssertionError("Navigation/guide condition timed out after "+timeout+" ms");
    }
    private void check(boolean condition,String message) { checks++;if(!condition)throw new AssertionError(message); }
    private PlaybackController live() throws Exception { return (PlaybackController)field(activity,"playback"); }
    private ReplayPlayer replay() throws Exception { return (ReplayPlayer)field(activity,"replay"); }
    private ChannelNavigation nav() throws Exception { return (ChannelNavigation)field(activity,"navigation"); }
    private ListView list(String name) throws Exception { return (ListView)field(activity,name); }
    private void key(int code) { runner.sendKeyDownUpSync(code);runner.waitForIdleSync(); }
    private Channel channel(String id,String group) { return new Channel(id,id,group,"",Collections.singletonList(new Channel.Source(id,"http://10.0.2.2:18765/hd-mp2.ts","fixture","video/mp2t",80))); }
    private void navigation() throws Exception {
        Channel a=channel("CCTV1.cn","央视"),b=channel("CCTV2.cn","央视"),c=channel("CCTV3.cn","央视"),jade=channel("tvb-jade","香港"),pearl=channel("TVBPearl.hk","香港");
        List<Channel> all=Arrays.asList(a,new Channel("missing","missing","央视","",Collections.emptyList()),b,c,channel("sat","卫视"),jade,pearl,channel("international","国际"));
        ui(()->{
            Catalog catalog=new Catalog(2026091001,all);set(activity,"catalog",catalog);
            ((PlaybackSession)field(activity,"session")).catalog(all,a.id);call("populate",new Class<?>[]{});
            call("tune",new Class<?>[]{Channel.class},c);call("showPanel",new Class<?>[]{});
        });
        await(()->list("channelList").hasFocus(),5000);
        AtomicReference<Object> player=new AtomicReference<>();ui(()->player.set(field(live(),"player")));
        key(KeyEvent.KEYCODE_DPAD_LEFT);await(()->list("groups").hasFocus(),3000);
        ui(()->check(list("groups").getSelectedItemPosition()==1,"left focus did not retain active CCTV category"));
        key(KeyEvent.KEYCODE_DPAD_DOWN);await(()->nav().browsingGroup().equals("卫视"),3000);key(KeyEvent.KEYCODE_DPAD_DOWN);
        await(()->nav().browsingGroup().equals("香港"),3000);
        ui(()->{
            check(list("channelList").getAdapter().getCount()==2,"Hong Kong focus did not preview channels");
            check(list("channelList").getAdapter().getItem(0).toString().contains("tvb-jade"),"preview shows wrong category");
            check(field(live(),"player")==player.get(),"category focus restarted playback");
            check(nav().playingGroup().equals("央视"),"preview changed zapping scope");
            check(!containsButton((View)field(activity,"panel")),"left channel panel still contains function buttons");
        });
        screenshot("category-focus-preview");
        key(KeyEvent.KEYCODE_BACK);key(KeyEvent.KEYCODE_DPAD_DOWN);
        await(()->live().channel().id.equals(a.id),5000);
        ui(()->check(nav().playingGroup().equals("央视"),"CCTV wrap escaped category after Hong Kong preview"));
        key(KeyEvent.KEYCODE_DPAD_UP);await(()->live().channel().id.equals(c.id),5000);
        ui(()->check(nav().playingGroup().equals("央视"),"reverse wrap escaped category"));
        key(KeyEvent.KEYCODE_DPAD_UP);await(()->live().channel().id.equals(b.id),5000);
        ui(()->check(live().channel().id.equals(b.id),"up did not select previous channel"));
        key(KeyEvent.KEYCODE_DPAD_UP);await(()->live().channel().id.equals(a.id),5000);
        ui(()->check(live().channel().id.equals(a.id),"up did not skip channel without sources"));
        key(KeyEvent.KEYCODE_DPAD_DOWN);await(()->live().channel().id.equals(b.id),5000);
        ui(()->check(live().channel().id.equals(b.id),"down did not select next channel"));
        key(KeyEvent.KEYCODE_CHANNEL_UP);await(()->live().channel().id.equals(c.id),5000);
        key(KeyEvent.KEYCODE_CHANNEL_DOWN);await(()->live().channel().id.equals(b.id),5000);
        ui(()->check(live().channel().id.equals(b.id),"dedicated channel keys changed direction"));
        new LocalFavoritesRepository(activity).saveOrder(Arrays.asList(jade.id,a.id));
        key(KeyEvent.KEYCODE_DPAD_LEFT);await(()->list("channelList").hasFocus(),3000);
        key(KeyEvent.KEYCODE_DPAD_LEFT);key(KeyEvent.KEYCODE_DPAD_UP);
        await(()->nav().browsingGroup().equals(ChannelSelection.FAVORITES),3000);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);await(()->list("channelList").hasFocus(),3000);
        ui(()->list("channelList").setSelection(0));runner.waitForIdleSync();key(KeyEvent.KEYCODE_DPAD_CENTER);
        await(()->live().channel().id.equals(jade.id),5000);
        ui(()->check(nav().playingGroup().equals(ChannelSelection.FAVORITES),"favorite confirmation did not enter favorite scope"));
        key(KeyEvent.KEYCODE_DPAD_UP);await(()->live().channel().id.equals(a.id),5000);
        ui(()->check(activity.getSharedPreferences("settings",0).getString("playingGroup","").equals(ChannelSelection.FAVORITES),"scope not saved"));
        new LocalFavoritesRepository(activity).saveOrder(Collections.emptyList());key(KeyEvent.KEYCODE_DPAD_UP);
        await(()->((View)field(activity,"panel")).isShown(),3000);
        ui(()->{check(list("channelList").getAdapter().isEmpty(),"empty favorites borrowed another category");check(list("groups").hasFocus(),"empty category has no usable focus");});
        key(KeyEvent.KEYCODE_SETTINGS);
        await(()->field(activity,"modeButton")!=null && ((Button)field(activity,"modeButton")).isShown(),3000);
        ui(()->{
            Button button=(Button)field(activity,"modeButton");button.requestFocus();check(button.hasFocus(),"settings mode button unreachable");
            PlaybackMode before=(PlaybackMode)field(activity,"mode");button.performClick();check(field(activity,"mode")==before.toggle(),"settings mode button failed");
            ((AlertDialog)field(activity,"maintenanceDialog")).dismiss();
        });
    }
    private boolean containsButton(View view) {
        if(view instanceof Button)return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(containsButton(((ViewGroup)view).getChildAt(i)))return true;
        return false;
    }
    private long today() { Calendar c=Calendar.getInstance(GuideTime.ZONE);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return c.getTimeInMillis(); }
    private void cache() throws Exception {
        long from=today(),until=from+86400000L;
        String raw=new JSONObject().put("cctv17",new JSONObject().put("program",new JSONArray().put(new JSONObject().put("t","缓存测试").put("st",from/1000).put("et",until/1000)))).toString();
        AtomicInteger calls=new AtomicInteger();AtomicReference<String> response=new AtomicReference<>(raw);
        String webFixture=new JSONObject().put("data",new JSONObject().put("cctv17",new JSONObject().put("list",new JSONArray().put(new JSONObject().put("title","缓存测试").put("startTime",from/1000).put("endTime",until/1000))))).toString();
        CctvProgramGuide guide=new CctvProgramGuide(activity,(url,r)->{if(calls.incrementAndGet()==1)throw new IOException("first transport failure");
            if(url.contains("getEpgInfoByChannelNew"))return webFixture;
            return response.get();});
        GuideService.Result recovered=guide.load("CCTV17.cn",from,until,new SafeHttp.Request(),true);
        check(recovered.programmes.size()==1 && calls.get()==2 && recovered.source.contains("官网"),"website fallback did not recover");
        check(!guide.load("CCTV17.cn",from,until).stale && calls.get()==2,"fresh cache not reused");
        guide.load("CCTV17.cn",from,until,new SafeHttp.Request(),true);check(calls.get()==3,"manual retry did not bypass cache");
        CctvProgramGuide emptyGuide=new CctvProgramGuide(activity,(url,r)->{if(url.contains("epginfo"))return "{\"cctv17\":{\"program\":[]}}";throw new IOException("fixture");});
        check(emptyGuide.load("CCTV17.cn",from,until,new SafeHttp.Request(),true).stale,"empty response erased valid daily cache");
        SafeHttp.Request cancelled=new SafeHttp.Request();cancelled.cancel();int before=calls.get();boolean stopped=false;
        try { guide.load("CCTV17.cn",from,until,cancelled,true); }catch(InterruptedIOException expected){stopped=true;}
        check(stopped && calls.get()==before,"cancelled guide performed network work");
    }
    private Object programmeDialog() throws Exception { return field(activity,"programmeDialog"); }
    @SuppressWarnings("unchecked") private List<Programme> programmes() throws Exception { return (List<Programme>)field(programmeDialog(),"programmes"); }
    private void dialog() throws Exception {
        AtomicReference<SafeHttp.Request> last=new AtomicReference<>();
        GuideService fake=new GuideService() {
            @Override public boolean supports(String id){return true;}
            @Override public Result load(String id,long from,long until,SafeHttp.Request request,boolean refresh) throws Exception {
                last.set(request);request.check();long now=System.currentTimeMillis();
                return new Result(Arrays.asList(new Programme(id,"可回看片段",now-200000,now-100000,"fixture"),new Programme(id,"正在播出",now-100000,now+100000),new Programme(id,"下一节目",now+100000,now+200000)),false,"合成节目单");
            }
            @Override public List<Channel.Source> resolve(Programme p,SafeHttp.Request r) { return channel("replay","央视").sources; }
        };
        ui(()->set(activity,"guide",fake));key(KeyEvent.KEYCODE_GUIDE);
        await(()->programmeDialog()!=null && programmes().size()==3,5000);
        ui(()->{
            ListView rows=(ListView)field(programmeDialog(),"list");check(rows.getSelectedItemPosition()==1,"guide did not focus now-playing programme");
            check(((TextView)field(programmeDialog(),"status")).getText().toString().contains("合成"),"source status missing");
            rows.performItemClick(rows.getChildAt(0),0,0);
        });
        await(()->replay().active(),5000);
        await(()->{ExoPlayer p=(ExoPlayer)field(replay(),"player");return p!=null&&p.getPlaybackState()==Player.STATE_READY;},15000);
        key(KeyEvent.KEYCODE_BACK);await(()->!replay().active(),3000);
        ui(()->check(live().channel().id.equals("CCTV1.cn"),"replay did not return to original channel"));
        key(KeyEvent.KEYCODE_GUIDE);await(()->programmes().size()==3,5000);
        ui(()->((AlertDialog)field(programmeDialog(),"dialog")).dismiss());
        await(()->last.get().isCancelled(),3000);
        check(last.get().isCancelled(),"guide dismissal did not cancel its request");
    }
    private void officialPage() throws Exception {
        String page="https://www.mytvsuper.com/tc/programme/bigcityshop_118508/";
        AtomicReference<android.content.Intent> opened=new AtomicReference<>();
        Instrumentation.ActivityMonitor monitor=new Instrumentation.ActivityMonitor() {
            @Override public Instrumentation.ActivityResult onStartActivity(android.content.Intent intent) {
                if(android.content.Intent.ACTION_VIEW.equals(intent.getAction())) {
                    opened.set(intent);return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);
                }
                return null;
            }
        };
        runner.addMonitor(monitor);
        try {
            GuideService fake=new GuideService() {
                @Override public boolean supports(String id){return true;}
                @Override public Result load(String id,long from,long until,SafeHttp.Request request,boolean refresh) {
                    long now=System.currentTimeMillis();return new Result(Collections.singletonList(new Programme(id,"官方重温测试",now-200000,now-100000,"",page)),false,"TVB 官方节目单");
                }
                @Override public String officialReplayPage(Programme p){return p.officialReplayUrl;}
                @Override public List<Channel.Source> resolve(Programme p,SafeHttp.Request r){throw new AssertionError("webpage sent to media resolver");}
            };
            ui(()->{set(activity,"guide",fake);call("showGuide",new Class<?>[]{Channel.class},channel("tvb-jade","香港"));});
            await(()->programmes().size()==1,5000);
            ui(()->{
                ListView rows=(ListView)field(programmeDialog(),"list");
                check(rows.getAdapter().getItem(0).toString().contains("官方重温页面"),"web entry lacks explicit label");
                rows.performItemClick(rows.getChildAt(0),0,0);
            });
            await(()->field(programmeDialog(),"externalDialog")!=null,3000);screenshot("official-replay-prompt");
            ui(()->((AlertDialog)field(programmeDialog(),"externalDialog")).getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            await(()->opened.get()!=null,3000);
            check(page.equals(opened.get().getDataString()),"wrong official page dispatched");
            ui(()->{check(!replay().active(),"official webpage started native replay");((AlertDialog)field(programmeDialog(),"dialog")).dismiss();});
        } finally {runner.removeMonitor(monitor);}
    }
    private void screenshot(String name) throws Exception {
        runner.waitForIdleSync();Thread.sleep(250);
        File directory=new File(activity.getFilesDir(),"navigation-evidence");directory.mkdirs();
        android.graphics.Bitmap image=runner.getUiAutomation().takeScreenshot();
        try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))) {image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
    private void publicGuides() throws Exception {
        StringBuilder evidence=new StringBuilder();
        for(String id:Arrays.asList("CCTV1.cn","CCTV12.cn","tvb-jade","TVBPearl.hk")) {
            ui(()->{
                Channel channel=null;for(Channel c:((Catalog)field(activity,"catalog")).channels)if(c.id.equals(id))channel=c;
                check(channel!=null,"missing public guide channel "+id);call("showGuide",new Class<?>[]{Channel.class},channel);
            });
            await(()->!programmes().isEmpty(),70000);
            ui(()->{
                check(programmes().size()>5,id+" unexpectedly empty/short guide");
                for(Programme p:programmes()) if(!p.channelId.equals(id)||p.endUtcMillis<=p.startUtcMillis)throw new AssertionError("invalid guide identity/time");
                GuideService.Result displayed=(GuideService.Result)field(programmeDialog(),"result");
                check(!displayed.source.isEmpty() && ((TextView)field(programmeDialog(),"status")).getText().toString().contains(displayed.source),"public source attribution missing");
                evidence.append(id).append(" count=").append(programmes().size()).append(" officialPages=").append(programmes().stream().filter(p->!p.officialReplayUrl.isEmpty()).count()).append('\n');
            });
            screenshot(id);ui(()->((AlertDialog)field(programmeDialog(),"dialog")).dismiss());
        }
        CctvProgramGuide guide=new CctvProgramGuide(activity);long from=today()-86400000L;
        GuideService.Result yesterday=guide.load("CCTV1.cn",from,from+86400000L,new SafeHttp.Request(),true);
        List<Channel.Source> sources=Collections.emptyList();Programme selected=null;
        for(Programme p:yesterday.programmes) if(!p.replayId.isEmpty()) { sources=guide.resolve(p,new SafeHttp.Request());if(!sources.isEmpty()){selected=p;break;} }
        check(!sources.isEmpty(),"no actual CCTV replay resource returned");
        Channel.Source source=sources.get(0);ui(()->{live().suspend();replay().start(source);});
        await(()->{ExoPlayer p=(ExoPlayer)field(replay(),"player");return p!=null&&p.getPlaybackState()==Player.STATE_READY&&p.getVideoSize().height>0&&p.getAudioDecoderCounters()!=null&&p.getAudioDecoderCounters().renderedOutputBufferCount>10;},60000);
        await(()->{ExoPlayer p=(ExoPlayer)field(replay(),"player");return p.getCurrentPosition()>=5000&&p.getAudioDecoderCounters().renderedOutputBufferCount>100;},20000);
        ui(()->{
            ExoPlayer p=(ExoPlayer)field(replay(),"player");int maximum=0;
            for(androidx.media3.common.Tracks.Group group:p.getCurrentTracks().getGroups())
                if(group.getType()==androidx.media3.common.C.TRACK_TYPE_VIDEO)
                    for(int i=0;i<group.length;i++)if(group.isTrackSupported(i))maximum=Math.max(maximum,group.getTrackFormat(i).height);
            check(p.getVideoSize().height>0 && (maximum<720 || p.getVideoSize().height>=720),"replay dropped below available HD");
            if(maximum<720)check(((TextView)field(activity,"qualityBadge")).getText().toString().contains("源为标清"),"SD-only replay lacks explicit label");
            evidence.append("CCTV replay video=").append(p.getVideoSize().width).append('x').append(p.getVideoSize().height).append(" availableMaxHeight=").append(maximum).append(" positionMs=").append(p.getCurrentPosition()).append(" audioBuffers=").append(p.getAudioDecoderCounters().renderedOutputBufferCount).append('\n');
        });
        screenshot("cctv-replay");evidence.append("replayId=").append(selected.replayId).append('\n').append("source=").append(source.url).append('\n');
        try(FileOutputStream out=new FileOutputStream(new File(activity.getFilesDir(),"navigation-evidence/results.txt"))) {out.write(evidence.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    }
}
