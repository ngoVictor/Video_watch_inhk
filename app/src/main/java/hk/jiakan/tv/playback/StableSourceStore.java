package hk.jiakan.tv.playback;

import android.content.Context;
import android.content.SharedPreferences;
import hk.jiakan.core.Channel;
import hk.jiakan.core.FailoverPolicy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Optional, bounded device storage. Core cooldown timestamps never enter preferences. */
public final class StableSourceStore {
    private final SharedPreferences prefs;
    public StableSourceStore(Context context) { prefs=context.getSharedPreferences("stable-sources",Context.MODE_PRIVATE); }
    public void restore(Channel channel,FailoverPolicy policy,long monotonicNow,long wallNow) {
        try {
            String raw=prefs.getString(channel.id,"");StableSourcePreference saved=StableSourcePreference.decode(raw);
            Channel.Source match=saved==null ? null : saved.match(channel,wallNow);
            if(match!=null) policy.restorePreferred(channel,match,monotonicNow-(wallNow-saved.savedAt));
            else if(!raw.isEmpty()) prefs.edit().remove(channel.id).apply();
        } catch(RuntimeException unavailable) { /* Preference storage must not prevent viewing. */ }
    }
    public void update(Channel channel,Channel.Source preferred,long wallNow,long confirmedWallAt) {
        try {
            String raw=prefs.getString(channel.id,"");
            if(preferred==null) { if(!raw.isEmpty()) prefs.edit().remove(channel.id).apply(); return; }
            if(confirmedWallAt<0 || confirmedWallAt>wallNow) return;
            StableSourcePreference old=StableSourcePreference.decode(raw);
            Channel.Source previous=old==null ? null : old.match(channel,wallNow);
            if(previous!=null && previous.id.equals(preferred.id) && previous.url.equals(preferred.url)
                    && confirmedWallAt-old.savedAt<3_600_000L) return;
            // The core returns the exact current catalog source; validate even this boundary.
            StableSourcePreference next=new StableSourcePreference(channel.id,preferred,confirmedWallAt);
            if(next.match(channel,wallNow)==null) return;
            SharedPreferences.Editor edit=prefs.edit().putString(channel.id,next.encode());
            List<StableSourcePreference> others=new ArrayList<>();
            for(Map.Entry<String,?> entry:prefs.getAll().entrySet()) {
                if(entry.getKey().equals(channel.id)) continue;
                StableSourcePreference item=entry.getValue() instanceof String ? StableSourcePreference.decode((String)entry.getValue()) : null;
                if(item==null || !item.channelId.equals(entry.getKey()) || wallNow<item.savedAt || wallNow-item.savedAt>86_400_000L) edit.remove(entry.getKey());
                else others.add(item);
            }
            others.sort(Comparator.comparingLong(item->item.savedAt));
            for(int i=0;i<others.size()-127;i++) edit.remove(others.get(i).channelId);
            edit.apply();
        } catch(RuntimeException unavailable) { /* Keep playback independent of optional persistence. */ }
    }
}
