package hk.jiakan.tv.net;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
public class SafeHttpTest {
    private static final String ENDPOINT="https://api.github.com/repos/example/catalog/contents/channels.json?ref=main";
    private static final String NEXT="https://api.github.com/repos/example/catalog/contents/other.json?ref=main";
    private static class Connection extends HttpURLConnection {
        int status=200; String location,length; InputStream input=new ByteArrayInputStream(new byte[0]); boolean disconnected;
        Runnable response=()->{};
        Connection(String url) throws Exception {super(new URL(url));}
        public void disconnect(){disconnected=true;}
        public boolean usingProxy(){return false;}
        public void connect(){}
        public int getResponseCode(){response.run();return status;}
        public String getHeaderField(String name){return name.equals("Location")?location:name.equals("Content-Length")?length:null;}
        public InputStream getInputStream(){return input;}
    }
    private byte[] transfer(SafeHttp.Request request,SafeHttp.ConnectionFactory factory,SafeHttp.Clock clock,int limit,long millis) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        SafeHttp.transfer(ENDPOINT,out,limit,millis,u->GithubRequestPolicy.headers(u,ENDPOINT,"test_token"),request,factory,clock);
        return out.toByteArray();
    }
    @Test public void cancelledBeforeStartDoesNotOpen() throws Exception {
        SafeHttp.Request request=new SafeHttp.Request();request.cancel();AtomicInteger calls=new AtomicInteger();
        try {transfer(request,u->{calls.incrementAndGet();return null;},System::nanoTime,10,1000);fail();}catch(InterruptedIOException expected){}
        assertEquals(0,calls.get());
    }
    @Test public void interruptedBeforeStartDoesNotOpen() throws Exception {
        Thread.currentThread().interrupt();
        try {transfer(new SafeHttp.Request(),u->{throw new AssertionError();},System::nanoTime,10,1000);fail();}
        catch(InterruptedIOException expected){}finally{Thread.interrupted();}
    }
    @Test public void cancellationAfterHeadersRejectsEmptySuccess() throws Exception {
        SafeHttp.Request request=new SafeHttp.Request();Connection c=new Connection(ENDPOINT);c.response=request::cancel;
        try {transfer(request,u->c,System::nanoTime,10,1000);fail();}catch(InterruptedIOException expected){}
        assertTrue(c.disconnected);
    }
    @Test public void eofStillChecksCancellation() throws Exception {
        SafeHttp.Request request=new SafeHttp.Request();Connection c=new Connection(ENDPOINT);
        c.input=new InputStream(){public int read(){request.cancel();return -1;}};
        try {transfer(request,u->c,System::nanoTime,10,1000);fail();}catch(InterruptedIOException expected){}
    }
    @Test public void redirectsShareDeadline() throws Exception {
        AtomicLong clock=new AtomicLong();Connection c=new Connection(ENDPOINT);c.status=302;c.location="/next";c.response=()->clock.addAndGet(31_000_000_000L);
        AtomicInteger calls=new AtomicInteger();
        try {transfer(new SafeHttp.Request(),u->{calls.incrementAndGet();return c;},clock::get,10,30000);fail();}catch(SocketTimeoutException expected){}
        assertEquals(1,calls.get());assertTrue(c.disconnected);
    }
    @Test public void relativeRedirectThenCdnDropsAuthentication() throws Exception {
        Connection first=new Connection(ENDPOINT);first.status=302;first.location="other.json?ref=main";
        Connection second=new Connection(NEXT);second.status=302;second.location="https://release-assets.githubusercontent.com/file";
        Connection third=new Connection(second.location);third.input=new ByteArrayInputStream(new byte[]{1,2});
        List<URI> visited=new ArrayList<>();ArrayDeque<Connection> queue=new ArrayDeque<>(Arrays.asList(first,second,third));
        assertArrayEquals(new byte[]{1,2},transfer(new SafeHttp.Request(),u->{visited.add(u);return queue.remove();},System::nanoTime,10,30000));
        assertEquals(NEXT,visited.get(1).toString());assertNotNull(first.getRequestProperty("Authorization"));assertNull(second.getRequestProperty("Authorization"));assertNull(third.getRequestProperty("Authorization"));
        assertTrue(first.disconnected && second.disconnected && third.disconnected);
    }
    @Test public void insecureRedirectRejected() throws Exception {
        Connection c=new Connection(ENDPOINT);c.status=302;c.location="http://example.org/file";
        try {transfer(new SafeHttp.Request(),u->c,System::nanoTime,10,1000);fail();}catch(IllegalArgumentException expected){}
        assertTrue(c.disconnected);
    }
    @Test public void exposesHttpStatusWithoutReadingErrorBody() throws Exception {
        Connection c=new Connection(ENDPOINT){
            @Override public InputStream getInputStream(){throw new AssertionError("Do not read an error body");}
        };
        c.status=404;
        try {transfer(new SafeHttp.Request(),u->c,System::nanoTime,10,1000);fail();}
        catch(SafeHttp.HttpException expected){assertEquals(404,expected.status);}
        assertTrue(c.disconnected);
    }
    @Test public void sizeLimitRejectsDeclaredAndActualBytes() throws Exception {
        for(boolean declared:new boolean[]{true,false}) {
            Connection c=new Connection(ENDPOINT);c.length=declared?"11":null;c.input=new ByteArrayInputStream(new byte[11]);
            try {transfer(new SafeHttp.Request(),u->c,System::nanoTime,10,1000);fail();}catch(IOException expected){}
            assertTrue(c.disconnected);
        }
    }
    @Test public void activeCancelDisconnectsBlockedReadAndWorkerCompletes() throws Exception {
        CountDownLatch reading=new CountDownLatch(1),closed=new CountDownLatch(1);SafeHttp.Request request=new SafeHttp.Request();
        Connection c=new Connection(ENDPOINT){public void disconnect(){super.disconnect();closed.countDown();}};
        c.input=new InputStream(){public int read() throws IOException {reading.countDown();try{if(!closed.await(2,TimeUnit.SECONDS))throw new IOException("not disconnected");}catch(InterruptedException e){throw new IOException(e);}return -1;}};
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try {
            Future<?> done=worker.submit(()->{try{transfer(request,u->c,System::nanoTime,10,5000);throw new AssertionError("cancelled transfer succeeded");}catch(InterruptedIOException expected){}catch(Exception e){throw new RuntimeException(e);}});
            assertTrue(reading.await(2,TimeUnit.SECONDS));request.cancel();done.get(3,TimeUnit.SECONDS);assertTrue(c.disconnected);
        }finally{worker.shutdownNow();}
    }
    @Test public void timeoutUsesRemainingBudget() throws Exception {
        AtomicLong clock=new AtomicLong();Connection c=new Connection(ENDPOINT);c.response=()->clock.set(9_500_000L);
        transfer(new SafeHttp.Request(),u->c,clock::get,10,10);assertEquals(1,c.getReadTimeout());
    }
}
