package tv.home.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Home screen widgets, glass cards with pixel touches: a dot-matrix clock, today's screen time with a pixel bar chart,
 * and the weather with an animated dot-matrix sky (rain falls, the sun's rays pulse) and the next four hours.
 */
final class Widgets extends LinearLayout {
  private final float u; private final Typeface sans, light, bold; private final Handler h = new Handler(Looper.getMainLooper()); private final Context c;
  private TextView day, temp, summary, city, screenToday, screenSub, holiday, alert; private LinearLayout hours; private Sky sky; private Week week; private PixelClock dots;

  Widgets(Context c, float u, Typeface sans, Typeface light, Typeface bold) {
    super(c); this.c = c; this.u = u; this.sans = sans; this.light = light; this.bold = bold; setOrientation(HORIZONTAL); setClipChildren(false);
    LinearLayout left = new LinearLayout(c); left.setOrientation(VERTICAL); left.setClipChildren(false);
    left.addView(clock(), new LayoutParams(px(420), px(196)));
    LayoutParams g = new LayoutParams(px(420), px(196)); g.topMargin = px(16); left.addView(usage(), g);
    addView(left, new LayoutParams(-2, -2));
    LayoutParams wp = new LayoutParams(px(480), px(408)); wp.leftMargin = px(16); addView(weather(), wp);
    tick.run(); h.postDelayed(this::loadWeather, 1200); h.postDelayed(this::loadHolidays, 1600);
  }
  private int px(float v) { return Math.round(v * u); }
  private TextView t(Typeface f, float size, int color) { TextView v = new TextView(c); v.setTypeface(f); v.setTextColor(color); v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u); v.setIncludeFontPadding(false); v.setSingleLine(true); v.setEllipsize(TextUtils.TruncateAt.END); return v; }
  private FrameLayout card(int glow, float gx, float gy) { FrameLayout f = new FrameLayout(c); f.setBackground(new Glass(px(40), glow, gx, gy)); f.setPadding(px(32), px(26), px(32), px(26)); f.setClipToOutline(true); return f; }
  boolean hasMedia() { return false; } void focusMedia(boolean on) {} void move(int d) {} void press() {}

  // ---- clock: dot-matrix time, day and date in sans ----
  private View clock() {
    FrameLayout f = card(0x26ffffff, .1f, .1f); LinearLayout col = new LinearLayout(c); col.setOrientation(VERTICAL); col.setGravity(Gravity.CENTER_VERTICAL);
    dots = new PixelClock(c, px(10)); col.addView(dots, new LayoutParams(-2, -2));
    day = t(sans, 26, 0x99f5f5f7); LayoutParams dp = new LayoutParams(-2, -2); dp.topMargin = px(16); col.addView(day, dp);
    holiday = t(sans, 24, Tokens.COLOR_ACCENT); holiday.setVisibility(GONE); LayoutParams hp = new LayoutParams(-2, -2); hp.topMargin = px(8); col.addView(holiday, hp);
    f.addView(col, new FrameLayout.LayoutParams(-1, -1)); return f;
  }
  private final Runnable tick = new Runnable() { public void run() {
    Calendar n = Calendar.getInstance(); day.setText(DateFormat.format("EEEE, d MMMM", n)); dots.invalidate(); showUsage();
    h.postDelayed(this, 60000 - System.currentTimeMillis() % 60000 + 50); } };

  /** Today's festival or holiday (or tomorrow's), from Google's public India holiday calendar via the engine. */
  private void loadHolidays() {
    Api.get("/holidays", new Api.Done<String>() {
      public void ok(String b) { try { JSONObject o = new JSONObject(b); JSONArray td = o.optJSONArray("today"), tm = o.optJSONArray("tomorrow");
        if (td != null && td.length() > 0) { holiday.setText("Today  ·  " + join(td)); holiday.setVisibility(VISIBLE); }
        else if (tm != null && tm.length() > 0) { holiday.setText("Tomorrow  ·  " + join(tm)); holiday.setVisibility(VISIBLE); }
        else holiday.setVisibility(GONE); } catch (Exception e) { holiday.setVisibility(GONE); } }
      public void fail(String w) {} });
    h.postDelayed(this::loadHolidays, 3 * 3600_000L);
  }
  private static String join(JSONArray a) { List<String> l = new ArrayList<>(); for (int i = 0; i < a.length(); i++) l.add(a.optString(i)); return TextUtils.join(", ", l); }

  // ---- screen time: today, and the week as pixel columns ----
  private View usage() {
    FrameLayout f = card(0x33ff9a3c, .95f, 1f); LinearLayout row = new LinearLayout(c); row.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout col = new LinearLayout(c); col.setOrientation(VERTICAL); TextView k = t(sans, 22, 0x8cf5f5f7); k.setText("SCREEN TIME"); k.setLetterSpacing(.12f); col.addView(k);
    screenToday = t(light, 56, 0xfff5f5f7); LayoutParams sp = new LayoutParams(-2, -2); sp.topMargin = px(8); col.addView(screenToday, sp);
    screenSub = t(sans, 21, 0x8cf5f5f7); LayoutParams ss = new LayoutParams(-2, -2); ss.topMargin = px(6); col.addView(screenSub, ss);
    row.addView(col, new LayoutParams(0, -2, 1f)); week = new Week(c); row.addView(week, new LayoutParams(px(130), px(110)));
    f.addView(row, new FrameLayout.LayoutParams(-1, -1)); return f;
  }
  static String dayKey(Calendar d) { return "st_" + DateFormat.format("yyyyMMdd", d); }
  private void showUsage() {
    SharedPreferences p = c.getSharedPreferences(HomeService.PREFS, 0); Calendar d = Calendar.getInstance(); long[] mins = new long[7]; long total = 0;
    for (int i = 6; i >= 0; i--) { mins[i] = p.getLong(dayKey(d), 0) / 60; if (i < 6) total += mins[i]; d.add(Calendar.DAY_OF_YEAR, -1); }
    long today = mins[6]; screenToday.setText(today >= 60 ? today / 60 + "h " + today % 60 + "m" : today + "m");
    long avg = total / 6; screenSub.setText(avg == 0 ? "Counting from today" : (today > avg ? "Above" : "Below") + " your daily average of " + (avg >= 60 ? avg / 60 + "h " + avg % 60 + "m" : avg + "m"));
    week.set(mins);
  }

  // ---- weather ----
  private View weather() {
    FrameLayout f = card(0x332f8fd6, .9f, .05f); LinearLayout col = new LinearLayout(c); col.setOrientation(VERTICAL);
    LinearLayout top = new LinearLayout(c); temp = t(light, 96, 0xfff5f5f7); temp.setText("--°"); temp.setEllipsize(null); temp.setSingleLine(false); temp.setMaxLines(1); top.addView(temp, new LayoutParams(-2, -2));
    LinearLayout r = new LinearLayout(c); r.setOrientation(VERTICAL); city = t(sans, 22, 0x8cf5f5f7); city.setText("WEATHER"); city.setLetterSpacing(.12f); r.addView(city);
    summary = t(sans, 28, 0xe6f5f5f7); summary.setSingleLine(false); summary.setMaxLines(3); summary.setEllipsize(null); summary.setLineSpacing(px(4), 1f); LayoutParams sp = new LayoutParams(-1, -2); sp.topMargin = px(8); r.addView(summary, sp);
    LayoutParams rl = new LayoutParams(0, -2, 1f); rl.leftMargin = px(22); rl.topMargin = px(10); top.addView(r, rl); col.addView(top);
    alert = t(sans, 22, 0xffff6b5e); alert.setVisibility(GONE); alert.setCompoundDrawablePadding(px(10)); alert.setPadding(px(14), px(8), px(18), px(8)); alert.setSingleLine(false); alert.setMaxLines(2);
    android.graphics.drawable.GradientDrawable ab = new android.graphics.drawable.GradientDrawable(); ab.setColor(0x33ff453a); ab.setStroke(px(2), 0x80ff453a); ab.setCornerRadius(px(18)); alert.setBackground(ab); alert.setCompoundDrawablesRelative(new Warn(px(26)), null, null, null); alert.setGravity(Gravity.CENTER_VERTICAL);
    LayoutParams al = new LayoutParams(-1, -2); al.topMargin = px(12); col.addView(alert, al);
    sky = new Sky(c); col.addView(sky, new LayoutParams(-1, 0, 1f));
    hours = new LinearLayout(c); col.addView(hours, new LayoutParams(-1, px(76)));
    f.addView(col, new FrameLayout.LayoutParams(-1, -1)); return f;
  }
  private void loadWeather() {
    Api.weather(new Api.Done<String>() {
      public void ok(String s) { try { JSONObject o = new JSONObject(s);
        temp.setText(Math.round(o.getDouble("temp")) + "°"); city.setText(o.optString("city", "Weather").toUpperCase(Locale.US)); summary.setText(o.optString("summary", o.optString("text")));
        sky.set(o.optInt("code"), o.optBoolean("day", true)); JSONObject wd = o.optJSONObject("wind"); if (wd != null) sky.wind(wd.optDouble("speed", 0), wd.optDouble("from", 0));
        JSONArray al = o.optJSONArray("alerts"); if (al != null && al.length() > 0) { alert.setText(al.getJSONObject(0).optString("text")); alert.setVisibility(VISIBLE); } else alert.setVisibility(GONE); hours.removeAllViews(); JSONArray hs = o.optJSONArray("hours");
        for (int i = 0; hs != null && i < hs.length(); i++) { JSONObject x = hs.getJSONObject(i);
          if (i > 0) { View rule = new View(c); rule.setBackgroundColor(0x2effffff); hours.addView(rule, new LayoutParams(px(2), -1)); }
          LinearLayout one = new LinearLayout(c); one.setOrientation(VERTICAL); one.setGravity(Gravity.CENTER);
          TextView tm = t(sans, 22, 0x80f5f5f7); tm.setText(x.optString("time")); tm.setGravity(Gravity.CENTER); one.addView(tm, new LayoutParams(-1, -2));
          TextView tt = t(light, 34, 0xfff5f5f7); tt.setText(Math.round(x.getDouble("temp")) + "°"); tt.setGravity(Gravity.CENTER); LayoutParams tp = new LayoutParams(-1, -2); tp.topMargin = px(6); one.addView(tt, tp);
          hours.addView(one, new LayoutParams(0, -1, 1f)); }
      } catch (Exception e) { Log.w(HomeService.T, "weather: " + e); } }
      public void fail(String why) { Log.w(HomeService.T, "weather: " + why); }
    });
    h.postDelayed(this::loadWeather, 30 * 60000);
  }

  /** The animated dot-matrix sky: a pixel cloud or sun, with rain, snow, storm or fog moving through it. */
  private final class Sky extends View {
    private int code = 3; private boolean day = true; private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); private final long t0 = System.currentTimeMillis();
    Sky(Context c) { super(c); }
    private float windSpeed, windTo; void wind(double speed, double from) { windSpeed = (float) speed; windTo = (float) ((from + 180) % 360); }
    void set(int code, boolean day) { this.code = code; this.day = day; invalidate(); }
    @Override protected void onDraw(Canvas cv) {
      float w = getWidth(), hh = getHeight(), pitch = px(9), r = pitch * .3f, t = (System.currentTimeMillis() - t0) / 1000f;
      boolean clear = code <= 1, part = code == 2, rain = (code >= 51 && code <= 67) || (code >= 80 && code <= 82), snow = (code >= 71 && code <= 77) || code == 85 || code == 86, storm = code >= 95, fog = code == 45 || code == 48;
      int cols = (int) (w / pitch), rows = (int) (hh / pitch); float ox = (w - cols * pitch) / 2 + pitch / 2, oy = (hh - rows * pitch) / 2 + pitch / 2;
      float drift = (float) Math.sin(t * .4f) * pitch * 1.2f;
      float scx = clear ? w / 2 : w * .64f, scy = clear ? hh * .5f : hh * .3f, sR = (clear ? .26f : .17f) * Math.min(w, hh) * 1.3f;
      float ccx = w / 2 + drift, ccy = hh * .48f;
      for (int i = 0; i < cols; i++) for (int j = 0; j < rows; j++) {
        float x = ox + i * pitch, y = oy + j * pitch; int a = 0, col = 0xffffff;
        if (clear || part) { float d = (float) Math.hypot(x - scx, y - scy);
          if (d < sR * .55f) { a = day ? 235 : 210; col = day ? 0xffc24d : 0xe8e6ff; if (!day && Math.hypot(x - scx - sR * .25f, y - scy - sR * .12f) < sR * .45f) a = 0; }
          else if (day && d < sR && ((int) (Math.atan2(y - scy, x - scx) / (Math.PI / 6) + 12) % 2 == 0)) { a = (int) (110 + 90 * Math.sin(t * 3 - d / pitch * .6f)); col = 0xffc24d; } }
        if (!clear && !fog) { float s = Math.min(w, hh) * .42f;
          boolean in = Math.hypot(x - (ccx - s * .5f), y - (ccy + s * .08f)) < s * .34f || Math.hypot(x - (ccx + s * .05f), y - (ccy - s * .18f)) < s * .46f || Math.hypot(x - (ccx + s * .55f), y - (ccy + s * .1f)) < s * .3f || (y > ccy && y < ccy + s * .38f && x > ccx - s * .8f && x < ccx + s * .82f);
          if (in) { a = (int) (150 + 80 * Math.sin((x + y) / pitch * .35f + t * .8f)); col = storm ? 0xb7bcc8 : 0xf5f5f7; } }
        if (fog && j % 3 == 0) { float ph = (x / pitch + t * 2 + j) % 7; a = ph < 4 ? 150 : 40; col = 0xf5f5f7; }
        if (a > 0) { p.setColor((a << 24) | col); cv.drawCircle(x, y, r, p); }
        else { p.setColor(0x0fffffff); cv.drawCircle(x, y, r * .7f, p); }
      }
      if (rain || storm || snow) { float s = Math.min(w, hh) * .42f, top = ccy + s * .45f; p.setColor(snow ? 0xe6f5f5f7 : 0xe68ec5ff);
        for (int k = 0; k < 14; k++) { float x = ccx - s * .7f + (k * 0.618f % 1f) * s * 1.5f; float speed = snow ? 18 : 60, y = top + ((t * speed * u + k * 37) % (hh - top));
          x = ox + Math.round((x - ox) / pitch) * pitch; y = oy + Math.round((y - oy) / pitch) * pitch; cv.drawCircle(x, y, r * 1.15f, p); if (!snow) cv.drawCircle(x, y - pitch, r * .8f, p); } }
      if (storm && (int) (t * 1.7f) % 4 == 0) { p.setColor(0xffffc24d); float bx = ccx, by = ccy + Math.min(w, hh) * .2f; for (int k = 0; k < 6; k++) cv.drawCircle(ox + Math.round((bx - (k % 2) * pitch - ox) / pitch) * pitch, oy + Math.round((by + k * pitch - oy) / pitch) * pitch, r * 1.2f, p); }
      // Wind: faint dots drifting the way the wind blows (east is right, north is up), faster when it's stronger.
      if (windSpeed > 2) { double a = Math.toRadians(windTo); float dx = (float) Math.sin(a), dy = (float) -Math.cos(a), v = Math.min(1f, windSpeed / 30f) * px(70) + px(18);
        for (int k = 0; k < 22; k++) { float sx = (k * 0.618f % 1f) * w, sy = (k * 0.382f % 1f) * hh, travel = (t * v + k * 53) % (w + hh);
          for (int tail = 0; tail < 3; tail++) { float x = (sx + dx * (travel - tail * pitch * 1.2f)) % w, y = (sy + dy * (travel - tail * pitch * 1.2f)) % hh; if (x < 0) x += w; if (y < 0) y += hh;
            x = ox + Math.round((x - ox) / pitch) * pitch; y = oy + Math.round((y - oy) / pitch) * pitch; p.setColor((0x38 - tail * 0x10) << 24 | 0xffffff); cv.drawCircle(x, y, r * .9f, p); } } }
      if (getVisibility() == VISIBLE && isShown()) postInvalidateDelayed(66);
    }
  }

  /** A small red warning triangle with an exclamation mark. */
  private static final class Warn extends Drawable { private final int s; private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); Warn(int s) { this.s = s; setBounds(0, 0, s, s); }
    @Override public void draw(Canvas c) { Path tri = new Path(); tri.moveTo(s / 2f, s * .06f); tri.lineTo(s * .97f, s * .92f); tri.lineTo(s * .03f, s * .92f); tri.close(); p.setColor(0xffff453a); c.drawPath(tri, p); p.setColor(0xff000000); c.drawRoundRect(s * .45f, s * .34f, s * .55f, s * .64f, s * .05f, s * .05f, p); c.drawCircle(s / 2f, s * .76f, s * .055f, p); }
    @Override public int getIntrinsicWidth() { return s; } @Override public int getIntrinsicHeight() { return s; }
    @Override public void setAlpha(int a) {} @Override public void setColorFilter(ColorFilter f) {} @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; } }

  /** Seven columns of dots, one per day, today in orange. */
  private final class Week extends View {
    private long[] m = new long[7]; private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    Week(Context c) { super(c); }
    void set(long[] m) { this.m = m; invalidate(); }
    @Override protected void onDraw(Canvas cv) { float w = getWidth(), hh = getHeight(), pitch = px(10), r = pitch * .32f; int rows = (int) (hh / pitch); long max = 60; for (long v : m) max = Math.max(max, v);
      for (int i = 0; i < 7; i++) { float x = w - (7 - i) * (w / 7f) + w / 14f; int lit = (int) Math.ceil(rows * m[i] / (double) max);
        for (int j = 0; j < rows; j++) { boolean on = j >= rows - lit; p.setColor(on ? (i == 6 ? Tokens.COLOR_ACCENT : 0xccf5f5f7) : 0x14ffffff); cv.drawCircle(x, hh - pitch / 2 - j * pitch, r, p); } } }
  }

  /** Frosted card: translucent fill, a colour glowing from one corner, a thin bright edge and a top sheen. */
  static final class Glass extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float r, gx, gy; private int color; private final RectF b = new RectF();
    Glass(float r, int color, float gx, float gy) { this.r = r; this.color = color; this.gx = gx; this.gy = gy; fill.setColor(0xb30a0a0c); edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(2); }
    void glow(int c) { color = c; onBoundsChange(getBounds()); invalidateSelf(); }
    @Override protected void onBoundsChange(Rect bb) { if (bb.isEmpty()) return; float w = bb.width(), hh = bb.height();
      glow.setShader(new RadialGradient(bb.left + w * gx, bb.top + hh * gy, Math.max(w, hh) * .85f, new int[]{color, color & 0x22ffffff, 0}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
      edge.setShader(new LinearGradient(bb.left, bb.top, bb.left, bb.bottom, new int[]{0x40ffffff, 0x14ffffff}, null, Shader.TileMode.CLAMP));
      sheen.setShader(new LinearGradient(bb.left, bb.top, bb.left, bb.top + hh * .45f, new int[]{0x10ffffff, 0x00ffffff}, null, Shader.TileMode.CLAMP)); }
    @Override public void draw(Canvas cv) { b.set(getBounds()); b.inset(1, 1); cv.drawRoundRect(b, r, r, fill); cv.drawRoundRect(b, r, r, glow); cv.drawRoundRect(b, r, r, sheen); cv.drawRoundRect(b, r, r, edge); }
    @Override public void setAlpha(int a) {} @Override public void setColorFilter(ColorFilter f) {} @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public void getOutline(android.graphics.Outline o) { o.setRoundRect(getBounds(), r); }
  }
}
