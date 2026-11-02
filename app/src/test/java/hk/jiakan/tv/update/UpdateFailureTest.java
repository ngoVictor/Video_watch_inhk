package hk.jiakan.tv.update;

import hk.jiakan.tv.net.SafeHttp;
import hk.jiakan.tv.net.UpdateAccessException;
import java.io.IOException;
import java.net.*;
import javax.net.ssl.SSLHandshakeException;
import org.json.JSONException;
import org.junit.Test;
import static org.junit.Assert.*;

public class UpdateFailureTest {
    @Test public void separatesDeviceCredentialsFromServerPermissions() {
        assertEquals("CREDENTIAL_READ",UpdateFailure.from(new UpdateAccessException(false)).code);
        assertEquals("CREDENTIAL_SAVE",UpdateFailure.from(new UpdateAccessException(true)).code);
        for(int status:new int[]{401,403,404,429,500}) {
            UpdateFailure failure=UpdateFailure.from(new SafeHttp.HttpException(status));
            assertEquals("HTTP_"+status,failure.code);
        }
        assertTrue(UpdateFailure.from(new SafeHttp.HttpException(404)).hint.contains("或"));
        assertFalse(UpdateFailure.from(new SafeHttp.HttpException(403)).hint.contains("已到期"));
    }
    @Test public void identifiesNetworkAndManifestProblemsWithoutRawMessages() {
        Throwable[] failures={new UnknownHostException("secret"),new SocketTimeoutException("secret"),
                new ConnectException("secret"),new SSLHandshakeException("secret"),new JSONException("secret"),new IllegalArgumentException("secret")};
        String[] codes={"DNS","TIMEOUT","CONNECT","TLS","FORMAT","FORMAT"};
        for(int i=0;i<failures.length;i++) {
            UpdateFailure failure=UpdateFailure.from(new IOException("outer secret",failures[i]));
            assertEquals(codes[i],failure.code); assertFalse(failure.display().contains("secret"));
        }
    }
    @Test public void verificationReasonsRemainDistinctAndDoNotExposeMessages() {
        for(UpdateVerificationException.Reason reason:UpdateVerificationException.Reason.values()) {
            UpdateFailure failure=UpdateFailure.from(new UpdateVerificationException(reason,"https://example.org/?token=secret"));
            assertEquals("VERIFY_"+reason.name(),failure.code);assertFalse(failure.display().contains("secret"));
        }
    }
    @Test public void handlesUnknownNullAndCyclicErrorsSafely() {
        IOException error=new IOException("Bearer secret https://private.example/?token=secret");
        assertEquals("OTHER",UpdateFailure.from(error).code);
        assertFalse(UpdateFailure.from(error).display().contains("secret"));
        assertEquals("OTHER",UpdateFailure.from(null).code);
        Throwable cycle=new Throwable(){@Override public synchronized Throwable getCause(){return this;}};
        assertEquals("OTHER",UpdateFailure.from(cycle).code);
    }
}
