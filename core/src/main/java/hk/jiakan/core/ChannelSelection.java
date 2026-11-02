package hk.jiakan.core;

import java.util.*;

/** Selection uses stable IDs, never a previous catalog's row index. */
public final class ChannelSelection {
    public static final String FAVORITES="我的收藏";
    private ChannelSelection() {}
    public static List<Channel> filter(List<Channel> all,String group,List<String> favorites) {
        List<Channel> result=new ArrayList<>();
        if(FAVORITES.equals(group)) {
            Map<String,Channel> byId=new HashMap<>();
            for(Channel c:all) byId.put(c.id,c);
            for(String id:new LinkedHashSet<>(favorites)) if(byId.containsKey(id)) result.add(byId.get(id));
        } else for(Channel c:all) if(c.group.equals(group) || (group.equals("卫视") && c.group.equals("地方"))) result.add(c);
        return result;
    }
    public static Channel next(List<Channel> scope,String current,int direction) {
        List<Channel> playable=new ArrayList<>();
        for(Channel c:scope) if(!c.sources.isEmpty()) playable.add(c);
        if(playable.isEmpty()) return null;
        int at=-1;
        for(int i=0;i<playable.size();i++) if(playable.get(i).id.equals(current)) at=i;
        return playable.get(at<0 ? 0 : Math.floorMod(at+direction,playable.size()));
    }
}
