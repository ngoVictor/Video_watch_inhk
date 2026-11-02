package hk.jiakan.tv.playback;

import hk.jiakan.core.Channel;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public class StableSourcePreferenceTest {
    private Channel channel(String id,String url) {
        return new Channel(id,id,"央视","",Collections.singletonList(new Channel.Source("s",url,"","",80)));
    }
    @Test public void persistedPreferenceMatchesCurrentCatalogAndDoesNotExposeUrl() {
        Channel channel=channel("c","https://example.org/live?token=secret");
        String encoded=new StableSourcePreference(channel.id,channel.sources.get(0),1000).encode();
        assertFalse(encoded.contains("secret"));assertFalse(encoded.contains("https"));
        StableSourcePreference saved=StableSourcePreference.decode(encoded);
        assertSame(channel.sources.get(0),saved.match(channel,2000));
        assertNull(saved.match(channel("other",channel.sources.get(0).url),2000));
        assertNull(saved.match(channel("c","https://example.org/replaced"),2000));
        assertNull(saved.match(new Channel("c","c","央视","",Collections.emptyList()),2000));
    }
    @Test public void staleFutureAndMalformedValuesAreIgnored() {
        Channel c=channel("c","https://example.org/live");
        StableSourcePreference saved=new StableSourcePreference(c.id,c.sources.get(0),1000);
        assertNull(saved.match(c,999));assertNull(saved.match(c,86_401_001));
        assertNull(StableSourcePreference.decode("not json"));assertNull(StableSourcePreference.decode("{}"));
        assertNull(StableSourcePreference.decode(saved.encode().replace("1000","-1")));
    }
}
