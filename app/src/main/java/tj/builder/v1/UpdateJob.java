package tj.builder.v1;
import android.Manifest;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;

public final class UpdateJob extends JobService {
  private static final int ID=731;
  private static final String CHANNEL="builder-updates";
  private volatile JobParameters running;
  static void schedule(Context c){
    // In the Play flavor, Google Play supplies background update notifications.
    if(!BuildConfig.DIRECT_UPDATE||BuildConfig.UPDATE_FEED.isEmpty())return;
    JobScheduler js=c.getSystemService(JobScheduler.class);
    if(js==null||js.getPendingJob(ID)!=null)return;
    js.schedule(new JobInfo.Builder(ID,new ComponentName(c,UpdateJob.class)).setPeriodic(8L*60*60*1000)
        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).build());
  }
  @Override public boolean onStartJob(JobParameters p){
    running=p;
    new Thread(()->{
      boolean retry=false;
      try{
        if(Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED){
          UpdateInfo i=UpdateInfo.check(this,true);
          if(i!=null&&running==p)inform(i);
        }
      }catch(Exception e){retry=true;}
      final boolean again=retry;
      new android.os.Handler(getMainLooper()).post(()->{
        if(running==p){running=null;jobFinished(p,again);}
      });
    },"builder-update-check").start();
    return true;
  }
  @Override public boolean onStopJob(JobParameters p){if(running==p)running=null;return true;}
  private void inform(UpdateInfo i){
    android.content.SharedPreferences prefs=getSharedPreferences("update_notice",0);
    if(prefs.getLong("shown",0)>=i.code)return;
    NotificationManager m=getSystemService(NotificationManager.class);
    if(!m.areNotificationsEnabled())return;
    String channelName=BuilderLocale.t(this,"Навсозиҳо","Обновления","Updates","به‌روزرسانی‌ها");
    m.createNotificationChannel(new NotificationChannel(CHANNEL,channelName,NotificationManager.IMPORTANCE_DEFAULT));
    if(m.getNotificationChannel(CHANNEL).getImportance()==NotificationManager.IMPORTANCE_NONE)return;
    Intent intent=new Intent(this,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
    PendingIntent tap=PendingIntent.getActivity(this,ID,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Notification n=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle(BuilderLocale.t(this,"Версияи нав: ","Новая версия: ","New version: ","نسخه جدید: ")+i.version)
        .setContentText(i.description).setStyle(new Notification.BigTextStyle().bigText(i.description)).setContentIntent(tap).setAutoCancel(true).build();
    m.notify(ID,n);
    prefs.edit().putLong("shown",i.code).apply();
  }
}
