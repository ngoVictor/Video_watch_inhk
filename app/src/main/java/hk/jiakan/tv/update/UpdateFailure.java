package hk.jiakan.tv.update;

import hk.jiakan.tv.net.SafeHttp;
import hk.jiakan.tv.net.UpdateAccessException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import org.json.JSONException;

/** Only fixed text and numeric HTTP status may leave this boundary. */
public final class UpdateFailure {
    public final String code, hint;
    private UpdateFailure(String code,String hint) { this.code=code; this.hint=hint; }
    public String display() { return hint+"（"+code+"）"; }
    public static UpdateFailure from(Throwable error) {
        // Bounded even for a malicious/cyclic cause chain. Never expose its messages or URLs.
        for(int depth=0;error!=null && depth<8;depth++,error=error.getCause()) {
            if(error instanceof UpdateVerificationException) {
                UpdateVerificationException.Reason reason=((UpdateVerificationException)error).reason;
                String hint=reason==UpdateVerificationException.Reason.SDK ? "新版本不支持本机 Android，请由家人检查兼容版本。"
                        : reason==UpdateVerificationException.Reason.SIGNATURE ? "安装包签名与当前应用不一致，请由家人检查发行签名。"
                        : "安装包校验未通过，请重新下载；若仍失败，请由家人检查发布文件。";
                return new UpdateFailure("VERIFY_"+reason.name(),hint);
            }
            if(error instanceof UpdateAccessException) {
                boolean saving=((UpdateAccessException)error).saving;
                return new UpdateFailure(saving ? "CREDENTIAL_SAVE" : "CREDENTIAL_READ",
                        saving ? "本机无法加密保存更新凭据，请由家人检查设备安全存储。" : "本机无法读取更新凭据，请在维护页更换更新凭据。");
            }
            if(error instanceof SafeHttp.HttpException) {
                int status=((SafeHttp.HttpException)error).status;
                String hint=status==401 ? "更新凭据无效或已到期，请由家人更换更新凭据。"
                        : status==403 ? "服务器拒绝访问，请检查仓库权限或稍后重试。"
                        : status==404 ? "发布文件不存在，或更新凭据没有私有仓库权限。"
                        : status==429 ? "更新请求过多，请稍后重试。" : "更新服务器暂时不可用，请稍后重试。";
                return new UpdateFailure("HTTP_"+status,hint);
            }
            if(error instanceof UnknownHostException) return new UpdateFailure("DNS","无法解析更新服务器，请检查盒子网络和 DNS。");
            if(error instanceof SocketTimeoutException) return new UpdateFailure("TIMEOUT","连接或下载超时，请稍后重试并检查网络。");
            if(error instanceof SSLException) return new UpdateFailure("TLS","安全连接失败，请检查盒子的日期时间及系统证书。");
            if(error instanceof ConnectException) return new UpdateFailure("CONNECT","无法连接更新服务器，请检查网络后重试。");
            if(error instanceof JSONException || error instanceof IllegalArgumentException)
                return new UpdateFailure("FORMAT","更新地址或发布文件格式无效，请由家人检查发布配置。");
        }
        return new UpdateFailure("OTHER","请重试；若仍失败，请把本页的软件版本和完整提示告诉家人。");
    }
}
