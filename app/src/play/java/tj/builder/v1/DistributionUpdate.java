package tj.builder.v1;
import android.app.*;
import android.os.SystemClock;
import android.widget.Toast;
import com.google.android.play.core.appupdate.*;
import com.google.android.play.core.install.InstallStateUpdatedListener;
import com.google.android.play.core.install.model.*;
import java.util.concurrent.*;
final class DistributionUpdate {
  private final MainActivity a;
  private final AppUpdateManager manager;
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private final InstallStateUpdatedListener listener;
  private boolean checked,prompted;
  private long lastTime, bytes;
  DistributionUpdate(MainActivity a){
    this.a=a;manager=AppUpdateManagerFactory.create(a);
    listener=s->{
      if(a.isDestroyed())return;
      int state=s.installStatus();
      if(state==InstallStatus.DOWNLOADED){ready();return;}
      if(state==InstallStatus.DOWNLOADING){
        long now=SystemClock.elapsedRealtime(), total=s.totalBytesToDownload();
        if(now-lastTime>=2500){
          long n=s.bytesDownloaded();
          String pct=total>0?Math.min(100,n*100/total)+"%. ":"";
          String speed=lastTime==0?"":Math.max(0,(n-bytes)*1000/Math.max(1,now-lastTime)/1024)+" "+a.t("КБ дар сония","КБ/с","KB per second","کیلوبایت در ثانیه");
          lastTime=now;bytes=n;a.updateStatus(pct+speed);
        }
      }
    };
    manager.registerListener(listener);
  }
  void resume(){
    if(!BuilderLocale.chosen(a))return;
    if(!checked){checked=true;check(false);}
    else manager.getAppUpdateInfo().addOnSuccessListener(info->{if(info.installStatus()==InstallStatus.DOWNLOADED)ready();});
  }
  void close(){manager.unregisterListener(listener);worker.shutdownNow();}
  private void toast(String m){if(!a.isDestroyed())Toast.makeText(a,m,Toast.LENGTH_LONG).show();}
  void check(boolean manual){
    manager.getAppUpdateInfo().addOnSuccessListener(info->{
      if(a.isDestroyed())return;
      if(info.installStatus()==InstallStatus.DOWNLOADED){ready();return;}
      if(info.updateAvailability()!=UpdateAvailability.UPDATE_AVAILABLE||!info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)){
        if(manual)toast(a.t("Google Play навсозии дастрас надорад.","Google Play не предлагает обновление.","No update is available through Google Play.","به‌روزرسانی در گوگل پلی موجود نیست."));
        return;
      }
      worker.execute(()->{
        UpdateInfo meta=null;try{meta=UpdateInfo.check(a,false);}catch(Exception ignored){}
        final UpdateInfo item=meta!=null&&meta.code==info.availableVersionCode()?meta:null;
        a.runOnUiThread(()->{if(!a.isDestroyed())offer(info,item);});
      });
    }).addOnFailureListener(e->{if(manual)toast(a.t("Google Play санҷида нашуд.","Не удалось проверить Google Play.","Couldn't check Google Play.","بررسی گوگل پلی انجام نشد."));});
  }
  private void offer(AppUpdateInfo info,UpdateInfo metadata){
    new AlertDialog.Builder(a).setTitle(a.t("Версияи нав","Новая версия","New version","نسخه جدید")+(metadata==null?"":": "+metadata.version))
      .setMessage(metadata==null?a.t("Навсозӣ аз Google Play дастрас аст.","Обновление доступно в Google Play.","An update is available through Google Play.","به‌روزرسانی از گوگل پلی در دسترس است."):metadata.description)
      .setNegativeButton(a.t("Баъдтар","Позже","Later","بعداً"),null)
      .setPositiveButton(a.t("Навсозӣ","Обновить","Update","به‌روزرسانی"),(d,w)->{
        try{manager.startUpdateFlowForResult(info,a,AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),671);}
        catch(Exception ignored){toast(a.t("Навсозӣ оғоз нашуд.","Обновление не началось.","Couldn't start updating.","به‌روزرسانی شروع نشد."));}
      }).show();
  }
  private void ready(){
    if(prompted||a.isDestroyed()||a.isFinishing())return;prompted=true;
    new AlertDialog.Builder(a).setTitle(a.t("Навсозӣ тайёр аст","Обновление готово","Update ready","به‌روزرسانی آماده است"))
      .setNegativeButton(a.t("Баъдтар","Позже","Later","بعداً"),null)
      .setPositiveButton(a.t("Насб","Установить","Install","نصب"),(d,w)->manager.completeUpdate().addOnFailureListener(e->toast(a.t("Насб оғоз нашуд.","Установка не запустилась.","Installation couldn't start.","نصب شروع نشد."))))
      .setOnDismissListener(d->prompted=false).show();
  }
}
