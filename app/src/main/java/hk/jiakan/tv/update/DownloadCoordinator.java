package hk.jiakan.tv.update;

import hk.jiakan.tv.net.SafeHttp;
import java.io.File;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** App-process download ownership; completion means the worker has released its resources. */
public final class DownloadCoordinator {
    public interface Work { File run(SafeHttp.Request request) throws Exception; }
    public static final class Result {
        public final File file; public final Exception error; public final boolean cancelled;
        Result(File file,Exception error,boolean cancelled) {this.file=file;this.error=error;this.cancelled=cancelled;}
    }
    private SafeHttp.Request active;
    private final Map<Runnable,Executor> listeners=new LinkedHashMap<>();
    public synchronized boolean busy() {return active!=null;}
    public synchronized void observe(Runnable listener,Executor callback) {listeners.put(listener,callback);}
    public synchronized void remove(Runnable listener) {listeners.remove(listener);}
    public synchronized SafeHttp.Request start(Executor worker,Executor callback,Work work,Consumer<Result> completed) {
        if(active!=null) throw new IllegalStateException("Download already active");
        SafeHttp.Request request=new SafeHttp.Request();active=request;
        try {
            worker.execute(()-> {
                File file=null;Exception error=null;
                try {request.check();file=work.run(request);request.check();}catch(Exception e){error=e;}
                Result result=new Result(request.isCancelled()?null:file,error,request.isCancelled());
                Map<Runnable,Executor> notify;
                synchronized(this){active=null;notify=new LinkedHashMap<>(listeners);}
                callback.execute(()->completed.accept(result));
                for(Map.Entry<Runnable,Executor> entry:notify.entrySet())entry.getValue().execute(entry.getKey());
            });
        } catch(RuntimeException rejected) {active=null;throw rejected;}
        return request;
    }
}
