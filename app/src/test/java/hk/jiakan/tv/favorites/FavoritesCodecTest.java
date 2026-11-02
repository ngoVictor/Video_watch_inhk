package hk.jiakan.tv.favorites;
import org.junit.Test;
import org.json.JSONException;
import java.util.*;
import static org.junit.Assert.*;
public class FavoritesCodecTest {
    @Test public void roundTripPreservesOrderAndDeduplicates() throws Exception {
        assertEquals(Arrays.asList("CCTV5.cn","rthk31"),FavoritesCodec.decode(FavoritesCodec.encode(Arrays.asList("CCTV5.cn","rthk31","CCTV5.cn"))));
    }
    @Test(expected=JSONException.class) public void nonStringIdIsRejected() throws Exception { FavoritesCodec.decode("[123]"); }
    @Test(expected=JSONException.class) public void malformedDataIsRejected() throws Exception { FavoritesCodec.decode("[\"../other\"]"); }
}
