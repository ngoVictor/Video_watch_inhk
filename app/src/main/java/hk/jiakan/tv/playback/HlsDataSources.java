package hk.jiakan.tv.playback;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.ResolvingDataSource;
import androidx.media3.exoplayer.hls.HlsDataSourceFactory;
import java.util.Collections;

/** Media3 supplies the request type for both master and media playlists, regardless of URI. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
public final class HlsDataSources implements HlsDataSourceFactory {
    private final DataSource.Factory media,manifest;
    public HlsDataSources(DataSource.Factory media) {
        this.media=media;
        manifest=new ResolvingDataSource.Factory(media,spec -> spec.withAdditionalHeaders(Collections.singletonMap("Cache-Control","no-cache")));
    }
    @Override public DataSource createDataSource(int dataType) {
        return (dataType==C.DATA_TYPE_MANIFEST?manifest:media).createDataSource();
    }
}
