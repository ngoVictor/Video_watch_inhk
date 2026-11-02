package hk.jiakan.tv.update;
import java.io.IOException;
import java.util.Set;

/** Platform package metadata is supplied by ApkUpdater; this validation has no Android dependency. */
public final class ApkIdentity {
    private ApkIdentity(){}
    public static void verify(String expectedPackage,String archivePackage,long installedVersion,long archiveVersion,
                              long manifestVersion,int deviceSdk,int archiveMinSdk,Set<String> installedSigners,Set<String> archiveSigners)throws IOException {
        if(!expectedPackage.equals(archivePackage))throw new UpdateVerificationException(UpdateVerificationException.Reason.PACKAGE,"安装包身份不匹配");
        if(archiveVersion!=manifestVersion||archiveVersion<=installedVersion)throw new UpdateVerificationException(UpdateVerificationException.Reason.VERSION,"安装包版本不匹配");
        if(archiveMinSdk>deviceSdk)throw new UpdateVerificationException(UpdateVerificationException.Reason.SDK,"安装包不兼容本机");
        if(installedSigners.isEmpty()||!installedSigners.equals(archiveSigners))throw new UpdateVerificationException(UpdateVerificationException.Reason.SIGNATURE,"安装包签名不匹配");
    }
}
