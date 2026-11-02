package hk.jiakan.tv.update;
import hk.jiakan.tv.net.SafeHttp;
import org.junit.Test;
import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
public class DownloadCoordinatorTest {
    @Test public void cancellationKeepsPlaybackBlockedUntilWorkerCleanupCompletes() throws Exception {
        DownloadCoordinator d=new DownloadCoordinator();ExecutorService worker=Executors.newSingleThreadExecutor();
        CountDownLatch running=new CountDownLatch(1),release=new CountDownLatch(1),finished=new CountDownLatch(1);
        AtomicBoolean cleaned=new AtomicBoolean(),observed=new AtomicBoolean();
        d.observe(()->{observed.set(cleaned.get() && !d.busy());finished.countDown();},Runnable::run);
        try {
            SafeHttp.Request r=d.start(worker,Runnable::run,request->{try{running.countDown();release.await();request.check();return new File("unused");}finally{cleaned.set(true);}},result->assertTrue(result.cancelled));
            assertTrue(running.await(2,TimeUnit.SECONDS));r.cancel();assertTrue(d.busy());assertFalse(cleaned.get());
            try{d.start(worker,Runnable::run,q->null,result->{});fail();}catch(IllegalStateException expected){}
            release.countDown();assertTrue(finished.await(2,TimeUnit.SECONDS));assertTrue(observed.get());
        }finally{release.countDown();worker.shutdownNow();}
    }
    @Test public void cancelledQueuedWorkStillNotifiesCompletion(){
        DownloadCoordinator d=new DownloadCoordinator();AtomicReference<Runnable> queued=new AtomicReference<>();AtomicBoolean completed=new AtomicBoolean();
        SafeHttp.Request r=d.start(queued::set,Runnable::run,q->{throw new AssertionError("cancelled work ran");},result->completed.set(result.cancelled));
        r.cancel();assertTrue(d.busy());queued.get().run();assertFalse(d.busy());assertTrue(completed.get());
    }
    @Test public void detachedActivityDoesNotObserveNewCompletion(){
        DownloadCoordinator d=new DownloadCoordinator();AtomicBoolean called=new AtomicBoolean();Runnable observer=()->called.set(true);d.observe(observer,Runnable::run);d.remove(observer);
        d.start(Runnable::run,Runnable::run,q->null,result->{});assertFalse(called.get());assertFalse(d.busy());
    }
    @Test public void rejectedExecutionDoesNotLeaveDownloadBusy(){
        DownloadCoordinator d=new DownloadCoordinator();try{d.start(r->{throw new RejectedExecutionException();},Runnable::run,q->null,result->{});fail();}catch(RejectedExecutionException expected){}
        assertFalse(d.busy());
    }
}
