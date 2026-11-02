package hk.jiakan.tv.guide;

import android.content.Context;
import android.util.AtomicFile;
import hk.jiakan.core.FutureFeatures.Programme;
import hk.jiakan.tv.net.SafeHttp;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Real Android AtomicFile checks; all data is synthetic and scoped to a temporary directory. */
public final class GuideCacheChecks {
    private int checks;
    private void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    private interface Work {void run() throws Exception;}
    private void cancelled(Work work)throws Exception {try{work.run();throw new AssertionError("cancelled operation returned data");}catch(InterruptedIOException expected){checks++;}}
    private List<Programme> rows(String raw) {return Collections.singletonList(new Programme("fixture",raw,1000,2000));}
    private String text(File target)throws IOException {try(InputStream in=new FileInputStream(target);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[128];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    private void write(File target,String raw)throws IOException {try(FileOutputStream out=new FileOutputStream(target)){out.write(raw.getBytes(StandardCharsets.UTF_8));}}
    private void cleanup(File directory) {File[] entries=directory.listFiles();if(entries!=null)for(File entry:entries)entry.delete();directory.delete();}
    public static int run(Context context)throws Exception {return new GuideCacheChecks().verify(context);}
    private int verify(Context context)throws Exception {
        for(String scenario:new String[]{"before","parse","empty","writing","committed","disk","fresh-cache","stale-cancel","success","clock-rollback"}) {
            File dir=new File(context.getCacheDir(),"cache-contract-"+scenario+"-"+System.nanoTime());check(dir.mkdirs(),"test directory unavailable");
            try {
                File target=new File(dir,"fixture.json");write(target,"old");check(target.setLastModified(System.currentTimeMillis()-3600000),"mtime setup failed");long stamp=target.lastModified();
                for(int i=0;i<65;i++){File f=new File(dir,"other-"+i+".json");write(f,"old");f.setLastModified(stamp-10000);}
                SafeHttp.Request request=new SafeHttp.Request();int[] starts={0},fetches={0};
                GuideCache cache=new GuideCache(dir,file->new AtomicFile(file){
                    @Override public FileOutputStream startWrite()throws IOException {
                        starts[0]++;if(scenario.equals("disk"))throw new IOException("fixture storage failure");
                        FileOutputStream stream=super.startWrite();
                        if(scenario.equals("writing")){stream.write(new byte[]{1,2,3});request.cancel();}
                        return stream;
                    }
                    @Override public void finishWrite(FileOutputStream stream) {
                        super.finishWrite(stream);if(scenario.equals("committed"))request.cancel();
                        if(scenario.equals("clock-rollback"))getBaseFile().setLastModified(1000);
                    }
                });
                if(scenario.equals("before"))request.cancel();
                if(scenario.equals("fresh-cache")){target.setLastModified(System.currentTimeMillis());stamp=target.lastModified();}
                GuideCache.Parse parse=raw->{
                    if(raw.equals("fresh") && (scenario.equals("parse") || scenario.equals("empty")) || raw.equals("old") && scenario.equals("fresh-cache"))request.cancel();
                    return scenario.equals("empty") && raw.equals("fresh") ? Collections.emptyList() : rows(raw);
                };
                GuideCache.Fetch fetch=()->{fetches[0]++;if(scenario.equals("stale-cancel")){request.cancel();throw new IOException("fixture offline");}return "fresh";};
                Work load=()->cache.load("fixture",1024,"fixture",request,!scenario.equals("fresh-cache"),fetch,parse);
                boolean completed=scenario.equals("success") || scenario.equals("clock-rollback");
                if(completed || scenario.equals("disk")) load.run();else cancelled(load);
                boolean saved=completed || scenario.equals("committed");
                check(text(target).equals(saved?"fresh":"old"),scenario+": cached content changed incorrectly");
                if(!saved)check(target.lastModified()==stamp,scenario+": old mtime changed");
                check(!new File(dir,"fixture.json.new").exists() && !new File(dir,"fixture.json.bak").exists(),scenario+": incomplete transaction remains");
                check(dir.listFiles().length==(completed?64:66),scenario+": cancelled/failed request evicted other caches or size bound was missed");
                if(Arrays.asList("before","parse","empty","fresh-cache","stale-cancel").contains(scenario))check(starts[0]==0,scenario+": unexpected write start");
                if(scenario.equals("before") || scenario.equals("fresh-cache"))check(fetches[0]==0,"cancelled cache hit reached network");
                if(saved){request.cancel();if(scenario.equals("clock-rollback"))target.setLastModified(System.currentTimeMillis());GuideService.Result next=new GuideCache(dir).load("fixture",1024,"fixture",new SafeHttp.Request(),false,()->{throw new AssertionError("saved cache not reused");},this::rows);check(next.programmes.get(0).title.equals("fresh"),"commit lost after cancellation");}
            } finally {cleanup(dir);}
        }
        return checks;
    }
}
