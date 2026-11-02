package hk.jiakan.tv.net;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** HTTPS transfers with a per-operation cancellation handle and a shared redirect budget. */
public final class SafeHttp {
    public static final class HttpException extends IOException {
        public final int status;
        public HttpException(int status) { super("服务器返回 HTTP "+status); this.status=status; }
    }
    public interface RequestHeaders { java.util.Map<String,String> forUri(URI uri) throws IOException; }
    interface ConnectionFactory { HttpURLConnection open(URI uri) throws IOException; }
    interface Clock { long nanos(); }
    public static final RequestHeaders NO_AUTH=uri -> java.util.Collections.emptyMap();
    private static final ExecutorService DISCONNECT=Executors.newCachedThreadPool(r -> {
        Thread t=new Thread(r,"http-disconnect"); t.setDaemon(true); return t;
    });
    /** One operation only. Cancellation never blocks the UI waiting for a socket lock. */
    public static final class Request {
        private static final int ACTIVE=0, COMMITTING=1, CANCELLED=2;
        private final AtomicInteger state=new AtomicInteger(ACTIVE);
        private HttpURLConnection connection;
        public void cancel() {
            HttpURLConnection current;
            synchronized(this) { state.set(CANCELLED); current=connection; }
            if(current!=null) DISCONNECT.execute(current::disconnect);
        }
        public boolean isCancelled() { return state.get()==CANCELLED; }
        public void check() throws InterruptedIOException {
            if(isCancelled() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("请求已取消");
        }
        public interface Commit { void run() throws IOException; }
        /** Linearizes commit admission against cancel, without holding a lock during disk I/O.
         * Cancellation after admission cannot undo that commit, but still suppresses its result. */
        public void commit(Commit action) throws IOException {
            check();
            if(!state.compareAndSet(ACTIVE,COMMITTING)) {
                check();
                throw new IllegalStateException("Operation already committing");
            }
            try { action.run(); }
            finally { state.compareAndSet(COMMITTING,ACTIVE); }
        }
        private synchronized void attach(HttpURLConnection current) throws IOException {
            check(); connection=current;
        }
        private synchronized void detach(HttpURLConnection current) { if(connection==current) connection=null; }
    }
    private SafeHttp() {}
    public static URI validate(String url, boolean httpsOnly) {
        try {
            URI uri=new URI(url); String scheme=uri.getScheme();
            if (!("https".equalsIgnoreCase(scheme) || (!httpsOnly && "http".equalsIgnoreCase(scheme)))
                    || uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null)
                throw new IllegalArgumentException("请输入有效的"+(httpsOnly ? " HTTPS" : " HTTP/HTTPS")+"地址");
            return uri;
        } catch(URISyntaxException e) { throw new IllegalArgumentException("地址格式无效"); }
    }
    public static String text(String url,int limit) throws IOException { return text(url,limit,NO_AUTH); }
    public static String text(String url,int limit,RequestHeaders headers) throws IOException { return text(url,limit,headers,new Request()); }
    public static String text(String url,int limit,RequestHeaders headers,Request request) throws IOException {
        return text(url,limit,headers,request,30_000);
    }
    public static String text(String url,int limit,RequestHeaders headers,Request request,long maxMillis) throws IOException {
        if(maxMillis<=0 || maxMillis>30_000) throw new IllegalArgumentException("文本请求时限无效");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        transfer(url,out,limit,maxMillis,headers,request,uri -> (HttpURLConnection)uri.toURL().openConnection(),System::nanoTime);
        return out.toString(StandardCharsets.UTF_8.name());
    }
    public static void download(String url,File target,int limit) throws IOException { download(url,target,limit,NO_AUTH); }
    public static void download(String url,File target,int limit,RequestHeaders headers) throws IOException { download(url,target,limit,headers,new Request()); }
    public static void download(String url,File target,int limit,RequestHeaders headers,Request request) throws IOException {
        request.check();
        try(FileOutputStream out=new FileOutputStream(target)) {
            transfer(url,out,limit,300_000,headers,request,uri -> (HttpURLConnection)uri.toURL().openConnection(),System::nanoTime);
        } catch(IOException|RuntimeException e) { if(target.exists() && !target.delete()) target.deleteOnExit(); throw e; }
    }
    private static int remaining(long deadline,Clock clock,Request request) throws IOException {
        request.check(); long nanos=deadline-clock.nanos();
        if(nanos<=0) throw new SocketTimeoutException("请求总时限已到");
        return (int)Math.min(8000,Math.max(1,(nanos+999999)/1_000_000));
    }
    static void transfer(String url,OutputStream out,int limit,long maxMillis,RequestHeaders headers,
                         Request request,ConnectionFactory factory,Clock clock) throws IOException {
        request.check(); URI uri=validate(url,true);
        long deadline=clock.nanos()+maxMillis*1_000_000;
        for(int redirect=0;redirect<=5;redirect++) {
            remaining(deadline,clock,request);
            HttpURLConnection c=factory.open(uri);
            try {
                request.attach(c);
                c.setInstanceFollowRedirects(false); c.setUseCaches(false);
                c.setConnectTimeout(remaining(deadline,clock,request)); c.setReadTimeout(remaining(deadline,clock,request));
                c.setRequestProperty("User-Agent","JiaKanTV/0.2");
                for(java.util.Map.Entry<String,String> h:headers.forUri(uri).entrySet()) c.setRequestProperty(h.getKey(),h.getValue());
                remaining(deadline,clock,request);
                int status=c.getResponseCode();
                remaining(deadline,clock,request);
                if(status>=300 && status<400) {
                    String location=c.getHeaderField("Location");
                    if(location==null) throw new IOException("更新服务器重定向无效");
                    uri=validate(uri.resolve(location).toString(),true); continue;
                }
                if(status!=200) throw new HttpException(status);
                String length=c.getHeaderField("Content-Length");
                if(length!=null) {
                    try { long count=Long.parseLong(length); if(count<0 || count>limit) throw new IOException("下载长度超出限制"); }
                    catch(NumberFormatException e) { throw new IOException("下载长度无效"); }
                }
                int total=0;
                try(InputStream in=c.getInputStream()) {
                    byte[] buf=new byte[8192];
                    while(true) {
                        c.setReadTimeout(remaining(deadline,clock,request));
                        int n=in.read(buf);
                        remaining(deadline,clock,request);
                        if(n==-1) break;
                        if(n>limit-total) throw new IOException("下载内容超出大小限制");
                        total+=n; out.write(buf,0,n);
                    }
                }
                remaining(deadline,clock,request); return;
            } finally { request.detach(c); c.disconnect(); }
        }
        throw new IOException("重定向过多");
    }
}
