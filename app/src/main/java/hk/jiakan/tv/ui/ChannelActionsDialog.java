package hk.jiakan.tv.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Toast;
import hk.jiakan.core.Channel;
import hk.jiakan.tv.favorites.LocalFavoritesRepository;

public final class ChannelActionsDialog {
    private ChannelActionsDialog() {}
    public static void show(Activity activity,Channel channel,LocalFavoritesRepository favorites,Runnable changed,Runnable guide,Runnable live) {
        boolean saved=favorites.contains(channel.id);
        String[] labels={saved ? "取消收藏" : "加入收藏","收藏并置顶","查看节目单 / 回看","观看直播"};
        new AlertDialog.Builder(activity).setTitle(channel.name).setItems(labels,(d,index)-> {
            if(index==0 || index==1) {
                try { if(index==0) favorites.toggle(channel.id); else favorites.moveToTop(channel.id); }
                catch(IllegalArgumentException e) { Toast.makeText(activity,"收藏已满，请先取消部分收藏",Toast.LENGTH_LONG).show(); return; }
                changed.run(); Toast.makeText(activity,index==1 ? "已置顶收藏" : saved ? "已取消收藏" : "已加入收藏",Toast.LENGTH_SHORT).show();
            } else if(index==2) guide.run(); else live.run();
        }).setNegativeButton("返回",null).show();
    }
}
