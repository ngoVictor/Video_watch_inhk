package hk.jiakan.tv.favorites;

import android.content.Context;
import android.content.SharedPreferences;
import hk.jiakan.core.FutureFeatures.FavoritesRepository;
import java.util.*;

public final class LocalFavoritesRepository implements FavoritesRepository {
    private final SharedPreferences preferences;
    public LocalFavoritesRepository(Context context) { preferences=context.getSharedPreferences("favorites",Context.MODE_PRIVATE); }
    @Override public List<String> orderedChannelIds() {
        try { return FavoritesCodec.decode(preferences.getString("order","[]")); }
        catch(Exception e) { return new ArrayList<>(); }
    }
    @Override public void saveOrder(List<String> ids) { preferences.edit().putString("order",FavoritesCodec.encode(ids)).apply(); }
    public boolean contains(String id) { return orderedChannelIds().contains(id); }
    public void toggle(String id) { List<String> ids=orderedChannelIds(); if(!ids.remove(id)) ids.add(id); saveOrder(ids); }
    public void moveToTop(String id) { List<String> ids=orderedChannelIds(); ids.remove(id); ids.add(0,id); saveOrder(ids); }
}
