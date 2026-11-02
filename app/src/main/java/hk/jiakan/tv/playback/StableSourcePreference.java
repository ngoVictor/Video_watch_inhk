package hk.jiakan.tv.playback;

import hk.jiakan.core.Channel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Serializable identity hint, never a media URL or permission to bypass the catalog. */
final class StableSourcePreference {
    final String channelId,sourceId,urlHash;
    final long savedAt;
    StableSourcePreference(String channelId,Channel.Source source,long savedAt) {
        this(channelId,source.id,hash(source.url),savedAt);
    }
    private StableSourcePreference(String channelId,String sourceId,String urlHash,long savedAt) {
        this.channelId=channelId;this.sourceId=sourceId;this.urlHash=urlHash;this.savedAt=savedAt;
    }
    static StableSourcePreference decode(String value) {
        try {
            JSONObject json=new JSONObject(value);
            String channel=json.getString("channel"),source=json.getString("source"),hash=json.getString("urlHash");
            long time=json.getLong("savedAt");
            if(!channel.matches("[A-Za-z0-9._-]{1,100}") || !source.matches("[A-Za-z0-9._-]{1,100}")
                    || !hash.matches("[a-f0-9]{64}") || time<0) return null;
            return new StableSourcePreference(channel,source,hash,time);
        } catch(Exception invalid) { return null; }
    }
    String encode() {
        try { return new JSONObject().put("channel",channelId).put("source",sourceId).put("urlHash",urlHash).put("savedAt",savedAt).toString(); }
        catch(org.json.JSONException invalid) { throw new IllegalStateException(invalid); }
    }
    Channel.Source match(Channel channel,long now) {
        if(!channelId.equals(channel.id) || now<savedAt || now-savedAt>86_400_000L) return null;
        for(Channel.Source source:channel.sources) if(sourceId.equals(source.id) && urlHash.equals(hash(source.url))) return source;
        return null;
    }
    private static String hash(String value) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder();
            for(byte b:bytes) result.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            return result.toString();
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
