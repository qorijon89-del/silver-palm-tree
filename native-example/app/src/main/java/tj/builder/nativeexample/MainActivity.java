package tj.builder.nativeexample;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
public final class MainActivity extends Activity {
 @Override public void onCreate(Bundle b) {
  super.onCreate(b);
  LinearLayout box=new LinearLayout(this);
  box.setOrientation(1);box.setPadding(28,28,28,28);
  TextView title=new TextView(this);title.setTextSize(24);title.setText("Лоиҳаи аслии Android");title.setAccessibilityHeading(true);
  Button test=new Button(this);test.setText("Санҷиш");test.setAllCaps(false);
  TextView status=new TextView(this);status.setTextSize(18);status.setText("Омода");status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
  test.setOnClickListener(v->status.setText("ZIP-и Android бомуваффақият сохта шуд"));
  box.addView(title);box.addView(test);box.addView(status);setContentView(box);
 }
}
