package ch.fiduciariamega.biglietto;

import android.app.Activity;import android.os.Bundle;import android.graphics.*;import android.graphics.drawable.GradientDrawable;import android.view.*;import android.widget.*;import com.google.zxing.*;import com.google.zxing.common.BitMatrix;import java.util.*;

public class MainActivity extends Activity {
 int green=Color.rgb(91,175,72); TextView tv(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(Color.rgb(45,45,45));t.setGravity(Gravity.CENTER);if(bold)t.setTypeface(null,1);t.setPadding(12,5,12,5);return t;}
 @Override public void onCreate(Bundle b){super.onCreate(b);ScrollView sv=new ScrollView(this); LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setGravity(Gravity.CENTER_HORIZONTAL);root.setPadding(36,36,36,36);root.setBackgroundColor(Color.WHITE);
 TextView logo=tv("FIDUCIARIA MEGA",28,true);logo.setTextColor(green);root.addView(logo); TextView sub=tv("SOCIETÀ FIDUCIARIA E DI REVISIONE",11,false);sub.setTextColor(green);root.addView(sub);
 root.addView(tv("Fabio Canciani",24,true));root.addView(tv("Vicedirettore",17,false));root.addView(tv("Fiduciaria Mega SA",17,false));root.addView(tv("Via Vela 1 · 6830 Chiasso",16,false));root.addView(tv("+41 91 682 04 00",16,false));root.addView(tv("+41 79 221 22 12",16,false));root.addView(tv("fabio.canciani@fiduciariamega.com",15,false));
 try{String v="BEGIN:VCARD\nVERSION:3.0\nN:Canciani;Fabio;;;\nFN:Fabio Canciani\nORG:Fiduciaria Mega SA\nTITLE:Vicedirettore\nTEL;TYPE=WORK:+41916820400\nTEL;TYPE=CELL:+41792212212\nEMAIL:fabio.canciani@fiduciariamega.com\nADR;TYPE=WORK:;;Via Vela 1;Chiasso;;6830;Switzerland\nEND:VCARD";BitMatrix m=new MultiFormatWriter().encode(v,BarcodeFormat.QR_CODE,650,650);Bitmap q=Bitmap.createBitmap(650,650,Bitmap.Config.RGB_565);for(int y=0;y<650;y++)for(int x=0;x<650;x++)q.setPixel(x,y,m.get(x,y)?Color.BLACK:Color.WHITE);ImageView iv=new ImageView(this);iv.setImageBitmap(q);root.addView(iv,new LinearLayout.LayoutParams(-1,700));}catch(Exception e){}
 TextView hint=tv("Inquadra il QR per salvare il contatto",14,false);hint.setTextColor(Color.GRAY);root.addView(hint);sv.addView(root);setContentView(sv);}
}
