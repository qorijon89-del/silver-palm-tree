package tj.builder.v1;
import android.content.Context;
import android.content.pm.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.net.ssl.HttpsURLConnection;

final class UpdateInfo {
    final String version,description,sha,download;
    final long code,size;
    private UpdateInfo(JSONObject o,String locale,boolean direct)throws Exception {
        version=o.getString("version").trim(); code=o.getLong("code");
        JSONObject notes=o.getJSONObject("notes");description=notes.optString(locale,notes.optString("en","")).trim();
        sha=direct?o.getString("sha256").toLowerCase(Locale.ROOT):"";
        download=direct?o.getString("url"):"";
        size=direct?o.getLong("size"):0;
        if(version.isEmpty()||version.length()>32||description.isEmpty()||description.length()>5000||code<1||code>2100000000L)throw new IOException("Invalid release data");
        if(direct){checkUrl(download);if(!sha.matches("[a-f0-9]{64}")||size<1||size>100L*1024*1024)throw new IOException("Invalid update");}
    }
    static URL checkUrl(String text)throws Exception{
        URL u=new URL(text);
        if(!"https".equals(u.getProtocol())||u.getHost().isEmpty()||u.getUserInfo()!=null||u.getRef()!=null)throw new IOException("Invalid HTTPS URL");
        return u;
    }
    static HttpsURLConnection connect(String url)throws Exception{
        HttpsURLConnection c=(HttpsURLConnection)checkUrl(url).openConnection();
        c.setInstanceFollowRedirects(false); c.setConnectTimeout(10000); c.setReadTimeout(20000);
        return c;
    }
    static UpdateInfo check(Context c,boolean direct)throws Exception{
        if(BuildConfig.UPDATE_FEED.isEmpty())return null;
        HttpsURLConnection h=connect(BuildConfig.UPDATE_FEED);
        try{
            if(h.getResponseCode()!=200)throw new IOException("Feed unavailable");
            try(InputStream in=h.getInputStream()){
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                byte[] buf=new byte[8192];int n;
                while((n=in.read(buf))>0){if(out.size()+n>65536)throw new IOException("Feed too large");out.write(buf,0,n);}
                return parse(out.toString("UTF-8"),c.getPackageName(),currentCode(c),BuilderLocale.CODES[BuilderLocale.index(c)],direct);
            }
        }finally{h.disconnect();}
    }
    static UpdateInfo parse(String s,String pkg,long installed,String locale,boolean direct)throws Exception{
        JSONObject root=new JSONObject(s);
        if(root.getInt("schema")!=1)throw new IOException("Unknown update format");
        JSONObject o=root.getJSONObject("packages").optJSONObject(pkg);
        if(o==null||!o.optString("channel").equals(direct?"direct":"play"))return null;
        UpdateInfo i=new UpdateInfo(o,locale,direct);
        return i.code>installed?i:null;
    }
    static long currentCode(Context c)throws Exception{return c.getPackageManager().getPackageInfo(c.getPackageName(),0).getLongVersionCode();}
    static Set<String> certificateDigests(PackageInfo info)throws Exception{
        if(info==null||info.signingInfo==null)throw new IOException("Missing signature");
        Set<String> result=new TreeSet<>();
        for(Signature s:info.signingInfo.getApkContentsSigners())result.add(hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));
        if(result.isEmpty())throw new IOException("No signer");
        return result;
    }
    static String hex(byte[] bytes){
        char[] lower="0123456789abcdef".toCharArray();char[] dst=new char[bytes.length*2];
        for(int i=0;i<bytes.length;i++){dst[i*2]=lower[(bytes[i]&255)>>>4];dst[i*2+1]=lower[bytes[i]&15];}
        return new String(dst);
    }
}
