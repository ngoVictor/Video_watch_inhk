package hk.jiakan.core;

import java.util.*;

/** Browsing focus is a preview; only a confirmed channel changes the zapping scope. */
public final class ChannelNavigation {
    private String browsing="央视", playing="央视";
    private final Map<String,String> rows=new HashMap<>();
    public String browsingGroup() { return browsing; }
    public String playingGroup() { return playing; }
    public static String group(Channel channel) { return "地方".equals(channel.group) ? "卫视" : channel.group; }
    public void restore(Channel current,String savedScope) {
        playing=ChannelSelection.FAVORITES.equals(savedScope) ? savedScope : current==null ? "央视" : group(current);
        browsing=playing;
    }
    public void browse(String group) { browsing=Objects.requireNonNull(group); }
    public void remember(String id) { if(id!=null && !id.isEmpty()) rows.put(browsing,id); }
    public String focusedId(String current) { return rows.getOrDefault(browsing,current); }
    public void confirm(Channel channel,List<String> favorites) {
        playing=ChannelSelection.FAVORITES.equals(browsing) && favorites.contains(channel.id) ? ChannelSelection.FAVORITES : group(channel);
        rows.put(playing,channel.id);
    }
    public void showPlaying() { browsing=playing; }
    public void reconcile(Channel current) {
        if(current==null || ChannelSelection.FAVORITES.equals(playing)) return;
        String old=playing;playing=group(current);
        if(browsing.equals(old)) browsing=playing;
    }
    public Channel next(List<Channel> all,String current,int direction,List<String> favorites) {
        return ChannelSelection.next(ChannelSelection.filter(all,playing,favorites),current,direction);
    }
}
