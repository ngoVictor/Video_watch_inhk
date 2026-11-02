package hk.jiakan.tv.net;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import static org.junit.Assert.*;

public class RequestCommitTest {
    @Test public void cancelledRequestNeverStartsCommit() throws Exception {
        SafeHttp.Request request=new SafeHttp.Request();request.cancel();
        try { request.commit(()->fail("cancelled commit executed"));fail(); } catch(InterruptedIOException expected) {}
    }
    @Test public void cancelDoesNotWaitForAdmittedCommitAndCannotReopenOperation() throws Exception {
        SafeHttp.Request request=new SafeHttp.Request();CountDownLatch admitted=new CountDownLatch(1),finish=new CountDownLatch(1);
        ExecutorService worker=Executors.newSingleThreadExecutor();AtomicBoolean committed=new AtomicBoolean();
        try {
            Future<?> task=worker.submit(()->{
                try {request.commit(()->{admitted.countDown();try {if(!finish.await(3,TimeUnit.SECONDS))throw new IOException("timeout");}catch(InterruptedException e){throw new IOException(e);}committed.set(true);});}
                catch(IOException e){throw new RuntimeException(e);}
            });
            assertTrue(admitted.await(2,TimeUnit.SECONDS));
            request.cancel();assertTrue(request.isCancelled());assertFalse(task.isDone());
            finish.countDown();task.get(3,TimeUnit.SECONDS);assertTrue(committed.get());
            try {request.commit(()->fail());fail();}catch(InterruptedIOException expected){}
            try {request.check();fail();}catch(InterruptedIOException expected){}
        } finally {finish.countDown();worker.shutdownNow();}
    }
    @Test public void failedCommitReleasesAdmissionAndInterruptedCommitIsRejected() throws Exception {
        SafeHttp.Request request=new SafeHttp.Request();
        try {request.commit(()->{throw new IOException("disk");});fail();}catch(IOException expected){}
        request.commit(()->{});
        Thread.currentThread().interrupt();
        try {request.commit(()->fail());fail();}catch(InterruptedIOException expected){}finally {Thread.interrupted();}
    }
}
