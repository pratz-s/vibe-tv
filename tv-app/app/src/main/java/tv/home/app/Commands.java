package tv.home.app;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Things you can say that act on the TV straight away, in English or Hinglish: music (pause, play, next, previous),
 * volume (set to a number, up, down, mute, unmute), apps (open, close) and going home. Anything else is a search.
 */
final class Commands {
  /** What happened, said back to the person; null when it wasn't a command. */
  /** What they call the assistant (ASSISTANT_NAME in .env, Jarvis by default): "Hey Jarvis, play Paatal Lok" is just "play Paatal Lok". */
  static String unJarvis(String said) { if (said == null) return null; String name = BuildConfig.ASSISTANT_NAME.trim().toLowerCase(Locale.US).replaceAll("[^a-z ]", ""); if (name.isEmpty()) return said;
    String alts = name + ("jarvis".equals(name) ? "|jarvish|jarwis|travis" : "");
    String t = said.replaceAll("(?i)^\\s*(hey|hi|ok|okay|hello|arre|oye)?[ ,]*(" + alts + ")\\b[ ,.!:]*", "").replaceAll("(?i)[ ,]+(" + alts + ")[.!?]*\\s*$", "").trim(); return t.isEmpty() ? said : t; }
  static String run(Context c, String said) { said = unJarvis(said);
    String s = " " + said.toLowerCase(Locale.US).replaceAll("[^a-z0-9% ]", " ").replaceAll("\\s+", " ").trim() + " ";
    AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    try {
      // volume
      Matcher n = Pattern.compile(" (?:volume|awaaz|aawaz|sound)\\b.*?(\\d{1,3})| (\\d{1,3}) (?:par|pe|on) (?:volume|awaaz)").matcher(s);
      if (n.find()) { int v = Integer.parseInt(n.group(1) != null ? n.group(1) : n.group(2)), max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC); int to = v <= max && max >= 50 ? v : Math.round(max * Math.min(100, v) / 100f);
        am.setStreamVolume(AudioManager.STREAM_MUSIC, to, AudioManager.FLAG_SHOW_UI); return "Volume " + v; }
      if (has(s, " unmute ", " sound on ", " awaaz chalu ", " awaaz wapas ")) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI); return "Sound on"; }
      if (has(s, " mute ", " sound off ", " awaaz band ", " chup ")) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI); return "Muted"; }
      if (Pattern.compile(" (volume|awaaz|sound) (up|badhao|bada|zyada|increase|louder|high)| (louder|increase volume|turn it up|volume badhao) ").matcher(s).find()) { for (int i = 0; i < 3; i++) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, i == 2 ? AudioManager.FLAG_SHOW_UI : 0); return "Louder"; }
      if (Pattern.compile(" (volume|awaaz|sound) (down|kam|ghatao|decrease|reduce|lower|low)| (quieter|softer|decrease volume|reduce volume|turn it down|lower the volume) ").matcher(s).find()) { for (int i = 0; i < 3; i++) am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, i == 2 ? AudioManager.FLAG_SHOW_UI : 0); return "Quieter"; }
      // music and playback
      if (Pattern.compile("^ (next|skip|agla|aage)( song| track| gaana| one| wala)?( please| karo| chalao)? $| (play|chalao) (the )?(next|agla) ").matcher(s).find()) { key(am, KeyEvent.KEYCODE_MEDIA_NEXT); return "Next"; }
      if (Pattern.compile("^ (previous|prev|pichla|back|last)( song| track| gaana| one| wala)?( please| karo| chalao)? $| (play|chalao) (the )?(previous|pichla|last) ").matcher(s).find()) { key(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS); return "Previous"; }
      if (Pattern.compile("^ (pause|stop|ruko|ruk ja|rok do|band karo)( it| the| music| song| gaana| video| movie| this)*( please)? $| (pause|stop) (the )?(music|song|video|movie|gaana) | (gaana|music|video) (band|rok|ruk)").matcher(s).find()) { key(am, KeyEvent.KEYCODE_MEDIA_PAUSE); return "Paused"; }
      if (Pattern.compile("^ (play|resume|continue|chalao|shuru|chala do)( it| the| music| song| gaana| video)*( please| karo)? $| (resume|continue) (the )?(music|song|video|movie) ").matcher(s).find()) { key(am, KeyEvent.KEYCODE_MEDIA_PLAY); return "Playing"; }
      // home
      if (Pattern.compile("^ (go )?(home|back to home|home screen|ghar)( please| jao| chalo)? $").matcher(s).find()) { home(c); return "Home"; }
      // apps
      Matcher o = Pattern.compile("^ (?:open|launch|start|go to|switch to|kholo|chalu karo) (.+?)(?: app)?(?: please)? $|^ (.+?) (?:app )?(?:kholo|khol do|open karo|chalu karo|chalao) $").matcher(s);
      if (o.find()) { String name = (o.group(1) != null ? o.group(1) : o.group(2)).trim(); ResolveInfo app = find(c, name); if (app != null) { open(c, app); return "Opening " + label(c, app); } }
      Matcher x = Pattern.compile("^ (?:close|exit|quit|band karo|band kar do) (.+?)(?: app)?(?: please)? $|^ (.+?) (?:app )?(?:band karo|band kar do|close karo) $").matcher(s);
      if (x.find()) { String name = (x.group(1) != null ? x.group(1) : x.group(2)).trim(); ResolveInfo app = find(c, name); if (app != null) { close(c, app); return "Closed " + label(c, app); } }
    } catch (Exception e) { Log.w(HomeService.T, "command: " + e); }
    return null;
  }
  /** Questions about the weather get a spoken answer instead of a search. */
  static boolean weatherQuestion(String said) { String s = " " + said.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]", " ") + " ";
    return Pattern.compile(" (weather|mausam|mosam|forecast|temperature|rain|raining|barish|baarish|umbrella|how (hot|cold|warm)|kitni (garmi|thand)|garmi|thand) ").matcher(s).find() && !Pattern.compile(" (movie|film|show|series|song|watch|dekh)").matcher(s).find(); }
  /** Turns the engine's weather into one or two spoken sentences. */
  static String weatherLine(String json) {
    try { org.json.JSONObject o = new org.json.JSONObject(json); String city = o.optString("city", "your area");
      String now = "It's " + Math.round(o.getDouble("temp")) + " degrees" + (o.optString("text").isEmpty() ? "" : " and " + o.optString("text").toLowerCase(Locale.US)) + " in " + city + ". ";
      return now + o.optString("summary", "") + ". Today's high is " + Math.round(o.getDouble("high")) + ", low " + Math.round(o.getDouble("low")) + ".";
    } catch (Exception e) { return "I couldn't get the weather right now."; }
  }
  private static boolean has(String s, String... any) { for (String a : any) if (s.contains(a)) return true; return false; }
  private static void key(AudioManager am, int code) { long t = SystemClock.uptimeMillis(); am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0)); am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0)); }
  private static final Map<String, String> ALIAS = new HashMap<>();
  static { ALIAS.put("hotstar", "jiohotstar"); ALIAS.put("jio hotstar", "jiohotstar"); ALIAS.put("prime", "prime video"); ALIAS.put("amazon", "prime video"); ALIAS.put("amazon prime", "prime video"); ALIAS.put("yt", "youtube"); ALIAS.put("you tube", "youtube"); ALIAS.put("music", "spotify"); ALIAS.put("settings", "settings"); ALIAS.put("google tv", "google tv"); ALIAS.put("sony", "sonyliv"); ALIAS.put("sony liv", "sonyliv"); ALIAS.put("zee", "zee5"); ALIAS.put("zee 5", "zee5"); }
  private static String label(Context c, ResolveInfo r) { return String.valueOf(r.loadLabel(c.getPackageManager())); }
  /** Best launchable TV app for a spoken name. */
  static ResolveInfo find(Context c, String name) {
    String want = ALIAS.getOrDefault(name, name).replace(" ", ""); PackageManager pm = c.getPackageManager(); List<ResolveInfo> apps = new ArrayList<>();
    for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) apps.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0));
    ResolveInfo best = null; int score = 0;
    for (ResolveInfo r : apps) { String l = label(c, r).toLowerCase(Locale.US).replaceAll("[^a-z0-9]", ""); int sc = l.equals(want) ? 3 : l.startsWith(want) || want.startsWith(l) ? 2 : l.contains(want) ? 1 : 0; if (sc > score) { score = sc; best = r; } }
    return best;
  }
  private static void open(Context c, ResolveInfo r) { Intent i = c.getPackageManager().getLeanbackLaunchIntentForPackage(r.activityInfo.packageName); if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(r.activityInfo.packageName); if (i != null) c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
  /** Closing another app: come home, then let the system stop it in the background. */
  private static void close(Context c, ResolveInfo r) { home(c); ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).killBackgroundProcesses(r.activityInfo.packageName); }
  private static void home(Context c) { c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT).putExtra("gate", MainActivity.gate(c)).putExtra("mode", "home")); }
}
