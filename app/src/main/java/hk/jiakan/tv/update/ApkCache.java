package hk.jiakan.tv.update;
import hk.jiakan.tv.net.SafeHttp;
import java.io.*;

/** File transaction separated from package inspection and Activity lifetime. */
public final class ApkCache {
    public interface Transfer {void download(File target,SafeHttp.Request request) throws Exception;}
    public interface Verifier {void verify(File file) throws Exception;}
    private ApkCache(){}
    public static File prepare(File directory,String hash,SafeHttp.Request request,Transfer transfer,Verifier verifier)throws Exception {
        request.check();
        if(!hash.matches("[a-f0-9]{64}"))throw new IOException("安装包校验值无效");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建更新目录");
        File file=new File(directory,hash+".apk");
        if(file.isFile()) {
            try{verifier.verify(file);request.check();return file;}
            catch(InterruptedIOException cancelled){throw cancelled;}
            catch(Exception invalidCache){/* Only discard it after a replacement verifies. */}
        }
        File partial=File.createTempFile("download-",".part",directory);
        try {
            transfer.download(partial,request);request.check();verifier.verify(partial);request.check();
            if(file.exists()&&!file.delete())throw new IOException("无法替换旧安装包缓存");
            if(!partial.renameTo(file))throw new IOException("无法保存已验证安装包");
            return file;
        }finally{if(partial.exists()&&!partial.delete())partial.deleteOnExit();}
    }
}
