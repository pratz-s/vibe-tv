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
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/**
 * The calm capsule at the top centre that means "talk to your TV".
 * Left, and always there: a mic in a circle and "Ask for something to watch…"; OK starts listening, and the capsule grows
 * (within limits) to show the voice, your words, or the thinking orbs. Right, only on the home screen while music plays:
 * a separate small pill with the song; select it to get previous, play/pause and next.
 * Both size to their content between a minimum and a maximum, with real padding, and the space they can grow into is
 * reserved so nothing underneath ever moves or gets covered.
 */
final class Island extends LinearLayout {
  static final int MIN_H = 84, MAX_H = 172, MAX_W = 1120, TEXT_W = 760, KEEP = 150;
  /** Long transcripts keep only their newest words, so the capsule stops growing at three lines. */
  static String tail(String s) { if (s == null || s.length() <= KEEP) return s; String t = s.substring(s.length() - KEEP); int sp = t.indexOf(' '); return "\u2026" + (sp > 0 ? t.substring(sp + 1) : t); }
  private final float u; private final Context c; private final Handler h = new Handler(Looper.getMainLooper()); private final Typeface sans, bold;
  private final LinearLayout cap, pill, keys; private final Circle mic; private final Bars voiceBars, musicBars; private final TextView prompt, songTitle; private final View orbs; private final ImageView art;
  private boolean focused, onPill, homeMedia = true, busy; private int key = 1; private MediaController controller; private boolean playing, hasSong;

  Island(Context c, float u, Typeface sans, Typeface bold, View orbs) {
    super(c); this.c = c; this.u = u; this.sans = sans; this.bold = bold; this.orbs = orbs; setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL); setClipChildren(false); setClipToPadding(false);
    // search capsule
    cap = new LinearLayout(c); cap.setGravity(Gravity.CENTER_VERTICAL); cap.setMinimumHeight(px(MIN_H)); cap.setPadding(px(18), px(16), px(40), px(16)); cap.setBackground(new Shell()); cap.setElevation(px(16));
    mic = new Circle(c); cap.addView(mic, new LayoutParams(px(52), px(52)));
    voiceBars = new Bars(c, true); voiceBars.setVisibility(GONE); cap.addView(voiceBars, new LayoutParams(px(112), px(56)));
    cap.addView(orbs, new LayoutParams(px(72), px(60))); orbs.setVisibility(GONE);
    prompt = text(sans, 30, 0x8cf5f5f7); prompt.setText("Ask for something to watch…"); prompt.setMaxWidth(px(MAX_W - 220)); prompt.setMaxLines(2); prompt.setEllipsize(TextUtils.TruncateAt.START);
    LayoutParams pp = new LayoutParams(-2, -2); pp.leftMargin = px(22); cap.addView(prompt, pp);
    addView(cap, new LayoutParams(-2, -2));
    // music pill
    pill = new LinearLayout(c); pill.setGravity(Gravity.CENTER_VERTICAL); pill.setMinimumHeight(px(MIN_H)); pill.setPadding(px(16), px(16), px(34), px(16)); pill.setBackground(new Shell()); pill.setElevation(px(16)); pill.setVisibility(GONE);
    art = new ImageView(c); art.setScaleType(ImageView.ScaleType.CENTER_CROP); art.setClipToOutline(true); art.setOutlineProvider(new android.view.ViewOutlineProvider() { public void getOutline(View v, Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); } }); pill.addView(art, new LayoutParams(px(52), px(52)));
    musicBars = new Bars(c, false); LayoutParams mb = new LayoutParams(px(34), px(30)); mb.leftMargin = px(20); pill.addView(musicBars, mb);
    songTitle = text(bold, 26, 0xfff5f5f7); songTitle.setMaxWidth(px(320)); LayoutParams sp = new LayoutParams(-2, -2); sp.leftMargin = px(16); pill.addView(songTitle, sp);
    keys = new LinearLayout(c); keys.setGravity(Gravity.CENTER_VERTICAL); keys.setVisibility(GONE);
    for (int k : new int[]{1, 2, 4}) { Key kv = new Key(c, k); LayoutParams kp = new LayoutParams(px(56), px(56)); kp.leftMargin = px(12); keys.addView(kv, kp); }
    LayoutParams kl = new LayoutParams(-2, -2); kl.leftMargin = px(14); pill.addView(keys, kl);
    LayoutParams pl = new LayoutParams(-2, -2); pl.leftMargin = px(20); addView(pill, pl);
    h.postDelayed(this::watchMedia, 800);
  }
  private int px(float v) { return Math.round(v * u); }
  private TextView text(Typeface f, float size, int color) { TextView v = new TextView(c); v.setTypeface(f); v.setTextColor(color); v.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u); v.setIncludeFontPadding(false); v.setSingleLine(true); v.setEllipsize(TextUtils.TruncateAt.END); return v; }
  private void morph() { if (getParent() instanceof ViewGroup) { AutoTransition t = new AutoTransition(); t.setDuration(260); TransitionManager.beginDelayedTransition((ViewGroup) getParent(), t); } }

  // ---- focus: OK on the capsule starts voice; right steps onto the music pill ----
  void focus(boolean on) { focused = on; onPill = false; key = 1; paint(); }
  boolean focused() { return focused; }
  /** The music pill shows only on the home screen. */
  void media(boolean allowed) { if (homeMedia == allowed) return; homeMedia = allowed; if (!allowed) onPill = false; paint(); }
  boolean hasMedia() { return hasSong && homeMedia; }
  void move(int d) {
    if (!focused) return;
    if (!onPill) { if (d > 0 && hasMedia()) { onPill = true; key = 1; morph(); paint(); } return; }
    if (d < 0 && key == 0) { onPill = false; morph(); paint(); return; }
    key = Math.max(0, Math.min(2, key + d)); paint();
  }
  /** Returns true when OK means "start listening". */
  boolean press() { if (!onPill) return true; if (controller == null) return false; MediaController.TransportControls tc = controller.getTransportControls();
    if (key == 0) tc.skipToPrevious(); else if (key == 2) tc.skipToNext(); else if (playing) tc.pause(); else tc.play(); h.postDelayed(this::show, 350); return false; }

  private void paint() {
    ((Shell) cap.getBackground()).lit = focused && !onPill && !busy; cap.getBackground().invalidateSelf(); mic.lit = focused && !onPill && !busy; mic.invalidate();
    if (!busy) { prompt.setTextColor(focused && !onPill ? 0xfff5f5f7 : 0x8cf5f5f7); prompt.setText(focused && !onPill ? "Press OK and say what you'd like to watch" : ("me".equals(Api.viewing) ? "" : Api.viewingLabel + "  \u00b7  ") + "Ask for something to watch…"); }
    pill.setVisibility(hasMedia() && !busy ? VISIBLE : GONE); keys.setVisibility(onPill ? VISIBLE : GONE); ((Shell) pill.getBackground()).lit = onPill; pill.getBackground().invalidateSelf();
    for (int i = 0; i < keys.getChildCount(); i++) { Key kv = (Key) keys.getChildAt(i); kv.on = onPill && i == key; if (i == 1) kv.kind = playing ? 3 : 2; kv.invalidate(); }
  }

  // ---- voice states, all inside the capsule ----
  boolean busy() { return busy; }
  private void voice(boolean bars, boolean think) { busy = true; morph(); mic.setVisibility(bars || think ? GONE : VISIBLE); voiceBars.setVisibility(bars ? VISIBLE : GONE); orbs.setVisibility(think ? VISIBLE : GONE); prompt.setSingleLine(false); prompt.setMaxLines(3); prompt.setEllipsize(null); prompt.setMaxWidth(px(TEXT_W)); prompt.setLineSpacing(px(6), 1f); paint(); }
  void listening(String s) { voiceBars.live = true; voice(true, false); prompt.setTextColor(0x99f5f5f7); prompt.setText(s); }
  void words(String s) { prompt.setTextColor(0xfff5f5f7); prompt.setText(tail(s)); }
  void level(float db) { voiceBars.level(db); }
  void speaking(String s) { voiceBars.live = false; voice(true, false); prompt.setTextColor(0xfff5f5f7); prompt.setText(tail(s)); }
  void thinking(String sub) { voice(false, true); prompt.setTextColor(0xe6f5f5f7); prompt.setText("Thinking…  " + sub); }
  void sub(String s) { if (orbs.getVisibility() == VISIBLE) prompt.setText("Thinking…  " + s); }
  void found(String took) { if (orbs.getVisibility() != VISIBLE) return; prompt.setText("Found it in " + took); h.postDelayed(this::settle, 1100); }
  void message(String m) { voiceBars.live = false; voice(false, false); prompt.setTextColor(0xccf5f5f7); prompt.setText(tail(m)); h.postDelayed(this::settle, 2600); }
  void settle() { if (!busy) { paint(); return; } busy = false; morph(); voiceBars.live = false; voiceBars.setVisibility(GONE); orbs.setVisibility(GONE); mic.setVisibility(VISIBLE); prompt.setSingleLine(true); prompt.setEllipsize(TextUtils.TruncateAt.END); prompt.setMaxWidth(px(MAX_W - 220)); prompt.setLineSpacing(0, 1f); paint(); }

  // ---- music ----
  private void watchMedia() {
    try { MediaSessionManager m = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE); ComponentName me = new ComponentName(c, Media.class); m.addOnActiveSessionsChangedListener(this::pick, me, h); pick(m.getActiveSessions(me)); }
    catch (Exception e) { Log.w(HomeService.T, "island media: " + e); }
  }
  private final MediaController.Callback cb = new MediaController.Callback() { @Override public void onMetadataChanged(MediaMetadata md) { show(); } @Override public void onPlaybackStateChanged(PlaybackState s) { show(); } };
  private void pick(List<MediaController> list) {
    if (controller != null) controller.unregisterCallback(cb); controller = null;
    if (list != null) for (MediaController mc : list) { PlaybackState s = mc.getPlaybackState(); if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { controller = mc; break; } }
    if (controller == null && list != null && !list.isEmpty()) controller = list.get(0);
    if (controller != null) controller.registerCallback(cb, h); show();
  }
  private void show() {
    MediaMetadata md = controller == null ? null : controller.getMetadata(); PlaybackState s = controller == null ? null : controller.getPlaybackState();
    boolean had = hasSong; hasSong = md != null && md.getString(MediaMetadata.METADATA_KEY_TITLE) != null;
    playing = hasSong && s != null && s.getState() == PlaybackState.STATE_PLAYING; musicBars.playing = playing;
    if (hasSong) { songTitle.setText(md.getString(MediaMetadata.METADATA_KEY_TITLE)); Bitmap b = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART); if (b == null) b = md.getBitmap(MediaMetadata.METADATA_KEY_ART); art.setImageBitmap(b); art.setVisibility(b == null ? GONE : VISIBLE); }
    if (!hasSong) onPill = false; if (had != hasSong) morph(); paint();
  }
  /** Lets the turntable drive the same Spotify session. */
  MediaController controller() { return controller; }

  // ---- drawing ----
  /** Black capsule with a hairline edge; when selected, a warm orange ring and a soft glow beneath. */
  private final class Shell extends Drawable {
    boolean lit; private final Paint f = new Paint(Paint.ANTI_ALIAS_FLAG), e = new Paint(Paint.ANTI_ALIAS_FLAG), r = new Paint(Paint.ANTI_ALIAS_FLAG); private final RectF b = new RectF();
    Shell() { f.setColor(0xf2000000); e.setStyle(Paint.Style.STROKE); e.setStrokeWidth(px(1.5f)); e.setColor(0x2effffff); r.setStyle(Paint.Style.STROKE); r.setStrokeWidth(px(3)); r.setColor(Tokens.COLOR_ACCENT); }
    @Override public void draw(Canvas cv) { b.set(getBounds()); float rad = Math.min(b.height() / 2, px(MIN_H) / 2f + px(8)); cv.drawRoundRect(b, rad, rad, f); b.inset(px(1.5f), px(1.5f)); cv.drawRoundRect(b, rad, rad, lit ? r : e); }
    @Override public void getOutline(Outline o) { Rect bb = getBounds(); o.setRoundRect(bb, Math.min(bb.height() / 2f, px(MIN_H) / 2f + px(8))); }
    @Override public void setAlpha(int a) {} @Override public void setColorFilter(ColorFilter cf) {} @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
  }
  /** The mic in a grey circle; orange ring when the capsule is selected. */
  private final class Circle extends View {
    boolean lit; private final Paint d = new Paint(Paint.ANTI_ALIAS_FLAG), g = new Paint(Paint.ANTI_ALIAS_FLAG), l = new Paint(Paint.ANTI_ALIAS_FLAG), r = new Paint(Paint.ANTI_ALIAS_FLAG);
    Circle(Context c) { super(c); d.setColor(0xff2c2c2e); g.setColor(0xfff5f5f7); l.setStyle(Paint.Style.STROKE); l.setStrokeCap(Paint.Cap.ROUND); l.setColor(0xfff5f5f7); r.setStyle(Paint.Style.STROKE); r.setStrokeWidth(px(3)); r.setColor(Tokens.COLOR_ACCENT); }
    @Override protected void onDraw(Canvas cv) { float w = getWidth(), cx = w / 2, cy = getHeight() / 2f, R = w / 2 - px(2), s = R * .44f; d.setColor(lit ? Tokens.COLOR_ACCENT : 0xff2c2c2e); cv.drawCircle(cx, cy, R, d);
      g.setColor(lit ? 0xff000000 : 0xfff5f5f7); l.setColor(g.getColor()); l.setStrokeWidth(Math.max(2, s * .2f));
      RectF m = new RectF(cx - s * .38f, cy - s * .95f, cx + s * .38f, cy + s * .2f); cv.drawRoundRect(m, s * .38f, s * .38f, g); RectF a = new RectF(cx - s * .7f, cy - s * .5f, cx + s * .7f, cy + s * .55f); cv.drawArc(a, 15, 150, false, l); cv.drawLine(cx, cy + s * .55f, cx, cy + s * .92f, l); }
  }
  /** Voice bars (white, follow the voice) or music bars (orange, dance while playing). */
  private final class Bars extends View {
    boolean live, playing; private final boolean voice; private float lvl; private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), glow = new Paint(Paint.ANTI_ALIAS_FLAG); private final long t0 = System.currentTimeMillis(); private final RectF r = new RectF(); private final float[] hs = new float[7];
    Bars(Context c, boolean voice) { super(c); this.voice = voice; p.setColor(voice ? 0xfff5f5f7 : Tokens.COLOR_ACCENT); if (voice) { setLayerType(LAYER_TYPE_SOFTWARE, null); glow.setColor(0x80ffffff); glow.setMaskFilter(new BlurMaskFilter(px(8), BlurMaskFilter.Blur.NORMAL)); } }
    void level(float db) { lvl = Math.max(0f, Math.min(1f, (db + 1f) / 10f)); }
    @Override protected void onDraw(Canvas cv) {
      int n = voice ? 7 : 3; float w = getWidth(), hh = getHeight(), slot = w / n, bw = slot * .46f, t = (System.currentTimeMillis() - t0) / 1000f;
      for (int i = 0; i < n; i++) { float shape = voice ? new float[]{.4f, .7f, .9f, 1f, .85f, .6f, .35f}[i] : 1f, target;
        if (voice) target = live ? Math.max(.16f, lvl * shape * (.6f + .4f * (float) Math.abs(Math.sin(t * 8 + i * 1.3)))) : .2f + .14f * (float) Math.sin(t * 2.4 + i);
        else target = playing ? .3f + .65f * (float) Math.abs(Math.sin(t * (3.1 + i) + i * 2)) : .3f;
        hs[i] += (target - hs[i]) * .3f; float bh = Math.max(bw, hh * hs[i]), cx = slot * (i + .5f); r.set(cx - bw / 2, (hh - bh) / 2, cx + bw / 2, (hh + bh) / 2);
        if (voice) cv.drawRoundRect(r, bw / 2, bw / 2, glow); cv.drawRoundRect(r, bw / 2, bw / 2, p); }
      if (getVisibility() == VISIBLE) postInvalidateOnAnimation();
    }
  }
  /** 1 previous, 2 play (triangle), 3 pause (two bars), 4 next. The selected one is a solid orange disc with a dark glyph. */
  private final class Key extends View {
    int kind; boolean on; private final Paint g = new Paint(Paint.ANTI_ALIAS_FLAG), d = new Paint(Paint.ANTI_ALIAS_FLAG);
    Key(Context c, int kind) { super(c); this.kind = kind; }
    @Override protected void onDraw(Canvas cv) { float w = getWidth(), cx = w / 2, cy = getHeight() / 2f, R = w / 2 - px(1), s = R * .42f; d.setColor(on ? Tokens.COLOR_ACCENT : 0xff2c2c2e); cv.drawCircle(cx, cy, R, d); g.setColor(on ? 0xff000000 : 0xfff5f5f7); Path p = new Path();
      if (kind == 3) { cv.drawRoundRect(cx - s * .7f, cy - s * .8f, cx - s * .2f, cy + s * .8f, s * .15f, s * .15f, g); cv.drawRoundRect(cx + s * .2f, cy - s * .8f, cx + s * .7f, cy + s * .8f, s * .15f, s * .15f, g); return; }
      if (kind == 2) { p.moveTo(cx - s * .5f, cy - s * .85f); p.lineTo(cx + s * .9f, cy); p.lineTo(cx - s * .5f, cy + s * .85f); p.close(); cv.drawPath(p, g); return; }
      float dir = kind == 1 ? -1 : 1; for (int k = 0; k < 2; k++) { float ox = cx + dir * (k == 0 ? -s * .4f : s * .4f); p.reset(); p.moveTo(ox - dir * s * .45f, cy - s * .65f); p.lineTo(ox + dir * s * .45f, cy); p.lineTo(ox - dir * s * .45f, cy + s * .65f); p.close(); cv.drawPath(p, g); } }
  }
}
