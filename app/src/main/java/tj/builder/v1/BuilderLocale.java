package tj.builder.v1;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import java.util.*;
final class BuilderLocale {
  static final String[] CODES={"tg","ru","en","fa"};
  static final String[] LABELS={"Тоҷикӣ","Русский","English","فارسی"};
  static int index(Context c){
    int n=c.getSharedPreferences("builder_ui",0).getInt("language",-1);
    return n>=0&&n<CODES.length?n:0;
  }
  static boolean chosen(Context c){return c.getSharedPreferences("builder_ui",0).contains("language");}
  static String t(Context c,String tg,String ru,String en,String fa){return new String[]{tg,ru,en,fa}[index(c)];}
  static void apply(Context c,View root){root.setLayoutDirection(index(c)==3?View.LAYOUT_DIRECTION_RTL:View.LAYOUT_DIRECTION_LTR);}
  static void pick(Activity a,boolean first){
    AlertDialog d=new AlertDialog.Builder(a).setTitle("Забон · Язык · Language · زبان").setItems(LABELS,(v,i)->{
      a.getSharedPreferences("builder_ui",0).edit().putInt("language",i).apply();a.recreate();
    }).setCancelable(!first).create();
    d.show();
  }
}
