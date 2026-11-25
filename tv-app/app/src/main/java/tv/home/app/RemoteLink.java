package tv.home.app;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import org.json.JSONArray;
import org.json.JSONObject;

/** Waits for presses from the iPhone remote page and acts on them: spoken or typed requests, and remote buttons. */
final class RemoteLink {
  interface Actions { void home(); void back(); void ok(); void move(int keyCode); void off(); }
  private final Context c; private final Actions act; private final Handler h = new Handler(Looper.getMainLooper()); private volatile boolean on; private volatile long after = -1, beat; private Thread worker;
  RemoteLink(Context c, Actions act) { this.c = c; this.act = act; }
  void start() { if (on) return; on = true; spawn(); h.postDelayed(watch, 20000); Log.i(HomeService.T, "remote link on"); }
  private void spawn() { worker = new Thread(this::loop, "remote"); worker.setDaemon(true); worker.start(); }
  /** If the waiting thread ever dies or hangs, start a fresh one. */
  private final Runnable watch = new Runnable() { public void run() { if (!on) return; if (worker == null || !worker.isAlive() || SystemClock.elapsedRealtime() - beat > 60000) { Log.w(HomeService.T, "remote link restarted"); if (worker != null) worker.interrupt(); spawn(); } h.postDelayed(this, 20000); } };
  private void loop() {
    while (on && !Thread.currentThread().isInterrupted()) {
      beat = SystemClock.elapsedRealtime();
      try {
        // First, just learn where the queue is (instant), so no press made while starting up is thrown away.
        if (after < 0) { after = new JSONObject(Api.getSync("/tv/next?peek=1")).optLong("last", 0); continue; }
        String b = Api.getSync("/tv/next?after=" + after); JSONObject o = new JSONObject(b); JSONArray items = o.optJSONArray("items");
        for (int i = 0; items != null && i < items.length(); i++) { JSONObject it = items.getJSONObject(i); after = Math.max(after, it.getLong("id")); String kind = it.optString("kind"), v = it.optString("value"); h.post(() -> handle(kind, v)); }
      } catch (Throwable e) { Log.w(HomeService.T, "remote wait: " + e); SystemClock.sleep(3000); }
    }
  }
  private void handle(String kind, String v) {
    Log.i(HomeService.T, "remote " + kind + ": " + v);
    if ("viewing".equals(kind)) { // who's watching, switched from the phone: the engine already knows; the TV just shows it
      Api.viewing = v; Api.viewingLabel = "partner".equals(v) ? "With partner" : "friends".equals(v) ? "With friends" : "guest".equals(v) ? "Guest" : "Just me"; return; }
    if ("text".equals(kind)) {
      String done = Commands.run(c, v);
      if (done == null) c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT).putExtra("gate", MainActivity.gate(c)).putExtra("mode", "ask").putExtra("text", v));
      return;
    }
    if ("voices".equals(kind) || "voice".equals(kind)) { // hear the voice samples on the TV, then pick one by number
      Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT).putExtra("gate", MainActivity.gate(c)).putExtra("mode", kind);
      if ("voice".equals(kind)) try { i.putExtra("n", Integer.parseInt(v.trim())); } catch (Exception e) { return; }
      c.startActivity(i); return;
    }
    if ("app".equals(kind)) { // only the dock's own apps can be opened from the phone
      if (!MainActivity.appAllowed(v)) { Log.w(HomeService.T, "remote: app not allowed " + v); return; }
      Intent i = c.getPackageManager().getLeanbackLaunchIntentForPackage(v); if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(v);
      if (i != null) c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return;
    }
    AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    switch (v) {
      case "playpause": media(am, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); break;
      case "next": media(am, KeyEvent.KEYCODE_MEDIA_NEXT); break;
      case "prev": media(am, KeyEvent.KEYCODE_MEDIA_PREVIOUS); break;
      case "volup": am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); break;
      case "voldown": am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); break;
      case "mute": am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI); break;
      case "home": act.home(); break;
      case "back": act.back(); break;
      case "ok": act.ok(); break;
      case "power": act.off(); break;
      case "up": act.move(KeyEvent.KEYCODE_DPAD_UP); break;
      case "down": act.move(KeyEvent.KEYCODE_DPAD_DOWN); break;
      case "left": act.move(KeyEvent.KEYCODE_DPAD_LEFT); break;
      case "right": act.move(KeyEvent.KEYCODE_DPAD_RIGHT); break;
    }
  }
  private static void media(AudioManager am, int code) { long t = SystemClock.uptimeMillis(); am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0)); am.dispatchMediaKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0)); }
}
