package hk.jiakan.tv.guide;

import android.util.AtomicFile;
import hk.jiakan.core.FutureFeatures.Programme;
import hk.jiakan.tv.net.SafeHttp;
import java.io.*;
import java.util.*;

/** Bounded, channel/day-specific cache, independent of the player and UI lifecycle. */
final class GuideCache {
    interface Fetch { String get() throws Exception; }
    interface Parse { List<Programme> get(String raw) throws Exception; }
    interface Source { String get(String raw) throws Exception; }
    interface FileFactory { AtomicFile open(File target); }
    private final File directory;
    private final FileFactory files;
    GuideCache(File directory) { this(directory,AtomicFile::new); }
    GuideCache(File directory,FileFactory files) { this.directory=directory;this.files=files; }
    GuideService.Result load(String key,int limit,String source,SafeHttp.Request request,boolean refresh,Fetch fetch,Parse parse) throws Exception {
        return load(key,limit,raw->source,request,refresh,true,fetch,parse);
    }
    GuideService.Result load(String key,int limit,Source source,SafeHttp.Request request,boolean refresh,boolean retryTransport,Fetch fetch,Parse parse) throws Exception {
        request.check();
        if(!directory.isDirectory() && !directory.mkdirs()) throw new IOException("节目缓存不可用");
        File target=new File(directory,key+".json"); List<Programme> cached=null;String cachedSource="";
        try(InputStream in=files.open(target).openRead(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192];int n;
            while((n=in.read(b))!=-1) { request.check();if(out.size()+n>limit) throw new IOException("缓存过大");out.write(b,0,n); }
            String raw=out.toString("UTF-8");List<Programme> rows=parse.get(raw);
            String label=source.get(raw);if(!rows.isEmpty()) {cached=rows;cachedSource=label;}
        } catch(Exception ignored) { /* Corrupt and empty caches cannot suppress a fresh request. */ }
        request.check();long age=System.currentTimeMillis()-target.lastModified();
        if(!refresh && cached!=null && age>=0 && age<20*60*1000L) return new GuideService.Result(cached,false,cachedSource);
        try {
            String raw;
            try { raw=fetch.get(); }
            catch(IOException first) { request.check();if(!retryTransport) throw first;raw=fetch.get(); }
            request.check();List<Programme> rows=parse.get(raw);String label=source.get(raw);
            request.check();
            if(rows.isEmpty()) return new GuideService.Result(cached==null ? rows : cached,cached!=null,cached==null ? label : cachedSource);
            byte[] bytes=raw.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if(bytes.length>limit) throw new IOException("节目缓存过大");
            save(target,bytes,request);
            request.check();return new GuideService.Result(rows,false,label);
        } catch(Exception e) { request.check();if(cached!=null) return new GuideService.Result(cached,true,cachedSource);throw e; }
    }
    private void save(File target,byte[] bytes,SafeHttp.Request request) throws IOException {
        AtomicFile file=files.open(target);FileOutputStream out=null;boolean committed=false;
        try {
            request.check();out=file.startWrite();
            request.check();out.write(bytes);request.check();out.getFD().sync();
            final FileOutputStream staged=out;
            request.commit(()->file.finishWrite(staged));
            committed=true;
        } catch(IOException diskFailure) {
            // Cache persistence is best effort; cancellation must never turn into success/stale data.
            request.check();
        } finally { if(out!=null && !committed) file.failWrite(out); }
        // Eviction is another side effect and needs its own admission after the file commit.
        if(committed) request.commit(()->trim(target));
    }
    private void trim(File target) {
        File[] entries=directory.listFiles((dir,name)->name.endsWith(".json"));
        if(entries!=null && entries.length>64) {
            Arrays.sort(entries,Comparator.comparingLong(File::lastModified));
            int remaining=entries.length;
            for(File entry:entries) {
                if(remaining<=64) break;
                if(!entry.equals(target) && entry.delete()) remaining--;
            }
        }
    }
}
