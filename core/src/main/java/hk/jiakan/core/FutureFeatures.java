package hk.jiakan.core;

import java.util.List;

/** Stable feature contracts; providers and storage remain outside core. */
public final class FutureFeatures {
    private FutureFeatures() {}
    public interface FavoritesRepository {
        List<String> orderedChannelIds();
        void saveOrder(List<String> channelIds);
    }
    public interface ProgramGuideProvider {
        List<Programme> query(String channelId, long fromUtcMillis, long untilUtcMillis) throws Exception;
    }
    public interface ReplayResolver {
        List<Channel.Source> resolve(Programme programme) throws Exception;
    }
    public static final class Programme {
        public final String channelId, title, replayId, officialReplayUrl;
        public final long startUtcMillis, endUtcMillis;
        public Programme(String channelId, String title, long start, long end) {
            this(channelId,title,start,end,"");
        }
        public Programme(String channelId, String title, long start, long end, String replayId) {
            this(channelId,title,start,end,replayId,"");
        }
        public Programme(String channelId, String title, long start, long end, String replayId,String officialReplayUrl) {
            this.channelId = channelId; this.title = title; startUtcMillis = start; endUtcMillis = end;
            this.replayId = replayId;
            this.officialReplayUrl = officialReplayUrl;
        }
    }
}
