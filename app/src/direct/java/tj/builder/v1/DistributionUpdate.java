package tj.builder.v1;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;
import com.android.apksig.ApkVerifier;

final class DistributionUpdate {
  private final MainActivity a;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private boolean checked,working;
  DistributionUpdate(MainActivity a){this.a=a;}
  String t(String tg,String ru,String en,String fa){return a.t(tg,ru,en,fa);}
  void resume(){if(!checked&&BuilderLocale.chosen(a)){checked=true;check(false);}}
  void close(){worker.shutdownNow();}
  private void toast(String message){if(!a.isDestroyed())Toast.makeText(a,message,Toast.LENGTH_LONG).show();}
  void check(boolean manual) {
    if(working)return;
    if(BuildConfig.UPDATE_FEED.isEmpty()){
      if(manual)toast(t("Манбаи навсозӣ пайваст нест.","Источник обновлений не настроен.","The update feed is not configured.","منبع به‌روزرسانی تنظیم نشده است."));
      return;
    }
    working=true;
    worker.execute(()->{
      UpdateInfo found=null;boolean bad=false;
      try{found=UpdateInfo.check(a,true);}catch(Exception e){bad=true;}
      final UpdateInfo update=found;final boolean failed=bad;
      a.runOnUiThread(()->{
        working=false;if(a.isDestroyed()||a.isFinishing())return;
        if(update!=null)offer(update);
        else if(manual)toast(failed?t("Навсозиро санҷида натавонистам.","Не удалось проверить обновления.","Couldn't check for updates.","بررسی به‌روزرسانی انجام نشد."):
                               t("Версияи нав нест.","Новых версий нет.","You have the latest available version.","نسخه جدیدی موجود نیست."));
      });
    });
  }
  private void offer(UpdateInfo i){
    new AlertDialog.Builder(a).setTitle(t("Версияи нав: ","Новая версия: ","New version: ","نسخه جدید: ")+i.version)
      .setMessage(i.description).setNegativeButton(t("Баъдтар","Позже","Later","بعداً"),null)
      .setPositiveButton(t("Зеркашӣ","Скачать","Download","دانلود"),(d,w)->download(i)).show();
  }
  private void download(UpdateInfo i){
    if(working)return;working=true;
    LinearLayout body=new LinearLayout(a);body.setOrientation(1);body.setPadding(24,12,24,12);
    ProgressBar bar=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);
    bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);body.addView(bar);
    TextView live=new TextView(a);
    live.setText(t("Зеркашӣ оғоз мешавад.","Загрузка начинается.","Starting download.","دانلود آغاز می‌شود."));
    live.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);body.addView(live);
    AlertDialog dialog=new AlertDialog.Builder(a).setTitle(t("Зеркашии навсозӣ","Загрузка обновления","Downloading update","دانلود به‌روزرسانی")).setView(body).setCancelable(false).create();
    dialog.show();
    worker.execute(()->{
      boolean ok=false;
      File partial=new File(a.getCacheDir(),"update-download.part"), ready=new File(a.getCacheDir(),"verified-update.apk");
      try{
        if(ready.isFile()&&valid(ready,i)){ok=true;}
        else{
          ready.delete();
          HttpsURLConnection c=UpdateInfo.connect(i.download);
          try{
            if(c.getResponseCode()!=200)throw new IOException("Download unavailable");
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            long previous=SystemClock.elapsedRealtime(),previousBytes=0,total=0;
            try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(partial)){
              byte[] b=new byte[32768];int n;
              while((n=in.read(b))!=-1){
                if(Thread.currentThread().isInterrupted())throw new IOException("Cancelled");
                total+=n;if(total>i.size)throw new IOException("Too large");
                sha.update(b,0,n);out.write(b,0,n);
                long now=SystemClock.elapsedRealtime();
                if(now-previous>=2250||total==i.size){
                  final int progress=(int)(100*total/i.size);
                  final long speed=(total-previousBytes)*1000/Math.max(1,now-previous)/1024;
                  previous=now;previousBytes=total;
                  a.runOnUiThread(()->{
                    if(a.isDestroyed())return;
                    bar.setProgress(progress);
                    live.setText(progress+"%. "+speed+" "+t("КБ дар сония","КБ/с","KB per second","کیلوبایت در ثانیه"));
                  });
                }
              }
            }
            if(total!=i.size||!i.sha.equals(UpdateInfo.hex(sha.digest()))||!valid(partial,i))throw new IOException("Wrong update signature");
            android.system.Os.rename(partial.getAbsolutePath(),ready.getAbsolutePath());ok=true;
          }finally{c.disconnect();}
        }
      }catch(Exception ignored){}finally{partial.delete();}
      final boolean safe=ok;
      a.runOnUiThread(()->{
        working=false;if(a.isDestroyed())return;dialog.dismiss();
        if(safe)installOffer();else toast(t("Зеркашӣ ё тафтиши имзо ноком шуд.","Загрузка или проверка подписи не удалась.","The download or signature verification failed.","دانلود یا بررسی امضا ناموفق بود."));
      });
    });
  }
  private boolean valid(File f,UpdateInfo i)throws Exception{
    if(f.length()!=i.size)return false;
    MessageDigest digest=MessageDigest.getInstance("SHA-256");
    try(InputStream in=new FileInputStream(f)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
    if(!i.sha.equals(UpdateInfo.hex(digest.digest())))return false;
    if(!new ApkVerifier.Builder(f).build().verify().isVerified())return false;
    PackageManager pm=a.getPackageManager();
    PackageInfo next=pm.getPackageArchiveInfo(f.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES);
    PackageInfo installed=pm.getPackageInfo(a.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
    return next!=null&&a.getPackageName().equals(next.packageName)&&next.getLongVersionCode()==i.code
         &&i.code>installed.getLongVersionCode()&&UpdateInfo.certificateDigests(next).equals(UpdateInfo.certificateDigests(installed));
  }
  private void installOffer(){
    new AlertDialog.Builder(a).setTitle(t("Навсозӣ тайёр аст","Обновление готово","Update ready","به‌روزرسانی آماده است"))
      .setMessage(t("Насбро Android аз шумо тасдиқ мепурсад.","Android попросит подтвердить установку.","Android will ask you to confirm installation.","اندروید برای نصب تأیید شما را می‌خواهد."))
      .setNegativeButton(t("Баъдтар","Позже","Later","بعداً"),null)
      .setPositiveButton(t("Насб","Установить","Install","نصب"),(d,w)->install()).show();
  }
  private void install(){
    try{
      if(!a.getPackageManager().canRequestPackageInstalls()){
        Intent settings=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+a.getPackageName()));
        toast(t("Пас аз иҷозат ба Builder баргардед ва аз нав «Навсозиро санҷед»-ро пахш кунед.","После разрешения вернитесь и снова проверьте обновление.","After granting permission, return and check for updates again.","بعد از دادن مجوز، برگردید و دوباره به‌روزرسانی را بررسی کنید."));
        a.startActivity(settings);return;
      }
      Uri apk=Uri.parse("content://"+a.getPackageName()+".update/update.apk");
      Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(apk,"application/vnd.android.package-archive")
         .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      intent.setClipData(ClipData.newRawUri("update",apk));
      a.startActivity(intent);
    }catch(Exception e){toast(t("Насб оғоз нашуд.","Установка не запустилась.","Installation couldn't start.","نصب شروع نشد."));}
  }
}
