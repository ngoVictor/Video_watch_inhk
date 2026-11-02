package hk.jiakan.tv.update;

import java.io.IOException;

/** A trusted local validation result; never stores untrusted server text. */
public final class UpdateVerificationException extends IOException {
    public enum Reason { HASH, PACKAGE, VERSION, SIGNATURE, SDK }
    public final Reason reason;
    public UpdateVerificationException(Reason reason,String message) { super(message); this.reason=reason; }
}
