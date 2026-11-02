package hk.jiakan.tv.guide;

import hk.jiakan.tv.net.SafeHttp;
import java.io.IOException;

public interface GuideLoader { String load(String url,SafeHttp.Request request) throws IOException; }
