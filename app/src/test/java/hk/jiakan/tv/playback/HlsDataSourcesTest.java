package hk.jiakan.tv.playback;
import androidx.media3.common.C;
import androidx.media3.datasource.*;
import android.net.Uri;
import org.junit.Test;
import static org.junit.Assert.*;
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public class HlsDataSourcesTest {
    @Test public void onlyManifestTypeGetsCacheRevalidationWrapper() {
        DataSource raw=new DataSource(){
            public void addTransferListener(TransferListener listener){}
            public long open(DataSpec spec){return 0;}
            public int read(byte[] buffer,int offset,int length){return -1;}
            public Uri getUri(){return null;}
            public void close(){}
        };
        HlsDataSources factory=new HlsDataSources(()->raw);
        assertTrue(factory.createDataSource(C.DATA_TYPE_MANIFEST) instanceof ResolvingDataSource);
        assertSame(raw,factory.createDataSource(C.DATA_TYPE_MEDIA));
        assertSame(raw,factory.createDataSource(C.DATA_TYPE_DRM));
    }
}
