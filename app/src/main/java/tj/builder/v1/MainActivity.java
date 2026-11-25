package tj.builder.v1;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.view.View;
import android.widget.*;
import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.zip.*;

public final class MainActivity extends Activity {
  private static final int SOURCE=10, ICON=11, SAVE=12, LIMIT=40*1024*1024;
  private static final String ALIAS="html_builder_preview";
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private Uri source, icon;
  private String sourceName, iconName;
  private TextView selected, iconText, status;
  private ImageView preview;
  private Button build, save;
  private boolean busy;
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    ScrollView scroll=new ScrollView(this);
    LinearLayout box=new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    int p=(int)(20*getResources().getDisplayMetrics().density);
    box.setPadding(p,p,p,p);
    scroll.setOnApplyWindowInsetsListener((v,insets)->{
      scroll.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
      return insets;
    });
    scroll.addView(box);
    TextView title=label(box,"Android Builder · Санҷиши 0.1"); title.setTextSize(25); title.setAccessibilityHeading(true);
    label(box,"Дар ин санҷиш HTML ё ZIP-и HTML-ро ба APK табдил диҳед. Файл ба сервер фиристода намешавад.");
    button(box,"Интихоби HTML ё ZIP",()->pick(SOURCE,"*/*"));
    selected=label(box,"Файл интихоб нашудааст");
    button(box,"Интихоби нишона (ихтиёрӣ)",()->pick(ICON,"image/*"));
    button(box,"Истифодаи нишонаи пешфарз",()->{icon=null; iconName=null;showSelections();});
    iconText=label(box,"Нишонаи пешфарз");
    preview=new ImageView(this); preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    box.addView(preview,new LinearLayout.LayoutParams(p*4,p*4)); preview.setVisibility(View.GONE);
    build=button(box,"Сохтани APK",this::startBuild);
    save=button(box,"Захира кардани APK",this::saveDialog);
    status=label(box,"Омода барои оғоз");
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    label(box,"APK-ҳои ин озмоиш як номи бастаи Android доранд. Барои лоиҳаҳои алоҳида ҳоло истифода набаред.");
    setContentView(scroll);
    if(state!=null) {
      String s=state.getString("source"), i=state.getString("icon");
      source=s==null?null:Uri.parse(s); icon=i==null?null:Uri.parse(i);
      sourceName=state.getString("sourceName");iconName=state.getString("iconName");
    }
    showSelections();
  }
  @Override protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    if(source!=null)b.putString("source",source.toString());if(icon!=null)b.putString("icon",icon.toString());
    b.putString("sourceName",sourceName);b.putString("iconName",iconName);
  }
  private TextView label(LinearLayout box,String text) {
    TextView item=new TextView(this);item.setText(text);item.setTextSize(17);
    item.setPadding(0,10,0,10);box.addView(item);return item;
  }
  private Button button(LinearLayout box,String text,Runnable click) {
    Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setMinHeight((int)(52*getResources().getDisplayMetrics().density));
    b.setOnClickListener(v->click.run());box.addView(b);return b;
  }
  private void pick(int id,String mime) {
    if(busy)return;
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(i,id);
  }
  private File output() {return new File(getFilesDir(),"HTML-test.apk");}
  private void showSelections() {
    selected.setText(sourceName==null?"Файл интихоб нашудааст":"Файл: "+sourceName);
    iconText.setText(iconName==null?"Нишонаи пешфарз":"Нишона: "+iconName);
    preview.setVisibility(View.GONE);
    if(icon!=null){final Uri chosen=icon;worker.execute(()->{
      try{Bitmap bmp=decodeIcon(chosen);runOnUiThread(()->{if(chosen.equals(icon)){preview.setImageBitmap(bmp);preview.setVisibility(View.VISIBLE);}});}
      catch(Exception ignored){}
    });}
    build.setEnabled(source!=null&&!busy);save.setEnabled(output().isFile()&&!busy);
  }
  private String filename(Uri u) {
    try(Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
      if(c!=null&&c.moveToFirst()&&c.getString(0)!=null)return c.getString(0);
    }catch(Exception ignored){}
    return "file";
  }
  @Override protected void onActivityResult(int request,int result,Intent data) {
    super.onActivityResult(request,result,data);
    if(result!=RESULT_OK||data==null||data.getData()==null)return;
    Uri u=data.getData();
    if(request==SAVE){
      busy=true;showSelections();status.setText("Файл захира шуда истодааст");
      worker.execute(()->{
        String resultText;
        try(InputStream in=new FileInputStream(output()); OutputStream out=getContentResolver().openOutputStream(u,"w")){
          if(out==null)throw new IOException();
          pipe(in,out,LIMIT*2);resultText="APK захира шуд: "+filename(u);
        }catch(Exception e){resultText="APK захира нашуд. Ҷойи дигарро интихоб кунед.";}
        finishWork(resultText);
      });
      return;
    }
    try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
    if(request==SOURCE){source=u;sourceName=filename(u);}
    if(request==ICON){icon=u;iconName=filename(u);}
    showSelections();
  }
  private void saveDialog() {
    if(!output().isFile()||busy)return;
    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
        .setType("application/vnd.android.package-archive").putExtra(Intent.EXTRA_TITLE,"HTML-test.apk");
    startActivityForResult(i,SAVE);
  }
  private void startBuild() {
    if(source==null||busy)return;
    final Uri file=source, image=icon; final String name=sourceName;
    busy=true;showSelections();status.setText("APK сохта истодааст");
    worker.execute(()->{
      String message;
      try {makeApk(file,name,image);message="APK тайёр аст. «Захира кардани APK»-ро пахш кунед.";}
      catch(Exception e){android.util.Log.w("Builder","APK build failed",e);
        message=e instanceof UserError ? e.getMessage() : "APK сохта нашуд. Файлҳо ва ҷойи холии телефонро санҷед.";}
      finishWork(message);
    });
  }
  private void finishWork(String msg){runOnUiThread(()->{if(!isDestroyed()){busy=false;showSelections();status.setText(msg);}});}
  private static final class UserError extends IOException { UserError(String msg){super(msg);} }
  private InputStream open(Uri file)throws IOException {
    InputStream in=getContentResolver().openInputStream(file);
    if(in==null)throw new IOException();return in;
  }
  private static long pipe(InputStream in,OutputStream out,long max)throws IOException {
    byte[] data=new byte[16384];long total=0;int n;
    while((n=in.read(data))!=-1){
      total+=n;if(total>max)throw new UserError("Файл ё муҳтавои ZIP аз ҳад калон аст.");
      out.write(data,0,n);
    }
    return total;
  }
  private static void entry(ZipOutputStream z,String name,byte[] bytes,boolean store)throws IOException{
    ZipEntry e=new ZipEntry(name);
    if(store){CRC32 crc=new CRC32();crc.update(bytes);e.setMethod(ZipEntry.STORED);e.setSize(bytes.length);e.setCrc(crc.getValue());}
    z.putNextEntry(e);z.write(bytes);z.closeEntry();
  }
  private Bitmap decodeIcon(Uri image)throws IOException {
    BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;
    try(InputStream in=open(image)){BitmapFactory.decodeStream(in,null,o);}
    if(o.outWidth<=0||o.outHeight<=0)throw new UserError("Нишона хонда нашуд. Расми PNG ё JPEG интихоб кунед.");
    o.inSampleSize=1;
    while(o.outWidth/o.inSampleSize>768||o.outHeight/o.inSampleSize>768)o.inSampleSize*=2;
    o.inJustDecodeBounds=false;
    try(InputStream in=open(image)){
      Bitmap b=BitmapFactory.decodeStream(in,null,o);
      if(b==null)throw new UserError("Нишона хонда нашуд. Расми дигар интихоб кунед.");
      int s=Math.min(b.getWidth(),b.getHeight());
      return Bitmap.createScaledBitmap(Bitmap.createBitmap(b,(b.getWidth()-s)/2,(b.getHeight()-s)/2,s,s),192,192,true);
    }
  }
  private void makeApk(Uri file,String name,Uri image)throws Exception {
    String lower=name==null?"":name.toLowerCase(Locale.ROOT);
    if(!(lower.endsWith(".html")||lower.endsWith(".htm")||lower.endsWith(".zip")))
      throw new UserError("Файли HTML ё ZIP-и HTML интихоб кунед.");
    File base=File.createTempFile("base",".apk",getCacheDir()), unsigned=File.createTempFile("unsigned",".apk",getCacheDir());
    File signed=File.createTempFile("signed",".apk",getFilesDir());
    try {
      try(InputStream in=getAssets().open("viewer.apk");OutputStream out=new FileOutputStream(base)){pipe(in,out,LIMIT);}
      byte[] newIcon=null;
      if(image!=null){ByteArrayOutputStream bytes=new ByteArrayOutputStream();decodeIcon(image).compress(Bitmap.CompressFormat.PNG,100,bytes);newIcon=bytes.toByteArray();}
      try(ZipFile template=new ZipFile(base);ZipOutputStream z=new ZipOutputStream(new FileOutputStream(unsigned))){
        boolean replaced=false;
        Enumeration<? extends ZipEntry> es=template.entries();
        while(es.hasMoreElements()){
          ZipEntry e=es.nextElement();String path=e.getName();
          if(e.isDirectory()||path.startsWith("assets/site/")||path.startsWith("META-INF/"))continue;
          if(newIcon!=null&&path.startsWith("res/")&&path.endsWith(".png")) {
            entry(z,path,newIcon,e.getMethod()==ZipEntry.STORED);replaced=true;
          } else {
            ByteArrayOutputStream buf=new ByteArrayOutputStream();
            try(InputStream in=template.getInputStream(e)){pipe(in,buf,LIMIT);}
            entry(z,path,buf.toByteArray(),e.getMethod()==ZipEntry.STORED);
          }
        }
        if(newIcon!=null&&!replaced)throw new UserError("Нишона иваз нашуд. Бо нишонаи пешфарз санҷед.");
        if(lower.endsWith(".zip")) {
          Set<String> names=new HashSet<>();long total=0; int count=0;
          try(ZipInputStream in=new ZipInputStream(open(file))){
            ZipEntry e;while((e=in.getNextEntry())!=null){
              String p=e.getName();
              if(++count>1000||p.isEmpty()||p.length()>300||p.startsWith("/")||p.contains("\\")||p.contains(":")||
                 Arrays.asList(p.split("/")).contains(".."))
                throw new UserError("ZIP дорои роҳҳои нодуруст ё файлҳои аз ҳад зиёд аст.");
              if(e.isDirectory()){
                if(in.read()!=-1)throw new UserError("Сохтори ZIP нодуруст аст.");
              } else {
                if(!names.add(p))throw new UserError("Дар ZIP номи файл такрор шудааст.");
                z.putNextEntry(new ZipEntry("assets/site/"+p));
                total+=pipe(in,z,LIMIT-total);z.closeEntry();
              }
              in.closeEntry();
            }
          }
          if(!names.contains("index.html"))throw new UserError("Дар дохили ZIP файли index.html бояд дар сатҳи аввал бошад.");
        } else {
          z.putNextEntry(new ZipEntry("assets/site/index.html"));
          try(InputStream in=open(file)){if(pipe(in,z,LIMIT)==0)throw new UserError("Файли HTML холӣ аст.");}
          z.closeEntry();
        }
      }
      KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);
      if(!keys.containsAlias(ALIAS)){
        KeyPairGenerator gen=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");
        gen.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
            .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,KeyProperties.SIGNATURE_PADDING_RSA_PSS).build());
        gen.generateKeyPair();
      }
      ApkSigner.SignerConfig cfg=new ApkSigner.SignerConfig.Builder("BUILDER",
          (PrivateKey)keys.getKey(ALIAS,null),Collections.singletonList((X509Certificate)keys.getCertificate(ALIAS))).build();
      new ApkSigner.Builder(Collections.singletonList(cfg)).setInputApk(unsigned).setOutputApk(signed)
          .setMinSdkVersion(28).setV1SigningEnabled(false).setV2SigningEnabled(true)
          .setV3SigningEnabled(false).setV4SigningEnabled(false).build().sign();
      if(!new ApkVerifier.Builder(signed).setMinCheckedPlatformVersion(28).build().verify().isVerified())
        throw new UserError("Имзои APK дуруст нест. Файл захира нашуд.");
      android.system.Os.rename(signed.getAbsolutePath(),output().getAbsolutePath());
    } finally {base.delete();unsigned.delete();signed.delete();}
  }
}
