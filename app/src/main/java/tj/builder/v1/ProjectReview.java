package tj.builder.v1;
import android.util.Xml;
import android.content.Context;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

final class ProjectReview {
    private static final String ANDROID="http://schemas.android.com/apk/res/android";
    private static final String[] KNOWN={"INTERNET","RECORD_AUDIO","CAMERA","ACCESS_COARSE_LOCATION","ACCESS_FINE_LOCATION","POST_NOTIFICATIONS"};
    private static final String[] REASONS={"муҳтавои онлайн","сабти садо","камера","ҷойгиршавии тахминӣ","ҷойгиршавии дақиқ","огоҳиномаҳо"};
    private static final Pattern REMOTE=Pattern.compile("(?:src|href)\\s*=\\s*[\"']https://|(?:fetch|new\\s+Audio)\\s*\\(\\s*[\"']https://",Pattern.CASE_INSENSITIVE);
    static String nativeReview(Context c,byte[] xml) throws Exception {
        if (xml==null) return LocalizedStatus.get(c,"Феҳристи иҷозатҳо дастрас нест.");
        XmlPullParser parser=Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);
        parser.setInput(new ByteArrayInputStream(xml),null);
        TreeSet<String> permissions=new TreeSet<>();
        for(int ev=parser.getEventType();ev!=XmlPullParser.END_DOCUMENT;ev=parser.nextToken()){
            if(ev==XmlPullParser.DOCDECL)throw new IOException();
            if(ev==XmlPullParser.START_TAG && ("uses-permission".equals(parser.getName())||"uses-permission-sdk-23".equals(parser.getName()))){
                String n=parser.getAttributeValue(ANDROID,"name");
                if(n!=null&&n.matches("[A-Za-z0-9_.]{1,150}")&&permissions.size()<24)permissions.add(n);
            }
        }
        if(permissions.isEmpty())return LocalizedStatus.get(c,"Дар файли асосӣ иҷозати эълоншуда нест. Китобхонаҳо метавонанд иҷозат илова кунанд.");
        ArrayList<String> summaries=new ArrayList<>();
        for(String p:permissions){
            String use="";
            for(int k=0;k<KNOWN.length;k++)if(p.equals("android.permission."+KNOWN[k]))use=": "+LocalizedStatus.get(c,REASONS[k]);
            summaries.add(p.replaceFirst("^android[.]permission[.]","")+use);
        }
        return LocalizedStatus.get(c,"Иҷозатҳои файли асосӣ: ")+String.join("; ",summaries)+LocalizedStatus.get(c,". Ин рӯйхат иҷозатҳоро тағйир намедиҳад.");
    }
    static String html(Context c,byte[] data) {
        String s=new String(data,StandardCharsets.UTF_8);
        ArrayList<String> a=new ArrayList<>();
        if(REMOTE.matcher(s).find())a.add(LocalizedStatus.get(c,"Интернет: пайванди HTTPS ёфт шуд. Барои муҳтавои онлайн метавонед интернетро фаъол кунед."));
        if(s.contains("getUserMedia") && s.matches("(?s).*audio\\s*:.*"))a.add(LocalizedStatus.get(c,"Микрофон: дархости сабти овоз дида шуд. Ин навъи табдили HTML онро ҳанӯз фаъол намекунад."));
        if(s.contains("getUserMedia") && s.matches("(?s).*video\\s*:.*"))a.add(LocalizedStatus.get(c,"Камера: дархости видео дида шуд. Дар ин нусха дастрас нест."));
        if(s.contains("navigator.geolocation"))a.add(LocalizedStatus.get(c,"Ҷойгиршавӣ: дархости GPS дида шуд. Дар ин нусха дастрас нест."));
        return a.isEmpty()?LocalizedStatus.get(c,"Дар матни санҷидашуда иҷозати равшани иловагӣ ёфт нашуд. Ин санҷиш ҳама вазифаҳоро муайян карда наметавонад."):String.join("\n",a);
    }
}
