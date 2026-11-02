package hk.jiakan.tv.net;

import java.io.IOException;

/** A device credential failure, distinct from a server rejecting that credential. */
public final class UpdateAccessException extends IOException {
    public final boolean saving;
    public UpdateAccessException(boolean saving) {
        super(saving ? "无法加密保存更新凭据" : "无法读取更新凭据");
        this.saving=saving;
    }
}
