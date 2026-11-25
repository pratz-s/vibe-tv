package tv.home.app;

import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Voice from anywhere: a small black capsule floats at the top of whatever is on screen (a film, YouTube, anything).
 * Commands like "pause", "volume 20" or "open Netflix" happen right there; anything else becomes a search in Vibe TV.
 */
final class Overlay {
  private final Context c; private final WindowManager wm; private final Handler h = new Handler(Looper.getMainLooper()); private final float u;
  private LinearLayout cap; private TextView words; private View bars; private Listen listen; private Voice voice; private float lvl;

  Overlay(Context c) { this.c = c; wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE); u = c.getResources().getDisplayMetrics().widthPixels / 1920f; }
  private int px(float v) { return Math.round(v * u); }
  boolean showing() { return cap != null; }

  void start() {
    if (cap != null) return;
    Typeface sans = new Typeface.Builder(c.getAssets(), "geist-sans.ttf").setFontVariationSettings("'wght' 500").build();
    cap = new LinearLayout(c); cap.setGravity(Gravity.CENTER_VERTICAL); cap.setPadding(px(22), px(18), px(40), px(18)); cap.setMinimumHeight(px(84));
    GradientDrawable bg = new GradientDrawable(); bg.setColor(0xf2000000); bg.setCornerRadius(px(50)); bg.setStroke(px(2), 0x33ffffff); cap.setBackground(bg); cap.setElevation(px(20));
    bars = new View(c) { final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); final float[] hs = new float[7]; final long t0 = System.currentTimeMillis();
      { p.setColor(0xfff5f5f7); }
      @Override protected void onDraw(Canvas cv) { float w = getWidth(), hh = getHeight(), slot = w / 7, bw = slot * .46f, t = (System.currentTimeMillis() - t0) / 1000f; float[] shape = {.4f, .7f, .9f, 1f, .85f, .6f, .35f};
        for (int i = 0; i < 7; i++) { float target = Math.max(.18f, lvl * shape[i] * (.6f + .4f * (float) Math.abs(Math.sin(t * 8 + i * 1.3)))); hs[i] += (target - hs[i]) * .3f; float bh = Math.max(bw, hh * hs[i]), cx = slot * (i + .5f); cv.drawRoundRect(cx - bw / 2, (hh - bh) / 2, cx + bw / 2, (hh + bh) / 2, bw / 2, bw / 2, p); }
        if (cap != null) postInvalidateOnAnimation(); } };
    cap.addView(bars, new LinearLayout.LayoutParams(px(100), px(48)));
    words = new TextView(c); words.setTypeface(sans); words.setTextColor(0x99f5f5f7); words.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(32)); words.setMaxWidth(px(1000)); words.setMaxLines(2); words.setEllipsize(TextUtils.TruncateAt.START); words.setText("Listening…");
    LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(-2, -2); wp.leftMargin = px(22); cap.addView(words, wp);
    WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
    lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL; lp.y = px(50);
    try { wm.addView(cap, lp); } catch (Exception e) { Log.w(HomeService.T, "overlay: " + e); cap = null; return; }
    cap.setAlpha(0f); cap.setTranslationY(-px(30)); cap.animate().alpha(1f).translationY(0).setDuration(260).start();
    if (voice == null) voice = new Voice(c); if (listen == null) listen = new Listen(c);
    listen.start(new Listen.Ear() {
      public void hearing(String s) { words.setTextColor(0xfff5f5f7); words.setText(s); }
      public void level(float db) { lvl = Math.max(0f, Math.min(1f, (db + 1f) / 10f)); }
      public void heard(String text) { lvl = 0;
        if (Commands.weatherQuestion(text)) { words.setText("Checking the weather…"); Api.weather(new Api.Done<String>() { public void ok(String b) { String line = Commands.weatherLine(b); words.setTextColor(0xfff5f5f7); words.setText(line); voice.say(line, () -> h.postDelayed(Overlay.this::stop, 800)); } public void fail(String w) { words.setText("I couldn't get the weather right now."); h.postDelayed(Overlay.this::stop, 1800); } }); return; }
        String done = Commands.run(c, text); Log.i(HomeService.T, "overlay heard: " + text + (done != null ? " -> " + done : " -> search"));
        if (done != null) { words.setText(done); h.postDelayed(Overlay.this::stop, 1400); return; }
        words.setText("Searching for that…"); h.postDelayed(() -> { stop(); c.startActivity(new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT).putExtra("gate", MainActivity.gate(c)).putExtra("mode", "ask").putExtra("text", text)); }, 500); }
      public void missed(String why) { words.setText(why); h.postDelayed(Overlay.this::stop, 1800); }
    });
  }
  /** OK finishes; Back cancels. */
  boolean finish() { if (listen != null && listen.active()) { listen.done(); return true; } return false; }
  void stop() { if (listen != null) listen.stop(); View v = cap; cap = null; if (v != null) v.animate().alpha(0f).translationY(-px(30)).setDuration(200).withEndAction(() -> { try { wm.removeView(v); } catch (Exception ignored) {} }).start(); }
}
