package tj.builder.v1;
import android.app.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import java.util.*;

final class ProjectWizard {
  private final MainActivity a;
  private final ProjectBook book;
  private final ProjectBook.Item old;
  private final Runnable pick;
  private final AlertDialog dialog;
  private final LinearLayout form, nav;
  private int step;
  private String title="",id="",version="1.0",format="apk",kind="html",icon="",iconName="";
  private boolean auto=true;
  private final Set<String> perms=new TreeSet<>();
  private EditText titleInput,idInput,versionInput;
  private Spinner formatInput, kindInput;
  private CheckBox autoInput;
  private TextView iconText;
  private final LinkedHashMap<String,CheckBox> permFields=new LinkedHashMap<>();
  ProjectWizard(MainActivity a,ProjectBook book,ProjectBook.Item old,Runnable pick){
    this.a=a;this.book=book;this.old=old;this.pick=pick;
    if(old!=null){title=old.title;id=old.id;version=old.auto?old.version:old.version;kind=old.kind;format=old.format;icon=old.icon;auto=old.auto;perms.addAll(old.permissions);}
    LinearLayout outer=new LinearLayout(a);outer.setOrientation(1);
    int p=(int)(16*a.getResources().getDisplayMetrics().density);outer.setPadding(p,p,p,p);
    ScrollView s=new ScrollView(a);form=new LinearLayout(a);form.setOrientation(1);
    s.addView(form);outer.addView(s,new LinearLayout.LayoutParams(-1,0,1));
    nav=new LinearLayout(a);nav.setOrientation(0);outer.addView(nav);
    BuilderLocale.apply(a,outer);
    dialog=new AlertDialog.Builder(a).setView(outer).create();
  }
  String t(String tg,String ru,String en,String fa){return BuilderLocale.t(a,tg,ru,en,fa);}
  void show(){dialog.show();dialog.getWindow().setLayout(-1,-1);render();}
  void setIcon(Uri i,String name){icon=i.toString();iconName=name;if(step==2)render();}
  private void render(){
    form.removeAllViews();nav.removeAllViews();permFields.clear();
    TextView h=label(t("Қадам ","Шаг ","Step ","مرحله ")+(step+1)+" / 4");h.setTypeface(null,Typeface.BOLD);h.setAccessibilityHeading(true);
    switch(step){
      case 0:
        titleInput=field(t("Номи барнома","Название","App name","نام برنامه"),title,false);
        idInput=field(t("Номи баста","Идентификатор","Package name","نام بسته"),id,true);
        idInput.setHint("com.example.pogoda");idInput.setEnabled(old==null);
        versionInput=field(t("Рақами версия","Версия","Version name","شماره نسخه"),version,true);
        autoInput=check(t("Версия худкор боло равад","Повышать версию автоматически","Increase version automatically","افزایش خودکار نسخه"),auto);
        if(old!=null){versionInput.setEnabled(!auto);autoInput.setOnCheckedChangeListener((b,on)->versionInput.setEnabled(!on));}
        kindInput=spin(t("Навъи файл","Тип файла","Input type","نوع فایل"),new String[]{"HTML / HTML ZIP",t("ZIP-и Android","ZIP Android","Android ZIP","زیپ اندروید")},kind.equals("html")?0:1);
        kindInput.setEnabled(old==null);
        label(t("Android-ро Builder танзим мекунад. Ҳадди ақал аз лоиҳа вобаста аст.","Версии Android задаёт Builder. Минимум зависит от проекта.","Builder sets Android targets. The minimum depends on the project.","نسخه اندروید را سازنده تنظیم می‌کند؛ حداقل به پروژه بستگی دارد."));
        break;
      case 1:
        label(t("Иҷозатҳоро бо сабабашон интихоб кунед.","Выберите нужные разрешения.","Choose permissions by purpose.","مجوزهای لازم را با توجه به کاربرد انتخاب کنید."));
        option("INTERNET",t("Интернет: муҳтавои онлайн","Интернет: онлайн-контент","Internet: online content","اینترنت: محتوای آنلاین"));
        if(kind.equals("android")){
          option("RECORD_AUDIO",t("Микрофон: сабти овоз","Микрофон: запись звука","Microphone: record audio","میکروفون: ضبط صدا"));
          option("CAMERA",t("Камера: акс ё видео","Камера: фото и видео","Camera: photos and video","دوربین: عکس و ویدیو"));
          option("ACCESS_COARSE_LOCATION",t("Ҷойгиршавӣ: маҳалли тахминӣ","Местоположение: приблизительное","Location: approximate position","مکان: موقعیت تقریبی"));
          option("ACCESS_FINE_LOCATION",t("GPS: ҷойгиршавии дақиқ","GPS: точное местоположение","GPS: precise location","جی‌پی‌اس: موقعیت دقیق"));
          option("POST_NOTIFICATIONS",t("Огоҳиномаҳо: ёдрасӣ","Уведомления: напоминания","Notifications: reminders","اعلان‌ها: یادآوری"));
        } else label(t("Дар табдили HTML ҳоло танҳо иҷозати интернет дастрас аст.","Для HTML сейчас доступен только интернет.","This HTML export supports only the Internet permission.","در تبدیل HTML فعلاً فقط مجوز اینترنت در دسترس است."));
        break;
      case 2:
        iconText=label(icon.isEmpty()?t("Нишонаи пешфарз","Значок по умолчанию","Default icon","نماد پیش‌فرض"):iconName.isEmpty()?t("Нишона захира шудааст","Значок сохранён","Icon saved","نماد ذخیره شد"):iconName);
        button(form,t("Интихоби нишона, ихтиёрӣ","Выбрать значок (необязательно)","Choose icon (optional)","انتخاب نماد (اختیاری)"),pick);
        button(form,t("Нишонаи пешфарз","Значок по умолчанию","Use default icon","استفاده از نماد پیش‌فرض"),()->{icon="";iconName="";render();});
        break;
      default:
        label(title+"\n"+id+"\n"+t("Версия: ","Версия: ","Version: ","نسخه: ")+item().nextVersion());
        formatInput=spin(t("Формати натиҷа","Формат файла","Output format","قالب خروجی"),new String[]{"APK","AAB"},format.equals("aab")?1:0);
        label(t("Баъд аз «Тайёр» файлро интихоб мекунед.","После «Готово» выберите файл.","After Finish, choose your input file.","پس از «انجام شد»، فایل را انتخاب کنید."));
    }
    button(nav,step==0?t("Бекор","Отмена","Cancel","لغو"):t("Бозгашт","Назад","Back","بازگشت"),()->{
      if(step==0)dialog.dismiss();else {capture();step--;render();}
    });
    button(nav,step==3?t("Тайёр","Готово","Finish","انجام شد"):t("Давом","Далее","Next","بعدی"),()->{
      capture();
      if(step==0){
        if(title.isEmpty()||title.length()>80||!ProjectBook.validId(id)||!ProjectBook.validVersion(version)){
          label(t("Ном, номи баста ва версияро ислоҳ кунед.","Проверьте имя, идентификатор и версию.","Check the app name, package and version.","نام، نام بسته و نسخه را بررسی کنید."));return;
        }
        if(old==null&&book.get(id)!=null){
          label(t("Ин баста аллакай лоиҳа дорад.","Этот идентификатор уже используется.","A project already uses this package.","این نام بسته قبلاً استفاده شده است."));return;
        }
      }
      if(step<3){step++;render();return;}
      ProjectBook.Item i=item();
      if(!book.store(i)){label(t("Лоиҳа захира нашуд.","Проект не сохранён.","Project could not be saved.","پروژه ذخیره نشد."));return;}
      dialog.dismiss();a.useProject(i);
    });
  }
  private ProjectBook.Item item(){return new ProjectBook.Item(title,id,version,format,kind,icon,auto,old==null?0:old.last,perms);}
  private void capture(){
    if(step==0){
      title=titleInput.getText().toString().trim();id=idInput.getText().toString().trim();version=versionInput.getText().toString().trim();
      auto=autoInput.isChecked();kind=kindInput.getSelectedItemPosition()==0?"html":"android";
    }
    if(step==1){
      perms.clear();for(Map.Entry<String,CheckBox> e:permFields.entrySet())if(e.getValue().isChecked())perms.add(e.getKey());
      if(perms.contains("ACCESS_FINE_LOCATION"))perms.add("ACCESS_COARSE_LOCATION");
    }
    if(step==3)format=formatInput.getSelectedItemPosition()==0?"apk":"aab";
  }
  private TextView label(String t){
    TextView v=new TextView(a);v.setText(t);v.setTextSize(18);v.setPadding(0,10,0,10);form.addView(v);return v;
  }
  private EditText field(String text,String value,boolean ascii){
    TextView l=label(text);EditText e=new EditText(a);e.setId(View.generateViewId());l.setLabelFor(e.getId());
    if(ascii){e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);e.setTextDirection(View.TEXT_DIRECTION_LTR);}
    e.setSingleLine(true);e.setText(value);form.addView(e);return e;
  }
  private CheckBox check(String title,boolean value){CheckBox c=new CheckBox(a);c.setText(title);c.setChecked(value);form.addView(c);return c;}
  private void option(String key,String title){permFields.put(key,check(title,perms.contains(key)));}
  private Spinner spin(String title,String[] items,int index){
    TextView l=label(title);Spinner s=new Spinner(a);s.setId(View.generateViewId());l.setLabelFor(s.getId());
    ArrayAdapter<String> adapter=new ArrayAdapter<>(a,android.R.layout.simple_spinner_item,items);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(adapter);s.setSelection(index);form.addView(s);return s;
  }
  private void button(LinearLayout parent,String title,Runnable work){
    Button b=new Button(a);b.setText(title);b.setAllCaps(false);b.setOnClickListener(v->work.run());parent.addView(b);
  }
}
