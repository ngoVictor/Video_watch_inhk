package hk.jiakan.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Channel {
    public final String id, name, group, region;
    public final List<Source> sources;
    public Channel(String id, String name, String group, String region, List<Source> sources) {
        this.id = id; this.name = name; this.group = group; this.region = region;
        this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
    }
    public static final class Source {
        public final String id, url, upstream, mimeType;
        public final int priority;
        public Source(String id, String url, String upstream, String mimeType, int priority) {
            this.id = id; this.url = url; this.upstream = upstream;
            this.mimeType = mimeType; this.priority = priority;
        }
    }
}
