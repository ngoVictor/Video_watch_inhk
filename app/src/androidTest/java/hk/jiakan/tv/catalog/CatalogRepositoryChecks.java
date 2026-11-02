package hk.jiakan.tv.catalog;

import android.content.Context;
import android.content.ContextWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Exercises real repository cache selection/refresh with an injected text loader, no HTTPS traffic. */
public final class CatalogRepositoryChecks {
    private int checks;
    private void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private void write(File file,String raw)throws IOException{try(FileOutputStream out=new FileOutputStream(file)){out.write(raw.getBytes(StandardCharsets.UTF_8));}}
    private String read(File file)throws IOException{try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    public static int run(Context context)throws Exception{return new CatalogRepositoryChecks().verify(context);}
    private int verify(Context context)throws Exception {
        long bundled=new CatalogRepository(context).load().revision;
        File dir=new File(context.getCacheDir(),"catalog-contract-"+System.nanoTime());check(dir.mkdirs(),"test directory unavailable");
        Context scoped=new ContextWrapper(context){@Override public Context getApplicationContext(){return this;}@Override public File getFilesDir(){return dir;}};
        try {
            JSONObject source=new JSONObject().put("id","s1").put("url","https://example.org/live.m3u8").put("priority",80);
            JSONObject channel=new JSONObject().put("id","one").put("name","fixture").put("group","央视").put("sources",new JSONArray().put(source));
            JSONObject valid=new JSONObject().put("schemaVersion",1).put("revision",bundled+1).put("channels",new JSONArray().put(channel));
            String old=valid.toString();File target=new File(dir,"channels.json");write(target,old);
            JSONObject second=new JSONObject(channel.toString()).put("id","two").put("sources",new JSONArray().put(new JSONObject(source.toString()).put("id","s2")));
            String invalid=new JSONObject(valid.toString()).put("revision",bundled+2).put("channels",new JSONArray().put(channel).put(second)).toString();
            CatalogRepository repository=new CatalogRepository(scoped,(CatalogRepository.Loader)url->invalid);Catalog previous=repository.load();
            check(previous.revision==bundled+1,"valid cache not loaded");
            try{repository.refresh("https://example.org/catalog");throw new AssertionError("duplicate URL accepted");}catch(JSONException expected){checks++;}
            check(repository.load()==previous,"failed refresh replaced in-memory catalog");check(read(target).equals(old),"failed refresh replaced cached bytes");
            write(target,invalid);check(new CatalogRepository(scoped).load().revision==bundled,"invalid startup cache did not fall back to bundled");
        } finally {File[] files=dir.listFiles();if(files!=null)for(File f:files)f.delete();dir.delete();}
        return checks;
    }
}
