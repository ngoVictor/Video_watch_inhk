package hk.jiakan.tv.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import hk.jiakan.tv.BuildConfig;
import hk.jiakan.tv.net.SafeHttp;
import java.io.*;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

public final class ApkUpdater {
    private final Context context;
    private final SafeHttp.RequestHeaders headers;
    public ApkUpdater(Context context) { this(context,SafeHttp.NO_AUTH); }
    public ApkUpdater(Context context,SafeHttp.RequestHeaders headers) { this.context=context.getApplicationContext(); this.headers=headers; }
    public UpdateManifest check(String endpoint) throws Exception {
        UpdateManifest manifest=new UpdateManifest(SafeHttp.text(endpoint,64*1024,headers));
        if(manifest.minSdk>Build.VERSION.SDK_INT) throw new UpdateVerificationException(UpdateVerificationException.Reason.SDK,"新版本不兼容本机 Android");
        return manifest.versionCode>BuildConfig.VERSION_CODE ? manifest : null;
    }
    public File prepare(UpdateManifest manifest) throws Exception { return prepare(manifest,new SafeHttp.Request()); }
    public File prepare(UpdateManifest manifest,SafeHttp.Request request) throws Exception {
        return ApkCache.prepare(new File(context.getCacheDir(),"updates"),manifest.sha256,request,
                (target,operation) -> SafeHttp.download(manifest.apkUrl,target,100*1024*1024,headers,operation),
                target -> verify(target,manifest));
    }
    public void verify(File file,UpdateManifest manifest) throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)) {
            byte[] bytes=new byte[8192]; int n; while((n=in.read(bytes))!=-1) hash.update(bytes,0,n);
        }
        StringBuilder hex=new StringBuilder(); for(byte b:hash.digest()) hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        if(!MessageDigest.isEqual(hex.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                manifest.sha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) throw new UpdateVerificationException(UpdateVerificationException.Reason.HASH,"安装包校验失败");
        PackageManager pm=context.getPackageManager();
        int flags=Build.VERSION.SDK_INT>=28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags);
        PackageInfo installed=pm.getPackageInfo(context.getPackageName(),flags);
        if(archive==null) throw new UpdateVerificationException(UpdateVerificationException.Reason.PACKAGE,"安装包身份不匹配");
        long version=Build.VERSION.SDK_INT>=28 ? archive.getLongVersionCode() : archive.versionCode;
        if(Build.VERSION.SDK_INT>=24 && archive.applicationInfo==null) throw new UpdateVerificationException(UpdateVerificationException.Reason.SDK,"安装包不兼容本机");
        int archiveMinSdk=Build.VERSION.SDK_INT>=24 ? archive.applicationInfo.minSdkVersion : manifest.minSdk;
        ApkIdentity.verify(context.getPackageName(),archive.packageName,BuildConfig.VERSION_CODE,version,
                manifest.versionCode,Build.VERSION.SDK_INT,archiveMinSdk,signers(installed),signers(archive));
    }
    private Set<String> signers(PackageInfo info) {
        Signature[] signatures=Build.VERSION.SDK_INT>=28
                ? (info.signingInfo==null ? null : info.signingInfo.getApkContentsSigners()) : info.signatures;
        Set<String> set=new HashSet<>(); if(signatures!=null) for(Signature sig:signatures) set.add(sig.toCharsString());
        return set;
    }
    /** Caller must verify on a worker immediately before this UI operation. False means permission settings opened. */
    public boolean install(Activity activity,File file,UpdateManifest manifest) throws Exception {
        if(Build.VERSION.SDK_INT>=26 && !context.getPackageManager().canRequestPackageInstalls()) {
            context.getSharedPreferences("updates",Context.MODE_PRIVATE).edit().putBoolean("resumeInstall",true).apply();
            activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+context.getPackageName())));
            return false;
        }
        Uri uri=FileProvider.getUriForFile(context,context.getPackageName()+".files",file);
        activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        return true;
    }
    public boolean consumePendingInstall() {
        android.content.SharedPreferences prefs=context.getSharedPreferences("updates",Context.MODE_PRIVATE);
        boolean pending=prefs.getBoolean("resumeInstall",false);
        if(pending) prefs.edit().remove("resumeInstall").apply();
        return pending;
    }
}
