package hk.jiakan.tv.playback;

import hk.jiakan.core.Channel;
import java.util.*;

/** Stable selection intent, independent of Android views, network and player instances. */
public final class PlaybackSession {
    public enum Kind {NONE,LIVE,MISSING}
    public static final class Decision {
        public final Kind kind; public final Channel channel;
        Decision(Kind kind,Channel channel){this.kind=kind;this.channel=channel;}
    }
    private final Map<String,Channel> channels=new LinkedHashMap<>();
    private String selected="";
    private boolean ready,pending=true;
    public String selectedId(){return selected;}
    public void catalog(List<Channel> latest,String lastChannel) {
        channels.clear();for(Channel channel:latest)channels.put(channel.id,channel);
        if(!ready || selected.isEmpty()) {
            pending=true;
            if(selected.isEmpty())selected=lastChannel;
            if(!channels.containsKey(selected)) {
                selected="";
                for(Channel channel:latest)if(!channel.sources.isEmpty()){selected=channel.id;break;}
            }
        }
        ready=true;
    }
    public void select(String id){selected=id;pending=true;}
    public void suspend(){pending=true;}
    public Decision resume(boolean foreground,boolean maintenance,boolean downloading) {
        if(!ready || !pending || !foreground || maintenance || downloading)return new Decision(Kind.NONE,null);
        pending=false;Channel channel=channels.get(selected);
        return new Decision(channel==null?Kind.MISSING:Kind.LIVE,channel);
    }
}
