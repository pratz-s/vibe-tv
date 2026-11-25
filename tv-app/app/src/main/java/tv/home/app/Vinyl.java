package tv.home.app;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The turntable: the playing song's art becomes a record label that spins while music plays, the tone arm swings on and off,
 * and below sits a crate of your Spotify albums and playlists. Pick one and it goes on the platter.
 */
final class Vinyl extends FrameLayout {
  private final float u; private final Context c; private final Handler h = new Handler(Looper.getMainLooper()); private final Typeface sans, bold, light;
  private final Deck deck; private final ImageView wash; private final TextView title, artist, pos, dur, status; private final Bar bar; private final LinearLayout keys, crate; private final HorizontalScrollView crateScroll; private final FrameLayout connect;
  private MediaController controller; private boolean playing; private int zone, key = 1, pick; private final List<JSONObject> records = new ArrayList<>(); private Runnable onClose;

  Vinyl(Context c, float u, Typeface sans, Typeface bold, Typeface light) {
    super(c); this.c = c; this.u = u; this.sans = sans; this.bold = bold; this.light = light; setVisibility(GONE); setBackgroundColor(0xff0b0b0d); setClickable(true);
    wash = new ImageView(c); wash.setScaleType(ImageView.ScaleType.CENTER_CROP); wash.setAlpha(.32f); if (android.os.Build.VERSION.SDK_INT >= 31) wash.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(px(120), px(120), Shader.TileMode.CLAMP)); addView(wash, new LayoutParams(-1, -1));
    View shade = new View(c); shade.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0x66000000, 0xcc0b0b0d})); addView(shade, new LayoutParams(-1, -1));
    deck = new Deck(c); LayoutParams dp = new LayoutParams(px(600), px(600), Gravity.TOP | Gravity.START); dp.leftMargin = px(Tokens.SAFE_LEFT + 40); dp.topMargin = px(Tokens.SAFE_TOP + 20); addView(deck, dp);

    LinearLayout right = new LinearLayout(c); right.setOrientation(LinearLayout.VERTICAL); LayoutParams rp = new LayoutParams(px(900), -2, Gravity.TOP | Gravity.END); rp.rightMargin = px(Tokens.SAFE_RIGHT); rp.topMargin = px(Tokens.SAFE_TOP + 90); addView(right, rp);
    TextView kicker = t(sans, 24, 0x8cf5f5f7); kicker.setText("ON THE TURNTABLE"); kicker.setLetterSpacing(.14f); right.addView(kicker);
    title = t(bold, 58, 0xfff5f5f7); title.setText("Nothing playing"); right.addView(title, lp(-1, -2, 16, 0));
    artist = t(sans, 32, 0x99f5f5f7); artist.setText("Pick a record below"); right.addView(artist, lp(-1, -2, 10, 0));
    bar = new Bar(c); right.addView(bar, lp(-1, px(8), 40, 0));
    LinearLayout times = new LinearLayout(c); pos = t(sans, 24, 0x8cf5f5f7); dur = t(sans, 24, 0x8cf5f5f7); dur.setGravity(Gravity.END); times.addView(pos, new LinearLayout.LayoutParams(0, -2, 1f)); times.addView(dur, new LinearLayout.LayoutParams(0, -2, 1f)); right.addView(times, lp(-1, -2, 12, 0));
    keys = new LinearLayout(c); keys.setGravity(Gravity.CENTER_VERTICAL); for (int k : new int[]{1, 2, 4}) keys.addView(new Key(c, k), new LinearLayout.LayoutParams(px(k == 2 ? 104 : 84), px(k == 2 ? 104 : 84))); right.addView(keys, lp(-2, -2, 30, 0));
    for (int i = 1; i < 3; i++) ((LinearLayout.LayoutParams) keys.getChildAt(i).getLayoutParams()).leftMargin = px(28);
    status = t(sans, 24, 0x99f5f5f7); status.setText(" "); right.addView(status, lp(-1, -2, 18, 0));

    TextView shelf = t(bold, 30, 0xfff5f5f7); shelf.setText("Your records"); LayoutParams sp = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START); sp.leftMargin = px(Tokens.SAFE_LEFT); sp.bottomMargin = px(335); addView(shelf, sp);
    crateScroll = new HorizontalScrollView(c); crateScroll.setHorizontalScrollBarEnabled(false); crateScroll.setClipToPadding(false); crateScroll.setPadding(px(Tokens.SAFE_LEFT), px(20), px(Tokens.SAFE_RIGHT), px(20));
    crate = new LinearLayout(c); crate.setClipChildren(false); crateScroll.addView(crate); crateScroll.setFocusable(false);
    LayoutParams cp = new LayoutParams(-1, px(270), Gravity.BOTTOM); cp.bottomMargin = px(Tokens.SAFE_BOTTOM - 20); addView(crateScroll, cp);
    connect = new FrameLayout(c); connect.setVisibility(GONE); LayoutParams kp = new LayoutParams(px(1100), px(250), Gravity.BOTTOM | Gravity.START); kp.leftMargin = px(Tokens.SAFE_LEFT); kp.bottomMargin = px(Tokens.SAFE_BOTTOM); addView(connect, kp);
  }
  private int px(float v) { return Math.round(v * u); }
  private TextView t(Typeface f, float size, int color) { TextView v = new TextView(c); v.setTypeface(f); v.setTextColor(color); v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u); v.setIncludeFontPadding(false); v.setSingleLine(true); v.setEllipsize(TextUtils.TruncateAt.END); return v; }
  private LinearLayout.LayoutParams lp(int w, int hh, int top, int left) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, hh); p.topMargin = px(top); p.leftMargin = px(left); return p; }

  boolean showing() { return getVisibility() == VISIBLE; }
  void open(Runnable onClose) { this.onClose = onClose; setVisibility(VISIBLE); setAlpha(0f); animate().alpha(1f).setDuration(300).start(); zone = 0; key = 1; paint(); watchMedia(); loadCrate(); deck.go(); }
  void close() { animate().alpha(0f).setDuration(220).withEndAction(() -> setVisibility(GONE)).start(); deck.stop(); h.removeCallbacksAndMessages(null); if (onClose != null) onClose.run(); }

  /** Up and down move between the controls and the crate; left and right move along; OK presses or puts a record on. */
  boolean key(KeyEvent e) {
    if (e.getAction() != KeyEvent.ACTION_DOWN) return true; int k = e.getKeyCode();
    if (k == KeyEvent.KEYCODE_BACK) { close(); return true; }
    if (k == KeyEvent.KEYCODE_DPAD_DOWN) { zone = 1; paint(); return true; }
    if (k == KeyEvent.KEYCODE_DPAD_UP) { zone = 0; paint(); return true; }
    if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) { int d = k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
      if (zone == 0) key = Math.max(0, Math.min(2, key + d)); else { int n = zone == 1 && records.isEmpty() ? 1 : records.size(); pick = Math.max(0, Math.min(n - 1, pick + d)); } paint(); return true; }
    if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) { if (zone == 0) control(); else if (records.isEmpty()) link(); else putOn(records.get(pick)); return true; }
    if (k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { key = 1; control(); return true; }
    return true;
  }
  private void paint() {
    for (int i = 0; i < keys.getChildCount(); i++) { Key kv = (Key) keys.getChildAt(i); kv.on = zone == 0 && i == key; if (i == 1) kv.kind = playing ? 3 : 2; kv.invalidate(); }
    for (int i = 0; i < crate.getChildCount(); i++) { View s = crate.getChildAt(i); boolean on = zone == 1 && i == pick; s.animate().scaleX(on ? 1.1f : 1f).scaleY(on ? 1.1f : 1f).translationY(on ? -px(14) : 0).setDuration(180).start(); s.setForeground(on ? new Ring(px(16), px(3)) : null); if (on) crateScroll.smoothScrollTo(Math.max(0, s.getLeft() - px(700)), 0); }
    connect.setForeground(zone == 1 && records.isEmpty() ? new Ring(px(32), px(3)) : null);
  }

  // ---- the music that's playing ----
  private void watchMedia() {
    try { MediaSessionManager m = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE); ComponentName me = new ComponentName(c, Media.class); m.addOnActiveSessionsChangedListener(this::pickSession, me, h); pickSession(m.getActiveSessions(me)); }
    catch (Exception e) { Log.w(HomeService.T, "vinyl media: " + e); }
  }
  private final MediaController.Callback cb = new MediaController.Callback() { @Override public void onMetadataChanged(MediaMetadata md) { show(); } @Override public void onPlaybackStateChanged(PlaybackState s) { show(); } };
  private void pickSession(List<MediaController> list) {
    if (controller != null) controller.unregisterCallback(cb); controller = null;
    if (list != null) { for (MediaController mc : list) if (mc.getPackageName().contains("spotify")) { controller = mc; break; } if (controller == null && !list.isEmpty()) controller = list.get(0); }
    if (controller != null) controller.registerCallback(cb, h); show();
  }
  private String lastArt = "";
  private void show() {
    if (!showing()) return; MediaMetadata md = controller == null ? null : controller.getMetadata(); PlaybackState s = controller == null ? null : controller.getPlaybackState();
    playing = s != null && s.getState() == PlaybackState.STATE_PLAYING; deck.spin(playing);
    if (md == null || md.getString(MediaMetadata.METADATA_KEY_TITLE) == null) { title.setText("Nothing playing"); artist.setText("Pick a record below"); bar.f = 0; bar.invalidate(); pos.setText(""); dur.setText(""); paint(); return; }
    title.setText(md.getString(MediaMetadata.METADATA_KEY_TITLE)); String a = md.getString(MediaMetadata.METADATA_KEY_ARTIST); artist.setText(a == null ? "" : a);
    Bitmap art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART); if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
    String key = title.getText() + "|" + artist.getText(); if (art != null && !key.equals(lastArt)) { lastArt = key; deck.label(art); wash.setImageBitmap(art); }
    long d = md.getLong(MediaMetadata.METADATA_KEY_DURATION), p = s == null ? 0 : s.getPosition(); if (s != null && playing) p += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
    bar.f = d > 0 ? Math.min(1f, p / (float) d) : 0; bar.invalidate(); pos.setText(mmss(p)); dur.setText(d > 0 ? "-" + mmss(Math.max(0, d - p)) : "");
    paint(); h.removeCallbacks(tick); if (playing) h.postDelayed(tick, 1000);
  }
  private final Runnable tick = this::show;
  private static String mmss(long ms) { long s = ms / 1000; return String.format(Locale.US, "%d:%02d", s / 60, s % 60); }
  private void control() { if (controller == null) { status.setText("Nothing to control yet. Pick a record below."); return; } MediaController.TransportControls tc = controller.getTransportControls();
    if (key == 0) tc.skipToPrevious(); else if (key == 2) tc.skipToNext(); else if (playing) tc.pause(); else tc.play(); h.postDelayed(this::show, 300); }

  // ---- the crate ----
  private void loadCrate() {
    status.setText(" ");
    Api.get("/spotify/crate", new Api.Done<String>() {
      public void ok(String b) { try { JSONObject o = new JSONObject(b); records.clear(); crate.removeAllViews();
          if (!o.optBoolean("connected")) { showConnect(); return; } connect.setVisibility(GONE); crateScroll.setVisibility(VISIBLE);
          JSONArray a = o.getJSONArray("records"); for (int i = 0; i < a.length(); i++) { records.add(a.getJSONObject(i)); crate.addView(sleeve(a.getJSONObject(i))); }
          pick = 0; paint(); } catch (Exception e) { status.setText("Couldn't load your records."); } }
      public void fail(String why) { status.setText("Couldn't reach Spotify. " + why); }
    });
  }
  private View sleeve(JSONObject r) {
    LinearLayout col = new LinearLayout(c); col.setOrientation(LinearLayout.VERTICAL); col.setClipChildren(false);
    ImageView cover = new ImageView(c); cover.setScaleType(ImageView.ScaleType.CENTER_CROP); cover.setBackgroundColor(0xff1c1c1f); cover.setClipToOutline(true); cover.setOutlineProvider(new android.view.ViewOutlineProvider() { public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(14)); } });
    if (r.optString("image", null) != null) Glide.with(c).load(r.optString("image")).override(px(190)).into(cover);
    col.addView(cover, new LinearLayout.LayoutParams(px(190), px(190)));
    TextView n = t(sans, 22, 0xe6f5f5f7); n.setText(r.optString("name")); col.addView(n, lp(px(190), -2, 10, 0));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2); p.rightMargin = px(28); col.setLayoutParams(p); col.setPivotY(px(190)); return col;
  }
  /** Puts a record on: the arm lifts, the new label drops onto the platter, the engine starts it on the TV's Spotify, the arm comes back down. */
  /** Puts a record on: the arm lifts, the new label drops onto the platter, and Spotify on the TV plays it. This works on Spotify Free:
   *  first through Spotify's own media controls (stays on the turntable), otherwise by opening the album in the Spotify app. */
  private void putOn(JSONObject r) {
    deck.lift(); String uri = r.optString("uri"); status.setText("Putting on " + r.optString("name") + "…");
    Glide.with(c).asBitmap().load(r.optString("image")).override(600).into(new CustomTarget<Bitmap>() { public void onResourceReady(Bitmap b, Transition<? super Bitmap> t) { deck.label(b); wash.setImageBitmap(b); } public void onLoadCleared(Drawable d) {} });
    boolean sent = false;
    if (controller != null && controller.getPackageName().contains("spotify")) { try { controller.getTransportControls().playFromUri(android.net.Uri.parse(uri), null); sent = true; } catch (Exception e) { Log.w(HomeService.T, "playFromUri: " + e); } }
    final boolean viaControls = sent;
    h.postDelayed(() -> { MediaMetadata md = controller == null ? null : controller.getMetadata(); PlaybackState s = controller == null ? null : controller.getPlaybackState();
      boolean ok = viaControls && s != null && s.getState() == PlaybackState.STATE_PLAYING;
      if (ok) { status.setText("Playing " + r.optString("name")); show(); return; }
      try { android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri)).setPackage("com.spotify.tv.android").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); status.setText("Opened in Spotify. Press play there, then Back to return."); }
      catch (Exception e) { status.setText("Open Spotify on the TV once, then try again."); } }, viaControls ? 2500 : 0);
  }
  private void showConnect() {
    crateScroll.setVisibility(GONE); connect.setVisibility(VISIBLE); connect.removeAllViews(); connect.setBackground(new Widgets.Glass(px(32), 0x5c1db954, .05f, .2f)); connect.setPadding(px(34), px(26), px(34), px(26));
    LinearLayout row = new LinearLayout(c); row.setGravity(Gravity.CENTER_VERTICAL); ImageView qr = new ImageView(c); qr.setTag("qr"); row.addView(qr, new LinearLayout.LayoutParams(px(190), px(190)));
    LinearLayout col = new LinearLayout(c); col.setOrientation(LinearLayout.VERTICAL); TextView a = t(bold, 36, 0xfff5f5f7); a.setText("Bring your records"); col.addView(a);
    TextView b = t(sans, 26, 0xb3f5f5f7); b.setSingleLine(false); b.setMaxLines(3); b.setText("Press OK, then scan the code with your phone and sign in to Spotify. Your albums and playlists will appear here."); col.addView(b, lp(-1, -2, 12, 0));
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f); cp.leftMargin = px(34); row.addView(col, cp); connect.addView(row, new LayoutParams(-1, -1)); paint();
  }
  private void link() {
    Api.post("/spotify/link", "{}", new Api.Done<String>() {
      public void ok(String b) { try { JSONObject o = new JSONObject(b); if (o.has("error")) { status.setText(o.getString("error")); return; } ImageView qr = connect.findViewWithTag("qr"); qr.setImageBitmap(qr(o.getString("url"), px(190))); status.setText("Scan with your phone. Waiting for Spotify…"); waitForLink(0); } catch (Exception e) { status.setText("Couldn't make a sign-in code."); } }
      public void fail(String why) { status.setText("Couldn't reach the engine."); }
    });
  }
  private void waitForLink(int n) { if (n > 100 || !showing()) return; h.postDelayed(() -> Api.get("/spotify/status", new Api.Done<String>() {
    public void ok(String b) { try { if (new JSONObject(b).optBoolean("connected")) { status.setText("Spotify connected."); loadCrate(); } else waitForLink(n + 1); } catch (Exception e) { waitForLink(n + 1); } }
    public void fail(String w) { waitForLink(n + 1); } }), 3000); }
  private static Bitmap qr(String text, int size) throws Exception {
    com.google.zxing.common.BitMatrix m = new com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size, java.util.Collections.singletonMap(com.google.zxing.EncodeHintType.MARGIN, 1));
    Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) b.setPixel(x, y, m.get(x, y) ? 0xff000000 : 0xffffffff); return b;
  }

  // ---- drawing ----
  /** The deck: a dark plinth, a metal platter, a grooved record with the album art as its label, and a tone arm. */
  private final class Deck extends View {
    private float angle, speed, target, arm = 0f, armTo = 0f; private long last; private boolean running; private Bitmap label; private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), groove = new Paint(Paint.ANTI_ALIAS_FLAG), metal = new Paint(Paint.ANTI_ALIAS_FLAG), shine = new Paint(Paint.ANTI_ALIAS_FLAG); private final Path clip = new Path(); private final RectF r = new RectF();
    Deck(Context c) { super(c); groove.setStyle(Paint.Style.STROKE); groove.setStrokeWidth(1); }
    void go() { running = true; last = SystemClock.uptimeMillis(); invalidate(); }
    void stop() { running = false; }
    void spin(boolean on) { target = on ? 200f : 0f; armTo = on ? 1f : 0f; }
    void lift() { armTo = 0f; target = 0f; h.postDelayed(() -> spin(true), 1400); }
    void label(Bitmap b) { label = b; invalidate(); }
    @Override protected void onDraw(Canvas cv) {
      long now = SystemClock.uptimeMillis(); float dt = Math.min(.05f, (now - last) / 1000f); last = now;
      speed += (target - speed) * Math.min(1f, dt * (target > speed ? 2.2f : 1.4f)); angle = (angle + speed * dt) % 360f; arm += (armTo - arm) * Math.min(1f, dt * 2.6f);
      float W = getWidth(), cx = W * .46f, cy = W * .5f, R = W * .4f;
      r.set(0, 0, W, W); p.setShader(new LinearGradient(0, 0, W, W, 0xff26262a, 0xff121214, Shader.TileMode.CLAMP)); cv.drawRoundRect(r, W * .05f, W * .05f, p); p.setShader(null);
      p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(0x24ffffff); r.inset(2, 2); cv.drawRoundRect(r, W * .05f, W * .05f, p); p.setStyle(Paint.Style.FILL);
      metal.setShader(new SweepGradient(cx, cy, new int[]{0xff6a6a70, 0xff2a2a2e, 0xff8a8a90, 0xff2a2a2e, 0xff6a6a70}, null)); cv.drawCircle(cx, cy, R * 1.06f, metal);
      p.setColor(0xff0a0a0b); cv.drawCircle(cx, cy, R, p);
      cv.save(); cv.rotate(angle, cx, cy);
      for (float g = R * .38f; g < R * .97f; g += Math.max(2f, R * .012f)) { groove.setColor((int) (g * 7) % 3 == 0 ? 0x1fffffff : 0x0dffffff); cv.drawCircle(cx, cy, g, groove); }
      float L = R * .34f; if (label != null) { clip.reset(); clip.addCircle(cx, cy, L, Path.Direction.CW); cv.save(); cv.clipPath(clip); r.set(cx - L, cy - L, cx + L, cy + L); cv.drawBitmap(label, null, r, p); cv.restore(); } else { p.setColor(Tokens.COLOR_ACCENT); cv.drawCircle(cx, cy, L, p); }
      cv.restore();
      shine.setShader(new SweepGradient(cx, cy, new int[]{0x00ffffff, 0x2effffff, 0x00ffffff, 0x00ffffff, 0x2effffff, 0x00ffffff}, new float[]{0f, .12f, .25f, .5f, .62f, .75f})); cv.drawCircle(cx, cy, R, shine);
      p.setColor(0xffd0d0d4); cv.drawCircle(cx, cy, R * .025f, p);
      float px0 = W * .88f, py0 = W * .16f; p.setShader(new RadialGradient(px0 - W * .01f, py0 - W * .01f, W * .06f, 0xff9a9aa0, 0xff3a3a3e, Shader.TileMode.CLAMP)); cv.drawCircle(px0, py0, W * .055f, p); p.setShader(null);
      float rest = -8f, play = 27f, a = rest + (play - rest) * arm; cv.save(); cv.rotate(a, px0, py0);
      p.setColor(0xff505056); r.set(px0 - W * .02f, py0 - W * .12f, px0 + W * .02f, py0 - W * .05f); cv.drawRoundRect(r, W * .01f, W * .01f, p);
      p.setColor(0xffc8c8cc); p.setStrokeWidth(W * .012f); p.setStrokeCap(Paint.Cap.ROUND); cv.drawLine(px0, py0, px0, py0 + W * .56f, p); cv.drawLine(px0, py0 + W * .56f, px0 - W * .05f, py0 + W * .64f, p);
      p.setColor(0xff2c2c30); r.set(px0 - W * .085f, py0 + W * .62f, px0 - W * .025f, py0 + W * .7f); cv.save(); cv.rotate(-30, r.centerX(), r.centerY()); cv.drawRoundRect(r, W * .008f, W * .008f, p); cv.restore();
      cv.restore();
      if (running) postInvalidateOnAnimation();
    }
  }
  private final class Bar extends View { float f; private final Paint a = new Paint(Paint.ANTI_ALIAS_FLAG), b = new Paint(Paint.ANTI_ALIAS_FLAG); private final RectF r = new RectF();
    Bar(Context c) { super(c); a.setColor(0x26ffffff); b.setColor(Tokens.COLOR_ACCENT); }
    @Override protected void onDraw(Canvas cv) { float w = getWidth(), hh = getHeight(); r.set(0, 0, w, hh); cv.drawRoundRect(r, hh / 2, hh / 2, a); if (f > 0) { r.set(0, 0, Math.max(hh, w * f), hh); cv.drawRoundRect(r, hh / 2, hh / 2, b); } } }
  /** Round control: 1 previous, 2 play, 3 pause, 4 next. */
  private final class Key extends View { int kind; boolean on; private final Paint g = new Paint(Paint.ANTI_ALIAS_FLAG), d = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    Key(Context c, int kind) { super(c); this.kind = kind; g.setColor(0xfff5f5f7); d.setColor(0x33ffffff); ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(px(3)); ring.setColor(Tokens.COLOR_ACCENT); }
    @Override protected void onDraw(Canvas cv) { float w = getWidth(), cx = w / 2, cy = getHeight() / 2f, R = w / 2 - px(3), s = R * .38f; cv.drawCircle(cx, cy, R, d); if (on) cv.drawCircle(cx, cy, R, ring); Path p = new Path();
      if (kind == 3) { cv.drawRoundRect(cx - s * .7f, cy - s * .8f, cx - s * .2f, cy + s * .8f, s * .15f, s * .15f, g); cv.drawRoundRect(cx + s * .2f, cy - s * .8f, cx + s * .7f, cy + s * .8f, s * .15f, s * .15f, g); return; }
      if (kind == 2) { p.moveTo(cx - s * .55f, cy - s * .8f); p.lineTo(cx + s * .85f, cy); p.lineTo(cx - s * .55f, cy + s * .8f); p.close(); cv.drawPath(p, g); return; }
      float dir = kind == 1 ? -1 : 1; for (int k = 0; k < 2; k++) { float ox = cx + dir * (k == 0 ? -s * .4f : s * .4f); p.reset(); p.moveTo(ox - dir * s * .45f, cy - s * .65f); p.lineTo(ox + dir * s * .45f, cy); p.lineTo(ox - dir * s * .45f, cy + s * .65f); p.close(); cv.drawPath(p, g); } } }
}
