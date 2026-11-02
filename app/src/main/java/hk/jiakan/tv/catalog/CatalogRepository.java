package hk.jiakan.tv.catalog;

import android.content.Context;
import android.util.AtomicFile;
import hk.jiakan.tv.net.SafeHttp;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class CatalogRepository {
    private final Context context;
    private final AtomicFile file;
    private final CatalogParser parser=new CatalogParser();
    private Catalog catalog;
    interface Loader { String load(String url) throws Exception; }
    private final Loader loader;
    public CatalogRepository(Context context) {
        this(context,SafeHttp.NO_AUTH);
    }
    public CatalogRepository(Context context,SafeHttp.RequestHeaders headers) {
        this(context,(Loader)url->SafeHttp.text(url,CatalogParser.MAX_BYTES,headers));
    }
    CatalogRepository(Context context,Loader loader) {
        this.loader=loader;
        this.context=context.getApplicationContext(); file=new AtomicFile(new File(context.getFilesDir(),"channels.json"));
    }
    public synchronized Catalog load() throws Exception {
        if(catalog!=null) return catalog;
        Catalog bundled;
        try(InputStream in=context.getAssets().open("channels.json")) { bundled=parser.parseBundled(readBounded(in)); }
        try(InputStream in=file.openRead()) {
            Catalog cached=parser.parse(readBounded(in));
            catalog=cached.revision>=bundled.revision ? cached : bundled;
        } catch(Exception e) { catalog=bundled; }
        return catalog;
    }
    public synchronized Catalog refresh(String url) throws Exception {
        Catalog old=load();
        String raw=loader.load(url);
        Catalog fresh=parser.parse(raw);
        if(!old.channels.isEmpty() && fresh.revision<=old.revision) return old;
        FileOutputStream out=null;
        try {
            out=file.startWrite(); out.write(raw.getBytes(StandardCharsets.UTF_8)); file.finishWrite(out);
        } catch(Exception e) { if(out!=null) file.failWrite(out); throw e; }
        catalog=fresh; return fresh;
    }
    private String readBounded(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] bytes=new byte[8192]; int count;
        while((count=in.read(bytes))!=-1) {
            if(out.size()+count>CatalogParser.MAX_BYTES) throw new IOException("目录过大");
            out.write(bytes,0,count);
        }
        return out.toString("UTF-8");
    }
}
