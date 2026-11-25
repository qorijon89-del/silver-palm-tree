package tj.builder.v1;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.*;

final class ProjectBook {
  static final class Item {
    final String title,id,version,format,kind,icon;
    final boolean auto;
    final int last;
    final Set<String> permissions;
    Item(String t,String i,String v,String f,String k,String icon,boolean a,int n,Set<String> p){
      title=t;id=i;version=v;format=f;kind=k;this.icon=icon;auto=a;last=n;permissions=new TreeSet<>(p);
    }
    int nextCode(){if(last>=2100000000)throw new IllegalStateException("versionCode");return last+1;}
    String nextVersion(){
      if(!auto||last==0)return version;
      String[] parts=version.split("[.]",-1);
      try{int i=parts.length-1;parts[i]=Integer.toString(Math.addExact(Integer.parseInt(parts[i]),1));return String.join(".",parts);}
      catch(RuntimeException e){return version+"."+nextCode();}
    }
    Item built(){return new Item(title,id,nextVersion(),format,kind,icon,auto,nextCode(),permissions);}
  }
  private final SharedPreferences prefs;
  ProjectBook(Context c){prefs=c.getSharedPreferences("builder_projects",Context.MODE_PRIVATE);}
  Item get(String id){
    String data=prefs.getString("project:"+id,null);if(data==null)return null;
    try{
     JSONObject o=new JSONObject(data);Set<String> p=new TreeSet<>();JSONArray a=o.getJSONArray("permissions");
     for(int n=0;n<a.length();n++)p.add(a.getString(n));
     return new Item(o.getString("title"),id,o.getString("version"),o.getString("format"),o.getString("kind"),o.optString("icon",""),o.getBoolean("auto"),o.getInt("last"),p);
    }catch(JSONException e){return null;}
  }
  List<Item> all(){
    List<Item> a=new ArrayList<>();
    for(String k:prefs.getAll().keySet())if(k.startsWith("project:")){
      Item item=get(k.substring(8));if(item!=null)a.add(item);
    }
    a.sort(Comparator.comparing(i->i.title));return a;
  }
  boolean store(Item i){
    try{
     JSONObject o=new JSONObject().put("title",i.title).put("version",i.version).put("format",i.format).put("kind",i.kind)
       .put("icon",i.icon).put("auto",i.auto).put("last",i.last).put("permissions",new JSONArray(i.permissions));
     return prefs.edit().putString("project:"+i.id,o.toString()).commit();
    }catch(JSONException e){return false;}
  }
  static boolean validId(String s){return s.length()<=120&&s.matches("[A-Za-z][A-Za-z0-9_]*(?:[.][A-Za-z][A-Za-z0-9_]*)+");}
  static boolean validVersion(String s){return s.length()<=24&&s.matches("[0-9]+(?:[.][0-9]+){0,4}");}
}
