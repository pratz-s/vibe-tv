package tv.home.app;
import android.accessibilityservice.AccessibilityService; import android.content.*; import android.os.*; import android.util.Log; import android.view.KeyEvent;
import android.view.accessibility.*; import java.util.*;
/** Catches Home and the TLDR key, returns to our screen when Google's home appears, and reads the words Google hears. */
public class HomeService extends AccessibilityService {
  static final String T="TVHOME", PREFS="home"; private String last=""; private long lastAt; private boolean listening; private final Handler h=new Handler(Looper.getMainLooper());
  static boolean takeover(Context c){ return c.getSharedPreferences(PREFS,0).getBoolean("takeover",true); }
  static void setTakeover(Context c,boolean on){ c.getSharedPreferences(PREFS,0).edit().putBoolean("takeover",on).apply(); }
  private void open(String mode,String text){ Intent i=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT).putExtra("gate", MainActivity.gate(this)).putExtra("mode",mode); if(text!=null) i.putExtra("text",text); startActivity(i); }
  /** Keys a remote's mic or Assistant button can send. */
  static boolean micKey(int k){ return k==KeyEvent.KEYCODE_SEARCH||k==KeyEvent.KEYCODE_ASSIST||k==KeyEvent.KEYCODE_VOICE_ASSIST; }
  /** Home always comes back here, even after a visit to Google TV. In our app, the mic starts our own listening instead of Google's. */
  private volatile long lastKeyAt; private volatile String fgPkg; private static boolean watching;
  // every half minute: a video app in front with the remote idle counts as watching (for apps that don't say what's playing)
  // and what the TV is being used for, half a minute at a time: watching, music, YouTube, a device on HDMI, browsing
  private final java.util.Map<String,Double> use=new java.util.HashMap<>(); private int useTicks;
  private final Runnable fgTick=new Runnable(){ public void run(){ try{ if(((android.os.PowerManager)getSystemService(POWER_SERVICE)).isInteractive()){ Watcher.foreground(fgPkg, lastKeyAt);
      String p=fgPkg==null?"":fgPkg, k=Watcher.VIDEO.contains(p)?"watching":(p.contains("spotify")||p.contains("tvmusic"))?"music":p.contains("youtube")?"youtube":(p.contains("inputplayer")||p.contains("tvinput")||p.contains("hdmi"))?"hdmi":(p.equals("tv.home.app")||p.contains("launcher"))?"browsing":"other";
      use.put(k,use.containsKey(k)?use.get(k)+0.5:0.5); }
      if(++useTicks>=20&&!use.isEmpty()){ useTicks=0; java.util.Calendar c=java.util.Calendar.getInstance(); org.json.JSONObject o=new org.json.JSONObject().put("day",String.format(java.util.Locale.US,"%tF",c)).put("hour",c.get(java.util.Calendar.HOUR_OF_DAY)).put("minutes",new org.json.JSONObject(use)); use.clear();
        Api.post("/activity",o.toString(),new Api.Done<String>(){ public void ok(String b){} public void fail(String w){ Log.w(T,"activity: "+w); } }); }
    }catch(Exception ignored){} h.postDelayed(this,30000); } };
  @Override protected boolean onKeyEvent(KeyEvent e){ int k=e.getKeyCode(); lastKeyAt=SystemClock.elapsedRealtime();
    if(overlay!=null&&overlay.showing()){ if(k==KeyEvent.KEYCODE_DPAD_CENTER||k==KeyEvent.KEYCODE_ENTER){ if(e.getAction()==KeyEvent.ACTION_UP&&!overlay.finish()) overlay.stop(); return true; } if(k==KeyEvent.KEYCODE_BACK){ if(e.getAction()==KeyEvent.ACTION_UP) overlay.stop(); return true; } } if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0) Log.i(T,"key "+k+" "+KeyEvent.keyCodeToString(k)+(MainActivity.shown?" (in app)":""));
    // The voice button picked during setup (VOICE_KEY in .env) starts listening from anywhere, even inside other apps.
    if(BuildConfig.VOICE_KEY>0&&k==BuildConfig.VOICE_KEY){ if(e.getAction()==KeyEvent.ACTION_UP&&e.getRepeatCount()==0){
        if(overlay!=null&&overlay.showing()){ if(!overlay.finish()) overlay.stop(); }
        else if(MainActivity.shown) open("listen",null);
        else if(overlay!=null) overlay.start(); }
      return true; }
    if(k==KeyEvent.KEYCODE_HOME||k==KeyEvent.KEYCODE_BUTTON_13){ if(e.getAction()==KeyEvent.ACTION_UP){ setTakeover(this,true); open("home",null); } return true; }
    if(micKey(k)&&MainActivity.shown){ if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0) open("listen",null); return true; }
    return false; }
  private void read(AccessibilityNodeInfo n,Set<String> out,int d){ if(n==null||d>14) return; if(n.getText()!=null&&n.getText().length()>0) out.add(n.getText().toString()); for(int i=0;i<n.getChildCount();i++) read(n.getChild(i),out,d+1); }
  private final Runnable finish=new Runnable(){ public void run(){ if(!listening||last.isEmpty()) return; if(SystemClock.elapsedRealtime()-lastAt<SILENCE){ h.postDelayed(this,200); return; } hand(); } };
  /** Google ends speech itself when it's sure, so this is only the fallback for when it doesn't. People pause while thinking. */
  private static final int SILENCE=3200;
  /** Google's own prompts sit in the same window as the words it hears. */
  private static final java.util.regex.Pattern PROMPT=java.util.regex.Pattern.compile("(?i)^\\s*(hold (the|down)|press (and|the)|listening|speak now|say something|try (saying|asking)|ask (me|gemini|google)|how can i help|what can i|hi,|hello,|talk to|tap |use your voice|search (movies|for|apps|youtube|google))");
  /** While visiting Google TV, these can come and go without ending the visit; anything else (an app) ends it. */
  private static final java.util.regex.Pattern VISIT_OK=java.util.regex.Pattern.compile("launcherx|katniss|systemui|^android$|inputmethod|tv\\.home\\.app|partnercustomizer|leanbacklauncher");
  /** Screen time: every minute the screen is on counts toward today. */
  private int minutes;
  private final Runnable screenTime=new Runnable(){ public void run(){ try{ if(((android.os.PowerManager)getSystemService(POWER_SERVICE)).isInteractive()){ android.content.SharedPreferences p=getSharedPreferences(PREFS,0); String k=Widgets.dayKey(java.util.Calendar.getInstance()); p.edit().putLong(k,p.getLong(k,0)+60).apply(); if(++minutes%30==0){ try{ Api.post("/screen",new org.json.JSONObject().put("day",k.substring(3)).put("minutes",p.getLong(k,0)/60).toString(),new Api.Done<String>(){ public void ok(String b){} public void fail(String w){} }); }catch(Exception ignored){} } } }catch(Exception ignored){} h.postDelayed(this,60000); } };
  private long quietUntil, guardUntil, wokeAt, stoleAt, profileAt; private Overlay overlay; private String lastPkg="";
  /** Power on and waking from the screensaver always land on our screen, with the splash. */
  private void wake(long guardMs){ long now=SystemClock.elapsedRealtime(); guardUntil=now+guardMs; if(now-wokeAt<3000||!takeover(this)) return; wokeAt=now; Log.i(T,"wake"); open("wake",null); }
  private final BroadcastReceiver power=new BroadcastReceiver(){ @Override public void onReceive(Context c,Intent i){ wake(12000); } };
  private void hand(){ if(!listening) return; listening=false; String said=last; last=""; quietUntil=SystemClock.elapsedRealtime()+4000; Log.i(T,"heard: "+said); performGlobalAction(GLOBAL_ACTION_BACK); h.postDelayed(()->open("ask",said),120); }
  private String lastDump="";
  // ---- skip "Who's watching?": when Prime or JioHotstar opens on its profile screen, pick the highlighted (last used) profile ----
  // Neither app exposes that screen to accessibility, so we glance at the screen itself (kept in memory on the TV, never saved or sent)
  // and press OK only when the picture matches that app's profile screen.
  private static final String PRIME="com.amazon.amazonvideo.livingroom", HOTSTAR="in.startv.hotstar"; private String pickPkg=""; private int pickTry;
  private void watchProfile(String pkg){ if(android.os.Build.VERSION.SDK_INT<33) return; pickPkg=pkg; pickTry=0; Log.i(T,"profile watch: "+pkg); h.postDelayed(()->lookForPicker(pkg),1500); }
  private void lookForPicker(String pkg){ if(!pkg.equals(pickPkg)||++pickTry>18) return;
    takeScreenshot(android.view.Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback(){
      @Override public void onSuccess(ScreenshotResult r){ boolean hit=false; try{ android.hardware.HardwareBuffer hb=r.getHardwareBuffer(); android.graphics.Bitmap b=android.graphics.Bitmap.wrapHardwareBuffer(hb,r.getColorSpace()).copy(android.graphics.Bitmap.Config.ARGB_8888,false); hb.close();
          hit=PRIME.equals(pkg)?primePicker(b):hotstarPicker(b); b.recycle(); }catch(Throwable t){ Log.w(T,"profile look: "+t); }
        if(hit){ Log.i(T,"profile screen in "+pkg+": picking the highlighted profile"); pickPkg=""; performGlobalAction(GLOBAL_ACTION_DPAD_CENTER); } else h.postDelayed(()->lookForPicker(pkg),700); }
      @Override public void onFailure(int code){ h.postDelayed(()->lookForPicker(pkg),900); } }); }
  private static int luma(android.graphics.Bitmap b,float fx,float fy){ int c=b.getPixel(Math.min(b.getWidth()-1,(int)(fx*b.getWidth())),Math.min(b.getHeight()-1,(int)(fy*b.getHeight()))); return (299*((c>>16)&255)+587*((c>>8)&255)+114*(c&255))/1000; }
  /** Prime's picker: a column of avatars on the left and pure black everywhere else. */
  private static boolean primePicker(android.graphics.Bitmap b){ for(float x=.40f;x<.97f;x+=.05f) for(float y=.06f;y<.95f;y+=.08f) if(luma(b,x,y)>12) return false;
    int bright=0; for(float y=.04f;y<.75f;y+=.01f) if(luma(b,.123f,y)>60) bright++; return bright>=8; }
  /** JioHotstar's picker: the "Who's watching?" title across the top centre and the white ring of the highlighted avatar. */
  private static boolean hotstarPicker(android.graphics.Bitmap b){ int title=0; for(float x=.40f;x<.60f;x+=.004f) for(float y=.095f;y<.125f;y+=.008f) if(luma(b,x,y)>190) title++;
    int ring=0; for(float x=.47f;x<.53f;x+=.004f) for(float y=.64f;y<.655f;y+=.004f) if(luma(b,x,y)>200) ring++;
    int corner=0; for(float x=.75f;x<.98f;x+=.05f) for(float y=.05f;y<.12f;y+=.03f) if(luma(b,x,y)<60) corner++; return title>=20&&corner>=8; }
  @Override public void onAccessibilityEvent(AccessibilityEvent e){ CharSequence p=e.getPackageName(); if(p==null) return; String pkg=p.toString(), cls=String.valueOf(e.getClassName());
    if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&!pkg.contains("systemui")&&!pkg.contains("katniss")) fgPkg=pkg;
    if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&(pkg.equals(PRIME)||pkg.equals(HOTSTAR))&&!pkg.equals(pickPkg)) watchProfile(pkg);
    else if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&!pkg.equals(pickPkg)&&!pkg.contains("systemui")) pickPkg="";
    if(!takeover(this)){ if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&!VISIT_OK.matcher(pkg).find()){ Log.i(T,"visit over: "+pkg); setTakeover(this,true); } return; }
    if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&!pkg.equals("tv.home.app")&&!pkg.contains("launcherx")&&!pkg.contains("systemui")) lastPkg=pkg;
    // The remote's profile button opens Google TV's profile picker. Over our screen, it becomes our voice search instead.
    if(pkg.contains("launcherx")&&e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&cls.toLowerCase(Locale.US).contains("profile")){ long now=SystemClock.elapsedRealtime();
      boolean first=now-profileAt>2500; profileAt=now; guardUntil=0;
      if(MainActivity.shown||now-MainActivity.leftAt<1500){ Log.i(T,"profile button: voice search"+(first?"":" (repeat)")); if(first) open("listen",null); return; }
      // Anywhere else: close the picker and float our capsule over whatever is playing.
      if(first){ Log.i(T,"profile button: voice over "+lastPkg); performGlobalAction(GLOBAL_ACTION_BACK); h.postDelayed(()->overlay.start(),350); } return; }
    // The remote's voice button always starts Google's voice screen. If that happens over our screen, close it at once and listen ourselves.
    if(pkg.contains("katniss")&&e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){ long now=SystemClock.elapsedRealtime();
      if((MainActivity.shown||now-MainActivity.leftAt<2500)&&now-stoleAt>3000){ stoleAt=now; quietUntil=now+5000; listening=false; last=""; Log.i(T,"voice button: taking over from Google"); performGlobalAction(GLOBAL_ACTION_BACK); h.postDelayed(()->open("listen",null),350); return; } }
    if(pkg.contains("katniss")){ if(SystemClock.elapsedRealtime()<quietUntil) return; AccessibilityNodeInfo root=getRootInActiveWindow(); if(root==null||!String.valueOf(root.getPackageName()).contains("katniss")) return; Set<String> o=new LinkedHashSet<>(); read(root,o,0); String best=""; for(String s:o) if(s.length()>best.length()&&!PROMPT.matcher(s).find()) best=s; if(best.length()>2&&!best.equals(last)){ last=best; lastAt=SystemClock.elapsedRealtime(); if(!listening){ listening=true; h.postDelayed(finish,SILENCE); } } return; }
    if(e.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
    if(pkg.contains("launcherx")){ if(cls.contains("gemini")){ if(listening) hand(); else if(!last.isEmpty()){ listening=true; hand(); } return; } long now=SystemClock.elapsedRealtime(); if(listening||now<quietUntil) return;
      // Google's launcher comes up late after a boot and covers us, so for a short while we keep taking the screen back.
      if(now<guardUntil){ Log.i(T,"front over "+cls); open("front",null); return; } if(cls.contains("HomeActivity")||cls.contains("MainActivity")) open(now-profileAt<4000||now-stoleAt<4000?"front":"home",null); } }
  @Override public void onInterrupt(){} @Override protected void onServiceConnected(){ Log.i(T,"service connected"); /* LEARN=false in .env: nothing about what plays or which app is open leaves the TV */ if(!watching&&BuildConfig.LEARN){ watching=true; new Watcher(this).start(); } h.removeCallbacks(fgTick); if(BuildConfig.LEARN) h.postDelayed(fgTick,30000); /* the system can connect twice: one watcher only, or every play is counted twice */ overlay=new Overlay(this); new RemoteLink(this, new RemoteLink.Actions(){ public void home(){ setTakeover(HomeService.this,true); open("home",null); } public void back(){ performGlobalAction(GLOBAL_ACTION_BACK); } public void move(int code){ if(MainActivity.shown){ open("key:"+code,null); return; }
        // A real D-pad press works in every app, including ones that draw their own screen (Prime, JioHotstar).
        if(android.os.Build.VERSION.SDK_INT>=33){ performGlobalAction(code==KeyEvent.KEYCODE_DPAD_UP?GLOBAL_ACTION_DPAD_UP:code==KeyEvent.KEYCODE_DPAD_DOWN?GLOBAL_ACTION_DPAD_DOWN:code==KeyEvent.KEYCODE_DPAD_LEFT?GLOBAL_ACTION_DPAD_LEFT:GLOBAL_ACTION_DPAD_RIGHT); return; }
        int dir=code==KeyEvent.KEYCODE_DPAD_UP?android.view.View.FOCUS_UP:code==KeyEvent.KEYCODE_DPAD_DOWN?android.view.View.FOCUS_DOWN:code==KeyEvent.KEYCODE_DPAD_LEFT?android.view.View.FOCUS_LEFT:android.view.View.FOCUS_RIGHT;
        android.view.accessibility.AccessibilityNodeInfo f=findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT); android.view.accessibility.AccessibilityNodeInfo n=f==null?null:f.focusSearch(dir);
        if(n!=null) n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS); else Log.i(T,"remote move: nothing to focus"); }
      public void off(){ Log.i(T,"remote: turn off"); performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN); }
      public void ok(){ if(!MainActivity.shown&&android.os.Build.VERSION.SDK_INT>=33){ performGlobalAction(GLOBAL_ACTION_DPAD_CENTER); return; } android.view.accessibility.AccessibilityNodeInfo f=findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT); if(f!=null) f.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK); else if(MainActivity.shown) open("press",null); } }).start(); h.postDelayed(screenTime,60000); IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_ON); f.addAction(Intent.ACTION_DREAMING_STOPPED); registerReceiver(power,f,Context.RECEIVER_EXPORTED); if(SystemClock.elapsedRealtime()<300000) wake(45000); }
  @Override public void onDestroy(){ try{ unregisterReceiver(power); }catch(Exception ignored){} super.onDestroy(); }
}
