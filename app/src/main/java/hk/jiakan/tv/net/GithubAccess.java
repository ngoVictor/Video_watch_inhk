package hk.jiakan.tv.net;

import android.content.Context;
import hk.jiakan.tv.catalog.CatalogConfiguration;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Map;
import java.util.Collections;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class GithubAccess implements SafeHttp.RequestHeaders {
    private static final String ALIAS="jiakan-private-updates";
    private final Context context;
    private final SharedPreferences prefs;
    private String cached;
    public GithubAccess(Context context) {
        this.context=context.getApplicationContext(); prefs=context.getSharedPreferences("update-access",Context.MODE_PRIVATE);
    }
    @Override public Map<String,String> forUri(URI uri) throws IOException {
        String endpoint=context.getSharedPreferences("settings",Context.MODE_PRIVATE).getString("catalogUrl","");
        if(!GithubRequestPolicy.accepts(uri,endpoint)) return Collections.emptyMap();
        String bound=prefs.getString("catalogEndpoint","");
        // Legacy credentials can be adopted only by an explicitly supplied private profile.
        if(bound.isEmpty() && endpoint.equals(CatalogConfiguration.bundledUrl(context))) {
            prefs.edit().putString("catalogEndpoint",endpoint).commit(); bound=endpoint;
        }
        return GithubRequestPolicy.headers(uri,endpoint,endpoint.equals(bound) ? token() : "");
    }
    public synchronized void replace(String value) throws IOException {
        String endpoint=context.getSharedPreferences("settings",Context.MODE_PRIVATE).getString("catalogUrl","");
        if(!GithubRequestPolicy.accepts(URI.create(endpoint),endpoint)) throw new IOException("请先设置 GitHub 频道目录地址");
        value=value.trim();
        if(value.length()<20 || value.length()>500 || !value.matches("[A-Za-z0-9_]+")) throw new IOException("更新凭据格式无效");
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
            String iv=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP);
            String encrypted=Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
            if(!prefs.edit().putString("iv",iv).putString("ciphertext",encrypted).putString("catalogEndpoint",endpoint).commit()) throw new IOException("无法保存更新凭据");
            cached=value;
        } catch(Exception e) { throw new UpdateAccessException(true); }
    }
    private synchronized String token() throws IOException {
        if(cached!=null) return cached;
        String encrypted=prefs.getString("ciphertext",null);
        if(encrypted!=null) {
            try {
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
                cached=new String(cipher.doFinal(Base64.decode(encrypted,Base64.NO_WRAP)),StandardCharsets.UTF_8);
                return cached;
            } catch(Exception e) { throw new UpdateAccessException(false); }
        }
        try(InputStream in=context.getAssets().open("private-update-token.txt")) {
            ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[128]; int n;
            while((n=in.read(buf))!=-1) { if(out.size()+n>512) throw new IOException("初始更新凭据过大"); out.write(buf,0,n); }
            replace(out.toString("UTF-8").trim()); return cached;
        } catch(FileNotFoundException e) { cached=""; return cached; }
    }
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if(store.containsAlias(ALIAS)) return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
}
