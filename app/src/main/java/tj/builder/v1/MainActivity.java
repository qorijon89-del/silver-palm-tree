package tj.builder.v1;
import android.app.Activity;
import android.content.Intent;
import android.content.Context;
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
  private static final int SOURCE=10, ICON=11, SAVE=12, NATIVE=13, WIZARD_ICON=15, LIMIT=40*1024*1024;

  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private Uri source, icon;
  private ProjectBook book;
  private DistributionUpdate updater;
  private ProjectBook.Item active;
  private ProjectWizard wizard;
  private TextView activeLabel;
  private LinearLayout htmlPanel,nativePanel;
  private Button nativeBuild;
  private String sourceName, iconName;
  private TextView selected, iconText, status;
  private ImageView preview;
  private Button build, save;
  private CheckBox allowInternet;
  private EditText buildServer, buildKey;
  private TextView permissions, nativeFileLabel;
  private Uri nativeFile;
  private String nativeFormat;
  private boolean busy;
  @Override protected void attachBaseContext(Context base){super.attachBaseContext(BuilderLocale.wrap(base));}
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    book=new ProjectBook(this);
    updater=new DistributionUpdate(this);
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
    TextView title=label(box,"Android Builder"); title.setTextSize(25); title.setAccessibilityHeading(true);
    BuilderLocale.apply(this,box);
    button(box,t("Лоиҳаи нав","Новый проект","New project","پروژه جدید"),()->projectWizard(null));
    button(box,t("Лоиҳаҳои ман","Мои проекты","My projects","پروژه‌های من"),this::selectProject);
    button(box,t("Танзими забон","Язык приложения","App language","زبان برنامه"),()->{if(!busy)BuilderLocale.pick(this,false);});
    button(box,t("Навсозиро санҷед","Проверить обновление","Check for updates","بررسی به‌روزرسانی"),()->updater.check(true));
    if(BuildConfig.DIRECT_UPDATE && !BuildConfig.UPDATE_FEED.isEmpty() && android.os.Build.VERSION.SDK_INT>=33 &&
       checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
      button(box,t("Фаъол кардани огоҳиҳои навсозӣ","Включить уведомления об обновлениях","Enable update notifications","فعال‌کردن اعلان به‌روزرسانی"),()->
        requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},6711));
    }
    activeLabel=label(box,t("Лоиҳа интихоб нашудааст","Проект не выбран","No project selected","پروژه‌ای انتخاب نشده"));
    status=label(box,"Омода барои оғоз");
    status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    permissions=label(box,"Пешниҳоди иҷозатҳо пас аз интихоби файл");
    permissions.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    permissions.setVisibility(View.GONE);

    htmlPanel=new LinearLayout(this);htmlPanel.setOrientation(1);box.addView(htmlPanel);
    selected=label(htmlPanel,"Файл интихоб нашудааст");
    iconText=label(htmlPanel,"Нишонаи пешфарз");
    preview=new ImageView(this);preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    htmlPanel.addView(preview,new LinearLayout.LayoutParams(p*4,p*4));preview.setVisibility(View.GONE);
    allowInternet=new CheckBox(this);
    allowInternet.setText(l("Ба барномаи тайёри HTML интернет иҷозат дода шавад"));
    htmlPanel.addView(allowInternet);
    label(htmlPanel,"Барои аудио ё дигар муҳтавои онлайн фаъол кунед. Танҳо пайвандҳои HTTPS дастгирӣ мешаванд.");
    build=button(htmlPanel,"Сохтани APK",this::startBuild);
    save=button(htmlPanel,"Захира кардани APK",this::saveDialog);

    nativePanel=new LinearLayout(this);nativePanel.setOrientation(1);box.addView(nativePanel);
    nativeFileLabel=label(nativePanel,"ZIP-и Android интихоб нашудааст");
    label(nativePanel,"Сохтани ZIP-и Android танҳо бо хадамоти хусусии фаъол кор мекунад.");
    LinearLayout service=new LinearLayout(this);service.setOrientation(1);service.setVisibility(View.GONE);
    button(nativePanel,t("Танзими хадамоти сохтмон","Настройки сервиса сборки","Build service settings","تنظیمات سرویس ساخت"),
      ()->service.setVisibility(service.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
    nativePanel.addView(service);
    buildServer=field(service,"Суроғаи HTTPS-и хадамот",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);
    buildKey=field(service,"Калиди якдафъаинаи хадамот",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
    nativeBuild=button(nativePanel,"Аз ZIP-и Android сохтани APK",()->startNative(active==null?"apk":active.format));
    button(nativePanel,"Захира кардани натиҷаи ZIP-и Android",this::saveNative);

    setContentView(scroll);
    if(state!=null) {
      String pid=state.getString("activeProject");if(pid!=null)active=book.get(pid);
      nativeFormat=state.getString("nativeFormat");
      String s=state.getString("source"), i=state.getString("icon");
      source=s==null?null:Uri.parse(s); icon=i==null?null:Uri.parse(i);
      sourceName=state.getString("sourceName");iconName=state.getString("iconName");
      allowInternet.setChecked(state.getBoolean("internet"));
    }
    showSelections();showActive();
    UpdateJob.schedule(this);
    if(!BuilderLocale.chosen(this))BuilderLocale.pick(this,true);
  }
  @Override public void onResume(){super.onResume();if(updater!=null)updater.resume();}
  @Override public void onDestroy(){if(updater!=null)updater.close();super.onDestroy();}
  void updateStatus(String text){setStatus(text);}
  private String l(String text){return LocalizedStatus.get(this,text);}
  private void setStatus(String text){if(status!=null)status.setText(l(text));}
  String t(String tg,String ru,String en,String fa){return BuilderLocale.t(this,tg,ru,en,fa);}
  private void projectWizard(ProjectBook.Item old){
    if(busy)return;
    wizard=new ProjectWizard(this,book,old,()->pick(WIZARD_ICON,"image/*"));
    wizard.show();
  }
  private void selectProject(){
    if(busy)return;
    List<ProjectBook.Item> projects=book.all();
    if(projects.isEmpty()){setStatus(t("Ҳоло лоиҳа нест","Проектов пока нет","No projects yet","هنوز پروژه‌ای وجود ندارد"));return;}
    String[] names=new String[projects.size()];
    for(int n=0;n<names.length;n++){ProjectBook.Item i=projects.get(n);names[n]=i.title+" · "+i.id;}
    new android.app.AlertDialog.Builder(this).setTitle(t("Лоиҳаҳои ман","Мои проекты","My projects","پروژه‌های من"))
        .setItems(names,(d,n)->projectWizard(projects.get(n))).show();
  }
  void useProject(ProjectBook.Item p){
    active=p;source=null;sourceName=null;nativeFile=null;nativeFormat=null;
    nativeFileLabel.setText(l("ZIP-и Android интихоб нашудааст"));
    permissions.setText(l("Пешниҳоди иҷозатҳо пас аз интихоби файл"));
    setStatus("Омода барои оғоз");
    icon=p.icon.isEmpty()?null:Uri.parse(p.icon);iconName=icon==null?null:filename(icon);
    allowInternet.setChecked(p.permissions.contains("INTERNET"));

    showSelections();showActive();
    pick(p.kind.equals("html")?SOURCE:NATIVE,"*/*");
  }
  private void showActive(){
    boolean html=active!=null&&active.kind.equals("html");
    boolean nativeMode=active!=null&&active.kind.equals("android");
    htmlPanel.setVisibility(html?View.VISIBLE:View.GONE);
    nativePanel.setVisibility(nativeMode?View.VISIBLE:View.GONE);
    permissions.setVisibility(active!=null?View.VISIBLE:View.GONE);
    if(active!=null){
      activeLabel.setText(active.title+" · "+active.id+" · "+active.version);
      if(html){
        build.setVisibility(active.format.equals("apk")?View.VISIBLE:View.GONE);
        if(active.format.equals("aab"))setStatus(t("AAB барои HTML ҳанӯз дастрас нест.","AAB из HTML пока недоступен.","HTML to AAB is not available yet.","تبدیل HTML به AAB هنوز در دسترس نیست."));
      }
      if(nativeMode)nativeBuild.setText(l(active.format.equals("aab")?"Аз ZIP-и Android сохтани AAB":"Аз ZIP-и Android сохтани APK"));
    }
  }
  @Override protected void onSaveInstanceState(Bundle b) {
    super.onSaveInstanceState(b);
    if(active!=null)b.putString("activeProject",active.id);
    b.putString("nativeFormat",nativeFormat);
    if(source!=null)b.putString("source",source.toString());if(icon!=null)b.putString("icon",icon.toString());
    b.putBoolean("internet",allowInternet.isChecked());
    b.putString("sourceName",sourceName);b.putString("iconName",iconName);
  }
  private EditText field(LinearLayout box,String title,int inputType) {
    TextView l=label(box,title); EditText e=new EditText(this); e.setId(View.generateViewId()); l.setLabelFor(e.getId());
    e.setSingleLine(true);e.setInputType(inputType);e.setSaveEnabled(false);box.addView(e);return e;
  }
  private TextView label(LinearLayout box,String text) {
    TextView item=new TextView(this);item.setText(l(text));item.setTextSize(17);
    item.setPadding(0,10,0,10);box.addView(item);return item;
  }
  private Button button(LinearLayout box,String text,Runnable click) {
    Button b=new Button(this);b.setAllCaps(false);b.setText(l(text));b.setMinHeight((int)(52*getResources().getDisplayMetrics().density));
    b.setOnClickListener(v->click.run());box.addView(b);return b;
  }
  private void pick(int id,String mime) {
    if(busy)return;
    Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE);
    startActivityForResult(i,id);
  }
  private File output() {return new File(getFilesDir(),active==null?"HTML-result.apk":active.id+".apk");}
  private void showSelections() {
    selected.setText(l(sourceName==null?"Файл интихоб нашудааст":"Файл: "+sourceName));
    iconText.setText(l(iconName==null?"Нишонаи пешфарз":"Нишона: "+iconName));
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
    if(request==SAVE||request==14){
      final File selectedOutput=request==SAVE?output():new File(getFilesDir(),"native-"+nativeFormat);
      busy=true;showSelections();setStatus("Файл захира шуда истодааст");
      worker.execute(()->{
        String resultText;
        try(InputStream in=new FileInputStream(selectedOutput); OutputStream out=getContentResolver().openOutputStream(u,"w")){
          if(out==null)throw new IOException();
          pipe(in,out,100L*1024*1024);resultText="Файл захира шуд: "+filename(u);
        }catch(Exception e){resultText="Файл захира нашуд. Ҷойи дигарро интихоб кунед.";}
        finishWork(resultText);
      });
      return;
    }
    try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
    if(request==WIZARD_ICON){if(wizard!=null)wizard.setIcon(u,filename(u));return;}
    if(request==NATIVE){inspectNative(u);return;}
    if(request==SOURCE){source=u;sourceName=filename(u); reviewHtml(u,sourceName);}
    if(request==ICON){icon=u;iconName=filename(u);}
    showSelections();
  }
  private void inspectNative(Uri file) {
    if(busy)return;
    nativeFile=null;nativeFileLabel.setText(l("ZIP-и Android санҷида мешавад"));
    busy=true;showSelections();setStatus("ZIP-и Android санҷида мешавад");
    worker.execute(()->{
      String result;
      try(ZipInputStream z=new ZipInputStream(open(file))) {
        Set<String> entries=new HashSet<>();
        ArrayList<String> settings=new ArrayList<>();
        OutputStream discard=new OutputStream(){@Override public void write(int b){} @Override public void write(byte[] b,int o,int n){}};
        ZipEntry e;long total=0;int count=0;
        while((e=z.getNextEntry())!=null) {
          String n=e.getName();
          if(++count>4000||n.length()>300||n.startsWith("/")||n.contains(":")||n.indexOf(92)>=0||Arrays.asList(n.split("/")).contains("..")||!entries.add(n))throw new UserError("Сохтори ZIP нодуруст аст.");
          if(n.endsWith("settings.gradle")||n.endsWith("settings.gradle.kts"))settings.add(n);
          total+=pipe(z,discard,100L*1024*1024-total);z.closeEntry();
        }
        int projects=0; String manifestPath=null;
        for(String n:settings) {
          int slash=n.lastIndexOf('/');
          String base=slash<0?"":n.substring(0,slash+1);
          if(base.indexOf('/')!=base.lastIndexOf('/'))continue;
          if(entries.contains(base+"app/src/main/AndroidManifest.xml")&&(entries.contains(base+"app/build.gradle")||entries.contains(base+"app/build.gradle.kts"))){projects++;manifestPath=base+"app/src/main/AndroidManifest.xml";}
        }
        if(projects==1){
          final String review=ProjectReview.nativeReview(this,readZipText(file,manifestPath));
          runOnUiThread(()->{nativeFile=file;nativeFileLabel.setText("ZIP: "+filename(file));permissions.setText(review);});
          result="ZIP-и лоиҳаи Android дуруст шинохта шуд.";
        } else { runOnUiThread(()->{nativeFile=null;nativeFileLabel.setText(l("ZIP-и Android интихоб нашудааст"));});
          result="Ин ZIP ҳамчун як лоиҳаи Android бо бахши app шинохта нашуд.";}

      }catch(Exception e) {result=e instanceof UserError?e.getMessage():"ZIP хонда нашуд.";}
      finishWork(result);
    });
  }
  private byte[] readZipText(Uri file,String name)throws IOException {
    try(ZipInputStream z=new ZipInputStream(open(file))){
      ZipEntry e;
      while((e=z.getNextEntry())!=null){
        if(name.equals(e.getName()))return readPrefix(z,256*1024);
        z.closeEntry();
      }
    }
    return null;
  }
  private static byte[] readPrefix(InputStream in,int max)throws IOException {
    ByteArrayOutputStream data=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
    while(data.size()<max&&(n=in.read(buf,0,Math.min(buf.length,max-data.size())))>0)data.write(buf,0,n);
    return data.toByteArray();
  }
  private void reviewHtml(Uri file,String name) {
    permissions.setText(l("Дархостҳои иҷозат санҷида мешаванд"));
    worker.execute(()->{
      String report;
      try{
        byte[] p;
        if(name.toLowerCase(Locale.ROOT).endsWith(".zip")){
          // Only inspect a top-level entry within a small, bounded copy.
          File f=File.createTempFile("review",".zip",getCacheDir());
          try {
            try(InputStream in=open(file);OutputStream out=new FileOutputStream(f)){pipe(in,out,LIMIT);}
            try(ZipFile z=new ZipFile(f)){
              ByteArrayOutputStream combined=new ByteArrayOutputStream();Enumeration<? extends ZipEntry> es=z.entries();int n=0;
              while(es.hasMoreElements()&&n++<1000&&combined.size()<768*1024){
                ZipEntry e=es.nextElement();String path=e.getName().toLowerCase(Locale.ROOT);
                if(path.endsWith(".html")||path.endsWith(".htm")||path.endsWith(".js")){
                  try(InputStream in=z.getInputStream(e)){combined.write(readPrefix(in,Math.min(256*1024,768*1024-combined.size())));}
                  if(combined.size()<768*1024) combined.write(10);
                }
              }
              p=combined.toByteArray();
            }
          }finally{f.delete();}
        }else try(InputStream in=open(file)){p=readPrefix(in,256*1024);}
        report=ProjectReview.html(this,p);
      }catch(Exception e){report="Пешниҳоди иҷозатҳо дастрас нашуд.";}
      final String msg=report;runOnUiThread(()->{if(file.equals(source))permissions.setText(l(msg));});
    });
  }
  private void saveNative(){
    if(busy||nativeFormat==null){setStatus("Ҳанӯз APK ё AAB-и Android тайёр нест.");return;}
    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
      .setType(nativeFormat.equals("apk")?"application/vnd.android.package-archive":"application/octet-stream")
      .putExtra(Intent.EXTRA_TITLE,"Android-Test."+nativeFormat);
    startActivityForResult(i,14);
  }
  private void startNative(String fmt){
    if(busy)return;
    if(nativeFile==null){setStatus("Аввал ZIP-и лоиҳаи Android-ро интихоб кунед.");return;}
    final BuildConnection con;
    try{con=new BuildConnection(buildServer.getText().toString(),buildKey.getText().toString());}
    catch(Exception e){setStatus("Хадамоти хусусӣ пайваст нест. ZIP фиристода нашуд.");return;}
    final Uri captured=nativeFile;
    ArrayList<String> p=new ArrayList<>();
    if(active!=null)p.addAll(active.permissions);
    if(p.contains("ACCESS_FINE_LOCATION")&&!p.contains("ACCESS_COARSE_LOCATION"))p.add("ACCESS_COARSE_LOCATION");
    final String allowed=String.join(",",p);
    new android.app.AlertDialog.Builder(this).setTitle(l("Фиристодани ZIP ба хадамоти хусусӣ"))
      .setMessage(l("Ҳамаи файлҳои ZIP ба суроғаи интихобшуда фиристода мешаванд. Танҳо лоиҳаи боэътимодро фиристед."))
      .setNegativeButton(l("Бекор"),null).setPositiveButton(l("Фиристодан"),(dialog,w)->{
        busy=true;nativeFormat=null;showSelections();
        worker.execute(()->{
          File in=null,out=null;String msg;
          try{
            in=File.createTempFile("upload",".zip",getCacheDir());out=File.createTempFile("native",".part",getFilesDir());
            try(InputStream s=open(captured);OutputStream d=new FileOutputStream(in)){pipe(s,d,100L*1024*1024);}
            con.build(in,out,fmt,allowed,n->runOnUiThread(()->setStatus(n)));
            android.system.Os.rename(out.getAbsolutePath(),new File(getFilesDir(),"native-"+fmt).getAbsolutePath());
            runOnUiThread(()->nativeFormat=fmt);
            msg="Файл тайёр шуд. «Захира кардани натиҷаи ZIP-и Android»-ро пахш кунед.";
          }catch(Exception e){msg="Сохтани натиҷа муваффақ нашуд. Пайвастшавӣ ё лоиҳаро санҷед.";}
          finally{if(in!=null)in.delete();if(out!=null)out.delete();}
          finishWork(msg);
        });
      }).show();
  }
  private void saveDialog() {
    if(!output().isFile()||busy)return;
    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
        .setType("application/vnd.android.package-archive").putExtra(Intent.EXTRA_TITLE,active==null?"HTML-result.apk":active.id+"-"+active.version+".apk");
    startActivityForResult(i,SAVE);
  }
  private void startBuild() {
    if(source==null||busy)return;
    final ProjectBook.Item p=active;
    if(p==null||!p.kind.equals("html")){
      setStatus(t("Аввал лоиҳаи HTML созед ё кушоед.","Сначала откройте или создайте проект HTML.","Create or open an HTML project first.","ابتدا یک پروژه HTML بسازید یا باز کنید."));return;
    }
    if(p.format.equals("aab")){
      setStatus(t("AAB барои HTML ҳанӯз дастрас нест.","AAB из HTML пока недоступен.","HTML to AAB is not available yet.","تبدیل HTML به AAB هنوز در دسترس نیست."));return;
    }
    final Uri file=source, image=icon; final String name=sourceName; final boolean internet=allowInternet.isChecked();
    busy=true;showSelections();setStatus("APK сохта истодааст");
    worker.execute(()->{
      String message;
      try {
        ProjectBook.Item done=p.built();
        makeApk(file,name,image,internet,done);
        if(!book.store(done))throw new UserError("Версияи лоиҳа захира нашуд.");
        runOnUiThread(()->{active=done;showActive();});
        message="APK тайёр аст. «Захира кардани APK»-ро пахш кунед.";
      }
      catch(Exception e){android.util.Log.w("Builder","APK build failed",e);
        message=e instanceof UserError ? e.getMessage() : "APK сохта нашуд. Файлҳо ва ҷойи холии телефонро санҷед.";}
      finishWork(message);
    });
  }
  private void finishWork(String msg){runOnUiThread(()->{if(!isDestroyed()){busy=false;showSelections();setStatus(msg);}});}
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
  private void makeApk(Uri file,String name,Uri image,boolean internet,ProjectBook.Item project)throws Exception {
    String lower=name==null?"":name.toLowerCase(Locale.ROOT);
    if(!(lower.endsWith(".html")||lower.endsWith(".htm")||lower.endsWith(".zip")))
      throw new UserError("Файли HTML ё ZIP-и HTML интихоб кунед.");
    File base=File.createTempFile("base",".apk",getCacheDir()), unsigned=File.createTempFile("unsigned",".apk",getCacheDir());
    File signed=File.createTempFile("signed",".apk",getFilesDir());
    try {
      try(InputStream in=getAssets().open(internet?"viewer-online.apk":"viewer.apk");OutputStream out=new FileOutputStream(base)){pipe(in,out,LIMIT);}
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
            byte[] content=buf.toByteArray();
            if(path.equals("AndroidManifest.xml"))content=ManifestIdentity.change(content,project.id,project.title,project.version,project.last);
            entry(z,path,content,e.getMethod()==ZipEntry.STORED);
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
      final String alias="html_signing_"+project.id;
      KeyStore keys=KeyStore.getInstance("AndroidKeyStore");keys.load(null);
      if(!keys.containsAlias(alias)&&project.last>1)throw new UserError("Калиди ин лоиҳа ёфт нашуд. Ба ҷойи он калиди нав сохта нашуд.");
      if(!keys.containsAlias(alias)){
        KeyPairGenerator gen=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");
        gen.initialize(new KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
            .setKeySize(2048).setDigests(KeyProperties.DIGEST_SHA256,KeyProperties.DIGEST_SHA512)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,KeyProperties.SIGNATURE_PADDING_RSA_PSS).build());
        gen.generateKeyPair();
      }
      ApkSigner.SignerConfig cfg=new ApkSigner.SignerConfig.Builder("BUILDER",
          (PrivateKey)keys.getKey(alias,null),Collections.singletonList((X509Certificate)keys.getCertificate(alias))).build();
      new ApkSigner.Builder(Collections.singletonList(cfg)).setInputApk(unsigned).setOutputApk(signed)
          .setMinSdkVersion(24).setV1SigningEnabled(false).setV2SigningEnabled(true)
          .setV3SigningEnabled(false).setV4SigningEnabled(false).build().sign();
      if(!new ApkVerifier.Builder(signed).setMinCheckedPlatformVersion(24).build().verify().isVerified())
        throw new UserError("Имзои APK дуруст нест. Файл захира нашуд.");
      android.system.Os.rename(signed.getAbsolutePath(),output().getAbsolutePath());
    } finally {base.delete();unsigned.delete();signed.delete();}
  }
}
