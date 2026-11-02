package hk.jiakan.tv.update;
import hk.jiakan.tv.net.SafeHttp;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
public class ApkCacheTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private final String hash=String.join("",Collections.nCopies(64,"a"));
    private void assertNoPartial(File dir){assertEquals(0,dir.listFiles((d,n)->n.endsWith(".part")).length);}
    @Test public void cacheHitIsVerifiedAndReusedWithoutDownload()throws Exception{
        File dir=temp.newFolder();File file=new File(dir,hash+".apk");Files.write(file.toPath(),new byte[]{1});AtomicInteger checks=new AtomicInteger();
        assertEquals(file,ApkCache.prepare(dir,hash,new SafeHttp.Request(),(f,r)->{throw new AssertionError();},f->checks.incrementAndGet()));assertEquals(1,checks.get());
    }
    @Test public void cancellationDuringTransferRemovesOnlyPartial()throws Exception{
        File dir=temp.newFolder();File previous=new File(dir,"previous.apk");Files.write(previous.toPath(),new byte[]{9});SafeHttp.Request request=new SafeHttp.Request();
        try{ApkCache.prepare(dir,hash,request,(f,r)->{Files.write(f.toPath(),new byte[]{1});r.cancel();},f->{throw new AssertionError();});fail();}catch(InterruptedIOException expected){}
        assertNoPartial(dir);assertArrayEquals(new byte[]{9},Files.readAllBytes(previous.toPath()));assertFalse(new File(dir,hash+".apk").exists());
    }
    @Test public void badReplacementLeavesOldCacheUntouched()throws Exception{
        File dir=temp.newFolder();File old=new File(dir,hash+".apk");Files.write(old.toPath(),new byte[]{9});
        try{ApkCache.prepare(dir,hash,new SafeHttp.Request(),(f,r)->Files.write(f.toPath(),new byte[]{1}),f->{throw new IOException("bad package");});fail();}catch(IOException expected){}
        assertNoPartial(dir);assertArrayEquals(new byte[]{9},Files.readAllBytes(old.toPath()));
    }
    @Test public void verifiedReplacementIsPublishedAfterVerification()throws Exception{
        File dir=temp.newFolder();File result=ApkCache.prepare(dir,hash,new SafeHttp.Request(),(f,r)->Files.write(f.toPath(),new byte[]{1}),f->{assertTrue(f.getName().endsWith(".part"));assertFalse(new File(dir,hash+".apk").exists());});
        assertArrayEquals(new byte[]{1},Files.readAllBytes(result.toPath()));assertNoPartial(dir);
    }
    @Test public void incompatiblePackageMetadataIsRejected()throws Exception{
        Set<String> signer=Collections.singleton("original");ApkIdentity.verify("app","app",6,7,7,30,23,signer,signer);
        for(int scenario=0;scenario<6;scenario++){
            try{ApkIdentity.verify("app",scenario==0?"other":"app",6,scenario==1?6:7,scenario==2?8:7,30,scenario==3?31:23,scenario==4?Collections.emptySet():signer,scenario==5?Collections.singleton("wrong"):signer);fail("accepted "+scenario);}catch(IOException expected){}
        }
    }
}
