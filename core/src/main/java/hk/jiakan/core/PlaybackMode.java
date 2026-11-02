package hk.jiakan.core;

/** User preference, independent of platform track selection and network I/O. */
public enum PlaybackMode {
    QUALITY("画质优先", 12_000_000, 6_000_000, 8_000),
    SMOOTH("流畅优先", 8_000_000, 4_000_000, 6_000);

    public final String label;
    public final int normalBitrate, recoveryBitrate;
    public final long bufferTimeoutMs;
    PlaybackMode(String label, int normalBitrate, int recoveryBitrate, long bufferTimeoutMs) {
        this.label=label; this.normalBitrate=normalBitrate; this.recoveryBitrate=recoveryBitrate; this.bufferTimeoutMs=bufferTimeoutMs;
    }
    public int maxHeight(boolean recovering) { return recovering ? 720 : 1080; }
    public int maxBitrate(boolean recovering) { return recovering ? recoveryBitrate : normalBitrate; }
    public PlaybackMode toggle() { return this==QUALITY ? SMOOTH : QUALITY; }
    public static PlaybackMode fromStored(String value) {
        return SMOOTH.name().equals(value) ? SMOOTH : QUALITY;
    }
}
