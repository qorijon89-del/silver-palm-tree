package tj.builder.html;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.Uri;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.Arrays;
public final class ViewerActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String BASE = "https://" + HOST + "/site/";
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout frame = new FrameLayout(this);
        frame.setOnApplyWindowInsetsListener((v,insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                         insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        WebView web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        final boolean network = checkSelfPermission(android.Manifest.permission.INTERNET)==android.content.pm.PackageManager.PERMISSION_GRANTED;
        web.getSettings().setBlockNetworkLoads(!network);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return assetPath(req.getUrl()) == null;
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                String p = assetPath(req.getUrl());
                if (p != null && "GET".equals(req.getMethod())) try {
                    String mime = URLConnection.guessContentTypeFromName(p);
                    if (p.endsWith(".svg")) mime="image/svg+xml";
                    if (p.endsWith(".js")) mime="text/javascript";
                    if (p.endsWith(".css")) mime="text/css";
                    if (p.endsWith(".woff2")) mime="font/woff2";
                    return new WebResourceResponse(mime == null ? "application/octet-stream":mime,
                        mime != null && (mime.startsWith("text/") || mime.contains("json")) ? "UTF-8":null,
                        getAssets().open("site/"+p));
                } catch (IOException ignored) {}
                if (p==null && network && "https".equals(req.getUrl().getScheme()) &&
                    !HOST.equals(req.getUrl().getHost())) return null;
                return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",null,new ByteArrayInputStream(new byte[0]));
            }
        });
        frame.addView(web,new FrameLayout.LayoutParams(-1,-1));
        setContentView(frame);
        web.loadUrl(BASE+"index.html");
    }
    private static String assetPath(Uri uri) {
        String p=uri.getPath();
        if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost()) || p==null || !p.startsWith("/site/")) return null;
        p=p.substring(6);
        if(p.contains("\\") || Arrays.asList(p.split("/")).contains("..")) return null;
        return p.isEmpty() ? "index.html" : p;
    }
}
