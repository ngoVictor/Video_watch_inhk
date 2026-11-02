package hk.jiakan.tv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import androidx.media3.ui.PlayerView;
import hk.jiakan.core.Channel;
import hk.jiakan.core.PlaybackMode;
import hk.jiakan.core.ChannelSelection;
import hk.jiakan.core.ChannelNavigation;
import hk.jiakan.core.FutureFeatures.Programme;
import hk.jiakan.tv.favorites.LocalFavoritesRepository;
import hk.jiakan.tv.guide.GuideService;
import hk.jiakan.tv.guide.ProgrammeGuide;
import hk.jiakan.tv.playback.ReplayPlayer;
import hk.jiakan.tv.playback.PlaybackSession;
import hk.jiakan.tv.BuildConfig;
import hk.jiakan.tv.catalog.*;
import hk.jiakan.tv.diagnostics.EventLog;
import hk.jiakan.tv.net.SafeHttp;
import hk.jiakan.tv.net.GithubAccess;
import hk.jiakan.tv.net.GithubRequestPolicy;
import hk.jiakan.tv.playback.PlaybackController;
import hk.jiakan.tv.update.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class MainActivity extends Activity implements PlaybackController.Listener {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ExecutorService guideIo=Executors.newSingleThreadExecutor();
    private final EventLog log=new EventLog();
    private static final DownloadCoordinator downloads=new DownloadCoordinator();
    private final PlaybackSession session=new PlaybackSession();
    private final ChannelNavigation navigation=new ChannelNavigation();
    private SafeHttp.Request download;
    private ProgrammeDialog programmeDialog;
    private final Runnable downloadFinished=this::restorePlayback;
    private SharedPreferences prefs;
    private CatalogRepository repository;
    private ApkUpdater updater;
    private GithubAccess access;
    private Catalog catalog;
    private PlaybackController playback;
    private ReplayPlayer replay;
    private LocalFavoritesRepository favorites;
    private GuideService guide;
    private LinearLayout panel;
    private TextView status,now,title;
    private TextView qualityBadge;
    private Button modeButton;
    private PlaybackMode mode;
    private ListView channelList,groups;
    private TextView empty;
    private final List<Channel> visible=new ArrayList<>();
    private final List<String> categories=new ArrayList<>();
    private ArrayAdapter<String> categoryAdapter,channelAdapter;
    private boolean foreground,maintenance,networkBusy;
    private boolean longConfirmHandled;
    private UpdateManifest update;
    private File prepared;
    private String updateStatus="本次启动尚未检查更新";
    private TextView maintenanceStatus;
    private AlertDialog maintenanceDialog;
    private final Runnable hideInfo=() -> now.setVisibility(View.GONE);
    private final Runnable hidePlaybackNotice=() -> status.setVisibility(View.GONE);
    private final Runnable scheduled=new Runnable() {
        @Override public void run() { if(foreground) { refresh(false); main.postDelayed(this,60_000); } }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        prefs=getSharedPreferences("settings",MODE_PRIVATE); CatalogConfiguration.initialize(this,prefs); access=new GithubAccess(this);
        mode=PlaybackMode.fromStored(prefs.getString("playbackMode",""));
        repository=new CatalogRepository(this,access); updater=new ApkUpdater(this);
        favorites=new LocalFavoritesRepository(this); guide=new ProgrammeGuide(this);
        createUi();
        downloads.observe(downloadFinished,command -> main.post(command));
        io.execute(() -> {
            try {
                Catalog initial=repository.load();
                main.post(() -> { if(isDestroyed()) return; catalog=initial; populate();
                    session.catalog(catalog.channels,prefs.getString("lastChannel",""));
                    Channel selected=null; for(Channel c:catalog.channels) if(c.id.equals(session.selectedId())) selected=c;
                    navigation.restore(selected,prefs.getString("playingGroup","")); filter();
                    restorePlayback();
                    if(initial.channels.isEmpty()) status("尚未配置频道 · 按菜单键或设置键添加频道目录");
                });
            } catch(Exception e) { main.post(() -> { if(!isDestroyed()) status("频道目录无法加载，请在维护菜单更新"); }); }
        });
    }
    private void createUi() {
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        PlayerView playerView=new PlayerView(this); playerView.setUseController(false);
        playerView.setFocusable(false); root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        playback=new PlaybackController(this,playerView,this,log);
        replay=new ReplayPlayer(this,playerView,this,log);
        playback.setMode(mode); replay.setMode(mode);
        status=text("正在加载频道…",23); status.setGravity(Gravity.CENTER); status.setPadding(dp(24),dp(16),dp(24),dp(16));
        status.setBackgroundColor(0xDD16232B);
        FrameLayout.LayoutParams statusLp=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER); root.addView(status,statusLp);
        now=text("家看电视",23); now.setPadding(dp(24),dp(18),dp(24),dp(18)); now.setBackgroundColor(0xDD16232B);
        FrameLayout.LayoutParams infoLp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START); infoLp.setMargins(dp(24),0,0,dp(24)); root.addView(now,infoLp);
        qualityBadge=text("",17); qualityBadge.setPadding(dp(12),dp(6),dp(12),dp(6)); qualityBadge.setBackgroundColor(0xBB16232B);
        FrameLayout.LayoutParams badgeLp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END); badgeLp.setMargins(0,0,dp(24),dp(24));
        qualityBadge.setVisibility(View.GONE); root.addView(qualityBadge,badgeLp);
        panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(24),dp(16),dp(24),dp(16)); panel.setBackgroundColor(0xF516232B);
        FrameLayout.LayoutParams panelLp=new FrameLayout.LayoutParams(dp(660),-1,Gravity.START); root.addView(panel,panelLp);
        title=text("家看电视 · 频道",27); panel.addView(title);
        TextView help=text("左右切换列表 · 确定播放 · 返回收起",17); help.setPadding(0,dp(8),0,dp(10)); panel.addView(help);
        LinearLayout lists=new LinearLayout(this); panel.addView(lists,new LinearLayout.LayoutParams(-1,0,1));
        groups=new ListView(this); groups.setId(View.generateViewId());
        channelList=new ListView(this); channelList.setId(View.generateViewId());
        groups.setNextFocusRightId(channelList.getId()); channelList.setNextFocusLeftId(groups.getId());
        categoryAdapter=adapter(new ArrayList<>()); channelAdapter=adapter(new ArrayList<>());
        groups.setAdapter(categoryAdapter); channelList.setAdapter(channelAdapter);
        groups.setSelector(hk.jiakan.tv.R.drawable.focused_row); channelList.setSelector(hk.jiakan.tv.R.drawable.focused_row);
        lists.addView(groups,new LinearLayout.LayoutParams(dp(170),-1));
        lists.addView(channelList,new LinearLayout.LayoutParams(0,-1,1));
        groups.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p,View v,int pos,long id) {
                if(groups.hasFocus()) previewCategory(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        groups.setOnKeyListener((v,key,event)-> {
            if(key!=KeyEvent.KEYCODE_DPAD_RIGHT && key!=KeyEvent.KEYCODE_DPAD_LEFT) return false;
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT && event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0 && !visible.isEmpty()) channelList.requestFocus();
            return true;
        });
        groups.setOnItemClickListener((p,v,pos,id) -> { previewCategory(pos); if(!visible.isEmpty()) channelList.requestFocus(); });
        channelList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p,View v,int pos,long id) {
                if(channelList.hasFocus() && pos>=0 && pos<visible.size()) navigation.remember(visible.get(pos).id);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        channelList.setOnItemClickListener((p,v,pos,id) -> tune(visible.get(pos)));
        channelList.setOnItemLongClickListener((p,v,pos,id) -> { actions(visible.get(pos)); return true; });
        channelList.setOnKeyListener((v,key,event)-> {
            if(key==KeyEvent.KEYCODE_DPAD_LEFT) {
                if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) {
                    int categoryIndex=categories.indexOf(navigation.browsingGroup());groups.requestFocus();groups.setSelection(categoryIndex);
                }
                return true;
            }
            if(key!=KeyEvent.KEYCODE_DPAD_CENTER && key!=KeyEvent.KEYCODE_ENTER) return false;
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) longConfirmHandled=false;
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.isLongPress()) {
                v.cancelLongPress(); longConfirmHandled=true; actions(selectedChannel()); return true;
            }
            return event.getAction()==KeyEvent.ACTION_UP && longConfirmHandled;
        });
        empty=text("",19); panel.addView(empty);
        panel.setVisibility(View.GONE); setContentView(root);
    }
    private ArrayAdapter<String> adapter(List<String> data) {
        return new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,data) {
            @Override public View getView(int position,View convert,ViewGroup parent) {
                TextView row=(TextView)super.getView(position,convert,parent); row.setTextColor(Color.WHITE); row.setTextSize(20);
                row.setMinHeight(dp(54)); row.setGravity(Gravity.CENTER_VERTICAL); return row;
            }
        };
    }
    private void populate() {
        if(categories.isEmpty()) { Collections.addAll(categories,ChannelSelection.FAVORITES,"央视","卫视","香港","国际"); categoryAdapter.addAll(categories); }
        filter();
    }
    private void toggleMode() {
        mode=mode.toggle(); prefs.edit().putString("playbackMode",mode.name()).apply();
        playback.setMode(mode); replay.setMode(mode);
        if(modeButton!=null) modeButton.setText(getString(hk.jiakan.tv.R.string.playback_mode_button,mode.label));
        log.add("playback_mode="+mode.name());
        Toast.makeText(this,mode.label+"：高清源最低720p，只有标清源时保留原画质",Toast.LENGTH_LONG).show();
    }
    private void previewCategory(int position) {
        if(position<0 || position>=categories.size() || categories.get(position).equals(navigation.browsingGroup())) return;
        Channel selected=selectedChannel(); if(selected!=null) navigation.remember(selected.id);
        navigation.browse(categories.get(position)); filter();
    }
    private void filter() {
        String category=navigation.browsingGroup();
        String selectedId=navigation.focusedId(session.selectedId());
        visible.clear(); if(catalog==null) { channelAdapter.clear(); return; }
        List<String> saved=favorites.orderedChannelIds();
        visible.addAll(ChannelSelection.filter(catalog.channels,category,saved));
        Set<String> savedIds=new HashSet<>(saved); List<String> labels=new ArrayList<>();
        for(Channel c:visible) labels.add((savedIds.contains(c.id) ? "★ " : "")+c.name+(c.sources.isEmpty() ? "  · 待接入" : ""));
        channelAdapter.setNotifyOnChange(false); channelAdapter.clear(); channelAdapter.addAll(labels); channelAdapter.notifyDataSetChanged();
        title.setText("家看电视 · "+category+"（"+visible.size()+"）");
        empty.setText(ChannelSelection.FAVORITES.equals(category) ? "还没有收藏。看电视时按右键可加入收藏。" : "这个分类暂无频道");
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        selectRow(selectedId);
    }
    private void tune(Channel channel) {
        play(channel,true);
    }
    private void play(Channel channel,boolean confirmed) {
        if(isDestroyed() || isFinishing()) return;
        if(confirmed) navigation.confirm(channel,favorites.orderedChannelIds());
        session.select(channel.id); prefs.edit().putString("lastChannel",channel.id).putString("playingGroup",navigation.playingGroup()).apply();
        replay.clear(); playback.suspend(); restorePlayback();
    }
    private void restorePlayback() {
        if(!foreground || maintenance || isDestroyed() || isFinishing()) return;
        if(downloads.busy()) { status("正在停止更新下载，请稍候…"); return; }
        if(replay.active()) { replay.resume(); return; }
        PlaybackSession.Decision decision=session.resume(foreground,maintenance,downloads.busy());
        if(decision.kind==PlaybackSession.Kind.MISSING) {
            playback.suspend(); status(catalog!=null && catalog.channels.isEmpty() ? "尚未配置频道 · 按菜单键或设置键添加频道目录" : "原频道已不在目录中，请重新选择频道"); showPanel();
        } else if(decision.kind==PlaybackSession.Kind.LIVE) {
            panel.setVisibility(View.GONE); playback.tune(decision.channel); info(decision.channel.name);
        }
    }
    private void cancelDownload() { if(download!=null) download.cancel(); }
    private void info(String message) {
        now.setText(message+"\n上键上一台 · 下键下一台 · 左键选台 · 右键频道操作 · 菜单键设置"); now.setVisibility(View.VISIBLE);
        main.removeCallbacks(hideInfo); main.postDelayed(hideInfo,6000);
    }
    private void selectRow(String id) {
        int index=0; for(int i=0;i<visible.size();i++) if(visible.get(i).id.equals(id)) index=i;
        if(!visible.isEmpty()) channelList.setSelection(index);
    }
    private Channel selectedChannel() {
        int index=channelList==null ? -1 : channelList.getSelectedItemPosition();
        return index>=0 && index<visible.size() ? visible.get(index) : panel!=null && panel.getVisibility()==View.VISIBLE ? null : playback==null ? null : playback.channel();
    }
    private void showPanel() {
        Channel current=playback.channel();
        navigation.showPlaying(); filter(); groups.setSelection(categories.indexOf(navigation.browsingGroup()));
        if(current!=null) selectRow(current.id);
        panel.setVisibility(View.VISIBLE); if(visible.isEmpty()) groups.requestFocus(); else channelList.requestFocus();
    }
    private void actions(Channel channel) {
        if(channel==null) { Toast.makeText(this,"先选择一个频道",Toast.LENGTH_SHORT).show(); return; }
        ChannelActionsDialog.show(this,channel,favorites,this::filter,()->showGuide(channel),()->tune(channel));
    }
    private void showGuide(Channel channel) {
        if(programmeDialog!=null) programmeDialog.dismiss();
        programmeDialog=new ProgrammeDialog(this,channel,guide,guideIo,new ProgrammeDialog.Listener() {
            @Override public void live(Channel c) { tune(c); }
            @Override public void replay(Programme programme,Channel.Source source) {
                if(!foreground || maintenance || downloads.busy() || isFinishing()) return;
                session.suspend(); playback.suspend(); panel.setVisibility(View.GONE); MainActivity.this.replay.start(source);
                now.setText("回看："+programme.title+"\n确定暂停/继续 · 左右快退/快进 · 返回直播");
                now.setVisibility(View.VISIBLE); main.removeCallbacks(hideInfo); main.postDelayed(hideInfo,8000);
            }
        }); programmeDialog.show();
    }
    private void returnLive() { replay.clear(); session.suspend(); restorePlayback(); }
    private void step(int direction) {
        if(catalog==null) return;
        Channel current=playback.channel(); Channel next=navigation.next(catalog.channels,current==null ? "" : current.id,direction,favorites.orderedChannelIds());
        if(next==null) showPanel(); else play(next,false);
    }
    @Override public boolean onKeyDown(int key,KeyEvent event) {
        if(event.getRepeatCount()>0 && (key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_DPAD_DOWN) && panel.getVisibility()!=View.VISIBLE) return true;
        if(key==KeyEvent.KEYCODE_MENU || key==KeyEvent.KEYCODE_SETTINGS || key==KeyEvent.KEYCODE_M || key==KeyEvent.KEYCODE_F10) {
            if(event.getRepeatCount()==0) maintenance(); return true;
        }
        if(key==KeyEvent.KEYCODE_GUIDE && !maintenance) { Channel c=panel.getVisibility()==View.VISIBLE ? selectedChannel() : playback.channel(); if(c!=null && event.getRepeatCount()==0) showGuide(c); return true; }
        if(panel.getVisibility()!=View.VISIBLE && !maintenance) {
            if(replay.active()) {
                if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER) { if(event.getRepeatCount()==0) replay.togglePause(); return true; }
                if(key==KeyEvent.KEYCODE_DPAD_LEFT || key==KeyEvent.KEYCODE_DPAD_RIGHT) { if(event.getRepeatCount()==0) replay.seek(key==KeyEvent.KEYCODE_DPAD_LEFT ? -30000 : 30000); return true; }
            } else if(key==KeyEvent.KEYCODE_DPAD_RIGHT) { actions(playback.channel()); return true; }
            if(key==KeyEvent.KEYCODE_DPAD_DOWN || key==KeyEvent.KEYCODE_CHANNEL_UP) { step(1); return true; }
            if(key==KeyEvent.KEYCODE_DPAD_UP || key==KeyEvent.KEYCODE_CHANNEL_DOWN) { step(-1); return true; }
            if(key==KeyEvent.KEYCODE_DPAD_CENTER || key==KeyEvent.KEYCODE_ENTER || key==KeyEvent.KEYCODE_DPAD_LEFT) { showPanel(); return true; }
        }
        return super.onKeyDown(key,event);
    }
    @Override public void onBackPressed() {
        if(panel.getVisibility()==View.VISIBLE) { panel.setVisibility(View.GONE); return; }
        if(replay.active()) { returnLive(); return; }
        new AlertDialog.Builder(this).setMessage("退出家看电视？").setNegativeButton("继续看",null)
                .setPositiveButton("退出",(d,w) -> finish()).show();
    }
    @Override public void status(String message) {
        main.removeCallbacks(hidePlaybackNotice); status.setText(message); status.setVisibility(View.VISIBLE);
    }
    @Override public void notice(String message) {
        status(message); main.postDelayed(hidePlaybackNotice,6000);
    }
    @Override public void connected() { main.removeCallbacks(hidePlaybackNotice); status.setVisibility(View.GONE); }
    @Override public void qualityLabel(String message) {
        if(qualityBadge==null) return;
        qualityBadge.setText(message); qualityBadge.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
    }
    private void maintenance() {
        if(maintenance) return;
        maintenance=true; session.suspend(); playback.suspend(); replay.suspend();
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(24),dp(8),dp(24),dp(8));
        maintenanceStatus=text("",18); box.addView(maintenanceStatus); updateMaintenanceStatus();
        addButton(box,"配置更新地址",this::configure);
        addButton(box,"更换私有仓库凭据",this::replaceAccess);
        addButton(box,"检查频道与软件更新",() -> refresh(true));
        modeButton=new Button(this); modeButton.setText(getString(hk.jiakan.tv.R.string.playback_mode_button,mode.label)); modeButton.setTextSize(19);
        modeButton.setOnClickListener(v -> toggleMode()); box.addView(modeButton);
        addButton(box,"安装已下载的新版本",this::installPrepared);
        addButton(box,"查看播放诊断",() -> new AlertDialog.Builder(this).setTitle("播放诊断（仅本机本次运行）")
                .setMessage(log.snapshot().isEmpty() ? "暂无播放事件" : log.snapshot()).setPositiveButton("关闭",null).show());
        addButton(box,"开源音频组件与许可",this::showAudioLicenses);
        ScrollView scroll=new ScrollView(this); scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("维护与更新").setView(scroll).setNegativeButton("返回看电视",null).create();
        maintenanceDialog=dialog;
        dialog.setOnDismissListener(d -> {
            maintenance=false; maintenanceStatus=null; maintenanceDialog=null; modeButton=null;
            cancelDownload(); restorePlayback();
        }); dialog.show();
        if(update!=null && prepared==null && !networkBusy) prepareUpdate();
    }
    private void addButton(LinearLayout box,String label,Runnable action) { Button button=new Button(this); button.setText(label); button.setTextSize(19); button.setOnClickListener(v -> action.run()); box.addView(button); }
    private String softwareUpdateUrl() {
        String value=prefs.getString("updateUrl",GithubRequestPolicy.UPDATE_URL);
        return value;
    }
    private void configure() {
        if(networkBusy) { Toast.makeText(this,"请等待当前更新检查或下载完成",Toast.LENGTH_SHORT).show(); return; }
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(24),0,dp(24),0);
        form.addView(text("频道目录 HTTPS 地址",17)); EditText channelUrl=new EditText(this); channelUrl.setSingleLine(true); channelUrl.setText(prefs.getString("catalogUrl",GithubRequestPolicy.CATALOG_URL)); form.addView(channelUrl);
        form.addView(text("软件更新清单 HTTPS 地址",17)); EditText updateUrl=new EditText(this); updateUrl.setSingleLine(true); updateUrl.setText(softwareUpdateUrl()); form.addView(updateUrl);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("由家人设置一次即可").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String c=channelUrl.getText().toString().trim(),u=updateUrl.getText().toString().trim();
            try { if(!c.isEmpty()) SafeHttp.validate(c,true); if(!u.isEmpty()) SafeHttp.validate(u,true); }
            catch(IllegalArgumentException e) { Toast.makeText(this,"请输入 HTTPS 地址，或留空停用",Toast.LENGTH_LONG).show(); return; }
            prefs.edit().putString("catalogUrl",c).putString("updateUrl",u).remove("catalogCheck").remove("apkCheck").apply();
            update=null; prepared=null; updateStatus="地址已保存"; dialog.dismiss(); refresh(true);
        })); dialog.show();
    }
    private void replaceAccess() {
        if(networkBusy) { Toast.makeText(this,"请等待更新检查或下载完成",Toast.LENGTH_SHORT).show(); return; }
        EditText input=new EditText(this); input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("输入新凭据，留空保持原样");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("设置私有频道读取凭据").setView(input)
                .setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String replacement=input.getText().toString().trim();
            if(replacement.isEmpty()) { dialog.dismiss(); return; }
            networkBusy=true; input.setText(""); dialog.dismiss();
            io.execute(() -> {
                try {
                    access.replace(replacement);
                    prefs.edit().remove("catalogCheck").remove("apkCheck").apply();
                    main.post(() -> { if(isDestroyed()) return; networkBusy=false; updateStatus="凭据已保存"; refresh(true); });
                } catch(Exception error) {
                    main.post(() -> { if(isDestroyed()) return; networkBusy=false; updateStatus="凭据未保存，请检查输入格式。"+UpdateFailure.from(error).display(); updateMaintenanceStatus(); });
                }
            });
        })); dialog.show();
    }
    private void refresh(boolean force) {
        if(networkBusy || downloads.busy() || isDestroyed()) return;
        final long time=System.currentTimeMillis();
        String c=prefs.getString("catalogUrl",GithubRequestPolicy.CATALOG_URL),u=softwareUpdateUrl();
        boolean checkC=!c.isEmpty() && (force || due(time,prefs.getLong("catalogCheck",0),6*60*60*1000L));
        boolean checkU=!u.isEmpty() && (force || due(time,prefs.getLong("apkCheck",0),24*60*60*1000L));
        if(!checkC && !checkU) { if(force) { updateStatus=c.isEmpty() && u.isEmpty() ? "尚未配置更新地址，请先设置频道目录" : "暂时无需更新"; updateMaintenanceStatus(); } return; }
        networkBusy=true; updateStatus="正在检查更新…"; updateMaintenanceStatus();
        io.execute(() -> {
            StringBuilder result=new StringBuilder(); Catalog fresh=null; UpdateManifest candidate=null; boolean checkedApk=false;
            if(checkC) {
                prefs.edit().putLong("catalogCheck",time).apply();
                try { fresh=repository.refresh(c); result.append("频道目录已检查。 "); }
                catch(Exception e) { result.append("频道更新失败，继续使用旧目录。").append(UpdateFailure.from(e).display()).append('\n'); log.add("catalog_update_failed "+UpdateFailure.from(e).code); }
            }
            if(checkU) {
                prefs.edit().putLong("apkCheck",time).apply();
                try { candidate=updater.check(u); checkedApk=true; result.append(candidate==null ? "已是最新软件。" : "发现新版本，进入维护页面会自动下载。"); }
                catch(Exception e) { result.append("软件更新检查失败。").append(UpdateFailure.from(e).display()); log.add("apk_check_failed "+UpdateFailure.from(e).code); }
            }
            final Catalog next=fresh; final UpdateManifest available=candidate; final boolean success=checkedApk;
            main.post(() -> { if(isDestroyed()) return; networkBusy=false;
                if(next!=null) {
                    catalog=next; session.catalog(next.channels,prefs.getString("lastChannel",""));
                    for(Channel channelRecord:next.channels) if(channelRecord.id.equals(session.selectedId())) navigation.reconcile(channelRecord);
                    prefs.edit().putString("playingGroup",navigation.playingGroup()).apply();populate();restorePlayback();
                }
                if(success) { if(update==null || available==null || update.versionCode!=available.versionCode || !update.sha256.equals(available.sha256)) prepared=null; update=available; }
                updateStatus=result.toString(); updateMaintenanceStatus();
                if(update!=null && prepared==null) { if(maintenance) prepareUpdate(); else info("发现软件新版本 · 菜单键查看"); }
            });
        });
    }
    private boolean due(long now,long last,long interval) { return last==0 || now<last || now-last>=interval; }
    private void prepareUpdate() {
        if(update==null || networkBusy || downloads.busy() || !foreground || !maintenance) return;
        networkBusy=true; updateStatus="正在下载并校验新版本…"; updateMaintenanceStatus();
        final UpdateManifest downloading=update;
        download=downloads.start(io,command -> main.post(command),request -> updater.prepare(downloading,request),result -> {
            download=null; networkBusy=false;
            if(isDestroyed() || isFinishing()) return;
            if(result.cancelled) updateStatus="下载已取消，可在维护页重新检查更新";
            else if(result.error!=null) { updateStatus="更新下载或校验失败，原应用不受影响。"+UpdateFailure.from(result.error).display(); log.add("apk_prepare_failed "+UpdateFailure.from(result.error).code); }
            else { if(update==downloading) prepared=result.file; updateStatus="更新已准备好，选择安装即可（系统可能要求确认）"; }
            updateMaintenanceStatus();
        });
    }
    private void installPrepared() {
        if(prepared==null || update==null) { Toast.makeText(this,"还没有可安装的新版本",Toast.LENGTH_LONG).show(); return; }
        // Package verification is repeated on the worker immediately before presenting system UI.
        if(networkBusy) return; networkBusy=true; final File apk=prepared; final UpdateManifest manifest=update;
        io.execute(() -> {
            try {
                updater.verify(apk,manifest);
                main.post(() -> { networkBusy=false; if(isDestroyed() || isFinishing() || !foreground || !maintenance) return;
                    try { if(!updater.install(this,apk,manifest)) { updateStatus="允许安装后返回，再选择安装新版本"; updateMaintenanceStatus(); } }
                    catch(Exception e) { updateStatus="无法启动安装，请检查系统是否支持安装 APK，再重试（INSTALL）。"; log.add("apk_install_failed INSTALL"); updateMaintenanceStatus(); }
                });
            } catch(Exception e) { main.post(() -> { if(isDestroyed()) return; networkBusy=false; prepared=null; updateStatus="安装包验证失败，请重新下载（VERIFY）。"; log.add("apk_verify_failed VERIFY"); updateMaintenanceStatus(); }); }
        });
    }
    private void updateMaintenanceStatus() {
        if(maintenanceStatus!=null) maintenanceStatus.setText("家看电视 "+BuildConfig.VERSION_NAME+"\n"+Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.RELEASE
                +"\n目录版本："+(catalog==null ? "未加载" : catalog.revision)+"\n"+updateStatus);
    }
    @Override protected void onStart() {
        super.onStart(); foreground=true;
        if(updater.consumePendingInstall()) { maintenance(); refresh(true); }
        restorePlayback();
        main.removeCallbacks(scheduled); main.post(scheduled);
    }
    @Override protected void onStop() { foreground=false; session.suspend(); cancelDownload(); if(programmeDialog!=null) programmeDialog.dismiss(); playback.suspend(); replay.suspend(); connected(); main.removeCallbacks(scheduled); super.onStop(); }
    @Override protected void onDestroy() { cancelDownload(); downloads.remove(downloadFinished); if(maintenanceDialog!=null) maintenanceDialog.dismiss(); if(programmeDialog!=null) programmeDialog.dismiss(); playback.suspend(); replay.clear(); main.removeCallbacksAndMessages(null); io.shutdown(); guideIo.shutdownNow(); super.onDestroy(); }
    private TextView text(String value,int size) { TextView v=new TextView(this); v.setText(value); v.setTextColor(Color.WHITE); v.setTextSize(size); return v; }
    private void showAudioLicenses() {
        StringBuilder content=new StringBuilder();
        for(String name:new String[]{"NOTICE.txt","GPL-3.0.txt"}) {
            try(java.io.InputStream in=getAssets().open("licenses/"+name);
                java.io.BufferedReader reader=new java.io.BufferedReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8))) {
                String line;while((line=reader.readLine())!=null) content.append(line).append('\n');
            } catch(java.io.IOException e) { content.append("许可文件暂无法读取\n"); }
        }
        new AlertDialog.Builder(this).setTitle("开源组件与许可").setMessage(content.toString()).setPositiveButton("关闭",null).show();
    }
    private int dp(int value) { return (int)(value*getResources().getDisplayMetrics().density+0.5f); }
}
