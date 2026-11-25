package tj.builder.v1;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.Locale;
import java.util.UUID;
import javax.net.ssl.HttpsURLConnection;
final class BuildConnection {
    interface Status { void update(String label); }
    private final URL root;
    private final String token;
    BuildConnection(String address,String key) throws Exception {
        root=new URL(address.trim());
        if(!"https".equals(root.getProtocol())||root.getHost().isEmpty()||root.getUserInfo()!=null||root.getQuery()!=null||
            root.getRef()!=null||!(root.getPath().isEmpty()||"/".equals(root.getPath()))||!key.matches("[A-Za-z0-9_-]{32,128}"))
            throw new IOException("server");
        token=key;
    }
    private HttpsURLConnection open(String path)throws IOException{
        HttpsURLConnection c=(HttpsURLConnection)new URL(root,path).openConnection();
        c.setConnectTimeout(15000);c.setReadTimeout(25000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Authorization","Bearer "+token);
        return c;
    }
    private JSONObject json(HttpsURLConnection c,int status)throws Exception{return json(c,status,2048);}
    private JSONObject json(HttpsURLConnection c,int status,int limit)throws Exception{
        if(c.getResponseCode()!=status && !(status==202&&c.getResponseCode()==200))throw new IOException("http");
        try(InputStream in=c.getInputStream()){
            byte[] bytes=new byte[limit];int used=0,k;
            while(used<bytes.length&&(k=in.read(bytes,used,bytes.length-used))>0)used+=k;
            return new JSONObject(new String(bytes,0,used,java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    byte[] generate(String prompt)throws Exception {
        HttpsURLConnection c=open("/v1/generate");
        try {
            byte[] data=new JSONObject().put("prompt",prompt).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            c.setRequestMethod("POST");c.setDoOutput(true);c.setReadTimeout(80000);
            c.setRequestProperty("Content-Type","application/json");c.setFixedLengthStreamingMode(data.length);
            try(OutputStream out=c.getOutputStream()){out.write(data);}
            if(c.getResponseCode()==503)throw new IOException("ai_not_configured");
            return json(c,200,800000).getString("html").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }finally{c.disconnect();}
    }
    void build(File input,File returned,String format,String permissions,Status status)throws Exception{
        if(!format.equals("apk")&&!format.equals("aab"))throw new IOException("format");
        String id=UUID.randomUUID().toString().replace("-","");
        String path="/v1/jobs/"+id;
        HttpsURLConnection c=open(path+"/"+format);
        try {
            c.setRequestMethod("POST");c.setDoOutput(true);c.setFixedLengthStreamingMode(input.length());
            c.setRequestProperty("Content-Type","application/zip");
            c.setRequestProperty("X-Builder-Permissions",permissions);
            status.update("ZIP фиристода мешавад");
            try(OutputStream o=c.getOutputStream();InputStream in=new FileInputStream(input)){copy(in,o,100L*1024*1024,null);}
            json(c,202);
        }finally{c.disconnect();}
        long deadline=android.os.SystemClock.elapsedRealtime()+385000;String last="";
        while(android.os.SystemClock.elapsedRealtime()<deadline){
            Thread.sleep(2200);
            c=open(path);String state;
            try{state=json(c,200).getString("status");}finally{c.disconnect();}
            if(state.equals("failed"))throw new IOException("build");
            if(state.equals("complete"))break;
            if(!state.equals(last)){
                status.update(state.equals("building")?"APK ё AAB сохта истодааст":"Дар навбати сохтмон");
                last=state;
            }
        }
        if(android.os.SystemClock.elapsedRealtime()>=deadline)throw new IOException("timeout");
        c=open(path+"/file");
        try {
            if(c.getResponseCode()!=200)throw new IOException("download");
            status.update("Файл зеркашӣ шуда истодааст");
            try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(returned)){
                long got=copy(in,out,100L*1024*1024,null);
                if(got<4||(c.getContentLengthLong()>0&&got!=c.getContentLengthLong()))throw new IOException("incomplete");
            }
            try(java.util.zip.ZipFile check=new java.util.zip.ZipFile(returned)){
                if(check.getEntry(format.equals("apk")?"AndroidManifest.xml":"BundleConfig.pb")==null)throw new IOException("wrong artifact");
            }
        }finally{c.disconnect();}
    }
    private static long copy(InputStream in,OutputStream out,long max,Void unused)throws IOException{
        byte[] b=new byte[32768];int n;long got=0;
        while((n=in.read(b))!=-1){got+=n;if(got>max)throw new IOException("size");out.write(b,0,n);}
        return got;
    }
}
