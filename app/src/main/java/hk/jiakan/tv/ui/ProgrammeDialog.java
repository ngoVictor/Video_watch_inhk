package hk.jiakan.tv.ui;

import android.app.*;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.text.TextUtils;
import hk.jiakan.core.Channel;
import hk.jiakan.core.FutureFeatures.Programme;
import hk.jiakan.tv.guide.*;
import hk.jiakan.tv.net.SafeHttp;
import java.util.*;
import java.util.concurrent.*;

/** Async dialog results are scoped to both its lifetime and the selected day. */
public final class ProgrammeDialog {
    public interface Listener { void live(Channel channel); void replay(Programme programme,Channel.Source source); }
    private final Activity activity;
    private final Channel channel;
    private final GuideService provider;
    private final ExecutorService worker;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final List<Programme> programmes=new ArrayList<>();
    private final List<Programme> dailyProgrammes=new ArrayList<>();
    private final List<Long> days=new ArrayList<>();
    private AlertDialog dialog,externalDialog;
    private TextView status,date,empty;
    private ListView list,dates;
    private Button retry,today;
    private final List<Button> periods=new ArrayList<>();
    private ArrayAdapter<String> adapter,dateAdapter;
    private int generation;
    private long selectedDay=GuidePresentation.dayStart(System.currentTimeMillis()),calendarDay=selectedDay;
    private GuidePresentation.Period period=GuidePresentation.Period.ALL;
    private boolean focusProgrammes=true,loading;
    private GuideService.Result result;
    private final Runnable delayedLoad=()->load(false);
    private final Runnable clockTick=new Runnable() {
        @Override public void run() {
            if(dialog==null || !dialog.isShowing())return;
            long currentDay=GuidePresentation.dayStart(System.currentTimeMillis());
            if(currentDay!=calendarDay) {
                calendarDay=currentDay;
                if(selectedDay<currentDay-7*GuidePresentation.DAY) { selectedDay=currentDay-7*GuidePresentation.DAY;load(); }
                rebuildDates();
            }
            date.setText(GuidePresentation.dateTitle(selectedDay,System.currentTimeMillis()));
            if(!loading && result!=null) render(false);
            main.postDelayed(this,30000);
        }
    };
    private Future<?> pending;
    private SafeHttp.Request request;
    private void cancelPending() { if(request!=null) request.cancel(); if(pending!=null) pending.cancel(true); }
    public void dismiss() { if(dialog!=null) dialog.dismiss(); }
    public ProgrammeDialog(Activity activity,Channel channel,GuideService provider,ExecutorService worker,Listener listener) {
        this.activity=activity; this.channel=channel; this.provider=provider; this.worker=worker; this.listener=listener;
    }
    public void show() {
        LinearLayout box=column();box.setPadding(dp(24),dp(12),dp(24),dp(12));box.setBackgroundColor(0xFF101C24);
        LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout headingText=column();TextView title=text(channel.name+" · 节目单",27);title.setSingleLine();title.setEllipsize(TextUtils.TruncateAt.END);headingText.addView(title);
        TextView help=text("节目列：左键日期 · 右键时段",14);help.setTextColor(0xFFB6C9D4);headingText.addView(help);heading.addView(headingText,new LinearLayout.LayoutParams(0,-2,1));
        today=button("回到今天",()->{focusProgrammes=true;period=GuidePresentation.Period.ALL;selectedDay=GuidePresentation.dayStart(System.currentTimeMillis());rebuildDates();load();});
        retry=button("重试",()->{focusProgrammes=list.hasFocus();load(true);});
        Button back=button("返回电视",this::dismiss);
        for(Button b:new Button[]{today,retry,back})heading.addView(b,new LinearLayout.LayoutParams(dp(112),dp(44)));
        box.addView(heading,new LinearLayout.LayoutParams(-1,dp(62)));
        LinearLayout body=new LinearLayout(activity);box.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout rail=column();rail.setPadding(0,0,dp(16),0);body.addView(rail,new LinearLayout.LayoutParams(dp(168),-1));
        rail.addView(text("日期 · 上下选择",16),new LinearLayout.LayoutParams(-1,dp(34)));
        dates=new ListView(activity);dates.setId(View.generateViewId());dates.setSelector(hk.jiakan.tv.R.drawable.focused_row);
        dateAdapter=new ArrayAdapter<String>(activity,android.R.layout.simple_list_item_1,new ArrayList<>()) {
            @Override public View getView(int position,View convert,ViewGroup parent) {
                LinearLayout row=convert instanceof LinearLayout ? (LinearLayout)convert : dateRow();
                long day=days.get(position);
                String label=GuidePresentation.relativeDay(day,System.currentTimeMillis());
                ((TextView)row.getChildAt(0)).setText(day==selectedDay ? activity.getString(hk.jiakan.tv.R.string.guide_selected,label) : label);
                ((TextView)row.getChildAt(1)).setText(GuidePresentation.railDate(day));return row;
            }
        };
        dates.setAdapter(dateAdapter);rail.addView(dates,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout schedule=column();body.addView(schedule,new LinearLayout.LayoutParams(0,-1,1));
        date=text("",25);schedule.addView(date,new LinearLayout.LayoutParams(-1,dp(34)));
        LinearLayout timeBar=new LinearLayout(activity);
        for(GuidePresentation.Period item:GuidePresentation.Period.values()) {
            Button b=button(item.label,()->{period=item;render(true);});periods.add(b);
            b.setOnKeyListener((v,key,event)->{if(key!=KeyEvent.KEYCODE_DPAD_DOWN)return false;if(event.getAction()==KeyEvent.ACTION_DOWN)focusList();return true;});
            timeBar.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));
        }
        schedule.addView(timeBar);
        list=new ListView(activity); list.setSelector(hk.jiakan.tv.R.drawable.focused_row);
        list.setId(View.generateViewId());list.setDividerHeight(dp(1));
        adapter=new ArrayAdapter<String>(activity,android.R.layout.simple_list_item_1,new ArrayList<>()) {
            @Override public View getView(int position,View convert,ViewGroup parent) {
                LinearLayout row=convert instanceof LinearLayout ? (LinearLayout)convert : programmeRow();
                Programme p=programmes.get(position);((TextView)row.getChildAt(0)).setText(GuidePresentation.timeRange(p,selectedDay));
                LinearLayout content=(LinearLayout)row.getChildAt(1);
                ((TextView)content.getChildAt(0)).setText(p.title);
                ((TextView)content.getChildAt(1)).setText(GuidePresentation.state(p,System.currentTimeMillis()));return row;
            }
        };
        list.setAdapter(adapter);schedule.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        empty=text("",22);empty.setGravity(Gravity.CENTER);empty.setPadding(dp(24),dp(24),dp(24),dp(24));schedule.addView(empty,new LinearLayout.LayoutParams(-1,0,1));empty.setVisibility(View.GONE);
        list.setOnItemClickListener((p,v,index,id)->select(programmes.get(index)));
        list.setOnKeyListener((v,key,event)->{
            if(key==KeyEvent.KEYCODE_DPAD_LEFT) {if(event.getAction()==KeyEvent.ACTION_DOWN)focusDates();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_RIGHT) {if(event.getAction()==KeyEvent.ACTION_DOWN)periods.get(period.ordinal()).requestFocus();return true;}
            if(key==KeyEvent.KEYCODE_DPAD_UP && list.getSelectedItemPosition()==0) {if(event.getAction()==KeyEvent.ACTION_DOWN)periods.get(period.ordinal()).requestFocus();return true;}
            return false;
        });
        dates.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id) {if(dates.hasFocus())chooseDay(position);}
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        dates.setOnItemClickListener((p,v,position,id)->{chooseDay(position);focusList();});
        dates.setOnKeyListener((v,key,event)->{if(key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;if(event.getAction()==KeyEvent.ACTION_DOWN)focusList();return true;});
        status=text("",16);status.setTextColor(0xFFB6C9D4);status.setMaxLines(2);
        box.addView(status,new LinearLayout.LayoutParams(-1,dp(40)));
        dialog=new AlertDialog.Builder(activity,android.R.style.Theme_Material_NoActionBar_Fullscreen).create();
        dialog.setOnDismissListener(d->{ generation++; cancelPending(); if(externalDialog!=null) externalDialog.dismiss(); main.removeCallbacksAndMessages(null); });
        dialog.show();dialog.setContentView(box,new ViewGroup.LayoutParams(-1,-1));dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(-1,-1);
        rebuildDates();load();main.postDelayed(clockTick,30000);
    }
    private LinearLayout column() {LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);return box;}
    private TextView text(String title,int size) {TextView t=new TextView(activity);t.setText(title);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private LinearLayout dateRow() {LinearLayout row=column();row.setPadding(dp(10),dp(6),dp(6),dp(6));row.setMinimumHeight(dp(58));row.addView(text("",21));TextView small=text("",16);small.setTextColor(0xFFDBE5EB);row.addView(small);return row;}
    private LinearLayout programmeRow() {
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(6),dp(12),dp(6));row.setMinimumHeight(dp(68));
        row.addView(text("",19),new LinearLayout.LayoutParams(dp(134),-2));
        LinearLayout content=column();TextView title=text("",23);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);content.addView(title);
        TextView label=text("",16);label.setTextColor(0xFFDBE5EB);content.addView(label);row.addView(content,new LinearLayout.LayoutParams(0,-2,1));return row;
    }
    private Button button(String title,Runnable action) {Button b=new Button(activity);b.setText(title);b.setTextSize(18);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackgroundResource(hk.jiakan.tv.R.drawable.guide_button);b.setOnClickListener(v->action.run());return b;}
    private void rebuildDates() {
        days.clear();dateAdapter.clear();long now=System.currentTimeMillis(),start=GuidePresentation.dayStart(now);
        for(int i=-7;i<=2;i++) {long day=start+i*GuidePresentation.DAY;days.add(day);dateAdapter.add(GuidePresentation.dateTitle(day,now));}
        dates.setSelection(days.indexOf(selectedDay));
        alignDates();
    }
    private void chooseDay(int position) {
        if(position<0 || position>=days.size() || days.get(position)==selectedDay)return;
        selectedDay=days.get(position);period=GuidePresentation.Period.ALL;focusProgrammes=false;
        generation++;cancelPending();main.removeCallbacks(delayedLoad);clearRows();showEmpty("正在获取这一天的节目单…");
        date.setText(GuidePresentation.dateTitle(selectedDay,System.currentTimeMillis()));dateAdapter.notifyDataSetChanged();updatePeriods();
        loading=true;result=null;status.setText("香港时间 · 左右切换日期与节目 · 返回收起");main.postDelayed(delayedLoad,220);
    }
    private void alignDates() {
        dates.post(()->{
            if(dialog==null || !dialog.isShowing())return;
            int index=days.indexOf(selectedDay);if(index<0)return;
            View row=dates.getChildAt(0);int height=(row==null ? dp(58) : row.getHeight())+dates.getDividerHeight();
            int count=Math.max(1,dates.getHeight()/Math.max(1,height)),first=Math.max(0,Math.min(index,days.size()-count));
            dates.setSelectionFromTop(index,(index-first)*height);
        });
    }
    private void focusDates() {focusProgrammes=false;dates.requestFocus();dates.setSelection(days.indexOf(selectedDay));alignDates();}
    private void focusList() {
        if(programmes.isEmpty()){focusProgrammes=loading;retry.requestFocus();}
        else {int selected=list.getSelectedItemPosition();if(selected<0)selected=GuidePresentation.initialSelection(programmes,System.currentTimeMillis());list.requestFocus();list.setSelectionFromTop(selected,0);}
    }
    private void clearRows() {dailyProgrammes.clear();programmes.clear();adapter.clear();}
    private void showEmpty(String message) {empty.setText(message);empty.setVisibility(View.VISIBLE);list.setVisibility(View.GONE);}
    private void updatePeriods() {for(int i=0;i<periods.size();i++){Button b=periods.get(i);b.setActivated(i==period.ordinal());String label=GuidePresentation.Period.values()[i].label;b.setText(i==period.ordinal() ? activity.getString(hk.jiakan.tv.R.string.guide_selected,label) : label);}}
    private boolean current(int token) { return token==generation && dialog.isShowing() && !activity.isDestroyed() && !activity.isFinishing(); }
    private void load() {
        load(false);
    }
    private void load(boolean refresh) {
        int token=++generation;cancelPending();main.removeCallbacks(delayedLoad);
        long from=selectedDay,until=from+GuidePresentation.DAY;
        date.setText(GuidePresentation.dateTitle(from,System.currentTimeMillis()));updatePeriods();clearRows();result=null;loading=false;
        if(!provider.supports(channel.id)) {showEmpty(activity.getString(hk.jiakan.tv.R.string.guide_unsupported));status.setText("香港时间 · 返回可继续看电视");return;}
        loading=true;showEmpty("正在获取这一天的节目单…");status.setText("香港时间 · 左右切换日期与节目 · 返回收起");
        final SafeHttp.Request operation=new SafeHttp.Request(); request=operation;
        pending=worker.submit(()-> {
            try {
                GuideService.Result loaded=provider.load(channel.id,from,until,operation,refresh);
                main.post(()-> {
                    if(!current(token)) return;
                    loading=false;result=loaded;dailyProgrammes.addAll(loaded.programmes);render(true);
                    if(focusProgrammes) {focusProgrammes=false;focusList();}
                });
            } catch(Exception e) {main.post(()->{if(current(token)){loading=false;showEmpty("节目单加载失败\n请按上方“重试”，或选择其他日期");status.setText("直播不受影响 · 香港时间");}});}
        });
    }
    private void render(boolean reposition) {
        updatePeriods();if(result==null)return;
        int previous=list.getSelectedItemPosition(),first=list.getFirstVisiblePosition();View top=list.getChildAt(0);int y=top==null ? 0 : top.getTop();
        programmes.clear();programmes.addAll(GuidePresentation.filter(dailyProgrammes,selectedDay,period));adapter.setNotifyOnChange(false);adapter.clear();
        long now=System.currentTimeMillis();for(Programme p:programmes)adapter.add(p.title+"\n"+GuidePresentation.state(p,now));adapter.notifyDataSetChanged();
        if(programmes.isEmpty())showEmpty(dailyProgrammes.isEmpty() ? "这一天尚未提供节目单\n可换一天或稍后重试，不代表当天没有节目" : "该时段暂无节目记录\n请选择其他时段，或切回“全天”");
        else {empty.setVisibility(View.GONE);list.setVisibility(View.VISIBLE);if(reposition)list.setSelectionFromTop(GuidePresentation.initialSelection(programmes,now),0);else if(list.hasFocus())list.setSelection(Math.max(0,previous));else list.setSelectionFromTop(first,y);}
        String range=period==GuidePresentation.Period.ALL ? "全天" : period.label+" "+period.range();
        status.setText(dailyProgrammes.isEmpty() ? activity.getString(hk.jiakan.tv.R.string.guide_no_data_status,range,result.source) : activity.getString(hk.jiakan.tv.R.string.guide_status,range,programmes.size(),result.stale ? "显示本日缓存，可按重试 · " : "",result.source));
    }
    private void select(Programme programme) {
        long now=System.currentTimeMillis();
        if(programme.startUtcMillis>now) { Toast.makeText(activity,"这是节目预告，尚未播出",Toast.LENGTH_SHORT).show(); return; }
        if(programme.endUtcMillis>now) { dialog.dismiss(); listener.live(channel); return; }
        if(programme.replayId.isEmpty()) {
            if(!provider.officialReplayPage(programme).isEmpty()) officialReplay(provider.officialReplayPage(programme));
            else status.setText("这个节目暂未提供回看；可选标有“查询回看”或“官方重温页面”的节目。");
            return;
        }
        int token=++generation; cancelPending(); status.setText("正在确认回看资源…");
        final SafeHttp.Request operation=new SafeHttp.Request(); request=operation;
        pending=worker.submit(()-> {
            try {
                List<Channel.Source> sources=provider.resolve(programme,operation);
                main.post(()-> {
                    if(!current(token)) return;
                    if(sources.isEmpty()) { status.setText("这个节目暂无可用回看，仍可选择其他节目。"); return; }
                    dialog.dismiss(); listener.replay(programme,sources.get(0));
                });
            } catch(Exception e) { main.post(()->{ if(current(token)) status.setText("回看资源暂时无法获取，请稍后重试。"); }); }
        });
    }
    private void officialReplay(String page) {
        String url=page;
        externalDialog=new AlertDialog.Builder(activity).setTitle("TVB 官方重温")
                .setMessage("将打开 myTV SUPER 的节目页面。可看集数和免费范围以官方为准；这不是本应用内直接回放。按返回可回到家看电视。")
                .setNegativeButton("留在节目单",null).setPositiveButton("打开官方页面",(d,w)-> {
                    try { activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(url))); }
                    catch(android.content.ActivityNotFoundException e) { status.setText("未找到可打开官方页面的应用；直播与节目单仍可使用。"); }
                }).show();
    }
    private int dp(int n) { return (int)(n*activity.getResources().getDisplayMetrics().density+0.5f); }
}
