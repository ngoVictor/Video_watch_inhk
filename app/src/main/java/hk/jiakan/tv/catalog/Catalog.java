package hk.jiakan.tv.catalog;

import hk.jiakan.core.Channel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Catalog {
    public final long revision;
    public final List<Channel> channels;
    public Catalog(long revision, List<Channel> channels) {
        this.revision = revision; this.channels = Collections.unmodifiableList(new ArrayList<>(channels));
    }
}
