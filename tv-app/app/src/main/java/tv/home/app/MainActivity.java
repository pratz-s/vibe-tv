package tv.home.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import androidx.dynamicanimation.animation.*;
import androidx.media3.common.*;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.*;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import java.util.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class MainActivity extends Activity {
  static final String T = "TVHOME";
  static final int ROOM = Tokens.COLOR_ROOM, INK = Tokens.COLOR_INK, SOFT = Tokens.COLOR_SOFT, VOICE = Tokens.COLOR_VOICE;
  static final int H0 = Tokens.HOME_ROW_TITLE_Y, STEP = Tokens.ROW_STEP, CW = Tokens.CARD_WIDTH, CH = Tokens.CARD_HEIGHT, GAP = Tokens.GRID_GUTTER, LEFT = Tokens.SAFE_LEFT;
  static final int TRACK_Y = Tokens.ROW_TITLE_HEIGHT + Tokens.ROW_TITLE_GAP, INFO_SHIFT = Tokens.HOME_INFO_BOTTOM - Tokens.BROWSE_INFO_BOTTOM;

  enum Mode { HOME, ASK, RESULT, DETAIL }
  static final int WIDE = 0, TALL = 1, SPOT = 2, APPS = 3, COVER = 4;
  /** Cover-flow rows: tall posters, the focused one big in the middle, neighbours stepping back behind it, a glass caption under each. */
  static final int DOCK = 104, DOCK_GAP = 34, DOCK_PAD = 40, DOCK_PAD_Y = 14;
  static final int COVER_W = 280, COVER_H = 400, COVER_PLATE = 0, COVER_GAP = 0;
  static final float SPOT_SMALL = (float) CW / Tokens.FORM_SPOT_WIDTH;
  static final class Row {
    int first, at, form, y; boolean system, grid; String name, section, note, why; List<Title> items = new ArrayList<>(); ViewGroup track; TextView head; View band; FrameLayout box; List<View> cards = new ArrayList<>();
    int w() { return form == COVER ? COVER_W : form == TALL ? Tokens.FORM_TALL_WIDTH : form == SPOT ? Tokens.FORM_SPOT_WIDTH : form == APPS ? DOCK : CW; }
    int h() { return form == COVER ? COVER_H : form == TALL ? Tokens.FORM_TALL_HEIGHT : form == SPOT ? Tokens.FORM_SPOT_HEIGHT : form == APPS ? DOCK : CH; }
    int cols() { return form == WIDE ? Tokens.LENS_COLUMNS : 6; }
    int height() { return grid ? Tokens.FORM_GRID_STEP : TRACK_Y + h() + (form == COVER ? COVER_GAP + COVER_PLATE : 0) + (system ? DOCK_PAD_Y + 48 : Tokens.FORM_ROW_GAP); }
  }

  float u; Mode mode = Mode.HOME; final Handler h = new Handler(Looper.getMainLooper());
  FrameLayout stage, rowsBox, askBox, detail; SurfaceView video; ImageView bgA, bgB, logo, dLogo; View dim, soft; boolean flip; Glass glass; Sound sound; Glass.Sheen sheen = new Glass.Sheen(); Glass.Surface veilGlass; Blueprint plan; long stoppedAt; Lens lens, btnLens; Morph morph; ImageView morphed; RectF morphRect; float travel; boolean comeBack; final Map<View, Map<DynamicAnimation.ViewProperty, SpringAnimation>> springs = new WeakHashMap<>(); com.bumptech.glide.request.target.CustomTarget<Bitmap> small; float soundVolume = .4f;
  Ticker ticker; LinearLayout info, dock, btns, top; TextView name, meta, why, where, clock, hint, said, veil, dName, dMeta, dText;
  List<Row> rows = new ArrayList<>(), home = new ArrayList<>(); int r; Title cur; ExoPlayer player; int vidW, vidH; boolean videoOn; Runnable dwell, idle; int ask; int btn; Mode before = Mode.HOME; long downAt; boolean longDone; String heardText = "";
  Typeface[] faces = new Typeface[10];

  int px(float v) { return Math.round(v * u); }

  Typeface font(int w) {
    int i = w / 100;
    if (faces[i] == null) faces[i] = new Typeface.Builder(getAssets(), "geist-sans.ttf").setFontVariationSettings("'wght' " + w).build();
    return faces[i];
  }

  Typeface serif, pixel, pixelCircle, pixelLine; Typeface[] monos = new Typeface[10];
  Typeface serif() { if (serif == null) serif = Typeface.createFromAsset(getAssets(), "geist-serif.ttf"); return serif; }
  Typeface pixel() { if (pixel == null) pixel = Typeface.createFromAsset(getAssets(), "geist-pixel.ttf"); return pixel; }
  /** The typeface a type token names, so a font change in Figma reaches the TV without touching this file. */
  Typeface face(String family, int weight) { switch (family) { case "serif": return serif(); case "mono": return mono(weight); case "pixel": return pixel(); case "pixelCircle": return pixelCircle(); case "pixelLine": return pixelLine(); default: return font(weight); } }

  Typeface pixelCircle() { if (pixelCircle == null) pixelCircle = Typeface.createFromAsset(getAssets(), "geist-pixel-circle.ttf"); return pixelCircle; }
  Typeface pixelLine() { if (pixelLine == null) pixelLine = Typeface.createFromAsset(getAssets(), "geist-pixel-line.ttf"); return pixelLine; }
  Typeface mono(int w) { int i = w / 100; if (monos[i] == null) monos[i] = new Typeface.Builder(getAssets(), "geist-mono.ttf").setFontVariationSettings("'wght' " + w).build(); return monos[i]; }

  TextView text(float size, int weight, int color) {
    TextView t = new TextView(this);
    t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size)); t.setTypeface(font(weight)); t.setTextColor(color); t.setIncludeFontPadding(false);
    return t;
  }

  /** A text view set from one type token: size, line height and tracking. */
  TextView type(float size, float line, float tracking, Typeface face, int color) {
    TextView t = new TextView(this); t.setIncludeFontPadding(false); t.setTypeface(face); t.setTextColor(color); fit(t, size, line, tracking); return t;
  }

  void fit(TextView t, float size, float line, float tracking) { t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size)); t.setLineHeight(px(line)); t.setLetterSpacing(tracking); }

  void clamp(TextView t, int lines) { t.setMaxLines(lines); t.setEllipsize(TextUtils.TruncateAt.END); }

  /** Retargets from wherever the view is, keeping its speed, so a held key never makes motion restart. */
  void spring(View v, DynamicAnimation.ViewProperty p, float to, float k, float d) {
    Map<DynamicAnimation.ViewProperty, SpringAnimation> m = springs.computeIfAbsent(v, x -> new HashMap<>()); SpringAnimation a = m.get(p);
    if (a == null) { a = new SpringAnimation(v, p); a.setSpring(new SpringForce(to)); m.put(p, a); }
    a.getSpring().setStiffness(k).setDampingRatio(d); a.animateToFinalPosition(to);
  }

  void slide(View v, DynamicAnimation.ViewProperty p, float to) { spring(v, p, to, Tokens.SPRING_SLIDE_STIFFNESS, Tokens.SPRING_SLIDE_DAMPING); }

  void cue(String name) { sound.play(name); if (videoOn && !"focus".equals(name) && !"row".equals(name)) { player.setVolume(soundVolume * Tokens.SOUND_VIDEO_DUCK); h.postDelayed(() -> player.setVolume(soundVolume), Tokens.SOUND_DUCK_MS * 2L); } }

  FrameLayout.LayoutParams at(int w, int hgt, float x, float y) {
    FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(w, hgt); p.leftMargin = px(x); p.topMargin = px(y); return p;
  }

  void round(View v, float radius) {
    v.setOutlineProvider(new ViewOutlineProvider() { public void getOutline(View x, Outline o) { o.setRoundRect(0, 0, x.getWidth(), x.getHeight(), px(radius)); } });
    v.setClipToOutline(true);
  }

  GradientDrawable fade(GradientDrawable.Orientation o, int... c) { return new GradientDrawable(o, c); }

  /** The streaming apps picked during setup (APPS in .env), in that order, that are actually installed on this TV. */
  static final String[][] KNOWN_APPS = {{"Prime Video", "com.amazon.amazonvideo.livingroom"}, {"JioHotstar", "in.startv.hotstar"}, {"YouTube", "com.google.android.youtube.tv"}, {"Netflix", "com.netflix.ninja"},
    {"Spotify", "com.spotify.tv.android"}, {"Stremio", "com.stremio.one"}, {"Disney+", "com.disney.disneyplus"}, {"Max", "com.wbd.stream"}, {"Apple TV", "com.apple.atve.androidtv.appletv"}, {"SonyLIV", "com.sonyliv"}, {"ZEE5", "com.zee5.androidtv"}};
  static boolean appAllowed(String pkg) { for (String p : BuildConfig.APPS.split(",")) if (p.trim().equals(pkg)) return true; return false; }
  java.util.List<String[]> apps() { java.util.List<String[]> out = new java.util.ArrayList<>();
    for (String p : BuildConfig.APPS.split(",")) { String pkg = p.trim(); if (pkg.isEmpty()) continue; String name = pkg;
      for (String[] k : KNOWN_APPS) if (k[1].equals(pkg)) name = k[0];
      if (getPackageManager().getLeanbackLaunchIntentForPackage(pkg) != null || getPackageManager().getLaunchIntentForPackage(pkg) != null) out.add(new String[]{name, pkg}); }
    return out; }

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    Point size = new Point(); getWindowManager().getDefaultDisplay().getRealSize(size); u = size.x / 1920f;
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN); getWindow().setDecorFitsSystemWindows(false); getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
    glass = new Glass(u); sound = new Sound(this);
    stage = new FrameLayout(this); stage.setBackground(PixelIcon.room(this, size.x, size.y)); stage.setClipChildren(false);
    video = new SurfaceView(this); stage.addView(video, new FrameLayout.LayoutParams(size.x, size.y, Gravity.CENTER));
    glow = new ImageView(this); glow.setScaleType(ImageView.ScaleType.FIT_XY); glow.setAlpha(0f); if (android.os.Build.VERSION.SDK_INT >= 31) glow.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(px(90), px(90), android.graphics.Shader.TileMode.CLAMP)); stage.addView(glow, new FrameLayout.LayoutParams(-1, -1));
    bgA = new ImageView(this); bgB = new ImageView(this);
    for (ImageView i : new ImageView[]{bgA, bgB}) { i.setScaleType(ImageView.ScaleType.CENTER_CROP); i.setAlpha(0f); stage.addView(i, new FrameLayout.LayoutParams(-1, -1)); }
    soft = new View(this); soft.setBackground(new Glass.Surface(glass, soft, 0, 0)); soft.setAlpha(0f); soft.setVisibility(View.GONE); stage.addView(soft, new FrameLayout.LayoutParams(-1, -1));
    Bitmap sb = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888); Canvas sc = new Canvas(sb);
    Drawable g1 = fade(GradientDrawable.Orientation.BOTTOM_TOP, ROOM, 0xe607080b, 0x4707080b, 0x0007080b), g2 = fade(GradientDrawable.Orientation.LEFT_RIGHT, 0xcc07080b, 0x5c07080b, 0x0007080b); g1.setBounds(0, 0, 480, 270); g2.setBounds(0, 0, 480, 270); g1.draw(sc); g2.draw(sc);
    View s1 = new View(this); s1.setBackground(new BitmapDrawable(getResources(), sb)); stage.addView(s1, new FrameLayout.LayoutParams(-1, -1));
    
    dim = new View(this); dim.setBackground(PixelIcon.room(this, size.x, size.y)); dim.setAlpha(0f); stage.addView(dim, new FrameLayout.LayoutParams(-1, -1));
    dots = new Widgets(this, u, font(400), font(300), font(600)); dots.setAlpha(0f); FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END); cp.topMargin = px(192); cp.rightMargin = px(Tokens.SAFE_RIGHT); stage.addView(dots, cp);
    LinearLayout scrollHint = new LinearLayout(this); scrollHint.setOrientation(LinearLayout.HORIZONTAL); scrollHint.setGravity(Gravity.CENTER_VERTICAL); scrollHint.setAlpha(0f);
    View chev = new View(this) { final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); { p.setColor(0xccf5f5f7); }
      @Override protected void onDraw(Canvas c) { float pitch = getWidth() / 9f, r = pitch * .32f; int[][] d = {{0,0},{1,1},{2,2},{3,3},{4,4},{5,3},{6,2},{7,1},{8,0},{1,0},{7,0},{2,1},{6,1},{3,2},{5,2},{4,3}};
        for (int[] q : d) { boolean core = Math.abs(q[0] - 4) == 4 - q[1]; p.setAlpha(core ? 230 : 70); c.drawCircle((q[0] + .5f) * pitch, (q[1] + .5f) * pitch, r, p); } } };
    scrollHint.addView(chev, new LinearLayout.LayoutParams(px(54), px(30)));
    TextView ht = type(24, 32, 0.02f, font(500), 0x8cf5f5f7); ht.setText("Scroll down for your recommendations"); LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2); hl.leftMargin = px(16); scrollHint.addView(ht, hl);
    FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL); hp.topMargin = px(1080 - Tokens.SAFE_BOTTOM - 14); stage.addView(scrollHint, hp); more = scrollHint;
    android.animation.ObjectAnimator bob = android.animation.ObjectAnimator.ofFloat(chev, View.TRANSLATION_Y, 0f, px(8)); bob.setDuration(700); bob.setRepeatMode(android.animation.ValueAnimator.REVERSE); bob.setRepeatCount(android.animation.ValueAnimator.INFINITE); bob.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator()); bob.start();

    info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setGravity(Gravity.BOTTOM);
    logo = new ImageView(this); logo.setAdjustViewBounds(true); logo.setMaxWidth(px(Tokens.LOGO_HOME_WIDTH)); logo.setMaxHeight(px(Tokens.LOGO_HOME_HEIGHT)); logo.setScaleType(ImageView.ScaleType.FIT_START);
    logo.addOnLayoutChangeListener((v, l1, t1, r1, b1, l0, t0, r0, b0) -> { v.setPivotX(0); v.setPivotY(v.getHeight()); });
    name = type(Tokens.TITLE_SIZE, Tokens.TITLE_LINE, Tokens.TITLE_TRACKING, face(Tokens.TITLE_FAMILY, 400), INK); meta = type(Tokens.META_SIZE, Tokens.META_LINE, Tokens.META_TRACKING, face(Tokens.META_FAMILY, 400), SOFT); why = type(Tokens.REASON_SIZE, Tokens.REASON_LINE, Tokens.REASON_TRACKING, face(Tokens.REASON_FAMILY, 400), INK); where = type(Tokens.SERVICE_SIZE, Tokens.SERVICE_LINE, Tokens.SERVICE_TRACKING, face(Tokens.SERVICE_FAMILY, 400), SOFT);
    meta.setAllCaps(false); where.setAllCaps(false); clamp(name, Tokens.CLAMP_TITLE); clamp(why, Tokens.CLAMP_REASON); name.setMaxWidth(px(Tokens.PANEL_TEXT_WIDTH)); why.setMaxWidth(px(Tokens.PANEL_TEXT_WIDTH));
    info.addView(logo, new LinearLayout.LayoutParams(-2, -2)); info.addView(name);
    LinearLayout.LayoutParams m = new LinearLayout.LayoutParams(-2, -2); m.topMargin = px(Tokens.SPACE_S); info.addView(meta, m);
    LinearLayout.LayoutParams w = new LinearLayout.LayoutParams(-2, -2); w.topMargin = px(Tokens.SPACE_XS); info.addView(why, w);
    LinearLayout.LayoutParams wh = new LinearLayout.LayoutParams(-2, -2); wh.topMargin = px(Tokens.SPACE_XS); info.addView(where, wh);
    FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(px(Tokens.GRID_CONTENT), -2, Gravity.BOTTOM); ip.leftMargin = px(LEFT); ip.bottomMargin = px(Tokens.CANVAS_HEIGHT - Tokens.HOME_INFO_BOTTOM); stage.addView(info, ip);

    lens = new Lens(this, glass, u); stage.addView(lens, new FrameLayout.LayoutParams(-1, -1));
    rowsBox = new FrameLayout(this); rowsBox.setClipChildren(false); stage.addView(rowsBox, new FrameLayout.LayoutParams(-1, -1));
    morph = new Morph(this); stage.addView(morph, new FrameLayout.LayoutParams(-1, -1));

    dock = new LinearLayout(this); dock.setOrientation(LinearLayout.HORIZONTAL); stage.addView(dock, at(-2, -2, LEFT, Tokens.SAFE_TOP));

    top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(px(Tokens.CHIP_PAD_X), 0, px(Tokens.CHIP_PAD_X), 0); top.setBackground(new Glass.Surface(glass, top, px(Tokens.CHIP_RADIUS), Tokens.GLASS_TINT));
    hint = type(Tokens.PROMPT_SIZE, Tokens.PROMPT_LINE, Tokens.PROMPT_TRACKING, face(Tokens.PROMPT_FAMILY, 400), Tokens.COLOR_PROMPT); hint.setText(Tokens.COPY_PROMPT); clock = type(Tokens.CLOCK_SIZE, Tokens.CLOCK_LINE, Tokens.CLOCK_TRACKING, face(Tokens.CLOCK_FAMILY, 400), INK);
    ticker = new Ticker(this, hint, clock, u, this::centreChip); top.setClipChildren(false); top.addView(ticker, new LinearLayout.LayoutParams(ticker.soloWidth(), -1)); top.addView(clock);
    FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, px(Tokens.CHIP_HEIGHT), Gravity.END); tp.rightMargin = px(Tokens.SAFE_RIGHT); tp.topMargin = px(Tokens.SAFE_TOP); stage.addView(top, tp);

    askBox = new FrameLayout(this); askBox.setVisibility(View.GONE); said = type(Tokens.SAID_SIZE, Tokens.SAID_LINE, Tokens.SAID_TRACKING, face(Tokens.SAID_FAMILY, 400), INK); said.setMaxWidth(px(Tokens.GRID_CONTENT)); clamp(said, Tokens.CLAMP_SAID);
    int listenGravity = Tokens.LISTEN_CENTERED ? Gravity.CENTER_HORIZONTAL : Gravity.START; said.setGravity(listenGravity);
    FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(px(Tokens.GRID_CONTENT), -2, Gravity.CENTER_VERTICAL | (Tokens.LISTEN_CENTERED ? Gravity.CENTER_HORIZONTAL : Gravity.START)); if (!Tokens.LISTEN_CENTERED) ap.leftMargin = px(LEFT); askBox.addView(said, ap); stage.addView(askBox, new FrameLayout.LayoutParams(-1, -1));

    detail = new FrameLayout(this); detail.setVisibility(View.GONE);
    LinearLayout dl = new LinearLayout(this); dl.setOrientation(LinearLayout.VERTICAL); int pad = px(Tokens.PANEL_PAD); dl.setPadding(pad, pad, pad, pad); dl.setBackground(new Glass.Surface(glass, dl, px(Tokens.PANEL_RADIUS), Tokens.GLASS_PANEL)); dl.setClipChildren(false); dl.setClipToPadding(false);
    dLogo = new ImageView(this); dLogo.setAdjustViewBounds(true); dLogo.setMaxWidth(px(Tokens.LOGO_HOME_WIDTH)); dLogo.setMaxHeight(px(Tokens.LOGO_HOME_HEIGHT)); dLogo.setScaleType(ImageView.ScaleType.FIT_START);
    dName = type(Tokens.TITLE_SIZE, Tokens.TITLE_LINE, Tokens.TITLE_TRACKING, face(Tokens.TITLE_FAMILY, 400), INK); clamp(dName, Tokens.CLAMP_TITLE); dMeta = type(Tokens.META_SIZE, Tokens.META_LINE, Tokens.META_TRACKING, face(Tokens.META_FAMILY, 400), SOFT); dMeta.setAllCaps(false); dText = type(Tokens.OVERVIEW_SIZE, Tokens.OVERVIEW_LINE, Tokens.OVERVIEW_TRACKING, face(Tokens.OVERVIEW_FAMILY, 400), INK); dText.setMaxWidth(px(Tokens.PANEL_TEXT_WIDTH)); clamp(dText, Tokens.CLAMP_OVERVIEW);
    btns = new LinearLayout(this); btns.setClipChildren(false); btns.setClipToPadding(false);
    FrameLayout btnWrap = new FrameLayout(this); btnWrap.setClipChildren(false); btnWrap.setClipToPadding(false); btnLens = new Lens(this, glass, u); btnWrap.addView(btnLens, new FrameLayout.LayoutParams(-1, -1)); btnWrap.addView(btns, new FrameLayout.LayoutParams(-2, -2));
    dl.addView(dLogo, new LinearLayout.LayoutParams(-2, -2)); dl.addView(dName);
    LinearLayout.LayoutParams a1 = new LinearLayout.LayoutParams(-2, -2); a1.topMargin = px(Tokens.SPACE_S); dl.addView(dMeta, a1);
    LinearLayout.LayoutParams a2 = new LinearLayout.LayoutParams(-2, -2); a2.topMargin = px(Tokens.SPACE_XS); dl.addView(dText, a2);
    LinearLayout.LayoutParams a3 = new LinearLayout.LayoutParams(-2, -2); a3.topMargin = px(Tokens.SPACE_L); dl.addView(btnWrap, a3);
    FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(px(Tokens.PANEL_WIDTH), -2, Gravity.BOTTOM); dp.leftMargin = px(LEFT); dp.bottomMargin = px(Tokens.SAFE_BOTTOM); detail.addView(dl, dp); stage.addView(detail, new FrameLayout.LayoutParams(-1, -1));

    veil = type(Tokens.HANDOFF_SIZE, Tokens.HANDOFF_LINE, Tokens.HANDOFF_TRACKING, face(Tokens.HANDOFF_FAMILY, 400), INK); veilGlass = new Glass.Surface(glass, veil, 0, Tokens.GLASS_VEIL); veil.setBackground(veilGlass); veil.setPadding(px(LEFT), 0, px(LEFT), 0); veil.setGravity(Gravity.CENTER); veil.setAlpha(0f); veil.setVisibility(View.GONE); stage.addView(veil, new FrameLayout.LayoutParams(-1, -1));
    plan = new Blueprint(this, u, mono(400)); stage.addView(plan, new FrameLayout.LayoutParams(-1, -1)); Warp warp = new Warp(this, u); stage.addView(warp, new FrameLayout.LayoutParams(-1, -1)); plan.warp = warp; final Set<View> hidden = new HashSet<>();
    warp.onStart = () -> { for (int k = 0; k < stage.getChildCount(); k++) { View v = stage.getChildAt(k); if (v != warp && v != plan && v.getVisibility() == View.VISIBLE) { v.setVisibility(View.INVISIBLE); hidden.add(v); } } };
    warp.onOpen = () -> { for (View v : hidden) v.setVisibility(View.VISIBLE); hidden.clear(); };
    warp.onStuck = () -> { Log.w(T, "boot was stuck: finishing it"); plan.go(); };
    ear = new TextView(this); ear.setTextColor(INK); ear.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(44)); ear.setGravity(Gravity.CENTER); ear.setPadding(px(48), px(28), px(48), px(28)); GradientDrawable eb = new GradientDrawable(); eb.setColor(0xd90b0c10); eb.setCornerRadius(px(40)); eb.setStroke(px(2), 0x33ffffff); ear.setBackground(eb); ear.setMaxWidth(px(1400)); ear.setVisibility(View.GONE);
    FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); ep.bottomMargin = px(Tokens.SAFE_BOTTOM + 40); stage.addView(ear, ep); vstage = new VoiceStage(this, u, font(500), font(300)); stage.addView(vstage, new FrameLayout.LayoutParams(-1, -1));
    island = new Island(this, u, font(500), font(600), new Orbs(this, u)); FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL); il.topMargin = px(Tokens.SAFE_TOP - 10); stage.addView(island, il); top.setVisibility(View.GONE); vinyl = new Vinyl(this, u, font(500), font(600), font(300)); stage.addView(vinyl, new FrameLayout.LayoutParams(-1, -1)); listen = new Listen(this); voice = new Voice(this); plan.start(() -> parts(false), () -> sound.play("boot"), null);
    setContentView(stage);
    WindowInsetsController wc = getWindow().getInsetsController(); if (wc != null) { wc.hide(WindowInsets.Type.systemBars()); wc.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE); }

    player = new ExoPlayer.Builder(this).build(); player.setVideoSurfaceView(video); player.setVolume(0.4f); player.setRepeatMode(Player.REPEAT_MODE_ONE);
    player.addListener(new Player.Listener() {
      @Override public void onVideoSizeChanged(VideoSize s) { if (s.width > 0) { vidW = s.width; vidH = s.height; cover(1f); } }
      @Override public void onRenderedFirstFrame() { h.postDelayed(MainActivity.this::measureBars, 700); }
      @Override public void onPlayerError(PlaybackException e) { Log.w(T, "player: " + e.getErrorCodeName()); stopVideo(); }
    });
    tick(); String cached = getSharedPreferences("home", 0).getString("feed", null);
    if (cached != null) showFeed(cached);
    Api.feed(new Api.Done<String>() {
      public void ok(String s) { getSharedPreferences("home", 0).edit().putString("feed", s).apply(); if (home.isEmpty() || mode == Mode.HOME && r == 0 && rowAt().at == 0) showFeed(s); }
      public void fail(String why2) { Log.w(T, "feed: " + why2); if (home.isEmpty()) { plan.stop(); showMessage("Can't reach your catalog. Check the Wi-Fi and press OK to try again."); } }
    });
    handle(getIntent());
  }

  /** Every open gets its own feed. */
  void freshFeed() { Api.feed(new Api.Done<String>() { public void ok(String s) { getSharedPreferences("home", 0).edit().putString("feed", s).apply(); if (mode == Mode.HOME) showFeed(s); } public void fail(String why2) { Log.w(T, "feed: " + why2); } }); }

  void showMessage(String m) { veil.setBackgroundColor(ROOM); veil.setText(m); veil.setVisibility(View.VISIBLE); veil.setAlpha(1f); cue("error"); }

  void tick() {
    Calendar c = Calendar.getInstance(); int hr = c.get(Calendar.HOUR); clock.setText((hr == 0 ? 12 : hr) + ":" + String.format(Locale.US, "%02d", c.get(Calendar.MINUTE)) + (c.get(Calendar.AM_PM) == 0 ? " AM" : " PM"));
    h.postDelayed(this::tick, 15000);
  }

  void showFeed(String raw) {
    try {
      home = new ArrayList<>(); boolean heroSet = false;
      for (Api.Row a : Api.parseFeed(raw)) { Row x = new Row(); x.name = "For you tonight".equals(a.name) ? "For you " + partOfDay() : a.name; x.items = a.items; x.section = a.section; x.note = a.note; x.why = a.why; boolean cont = "Continue watching".equals(a.name); x.form = !cont && !heroSet ? COVER : TALL; if (x.form == COVER) heroSet = true; home.add(x); }
      Row more = new Row(); more.name = "More"; more.system = true; more.form = APPS;
      for (String[] a : apps()) more.items.add(Title.system(a[0], a[1], "Open " + a[0] + "."));
      ordered(more.items); /* the order you arranged the apps in */ home.add(0, more); rows = home; r = 0; mode = Mode.HOME; cur = null; veil.setBackground(veilGlass); veil.setVisibility(View.GONE); build(false); focus(0); h.postDelayed(() -> { if (greet && visible && mode == Mode.HOME) { greet = false; island.speaking(Voice.greeting()); voice.greet(() -> island.settle()); } }, 3600); /* then they press the remote's voice button: a listen started by itself never opens the remote's mic */ h.postDelayed(plan::go, 3000);
    } catch (Exception e) { Log.w(T, "bad feed", e); }
  }

  Row rowAt() { return rows.get(r); }

  void build(boolean fresh) {
    lit = null; litRow = null; prevRow = -1; rowsBox.removeAllViews(); int y = H0;
    for (int i = 0; i < rows.size(); i++) { Row x = rows.get(i); x.first = 0; if (x.section != null) y += Tokens.FORM_BREAKER; x.y = y; makeRow(x, fresh); y += x.height(); }
  }

  void makeRow(Row x, boolean fresh) {
    x.box = new FrameLayout(this); x.box.setClipChildren(false); x.cards.clear();
    x.head = type(Tokens.ROW_TITLE_SIZE, Tokens.ROW_TITLE_LINE, Tokens.ROW_TITLE_TRACKING, font(600), SOFT); x.head.setText(x.name); x.head.setSingleLine(true); x.head.setEllipsize(TextUtils.TruncateAt.END); x.head.setMaxWidth(px(900));
    LinearLayout hl = new LinearLayout(this); hl.setBaselineAligned(true); hl.addView(x.head);
    if (!x.system && !x.grid) { String why = (x.why != null && !x.why.isEmpty() ? x.why + "   ·   " : "") + x.items.size() + " titles"; TextView w2 = type(24, 32, 0, font(500), 0x8cf5f5f7); w2.setText(why); w2.setSingleLine(true); w2.setEllipsize(TextUtils.TruncateAt.END); LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(-2, -2); wl.leftMargin = px(22); hl.addView(w2, wl); }
    x.box.addView(hl, at(-2, -2, LEFT, 0));
    if (x.system) { hl.setVisibility(View.GONE); View bar = new View(this); bar.setBackground(new Widgets.Glass(px(32), 0x14ffffff, .5f, 0f)); int dw = dockWidth(x); FrameLayout.LayoutParams bp = at(px(dw + 2 * DOCK_PAD), px(DOCK + 2 * DOCK_PAD_Y), LEFT + (Tokens.GRID_CONTENT - dw) / 2 - DOCK_PAD, TRACK_Y - DOCK_PAD_Y); x.box.addView(bar, 0, bp); }
    x.track = x.form == SPOT || x.form == COVER ? new FrameLayout(this) : new LinearLayout(this) { @Override public boolean hasOverlappingRendering() { return false; } }; x.track.setClipChildren(false); x.track.setClipToPadding(false);
    x.box.addView(x.track, at(-2, -2, LEFT, TRACK_Y));
    rowsBox.addView(x.box, at(-1, px(x.height()), 0, x.y));
    if (x.section != null) {
      LinearLayout band = new LinearLayout(this); band.setOrientation(LinearLayout.VERTICAL);
      TextView big = type(30, 38, 0.08f, font(500), 0x99f5f5f7); big.setAllCaps(true); big.setText(x.section); band.addView(big);
      TextView note = type(Tokens.REASON_SM_SIZE, Tokens.REASON_SM_LINE, Tokens.REASON_SM_TRACKING, face(Tokens.REASON_SM_FAMILY, 400), SOFT); note.setText(x.note); band.addView(note);
      rowsBox.addView(band, at(px(Tokens.GRID_CONTENT), px(Tokens.FORM_BREAKER), LEFT, x.y - Tokens.FORM_BREAKER + Tokens.SPACE_S)); x.band = band;
    }
    int n = 0; for (Title t : x.items) addCard(x, t, fresh ? n++ : -1);
    if (x.form == SPOT) placeSpot(x, true); if (x.form == COVER) placeCover(x, true);
  }

  /** Adds one card. A wave of zero or more makes it rise in, later ones after earlier ones. */
  void addCard(Row x, Title t, int wave) {
    View c = card(t, x); int label = Tokens.CARD_LABEL_GAP + Tokens.CARD_LABEL_HEIGHT;
    if (x.form == COVER) { x.track.addView(c, new FrameLayout.LayoutParams(px(x.w()), px(x.h() + COVER_GAP + COVER_PLATE))); c.setPivotX(px(x.w()) / 2f); c.setPivotY(px(x.h()) * .55f); }
    else if (x.form == SPOT) { x.track.addView(c, new FrameLayout.LayoutParams(px(x.w()), px(x.h() + label))); View art = ((ViewGroup) c).getChildAt(0); art.setPivotX(0); art.setPivotY(px(x.h())); }
    else if (x.system) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(px(DOCK), px(DOCK)); p.rightMargin = px(DOCK_GAP); x.track.addView(c, p); c.setPivotX(px(DOCK) / 2f); c.setPivotY(px(DOCK));
      }
    else { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(px(x.w()), px(x.h() + label)); p.rightMargin = px(GAP); x.track.addView(c, p); }
    x.cards.add(c);
    if (wave >= 0) { c.setAlpha(0f); c.setTranslationY(px(Tokens.SPACE_XL)); long wait = 120 + wave * 60L; c.animate().alpha(1f).setStartDelay(wait).setDuration(Tokens.MOTION_FADE_MS).start(); h.postDelayed(() -> spring(c, DynamicAnimation.TRANSLATION_Y, 0, Tokens.SPRING_REVEAL_STIFFNESS, Tokens.SPRING_REVEAL_DAMPING), wait); }
  }

  /** Spotlight rows: the selected card is big and its neighbours are small, and they trade as you move. It fits one big and two small on the grid exactly. */
  float spotAt(Row x) { int step = CW + GAP, n = x.items.size(); return x.at == 0 ? 0 : x.at == n - 1 && n > 2 ? 2 * step : step; }

  void placeSpot(Row x, boolean snap) {
    int step = CW + GAP; float base = spotAt(x);
    for (int i = 0; i < x.cards.size(); i++) {
      float pos = i < x.at ? base - (x.at - i) * step : i == x.at ? base : base + x.w() + GAP + (i - x.at - 1) * step; View c = x.cards.get(i);
      if (snap) { c.setTranslationX(px(pos)); View art = ((ViewGroup) c).getChildAt(0); float k = i == x.at ? 1f : SPOT_SMALL; art.setScaleX(k); art.setScaleY(k); } else spring(c, DynamicAnimation.TRANSLATION_X, px(pos), Tokens.SPRING_MORPH_STIFFNESS, Tokens.SPRING_MORPH_DAMPING);
    }
  }

  /** Every poster stays on screen. Neighbours step back and shrink a little; the further from the selected one, the softer the blur, for depth. */
  /** The selection ring drawn on the poster itself, so nothing ever sits over it. */
  Drawable coverRing() { android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(); g.setCornerRadius(px(Tokens.CARD_RADIUS)); g.setStroke(px(4), 0xe6ffffff); g.setColor(0); return g; }
  float coverOff(int a, int w) { return coverOff(a, w, w * .5f); }
  /** Distance from the selected poster: the first neighbour tucks close, the rest are spaced by step. */
  float coverOff(int a, int w, float step) { return a == 0 ? 0 : w * .96f + (a - 1) * step; }
  void placeCover(Row x, boolean snap) {
    int w = x.w(), n = x.items.size(); float mid = Math.min(Tokens.GRID_CONTENT * .3f, coverOff(x.at, w));
    // Spread each side so its last poster reaches that edge of the screen (within limits), so neither side is left empty.
    int right = n - 1 - x.at, left = x.at;
    float stepR = right > 1 ? Math.max(w * .5f, Math.min(w * .78f, (Tokens.GRID_CONTENT - mid - w * .62f - w * .96f) / (right - 1))) : w * .6f;
    float stepL = left > 1 ? Math.max(w * .5f, Math.min(w * .78f, (mid - w * .96f + w * .2f) / (left - 1))) : w * .6f;
    for (int i = 0; i < x.cards.size(); i++) {
      View c = x.cards.get(i); int d = i - x.at, a = Math.abs(d);
      float k = a == 0 ? 1f : Math.max(.58f, .84f - .06f * (a - 1)), pos = mid + Math.signum(d) * coverOff(a, w, d > 0 ? stepR : stepL);
      float alpha = a == 0 ? 1f : Math.max(.5f, .9f - .08f * (a - 1));
      c.setTranslationZ(px(80 - a * 6)); c.setTranslationY(0); View art0 = ((ViewGroup) c).getChildAt(0); art0.setForeground(a == 0 ? coverRing() : null);
      if (android.os.Build.VERSION.SDK_INT >= 31) { int blur = 0; Object was = c.getTag(); if (was == null || (int) was != blur) { c.setTag(blur); c.setRenderEffect(blur == 0 ? null : android.graphics.RenderEffect.createBlurEffect(px(blur), px(blur), android.graphics.Shader.TileMode.DECAL)); } }
      if (snap) { c.setTranslationX(px(pos)); c.setScaleX(k); c.setScaleY(k); c.setAlpha(alpha); }
      else { spring(c, DynamicAnimation.TRANSLATION_X, px(pos), Tokens.SPRING_MORPH_STIFFNESS, Tokens.SPRING_MORPH_DAMPING); spring(c, DynamicAnimation.SCALE_X, k, Tokens.SPRING_MORPH_STIFFNESS, Tokens.SPRING_MORPH_DAMPING); spring(c, DynamicAnimation.SCALE_Y, k, Tokens.SPRING_MORPH_STIFFNESS, Tokens.SPRING_MORPH_DAMPING); c.animate().alpha(alpha).setDuration(220).start(); }
      View plate = ((ViewGroup) c).getChildAt(3); if (plate != null) plate.animate().alpha(a == 0 ? 1f : 0f).setDuration(160).start();
    }
  }

  View pill(String label) { TextView t = type(Tokens.PILL_SIZE, Tokens.PILL_LINE, Tokens.PILL_TRACKING, font(600), INK); t.setText(label); t.setGravity(Gravity.CENTER); t.setPadding(px(Tokens.PILL_PAD_X), 0, px(Tokens.PILL_PAD_X), 0); t.setBackground(new Glass.Surface(glass, t, px(Tokens.PILL_RADIUS), Tokens.GLASS_TINT)); return t; }

  /** Apps show their own TV banner, so the logo is always the real one. */
  Drawable banner(Title t) {
    if ("about".equals(t.system)) return null; if ("arrange".equals(t.system)) return arrangeBanner(); if ("vinyl".equals(t.system)) return vinylBanner(); String pkg = "google".equals(t.system) ? "com.google.android.apps.tv.launcherx" : t.system;
    try { Drawable d = getPackageManager().getApplicationBanner(pkg); return d != null ? d : getPackageManager().getApplicationIcon(pkg); } catch (Exception e) { return null; }
  }

  int dockWidth(Row x) { return x.items.size() * (DOCK + DOCK_GAP) - DOCK_GAP; }
  /** Square app icons for the dock, the way the app draws itself on a phone or Mac. */
  /** 3D glass icons, drawn here rather than copied from the apps: a smoky glass tile with a bevelled rim, and each app's mark as solid coloured glass. */
  /** The supplied glass icons, by app. Anything else gets a drawn glass icon. */
  static String iconAsset(String k) { return k.equals("arrange") ? "shuffle" : k.contains("amazonvideo") ? "prime" : k.contains("hotstar") ? "hotstar" : k.contains("stremio") ? "stremio" : k.contains("youtube") ? "youtube" : k.contains("netflix") ? "netflix" : k.contains("spotify") ? "spotify" : null; }
  static int brand(String k) { String a = iconAsset(k); if (a == null) return Tokens.COLOR_ACCENT; switch (a) { case "prime": return 0xff1e7bff; case "hotstar": return 0xff9a3cff; case "stremio": return 0xff7b5bf5; case "spotify": return 0xff1ed760; case "shuffle": return 0xffd0d0d8; default: return 0xffff2a2a; } }
  /** Loads an icon image, trims the black around its glass tile and rounds the corners so it sits cleanly on the dock. */
  Bitmap assetIcon(String name) {
    try (java.io.InputStream in = getAssets().open("icons/" + name + ".png")) {
      Bitmap src = BitmapFactory.decodeStream(in); int w = src.getWidth(), h = src.getHeight(); Bitmap small = Bitmap.createScaledBitmap(src, 128, 128, true);
      int l = 128, t = 128, r = 0, b = 0; for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) { int c = small.getPixel(x, y); if (Color.red(c) + Color.green(c) + Color.blue(c) > 90) { l = Math.min(l, x); r = Math.max(r, x); t = Math.min(t, y); b = Math.max(b, y); } }
      if (r <= l || b <= t) return src; int side = Math.max(r - l, b - t) + 1, cx = (l + r) / 2, cy = (t + b) / 2;
      Rect box = new Rect((cx - side / 2) * w / 128, (cy - side / 2) * h / 128, (cx + side / 2 + 1) * w / 128, (cy + side / 2 + 1) * h / 128);
      Bitmap out = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(out); Path clip = new Path(); clip.addRoundRect(new RectF(0, 0, 256, 256), 58, 58, Path.Direction.CW); c.clipPath(clip);
      c.drawBitmap(src, box, new Rect(0, 0, 256, 256), new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG)); return out;
    } catch (Exception e) { Log.w(T, "icon " + name + ": " + e); return null; }
  }
  Drawable icon(Title t) {
    String k = t.system == null ? "" : t.system; String asset = iconAsset(k); if (asset != null) { Bitmap bm = assetIcon(asset); if (bm != null) return new BitmapDrawable(getResources(), bm); }
    Bitmap b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(b); glassBase(c);
    if (k.equals("arrange")) markShuffle(c);
    else if (k.contains("amazonvideo")) markPrime(c);
    else if (k.contains("hotstar")) markStar(c);
    else if (k.contains("stremio")) markStremio(c);
    else if (k.contains("youtube")) markYouTube(c);
    else if (k.contains("netflix")) markNetflix(c);
    else if (k.contains("spotify")) markSpotify(c);
    else markLetter(c, t.title == null || t.title.isEmpty() ? "?" : t.title.substring(0, 1).toUpperCase(Locale.US));
    glassTop(c); return new BitmapDrawable(getResources(), b);
  }
  static final RectF TILE = new RectF(8, 8, 248, 248); static final float TR = 60;
  /** Smoky dark glass, lit from the top: body, inner shade at the bottom. */
  void glassBase(Canvas c) { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setShader(new LinearGradient(0, 8, 0, 248, 0xff3a3b42, 0xff17181c, Shader.TileMode.CLAMP)); c.drawRoundRect(TILE, TR, TR, p);
    p.setShader(new RadialGradient(80, 40, 220, 0x33ffffff, 0x00ffffff, Shader.TileMode.CLAMP)); c.drawRoundRect(TILE, TR, TR, p);
    p.setShader(new LinearGradient(0, 170, 0, 248, 0x00000000, 0x59000000, Shader.TileMode.CLAMP)); c.drawRoundRect(TILE, TR, TR, p); }
  /** Bevelled rim and the crisp highlight across the top, drawn over the mark. */
  void glassTop(Canvas c) { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(5); p.setShader(new LinearGradient(8, 8, 248, 248, new int[]{0xe6ffffff, 0x4dffffff, 0x1affffff, 0x66ffffff}, null, Shader.TileMode.CLAMP)); RectF r = new RectF(TILE); r.inset(2.5f, 2.5f); c.drawRoundRect(r, TR - 2, TR - 2, p);
    p.setStrokeWidth(2); p.setShader(null); p.setColor(0x33000000); RectF in = new RectF(TILE); in.inset(8, 8); c.drawRoundRect(in, TR - 8, TR - 8, p);
    Path sheen = new Path(); sheen.addRoundRect(new RectF(22, 18, 234, 92), 40, 40, Path.Direction.CW); Paint s = new Paint(Paint.ANTI_ALIAS_FLAG); s.setShader(new LinearGradient(0, 18, 0, 92, 0x26ffffff, 0x00ffffff, Shader.TileMode.CLAMP)); c.drawPath(sheen, s); }
  /** A shape as coloured glass: crisp shadow, colour body, top highlight, darker base and a lit edge. */
  void glass(Canvas c, Path shape, int color) {
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); RectF b = new RectF(); shape.computeBounds(b, true);
    Paint sh = new Paint(Paint.ANTI_ALIAS_FLAG); sh.setColor(0x59000000); c.save(); c.translate(0, 7); c.drawPath(shape, sh); c.restore(); p.setColor(0xff000000);
    p.setShader(new LinearGradient(0, b.top, 0, b.bottom, lighter(color), color, Shader.TileMode.CLAMP)); c.drawPath(shape, p);
    c.save(); c.clipPath(shape); p.setShader(new LinearGradient(0, b.top, 0, b.centerY(), 0x73ffffff, 0x00ffffff, Shader.TileMode.CLAMP)); c.drawRect(b.left, b.top, b.right, b.centerY(), p);
    p.setShader(new LinearGradient(0, b.centerY(), 0, b.bottom, 0x00000000, 0x33000000, Shader.TileMode.CLAMP)); c.drawRect(b.left, b.centerY(), b.right, b.bottom, p); c.restore();
    p.setShader(new LinearGradient(0, b.top, 0, b.bottom, 0xb3ffffff, 0x26ffffff, Shader.TileMode.CLAMP)); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.5f); c.drawPath(shape, p); }
  static int lighter(int c) { return Color.rgb(Math.min(255, Color.red(c) + 50), Math.min(255, Color.green(c) + 50), Math.min(255, Color.blue(c) + 50)); }
  Path rr(float l, float t, float r, float b, float rad) { Path p = new Path(); p.addRoundRect(new RectF(l, t, r, b), rad, rad, Path.Direction.CW); return p; }
  void markYouTube(Canvas c) { glass(c, rr(52, 78, 204, 178, 34), 0xffe62117); Path tri = new Path(); tri.moveTo(112, 104); tri.lineTo(156, 128); tri.lineTo(112, 152); tri.close(); glass(c, tri, 0xfff2f2f4); }
  void markNetflix(Canvas c) { Path l = rr(80, 56, 112, 200, 6), r = rr(144, 56, 176, 200, 6); glass(c, l, 0xffb20710); glass(c, r, 0xffb20710);
    Path d = new Path(); d.moveTo(80, 56); d.lineTo(112, 56); d.lineTo(176, 200); d.lineTo(144, 200); d.close(); glass(c, d, 0xffe50914); }
  void markSpotify(Canvas c) { Path disc = new Path(); disc.addCircle(128, 128, 80, Path.Direction.CW); glass(c, disc, 0xff1ed760);
    Paint a = new Paint(Paint.ANTI_ALIAS_FLAG); a.setStyle(Paint.Style.STROKE); a.setStrokeCap(Paint.Cap.ROUND); a.setColor(0xd9093a1c);
    float[][] arcs = {{88, 96, 14}, {96, 124, 12}, {104, 150, 10}}; for (float[] q : arcs) { a.setStrokeWidth(q[2]); RectF o = new RectF(q[0] - 8, q[1], 256 - q[0] + 8, q[1] + 70); c.drawArc(o, 205, 130, false, a); } }
  void markStar(Canvas c) { Path st = new Path(); float cx = 128, cy = 128, R = 84, r = 18; st.moveTo(cx, cy - R);
    for (int i = 0; i < 4; i++) { double a = Math.toRadians(-90 + i * 90), m = Math.toRadians(-45 + i * 90), n = Math.toRadians(i * 90); st.quadTo(cx + (float) Math.cos(m) * r, cy + (float) Math.sin(m) * r, cx + (float) Math.cos(n) * R, cy + (float) Math.sin(n) * R); }
    st.close(); glass(c, st, 0xff4f6bff); Path small = new Path(); small.addCircle(176, 82, 9, Path.Direction.CW); glass(c, small, 0xffb9c4ff); }
  void markStremio(Canvas c) { Path d = new Path(); d.moveTo(128, 46); d.lineTo(210, 128); d.lineTo(128, 210); d.lineTo(46, 128); d.close(); glass(c, d, 0xff7b5bf5);
    Path tri = new Path(); tri.moveTo(112, 98); tri.lineTo(162, 128); tri.lineTo(112, 158); tri.close(); glass(c, tri, 0xfff2f2f4); }
  void markPrime(Canvas c) { Paint t = new Paint(Paint.ANTI_ALIAS_FLAG); t.setTypeface(font(700)); t.setTextSize(60); t.setTextAlign(Paint.Align.CENTER);
    Path w1 = new Path(), w2 = new Path(); t.getTextPath("prime", 0, 5, 128, 112, w1); t.getTextPath("video", 0, 5, 128, 166, w2); glass(c, w1, 0xffd8dde6); glass(c, w2, 0xffd8dde6);
    Path smile = new Path(); smile.moveTo(66, 186); smile.quadTo(128, 214, 186, 186); smile.lineTo(188, 194); smile.quadTo(128, 224, 64, 194); smile.close(); glass(c, smile, 0xff3aa7ff);
    Path tip = new Path(); tip.moveTo(176, 176); tip.lineTo(198, 184); tip.lineTo(186, 202); tip.close(); glass(c, tip, 0xff3aa7ff); }
  void markShuffle(Canvas c) { Paint l = new Paint(Paint.ANTI_ALIAS_FLAG); l.setStyle(Paint.Style.STROKE); l.setStrokeWidth(20); l.setStrokeCap(Paint.Cap.ROUND); l.setStrokeJoin(Paint.Join.ROUND);
    Path a = new Path(); a.moveTo(64, 92); a.lineTo(96, 92); a.cubicTo(130, 92, 130, 164, 164, 164); a.lineTo(186, 164);
    Path z = new Path(); z.moveTo(64, 164); z.lineTo(96, 164); z.cubicTo(130, 164, 130, 92, 164, 92); z.lineTo(186, 92);
    Path h1 = new Path(); h1.moveTo(170, 72); h1.lineTo(192, 92); h1.lineTo(170, 112); Path h2 = new Path(); h2.moveTo(170, 144); h2.lineTo(192, 164); h2.lineTo(170, 184);
    Path all = new Path(); for (Path q : new Path[]{a, z, h1, h2}) { Path o = new Path(); l.getFillPath(q, o); all.op(o, Path.Op.UNION); } glass(c, all, 0xffe8e8ec); }
  void markLetter(Canvas c, String ch) { Paint t = new Paint(Paint.ANTI_ALIAS_FLAG); t.setTypeface(font(700)); t.setTextSize(140); t.setTextAlign(Paint.Align.CENTER); Path p = new Path(); t.getTextPath(ch, 0, 1, 128, 176, p); glass(c, p, Tokens.COLOR_ACCENT); }
  View card(Title t, Row x) {
    int w = x.w(), hgt = x.h(); FrameLayout c = new FrameLayout(this); c.setClipChildren(false);
    ImageView i = new ImageView(this); i.setScaleType(ImageView.ScaleType.CENTER_CROP); i.setBackground(PixelIcon.placeholder(px(14))); round(i, Tokens.CARD_RADIUS);
    c.addView(i, new FrameLayout.LayoutParams(px(w), px(hgt)));
    TextView s = type(Tokens.CARD_LABEL_SIZE, Tokens.CARD_LABEL_LINE, Tokens.CARD_LABEL_TRACKING, face(Tokens.CARD_LABEL_FAMILY, 400), SOFT); s.setAllCaps(false); s.setText(x.system ? "" : t.under()); s.setSingleLine(true); s.setVisibility(View.GONE); FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(px(w), -2); p.topMargin = px(hgt + Tokens.CARD_LABEL_GAP); c.addView(s, p);
    TextView plain = type(Tokens.REASON_SM_SIZE, Tokens.REASON_SM_LINE, Tokens.REASON_SM_TRACKING, face(Tokens.REASON_SM_FAMILY, 400), INK); plain.setText(t.title); clamp(plain, Tokens.CLAMP_TITLE); plain.setVisibility(View.GONE); FrameLayout.LayoutParams q = new FrameLayout.LayoutParams(px(w - 2 * Tokens.SPACE_S), -2, Gravity.BOTTOM); q.leftMargin = px(Tokens.SPACE_S); q.bottomMargin = px(Tokens.CARD_LABEL_GAP + Tokens.CARD_LABEL_HEIGHT + Tokens.SPACE_S); c.addView(plain, q);
    if (x.system) { i.setVisibility(View.GONE); String app = iconAsset(t.system == null ? "" : t.system); PixelIcon pi = new PixelIcon(this, app == null ? (t.title == null ? "" : t.title.toLowerCase(Locale.US)) : app); pi.setTag("px"); c.addView(pi, new FrameLayout.LayoutParams(px(w), px(hgt))); return c; }
    // the title already sits at the top, so no caption under the poster
    if (t.saved || t.seen) { View mark = new View(this); mark.setBackground(new Badge(t.saved ? Badge.SAVED : Badge.SEEN, t.saved ? Tokens.COLOR_ACCENT : 0xcc15161c)); mark.setElevation(px(14));   // saved: bookmark, seen: tick
      FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(px(40), px(40)); hl.leftMargin = px(w - 52); hl.topMargin = px(12); c.addView(mark, hl); }
    else if (t.progress >= 0) { final float f = t.progress; View bar = new View(this) { final Paint a1 = new Paint(Paint.ANTI_ALIAS_FLAG), a2 = new Paint(Paint.ANTI_ALIAS_FLAG); { a1.setColor(0x59000000); a2.setColor(Tokens.COLOR_ACCENT); }
        @Override protected void onDraw(Canvas cv) { float w2 = getWidth(), h2 = getHeight(); cv.drawRoundRect(0, 0, w2, h2, h2 / 2, h2 / 2, a1); cv.drawRoundRect(0, 0, Math.max(h2, w2 * f), h2, h2 / 2, h2 / 2, a2); } };
      FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(px(w - 32), px(7)); bl.leftMargin = px(16); bl.topMargin = px(hgt - 22); c.addView(bar, bl); bar.setElevation(px(12)); }
    String flat = t.poster != null ? t.poster : t.backdrop, art = (x.form == TALL || x.form == COVER) && t.tall() != null ? t.tall() : flat;
    if (art == null) plain.setVisibility(View.VISIBLE);
    else Glide.with(this).load(art).override(px(w), px(hgt)).format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565).dontAnimate().error(Glide.with(this).load(flat).override(px(w), px(hgt))).listener(new RequestListener<Drawable>() {
      public boolean onLoadFailed(GlideException e, Object m, Target<Drawable> z, boolean f) { if (flat == null) plain.setVisibility(View.VISIBLE); return false; }
      public boolean onResourceReady(Drawable d, Object m, Target<Drawable> z, DataSource ds, boolean f) { return false; }
    }).into(i);
    return c;
  }

  /** The frosted caption under a cover-flow poster: title, then year and IMDb rating. */
  void plate(FrameLayout c, Title t, int w, int hgt) {
    LinearLayout p = new LinearLayout(this); p.setOrientation(LinearLayout.VERTICAL); p.setGravity(Gravity.CENTER); p.setBackground(new Widgets.Glass(px(20), 0x22ffffff, .5f, 0f)); p.setPadding(px(16), px(8), px(16), px(8));
    TextView a = new TextView(this); a.setTypeface(font(600)); a.setTextColor(INK); a.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(27)); a.setSingleLine(true); a.setEllipsize(TextUtils.TruncateAt.END); a.setGravity(Gravity.CENTER); a.setText(t.title); a.setIncludeFontPadding(false); p.addView(a, new LinearLayout.LayoutParams(-1, -2));
    if (t.progress >= 0) { final float f = t.progress; View bar = new View(this) { final Paint a1 = new Paint(Paint.ANTI_ALIAS_FLAG), a2 = new Paint(Paint.ANTI_ALIAS_FLAG); { a1.setColor(0x33ffffff); a2.setColor(Tokens.COLOR_ACCENT); }
        @Override protected void onDraw(Canvas cv) { float w2 = getWidth(), h2 = getHeight(); cv.drawRoundRect(0, 0, w2, h2, h2 / 2, h2 / 2, a1); cv.drawRoundRect(0, 0, Math.max(h2, w2 * f), h2, h2 / 2, h2 / 2, a2); } };
      LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(-1, px(6)); pb.topMargin = px(10); pb.leftMargin = px(14); pb.rightMargin = px(14); p.addView(bar, pb); }
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(w), px(COVER_PLATE)); lp.topMargin = px(hgt + COVER_GAP); c.addView(p, lp);
  }

  void liftPill(View c, boolean on) {
    float k = on ? Tokens.PILL_FOCUS_SCALE : 1f, st = on ? Tokens.SPRING_FOCUS_STIFFNESS : Tokens.SPRING_UNFOCUS_STIFFNESS, da = on ? Tokens.SPRING_FOCUS_DAMPING : Tokens.SPRING_UNFOCUS_DAMPING; spring(c, DynamicAnimation.SCALE_X, k, st, da); spring(c, DynamicAnimation.SCALE_Y, k, st, da);
    c.setElevation(on ? px(Tokens.SPACE_S) : 0); ((Glass.Surface) c.getBackground()).clear = on; c.invalidate();
  }

  /** Mac dock magnification: the selected icon grows and lifts, its neighbours a little. */
  void magnify(Row x) { for (int i = 0; i < x.cards.size(); i++) { int a = Math.abs(i - x.at); float k = topFocus ? 1f : a == 0 ? 1.5f : 1f; View c = x.cards.get(i);
      spring(c, DynamicAnimation.SCALE_X, k, 520f, .62f); spring(c, DynamicAnimation.SCALE_Y, k, 520f, .62f); spring(c, DynamicAnimation.TRANSLATION_Y, a == 0 && !topFocus ? -px(8) : 0, 520f, .62f); c.setTranslationZ(px(a == 0 ? 30 : 0));
      View pi = c.findViewWithTag("px"); if (pi instanceof PixelIcon) ((PixelIcon) pi).select(a == 0 && !topFocus); } }
  void lift(View c, boolean on, Row x) {
    if (x.system) { if (on) magnify(x); return; }
    if (x.form == COVER) { ImageView ca = (ImageView) ((ViewGroup) c).getChildAt(0); if (on) { ca.setForeground(sheen); sheen.sweep(Tokens.MOTION_FOCUS_MS * 2); } else if (ca.getForeground() == sheen) ca.setForeground(null); return; }
    ImageView art = (ImageView) ((ViewGroup) c).getChildAt(0); boolean spot = x.form == SPOT; float k = spot ? (on ? 1f : SPOT_SMALL) : on ? Tokens.CARD_FOCUS_SCALE : 1f;
    float st = spot ? Tokens.SPRING_MORPH_STIFFNESS : on ? Tokens.SPRING_FOCUS_STIFFNESS : Tokens.SPRING_UNFOCUS_STIFFNESS, da = spot ? Tokens.SPRING_MORPH_DAMPING : on ? Tokens.SPRING_FOCUS_DAMPING : Tokens.SPRING_UNFOCUS_DAMPING; spring(art, DynamicAnimation.SCALE_X, k, st, da); spring(art, DynamicAnimation.SCALE_Y, k, st, da);
    art.setElevation(on ? px(Tokens.SPACE_S) : 0);
    View label = ((ViewGroup) c).getChildAt(1); ((TextView) label).setTextColor(on ? INK : SOFT); label.setVisibility(View.GONE); label.setTranslationY(on && !spot ? px(x.h() * (Tokens.CARD_FOCUS_SCALE - 1f) / 2f) : 0);
    if (on) { art.setForeground(sheen); sheen.sweep(Tokens.MOTION_FOCUS_MS * 2); } else if (art.getForeground() == sheen) art.setForeground(null);
  }

  View lit; int prevRow = -1; boolean posture;

  void focus(int dx) {
    wake(); homeExtras();
    Row now = rowAt();
    if (r != prevRow) {
      for (int i = 0; i < rows.size(); i++) {
        Row x = rows.get(i); boolean on = i == r; x.head.setTextColor(on ? INK : SOFT); float a = i < r ? 0f : (r == 0 && i > 0 && mode == Mode.HOME) ? 0f : 1f; if (x.box.getAlpha() != a) x.box.animate().alpha(a).setDuration(220).start(); x.track.setAlpha(on ? 1f : .46f); if (!x.system && x.form != COVER) for (View k : x.cards) ((ViewGroup) k).getChildAt(1).setVisibility(View.GONE);
        if (x.band != null) { x.band.animate().alpha(i > r ? 1f : 0f).setDuration(Tokens.MOTION_FOCUS_MS).start(); }
      }
      prevRow = r;
    }
    View c = now.cards.get(now.at);
    if (c != lit) { if (lit != null && (litRow == now || litRow.form != SPOT)) lift(lit, false, litRow); lift(c, true, now); lit = c; litRow = now; }
    if (dx != 0) travel = dx;
    if (now.form == SPOT) placeSpot(now, false); else if (now.form == COVER) placeCover(now, false); else if (now.system) { slide(now.track, DynamicAnimation.TRANSLATION_X, px((Tokens.GRID_CONTENT - dockWidth(now)) / 2f)); magnify(now); } else slide(now.track, DynamicAnimation.TRANSLATION_X, -px(rowScroll(now)));
    blurRoom(now.form == COVER);
    moveLens(now);
    boolean deep = deep();
    slide(rowsBox, DynamicAnimation.TRANSLATION_Y, rowY());
    if (deep != posture) {
      posture = deep; slide(info, DynamicAnimation.TRANSLATION_Y, deep ? -px(INFO_SHIFT) : 0);
      float k = deep ? (float) Tokens.LOGO_BROWSE_HEIGHT / Tokens.LOGO_HOME_HEIGHT : 1f; logo.setPivotX(0); logo.setPivotY(logo.getHeight()); slide(logo, DynamicAnimation.SCALE_X, k); slide(logo, DynamicAnimation.SCALE_Y, k);
      if (deep) { why.setTypeface(face(Tokens.REASON_SM_FAMILY, 400)); name.setTypeface(face(Tokens.TITLE_SM_FAMILY, 400)); fit(why, Tokens.REASON_SM_SIZE, Tokens.REASON_SM_LINE, Tokens.REASON_SM_TRACKING); fit(name, Tokens.TITLE_SM_SIZE, Tokens.TITLE_SM_LINE, Tokens.TITLE_SM_TRACKING); } else { why.setTypeface(face(Tokens.REASON_FAMILY, 400)); name.setTypeface(face(Tokens.TITLE_FAMILY, 400)); fit(why, Tokens.REASON_SIZE, Tokens.REASON_LINE, Tokens.REASON_TRACKING); fit(name, Tokens.TITLE_SIZE, Tokens.TITLE_LINE, Tokens.TITLE_TRACKING); }
      why.setMaxWidth(px(deep ? Tokens.GRID_CONTENT : Tokens.PANEL_TEXT_WIDTH)); clamp(why, deep ? Tokens.CLAMP_REASON_BROWSE : Tokens.CLAMP_REASON); clamp(name, deep ? Tokens.CLAMP_TITLE_BROWSE : Tokens.CLAMP_TITLE);
    }
    rack(deep && !videoOn);
    float d = now.system ? .84f : deep ? .46f : 0f; if (dim.getAlpha() != d) { dim.setVisibility(View.VISIBLE); dim.animate().alpha(d).setDuration(300).withEndAction(() -> { if (dim.getAlpha() == 0f) dim.setVisibility(View.GONE); }).start(); }
    Title t = now.items.get(now.at);
    if (t != cur) { cur = t; h.removeCallbacks(showNext); showNext = () -> { if (cur == t) show(t); }; h.postDelayed(showNext, 140); }
    List<Title> it = now.items; int at = now.at;
    h.postDelayed(() -> { if (cur == t) for (int k = 1; k <= 2 && at + k < it.size(); k++) Trailer.prefetch(it.get(at + k).trailer); }, 1400);
  }

  Row litRow; Runnable showNext = () -> { };

  /** Rows ride high while browsing, and results always do, so the next rows show below. */
  boolean deep() { return r > 0 || mode == Mode.RESULT; }

  float rowY() { return deep() ? -px(rowAt().y - Tokens.BROWSE_ROW_TITLE_Y) : 0; }

  /** The focused card rides the left margin until the row's end reaches the right margin, then focus walks to it. */
  int rowScroll(Row x) {
    int step = x.w() + GAP, n = x.items.size(), vis = x.cols();
    if (x.at < x.first) x.first = x.at; if (x.at > x.first + vis - 1) x.first = x.at - vis + 1;
    return Math.min(x.first * step, Math.max(0, n * step - GAP - Tokens.GRID_CONTENT));
  }

  /** Where the selection belongs, worked out from the grid so it never chases a moving view. */
  void moveLens(Row x) {
    if (x.system) { lens.animate().alpha(0f).setDuration(120).start(); return; } else if (!topFocus && x.form != COVER && lens.getAlpha() < 1f) lens.animate().alpha(1f).setDuration(160).start();
    float top = (deep() ? Tokens.BROWSE_ROW_TITLE_Y : H0) + TRACK_Y, e = Tokens.LENS_EDGE; int w = x.w(), hgt = x.h();
    if (x.form == COVER) { lens.animate().alpha(0f).setDuration(120).start(); if (true) return; float left = LEFT + Math.min(Tokens.GRID_CONTENT * .3f, coverOff(x.at, w)); lens.to(new RectF(px(left - e), px(top - e), px(left + w + e), px(top + hgt + e)), px(Tokens.CARD_RADIUS + e)); return; }
    if (x.form == SPOT) { float left = LEFT + spotAt(x); lens.to(new RectF(px(left - e), px(top - e), px(left + w + e), px(top + hgt + e)), px(Tokens.CARD_RADIUS + e)); return; }
    float left = LEFT + x.at * (w + GAP) - rowScroll(x), gx = w * (Tokens.CARD_FOCUS_SCALE - 1) / 2 + e, gy = hgt * (Tokens.CARD_FOCUS_SCALE - 1) / 2 + e;
    lens.to(new RectF(px(left - gx), px(top - gy), px(left + w + gx), px(top + hgt + gy)), px(Tokens.CARD_RADIUS * Tokens.CARD_FOCUS_SCALE + e));
  }

  void moveBtnLens(boolean snap) { View c = btns.getChildAt(btn); if (c == null) return; if (c.getWidth() == 0) { c.post(() -> moveBtnLens(snap)); return; } float gw = c.getWidth() * (Tokens.PILL_FOCUS_SCALE - 1) / 2, gh = c.getHeight() * (Tokens.PILL_FOCUS_SCALE - 1) / 2; RectF q = new RectF(c.getLeft() - gw, c.getTop() - gh, c.getRight() + gw, c.getBottom() + gh); if (snap) btnLens.snap(q, px(Tokens.PILL_RADIUS * Tokens.PILL_FOCUS_SCALE)); else btnLens.to(q, px(Tokens.PILL_RADIUS * Tokens.PILL_FOCUS_SCALE)); }

  static Bitmap bits(Drawable d) { return d instanceof BitmapDrawable ? ((BitmapDrawable) d).getBitmap() : null; }

  /** The focused card grows until it is the screen. The rest of the page steps back as it does. */
  boolean grow(Runnable then) {
    Row x = rowAt(); if (x.system || mode == Mode.DETAIL || x.at >= x.cards.size()) return false;
    ImageView art = (ImageView) ((ViewGroup) x.cards.get(x.at)).getChildAt(0); Bitmap a = bits(art.getDrawable()); if (a == null) return false;
    int[] at = new int[2]; art.getLocationOnScreen(at); float w = art.getWidth() * art.getScaleX(), hh = art.getHeight() * art.getScaleY();
    quiet(); morphRect = new RectF(at[0], at[1], at[0] + w, at[1] + hh); morphed = art; art.setAlpha(0f);
    for (View v : new View[]{rowsBox, info, lens, dock}) v.animate().alpha(0f).setDuration(Tokens.MOTION_FOCUS_MS).start();
    morph.open(a, bits((flip ? bgA : bgB).getDrawable()), morphRect, px(Tokens.CARD_RADIUS * Tokens.CARD_FOCUS_SCALE), then); cue("select"); return true;
  }

  /** And back: it shrinks into the exact place it came from. */
  void shrink(Runnable then) {
    for (View v : new View[]{rowsBox, info, lens, dock}) { v.setVisibility(View.VISIBLE); v.animate().alpha(1f).setDuration(Tokens.MOTION_FADE_MS).start(); }
    ImageView art = morphed; morph.close(morphRect, () -> { if (art != null) art.setAlpha(1f); morphed = null; if (then != null) then.run(); });
  }

  /** Nowhere further to go, so the row gives a little and springs back. */
  void lean(float dx, float dy) {
    float v = Tokens.LENS_NUDGE * u; lens.lean(dx * v, dy * v); Row x = rowAt();
    if (dx != 0 && !x.system) { Map<DynamicAnimation.ViewProperty, SpringAnimation> m = springs.get(x.track); SpringAnimation a = m == null ? null : m.get(DynamicAnimation.TRANSLATION_X); if (a != null) { a.setStartVelocity(-dx * v); a.animateToFinalPosition(a.getSpring().getFinalPosition()); } }
    if (dy != 0) { Map<DynamicAnimation.ViewProperty, SpringAnimation> m = springs.get(rowsBox); SpringAnimation a = m == null ? null : m.get(DynamicAnimation.TRANSLATION_Y); if (a != null) { a.setStartVelocity(-dy * v); a.animateToFinalPosition(a.getSpring().getFinalPosition()); } }
  }


  /** The live screen, as blueprint parts in reading order. */
  List<Blueprint.Part> parts(boolean brief) {
    List<Blueprint.Part> out = new ArrayList<>(); int[] at = new int[2];
    if (!brief) { top.getLocationOnScreen(at); out.add(new Blueprint.Part(new RectF(at[0], at[1], at[0] + top.getWidth(), at[1] + top.getHeight()), "CHIP", px(Tokens.CHIP_RADIUS), false)); }
    if (logo.getVisibility() == View.VISIBLE && logo.getDrawable() != null) { logo.getLocationOnScreen(at); out.add(new Blueprint.Part(new RectF(at[0], at[1], at[0] + logo.getWidth(), at[1] + logo.getHeight()), "LOGO", 0, false)); }
    for (TextView t : new TextView[]{name, meta, why, where}) textParts(t, out);
    for (Row x : rows) {
      x.head.getLocationOnScreen(at); if (x.box.getAlpha() == 0f || at[1] > stage.getHeight()) continue; textParts(x.head, out);
      for (View c : x.cards) { View a = ((ViewGroup) c).getChildAt(0); a.getLocationOnScreen(at); float w = a.getWidth() * a.getScaleX(), hh = a.getHeight() * a.getScaleY(); if (at[0] > stage.getWidth() || at[0] + w < 0 || at[1] > stage.getHeight()) continue; out.add(new Blueprint.Part(new RectF(at[0], at[1], at[0] + w, at[1] + hh), x.system ? "APP" : "CARD", px(Tokens.CARD_RADIUS), false)); }
    }
    return out;
  }

  void textParts(TextView t, List<Blueprint.Part> out) {
    Layout l = t.getLayout(); if (l == null || t.getVisibility() != View.VISIBLE || t.length() == 0) return; int[] at = new int[2]; t.getLocationOnScreen(at);
    for (int i = 0; i < l.getLineCount(); i++) out.add(new Blueprint.Part(new RectF(at[0] + l.getLineLeft(i), at[1] + l.getLineTop(i), at[0] + l.getLineRight(i), at[1] + l.getLineBottom(i)), null, 0, true));
  }

  /** Runs the whole power-on sequence again when the TV has been away from this screen for a while. */
  void reboot() { Log.i(T, "splash"); stoppedAt = 0; goHome(); freshFeed(); plan.start(() -> parts(false), () -> sound.play("boot"), null); h.postDelayed(plan::go, 3000); }

  /** Rack focus: the picture goes soft while you browse and comes sharp again when a trailer takes over. */
  void rack(boolean on) {
    float to = on ? 1f : 0f; if (soft.getAlpha() == to && (on || soft.getVisibility() == View.GONE)) return;
    soft.setVisibility(View.VISIBLE); soft.animate().cancel(); soft.animate().alpha(to).setDuration(Tokens.MOTION_FADE_MS).withEndAction(() -> { if (!on) soft.setVisibility(View.GONE); }).start();
  }

  /** While a trailer plays, the glass follows it at low resolution. */
  final Bitmap[] frame = new Bitmap[1];
  final Runnable pump = new Runnable() { public void run() {
    if (!videoOn || !player.isPlaying()) return;
    if (frame[0] == null) frame[0] = Bitmap.createBitmap(Glass.W, Glass.H, Bitmap.Config.ARGB_8888);
    try { PixelCopy.request(video, frame[0], res -> { if (res == PixelCopy.SUCCESS) glass.feed(frame[0], 0); }, h); } catch (Exception ignored) { }
    h.postDelayed(this, 500);
  } };

  void feedGlass(String url) {
    if (small != null) Glide.with(this).clear(small);
    small = new com.bumptech.glide.request.target.CustomTarget<Bitmap>(Glass.W, Glass.H) {
      @Override public void onResourceReady(Bitmap b, com.bumptech.glide.request.transition.Transition<? super Bitmap> x) { glass.feed(b, Tokens.MOTION_FADE_MS); h.postDelayed(plan::go, 500); }
      @Override public void onLoadCleared(Drawable d) { }
    };
    Glide.with(this).asBitmap().load(url).centerCrop().into(small);
  }

  void show(Title t) {
    stopVideo();
    boolean sys = t.system != null;
    name.setMaxWidth(px(Tokens.PANEL_TEXT_WIDTH));
    why.setAllCaps(false); why.setTextColor(INK); if (!sys && !deep()) { fit(name, Tokens.TITLE_SIZE, Tokens.TITLE_LINE, Tokens.TITLE_TRACKING); fit(why, Tokens.REASON_SIZE, Tokens.REASON_LINE, Tokens.REASON_TRACKING); }
    Glide.with(this).clear(logo); logo.setVisibility(View.GONE); name.setVisibility(View.GONE); logo.clearColorFilter();
    meta.setVisibility(sys ? View.GONE : View.VISIBLE); where.setVisibility(sys ? View.GONE : View.VISIBLE);
    if (sys) { fit(name, 58, 68, -0.01f); fit(why, 22, 30, 0.12f); why.setAllCaps(true); why.setTextColor(0x8cf5f5f7); name.setText("What would you like\nto watch " + partOfDay() + "?"); name.setMaxLines(2); name.setSingleLine(false); name.setMaxWidth(px(760)); name.setVisibility(View.VISIBLE); why.setText("Press \u2630 and just say it"); h.postDelayed(this::ambient, 180); return; }
    name.setText(t.title); name.setVisibility(View.VISIBLE); logo.setImageDrawable(null); if (t.logo != null) Glide.with(this).load(t.logo).override(px(Tokens.LOGO_HOME_WIDTH), px(Tokens.LOGO_HOME_HEIGHT)).fitCenter().listener(logoCheck(logo)).into(logo);
    meta.setText(t.meta()); why.setText(t.why != null ? t.why : shortText(t.overview)); info.setAlpha(0f); info.animate().alpha(1f).setDuration(Tokens.MOTION_FOCUS_MS).start(); info.setTranslationX(px(Tokens.SPACE_L) * travel); slide(info, DynamicAnimation.TRANSLATION_X, 0);
    SpannableStringBuilder s = new SpannableStringBuilder();
    if (t.subscribed) { s.append("On "); int a = s.length(); s.append(t.serviceName()); s.setSpan(new ForegroundColorSpan(INK), a, s.length(), 0); if (t.leavingAt > 0) s.append(" · ").append(t.under()); }
    else { s.append("Not on your services · Opens in "); int a = s.length(); s.append("Stremio"); s.setSpan(new ForegroundColorSpan(INK), a, s.length(), 0); }
    if (t.note != null) s.append(" · ").append(t.note);
    { int a0 = s.length(); s.append("     ").append(t.saved ? "Saved for later" : t.seen ? "\u2713 Seen" : "Hold OK to save or mark seen"); s.setSpan(new ForegroundColorSpan(t.saved ? Tokens.COLOR_ACCENT : 0x80f5f5f7), a0, s.length(), 0); }
    where.setText(s);
    flip = !flip; ImageView in = flip ? bgA : bgB, out = flip ? bgB : bgA;
    in.bringToFront(); stageOrder();
    Glide.with(this).load(t.backdrop).override(px(1280), px(720)).format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565).centerCrop().dontAnimate().into(in);
    feedGlass(t.backdrop);
    in.animate().cancel(); out.animate().cancel(); in.setVisibility(View.VISIBLE); in.setAlpha(0f); in.animate().alpha(1f).setDuration(Tokens.MOTION_FADE_MS).start(); out.animate().alpha(0f).setDuration(Tokens.MOTION_FADE_MS).withEndAction(() -> out.setVisibility(View.INVISIBLE)).start();
    if (t.trailer != null) { dwell = () -> { if (cur == t) playTrailer(t); }; h.postDelayed(dwell, Tokens.MOTION_DWELL_MS + (plan.active() ? Tokens.BOOT_RING_MS + Tokens.BOOT_FIELD_MS + Tokens.BOOT_MAP_MS + Tokens.BOOT_SCAN_MS : 0)); }
  }

  void stageOrder() { for (View v : new View[]{stage.getChildAt(0)}) { } List<View> keep = new ArrayList<>(); for (int i = 0; i < stage.getChildCount(); i++) { View v = stage.getChildAt(i); if (v != video && v != bgA && v != bgB) keep.add(v); } for (View v : keep) v.bringToFront(); }

  String shortText(String o) { if (o == null) return ""; if (o.length() <= 96) return o; String c = o.substring(0, 96); int i = c.lastIndexOf(' '); return (i > 0 ? c.substring(0, i) : c) + "…"; }

  RequestListener<Drawable> logoCheck(ImageView v) {
    return new RequestListener<Drawable>() {
      public boolean onLoadFailed(GlideException e, Object m, Target<Drawable> t, boolean f) { Log.w(T, "logo failed " + m); return false; }
      public boolean onResourceReady(Drawable d, Object m, Target<Drawable> t, DataSource s, boolean f) {
        if (v == logo) { logo.setVisibility(View.VISIBLE); name.setVisibility(View.GONE); }
        Log.i(T, "logo ok " + d.getIntrinsicWidth() + "x" + d.getIntrinsicHeight() + " view " + v.getWidth() + "x" + v.getHeight()); try { Bitmap b = Bitmap.createBitmap(48, 24, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(b); Drawable k = d.getConstantState().newDrawable().mutate(); k.setBounds(0, 0, 48, 24); k.draw(c); long sum = 0; int n = 0;
          for (int y = 0; y < 24; y++) for (int x = 0; x < 48; x++) { int p = b.getPixel(x, y); if (Color.alpha(p) > 120) { sum += Math.max(Color.red(p), Math.max(Color.green(p), Color.blue(p))); n++; } }
          if (n > 0 && sum / n < 70) v.setColorFilter(INK, PorterDuff.Mode.SRC_IN);
        } catch (Exception ignored) { }
        return false;
      }
    };
  }

  /** Every request for a trailer gets a number. Leaving changes the number, so one that arrives late can't start playing behind another app. */
  int trailerRun; boolean visible; boolean choosing; long lastDockMove; boolean quietMiss; static volatile boolean shown; Voice voice; boolean greet = true; static volatile long leftAt; View more; ImageView glow; Widgets dots; VoiceStage vstage; Island island; Vinyl vinyl; boolean mediaFocus; long searchStart; boolean topFocus; Listen listen; TextView ear; boolean arranging;

  void quiet() { trailerRun++; player.setVolume(0f); }

  /** No trailer while the TV is listening, thinking or talking. */
  boolean quietForVoice() { return (listen != null && listen.active()) || (island != null && island.busy()) || (voice != null && voice.speaking()); }
  void playTrailer(Title t) {
    if (quietForVoice() || (mode == Mode.HOME && !rows.isEmpty() && rowAt().system)) return;
    int me = ++trailerRun;
    Trailer.get(t.trailer, s -> {
      if (s == null || me != trailerRun || !visible || cur != t || mode == Mode.ASK || mode == Mode.DETAIL || quietForVoice()) return; player.setVolume(0f); { final float to = soundVolume; android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, to); va.setDuration(1500); va.addUpdateListener(an -> { if (player != null) player.setVolume((float) an.getAnimatedValue()); }); va.start(); }
      DefaultHttpDataSource.Factory f = new DefaultHttpDataSource.Factory().setUserAgent(Trailer.UA); ProgressiveMediaSource.Factory m = new ProgressiveMediaSource.Factory(f);
      MediaSource v = m.createMediaSource(MediaItem.fromUri(Uri.parse(s.video)));
      player.setMediaSource(s.audio != null ? new MergingMediaSource(v, m.createMediaSource(MediaItem.fromUri(Uri.parse(s.audio)))) : v);
      player.seekTo(9000); player.prepare(); player.setPlayWhenReady(true);
    });
  }

  void cover(float zoom) {
    if (vidW == 0) return; float sw = stage.getWidth(), sh = stage.getHeight(); if (sw == 0) return;
    float k = Math.max(sw / vidW, sh / vidH) * zoom; FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) video.getLayoutParams(); p.width = Math.round(vidW * k); p.height = Math.round(vidH * k); p.gravity = Gravity.CENTER; video.setLayoutParams(p);
  }

  void measureBars() {
    if (!player.isPlaying()) { reveal(); return; }
    int W = 96, H = 54; Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
    try {
      PixelCopy.request(video, b, res -> {
        float zoom = 1f;
        if (res == PixelCopy.SUCCESS) { int top = 0; for (int y = 0; y < H / 3; y++) { long sum = 0; for (int x = 0; x < W; x++) { int p = b.getPixel(x, y); sum += Color.red(p) + Color.green(p) + Color.blue(p); } if (sum / (W * 3) < 10) top++; else break; } if (top >= 3 && top < H / 3) zoom = 1.015f * H / (H - 2f * top); Log.i(T, "letterbox rows " + top + " zoom " + zoom); }
        cover(zoom); reveal();
      }, h);
    } catch (Exception e) { reveal(); }
  }

  void reveal() {
    if (videoOn || mode == Mode.ASK || mode == Mode.DETAIL) return; videoOn = true;
    for (ImageView b : new ImageView[]{bgA, bgB}) b.animate().alpha(0f).setDuration(900).withEndAction(() -> b.setVisibility(View.INVISIBLE)).start(); rack(false); wake(); h.removeCallbacks(pump); h.postDelayed(pump, 900);
    idle = () -> { if (videoOn && r == 0 && mode == Mode.HOME) sleepUi(true); }; h.postDelayed(idle, Tokens.MOTION_IDLE_MS);
  }

  void sleepUi(boolean on) {
    asleep = on;
    rowsBox.animate().alpha(on ? 0f : 1f).setDuration(Tokens.MOTION_FADE_MS).start(); lens.animate().alpha(on ? 0f : 1f).setDuration(Tokens.MOTION_FADE_MS).start(); slide(rowsBox, DynamicAnimation.TRANSLATION_Y, on ? px(Tokens.CANVAS_HEIGHT - H0) : rowY());
    int hidden = why.getHeight() + where.getHeight() + 2 * px(Tokens.SPACE_XS); slide(info, DynamicAnimation.TRANSLATION_Y, on ? px(Tokens.CANVAS_HEIGHT - Tokens.SAFE_BOTTOM - Tokens.HOME_INFO_BOTTOM) + hidden : (r > 0 && mode == Mode.HOME ? -px(INFO_SHIFT) : 0));
    why.animate().alpha(on ? 0f : 1f).setDuration(Tokens.MOTION_FADE_MS).start(); where.animate().alpha(on ? 0f : 1f).setDuration(Tokens.MOTION_FADE_MS).start();
  }

  boolean asleep;
  void wake() { if (idle != null) h.removeCallbacks(idle); if (asleep) sleepUi(false); if (videoOn) { idle = () -> { if (videoOn && r == 0 && mode == Mode.HOME) sleepUi(true); }; h.postDelayed(idle, Tokens.MOTION_IDLE_MS); } }

  void stopVideo() {
    trailerRun++;
    if (!videoOn && !player.isPlaying() && player.getMediaItemCount() == 0) { if (dwell != null) h.removeCallbacks(dwell); return; }
    if (dwell != null) h.removeCallbacks(dwell); if (idle != null) h.removeCallbacks(idle); h.removeCallbacks(pump); videoOn = false; player.stop(); player.clearMediaItems(); cover(1f);
    ImageView in = flip ? bgA : bgB; in.setVisibility(View.VISIBLE); in.animate().alpha(1f).setDuration(300).start();
  }

  void launch(Title t) {
    if (t.progress >= 0 && t.resumePkg != null && Watcher.resume(t.resumePkg, t.resumeTitle)) { Api.event(t.id, "open"); cue("handoff"); return; }
    if (t.system == null && (t.link == null || t.link.isEmpty())) { openPkg(t.resumePkg != null ? t.resumePkg : t.pkg(), t.serviceName()); return; }
    if (t.system == null && !t.id.startsWith("w:")) Watcher.opened(t.id, t.title);
    if (t.system != null) { if ("about".equals(t.system)) { about(); return; } if ("arrange".equals(t.system)) { startChoosing(); return; } if ("vinyl".equals(t.system)) { openVinyl(); return; } if ("google".equals(t.system)) { HomeService.setTakeover(this, false); openPkg("com.google.android.apps.tv.launcherx", "Google TV home"); return; } openPkg(t.system, t.title); return; }
    if (mode != Mode.DETAIL && !morph.isOpen() && t == cur && grow(() -> { comeBack = true; launch(t); })) return;
    Api.event(t.id, "open"); Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(t.link)).setPackage(t.pkg()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); if ("netflix".equals(t.service)) i.putExtra("source", "30");
    handoff(t.subscribed ? t.serviceName() : "Stremio", i);
  }

  void openStremio(Title t) { handoff("Stremio", new Intent(Intent.ACTION_VIEW, Uri.parse(t.stremio)).setPackage("com.stremio.one").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }

  void openPkg(String pkg, String label) { Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg); if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg); if (i == null) { Toast.makeText(this, label + " isn't installed", Toast.LENGTH_SHORT).show(); return; } handoff(label, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }

  void handoff(String label, Intent i) {
    stopVideo(); cue("handoff"); veil.setText("Opening " + label + "…"); veil.setVisibility(View.VISIBLE); veil.setAlpha(0f); veil.animate().alpha(1f).setDuration(Tokens.MOTION_FADE_MS).start();
    h.postDelayed(() -> { try { if (i.getPackage() != null && i.resolveActivity(getPackageManager()) == null) throw new android.content.ActivityNotFoundException("no handler"); startActivity(i); }
      catch (Exception e) { Log.w(T, "open failed, opening the app instead: " + e); Intent app = i.getPackage() == null ? null : getPackageManager().getLeanbackLaunchIntentForPackage(i.getPackage());
        if (app == null && i.getPackage() != null) app = getPackageManager().getLaunchIntentForPackage(i.getPackage());
        if (app != null) { startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); if (cur != null && cur.title != null) island.message("Opened " + label + ". Search \u201c" + cur.title + "\u201d there."); }
        else island.message(label + " isn\u2019t installed on this TV."); } h.postDelayed(() -> { veil.setVisibility(View.GONE); veil.setAlpha(0f); }, 900); }, 320);
  }

  void openDetail() {
    if (cur == null || cur.system != null) return; Title g = cur; if (!morph.isOpen() && grow(this::openDetail)) return; before = mode; mode = Mode.DETAIL; Title t = g; stopVideo(); info.setVisibility(View.GONE); rowsBox.setVisibility(View.GONE); detail.setVisibility(View.VISIBLE); View panel = ((ViewGroup) detail).getChildAt(0); panel.setAlpha(0f); panel.setPivotX(0); panel.setPivotY(px(600)); panel.setScaleX(.94f); panel.setScaleY(.94f); panel.setTranslationY(px(Tokens.SPACE_XL)); panel.animate().alpha(1f).setDuration(Tokens.MOTION_FADE_MS).start(); spring(panel, DynamicAnimation.TRANSLATION_Y, 0, Tokens.SPRING_MORPH_STIFFNESS, Tokens.SPRING_MORPH_DAMPING); spring(panel, DynamicAnimation.SCALE_X, 1f, Tokens.SPRING_MORPH_STIFFNESS, Tokens.SPRING_MORPH_DAMPING); spring(panel, DynamicAnimation.SCALE_Y, 1f, Tokens.SPRING_MORPH_STIFFNESS, Tokens.SPRING_MORPH_DAMPING);
    dLogo.clearColorFilter(); if (t.logo != null) { dLogo.setVisibility(View.VISIBLE); dName.setVisibility(View.GONE); Glide.with(this).load(t.logo).override(px(Tokens.LOGO_HOME_WIDTH), px(Tokens.LOGO_HOME_HEIGHT)).fitCenter().listener(logoCheck(dLogo)).into(dLogo); } else { dLogo.setVisibility(View.GONE); dName.setVisibility(View.VISIBLE); dName.setText(t.title); }
    dMeta.setText(t.meta()); dText.setText(t.overview); List<String> b = new ArrayList<>(); b.add(t.subscribed ? "Play on " + t.serviceName() : "Open in Stremio"); if (t.subscribed) b.add("Open in Stremio"); if (!t.seen) b.add(t.saved ? "Saved" : "Save for later"); b.add(t.seen ? "\u2713 Seen" : "Seen it"); b.add("Not for me"); pills(b);
  }

  void about() { before = mode; mode = Mode.DETAIL; stopVideo(); info.setVisibility(View.GONE); rowsBox.setVisibility(View.GONE); detail.setVisibility(View.VISIBLE); dLogo.setVisibility(View.GONE); dName.setVisibility(View.VISIBLE); dName.setText("About"); dMeta.setText(""); dText.setText("Streaming availability information is provided by Streaming Availability API by Movie of the Night. movieofthenight.com/about/api"); pills(Collections.singletonList("Back")); }

  void pills(List<String> labels) { btns.removeAllViews(); btn = 0; for (int i = 0; i < labels.size(); i++) { View p = pill(labels.get(i)); LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, px(Tokens.PILL_HEIGHT)); l.rightMargin = px(Tokens.PILL_GAP); btns.addView(p, l); liftPill(p, i == 0); } btnLens.forget(); moveBtnLens(true); }

  void closeDetail() {
    mode = before; Runnable back = () -> { Title t = cur; cur = null; focus(0); h.postDelayed(this::applyListChange, 260); };
    View panel = ((ViewGroup) detail).getChildAt(0); panel.animate().alpha(0f).setDuration(Tokens.MOTION_FOCUS_MS).withEndAction(() -> detail.setVisibility(View.GONE)).start(); spring(panel, DynamicAnimation.TRANSLATION_Y, px(Tokens.SPACE_XL), Tokens.SPRING_MORPH_STIFFNESS, Tokens.SPRING_MORPH_DAMPING);
    if (morph.isOpen()) shrink(back); else { detail.setVisibility(View.GONE); info.setVisibility(View.VISIBLE); rowsBox.setVisibility(View.VISIBLE); back.run(); }
  }

  void pressButton() {
    String x = ((TextView) btns.getChildAt(btn)).getText().toString(); Title t = cur;
    if ("Back".equals(x)) { closeDetail(); return; } if ("Not for me".equals(x)) { mark("/notforme", t.id, true); t.saved = false; t.liked = false; listChange = new Object[]{t, "gone"}; cue("select"); island.message("Got it. You won't see it again."); closeDetail(); return; }
    if ("Save for later".equals(x) || "Saved".equals(x)) { t.saved = !t.saved; t.liked = t.saved; mark("/save", t.id, t.saved); listChange = new Object[]{t, t.saved ? "save" : "unsave"};
      ((TextView) btns.getChildAt(btn)).setText(t.saved ? "Saved" : "Save for later"); cue("select"); island.message(t.saved ? "Saved to Watch later." : "Removed from Watch later."); return; }
    if ("Seen it".equals(x) || "\u2713 Seen".equals(x)) { t.seen = !t.seen; mark("/seen", t.id, t.seen); if (t.seen) { t.saved = false; t.liked = false; listChange = new Object[]{t, "unsave"}; }
      cue("select"); island.message(t.seen ? "Marked as seen. It won't come up as new." : "Unmarked as seen."); openDetailButtons(t); return; } if ("Open in Stremio".equals(x)) { openStremio(t); return; } launch(t);
  }

  /** What the details panel changed, applied to the home rows when it closes: saved tiles form out of pixels at the front of
   *  Watch later; removed ones (unsaved, seen, not for me) dissolve into pixels that fall away. No undo: it was a deliberate choice. */
  Object[] listChange;
  void applyListChange() {
    Object[] c = listChange; listChange = null; if (c == null || mode != Mode.HOME || rows != home) return; Title t = (Title) c[0]; String what = (String) c[1];
    Row wl = null; for (Row x : rows) if ("Watch later".equals(x.name)) wl = x;
    if ("save".equals(what)) {
      if (wl == null) { wl = new Row(); wl.name = "Watch later"; wl.why = "Saved for later, ready to start"; int at = 0; while (at < rows.size() && (rows.get(at).system || "Continue watching".equals(rows.get(at).name))) at++; rows.add(at, wl); }
      for (Title x : wl.items) if (t.id.equals(x.id)) return;
      wl.items.add(0, t); reform(); int keep = r; build(false); r = Math.min(keep, rows.size() - 1); focus(0); final Row fw = wl;
      h.postDelayed(() -> { if (!fw.cards.isEmpty()) PixelFx.form(stage, fw.cards.get(0)); }, 60); return;
    }
    // unsave / seen: off Watch later. Not for me: off every row it's in.
    List<Row> from = new ArrayList<>(); View first = null;
    for (Row x : rows) { if (x.system || (!"gone".equals(what) && x != wl)) continue; for (int i = 0; i < x.items.size(); i++) if (t.id.equals(x.items.get(i).id)) { from.add(x); if (first == null || x == wl) first = i < x.cards.size() ? x.cards.get(i) : first; } }
    if (from.isEmpty()) return;
    PixelFx.dissolve(stage, first, () -> {
      for (Row x : from) for (Iterator<Title> it = x.items.iterator(); it.hasNext(); ) if (t.id.equals(it.next().id)) it.remove();
      for (Iterator<Row> it = rows.iterator(); it.hasNext(); ) { Row x = it.next(); if (!x.system && !x.grid && x.items.isEmpty()) it.remove(); }
      reform(); int keep = r; build(false); r = Math.min(keep, rows.size() - 1); focus(0); });
  }
  /** After rows come or go: the first content row (other than Continue watching) is the big cover row, the rest tall. */
  void reform() { boolean hero = false; for (Row x : rows) { if (x.system || x.grid) continue; boolean cont = "Continue watching".equals(x.name); x.form = !cont && !hero ? COVER : TALL; if (x.form == COVER) hero = true; } }
  /** Save / Seen it / Not for me go to the engine; the screen updates at once and doesn't wait for the answer. */
  void mark(String path, String id, boolean on) { try { Api.post(path, new org.json.JSONObject().put("id", id).put("on", on).toString(), new Api.Done<String>() { public void ok(String b) {} public void fail(String w) { Log.w(T, path + ": " + w); } }); } catch (Exception ignored) {} }
  void openDetailButtons(Title t) { List<String> b = new ArrayList<>(); b.add(t.subscribed ? "Play on " + t.serviceName() : "Open in Stremio"); if (t.subscribed) b.add("Open in Stremio"); if (!t.seen) b.add(t.saved ? "Saved" : "Save for later"); b.add(t.seen ? "\u2713 Seen" : "Seen it"); b.add("Not for me"); pills(b); }
  void talk(String text) { text = Commands.unJarvis(text);
    morph.hide(); comeBack = false; if (morphed != null) { morphed.setAlpha(1f); morphed = null; } rowsBox.setAlpha(1f); info.setAlpha(1f); lens.setAlpha(1f); dock.setAlpha(1f); thoughtText = "";
    int me = ++ask; mode = Mode.ASK; startTimer(me); homeExtras(); if (topFocus) lightTop(false); stopVideo(); cue("listenEnd"); heardText = text; dock.removeAllViews(); info.setVisibility(View.GONE); rowsBox.setVisibility(View.GONE); detail.setVisibility(View.GONE); searching(true); lens.animate().alpha(0f).setDuration(Tokens.MOTION_FOCUS_MS).start(); rack(true); dim.setVisibility(View.VISIBLE); dim.animate().alpha(.5f).setDuration(Tokens.MOTION_FADE_MS).start();
    askBox.setVisibility(View.VISIBLE); said.setAlpha(0f); said.setTranslationY(px(Tokens.SPACE_S)); said.setText(text); said.animate().alpha(1f).setDuration(Tokens.MOTION_FADE_MS).start(); spring(said, DynamicAnimation.TRANSLATION_Y, 0, Tokens.SPRING_REVEAL_STIFFNESS, Tokens.SPRING_REVEAL_DAMPING);
    Api.ask(text, new Api.Ask() {
      public void heart(String words) { if (me == ask) lightUp(words); }
      public void line(String words) { if (me == ask) ticker.say(words); }
      public void reply(String words) {} // results speak for themselves; the butler says a short line when they appear
      final List<Title> got = new ArrayList<>();
      public void pick(Title t) { if (me != ask) return; if (got.isEmpty()) timerDone(); got.add(t); if (mode == Mode.RESULT) place(t); else if (got.size() == 1) h.postDelayed(() -> { if (me == ask && mode == Mode.ASK) results(new ArrayList<>(got)); }, 1800); }
      public void picks(List<Title> t) { if (me != ask) return; timerDone(); if (t.isEmpty()) { fail("I couldn't find a good match for that. Try saying it another way."); return; } if (got.isEmpty()) h.postDelayed(() -> { if (me == ask) results(t); }, 650); }
      public void fail(String why2) { if (me != ask) return; timerStop(); Log.w(T, "ask: " + why2); cue("error"); searching(false); said.setText(why2.startsWith("I couldn") ? why2 : "That didn't work. Check the Wi-Fi and try again."); h.postDelayed(() -> { if (me == ask) goHome(); }, 3200); }
    });
  }

  String thoughtText = "";

  /** The words that mattered warm up inside the sentence they said. */
  void lightUp(String words) {
    String all = said.getText().toString(); int at = words == null || words.isEmpty() ? -1 : all.toLowerCase().indexOf(words.toLowerCase()); if (at < 0) return; int end = at + words.length();
    ValueAnimator warm = ValueAnimator.ofArgb(INK, VOICE); warm.setDuration(Tokens.MOTION_FADE_MS);
    warm.addUpdateListener(v -> { SpannableString lit = new SpannableString(all); lit.setSpan(new ForegroundColorSpan((int) v.getAnimatedValue()), at, end, 0); said.setText(lit); }); warm.start();
  }

  /** The chip comes to the middle of the screen and the mark works there, so you can see Claude searching. */
  void searching(boolean on) {
    ticker.think(on); float k = on ? Tokens.TICKER_THINK_SCALE : 1f, st = Tokens.SPRING_MORPH_STIFFNESS, da = Tokens.SPRING_MORPH_DAMPING;
    if (on) centreChip(top.getWidth()); else spring(top, DynamicAnimation.TRANSLATION_X, 0, st, da);
    spring(top, DynamicAnimation.TRANSLATION_Y, on ? px(Tokens.TICKER_THINK_Y - Tokens.SAFE_TOP) : 0, st, da); spring(top, DynamicAnimation.SCALE_X, k, st, da); spring(top, DynamicAnimation.SCALE_Y, k, st, da);
  }

  /** Keeps the chip centred while its width changes. Outside a search it simply stays in its corner. */
  void centreChip(int width) { if (mode == Mode.ASK) spring(top, DynamicAnimation.TRANSLATION_X, -(stage.getWidth() / 2f - px(Tokens.SAFE_RIGHT) - width / 2f), Tokens.SPRING_MORPH_STIFFNESS, Tokens.SPRING_MORPH_DAMPING); }

  void results(List<Title> picks) {
    voice.say(Voice.resultLine(), null);
    cue("picks"); searching(false);
    said.animate().alpha(0f).setDuration(320).withEndAction(() -> askBox.setVisibility(View.GONE)).start();
    mode = Mode.RESULT; rows = new ArrayList<>();
    for (int i = 0; i < picks.size(); i += Tokens.LENS_COLUMNS) { Row x = new Row(); x.grid = true; x.items = new ArrayList<>(picks.subList(i, Math.min(i + Tokens.LENS_COLUMNS, picks.size()))); x.name = i == 0 ? (heardText == null || heardText.isEmpty() ? "Your results" : "For \u201c" + heardText + "\u201d") : ""; rows.add(x); }
    r = 0; cur = null; info.setVisibility(View.VISIBLE); rowsBox.setVisibility(View.VISIBLE); lens.setAlpha(1f); lens.forget(); rack(false); dim.animate().alpha(0f).setDuration(Tokens.MOTION_FADE_MS).start(); build(false); focus(0); sound.play("build"); plan.brief(() -> parts(true), null);
  }

  /** A pick that arrives after the screen is up joins the end of the list without moving anything. */
  void place(Title t) {
    Row last = rows.get(rows.size() - 1);
    if (last.items.size() >= Tokens.LENS_COLUMNS) { Row x = new Row(); x.grid = true; x.name = ""; x.y = last.y + last.height(); rows.add(x); makeRow(x, false); x.track.setAlpha(.46f); last = x; }
    last.items.add(t); addCard(last, t, 0); if (rows.indexOf(last) == r) ((ViewGroup) last.cards.get(last.cards.size() - 1)).getChildAt(1).setVisibility(View.VISIBLE);
  }

  void goHome() { if (island != null) island.settle(); if (mode == Mode.ASK) searching(false); morph.hide(); comeBack = false; if (morphed != null) { morphed.setAlpha(1f); morphed = null; } rowsBox.setAlpha(1f); info.setAlpha(1f); lens.setAlpha(1f); dock.setAlpha(1f); ask++; stopVideo(); mode = Mode.HOME; veil.setVisibility(View.GONE); askBox.setVisibility(View.GONE); detail.setVisibility(View.GONE); dock.removeAllViews(); info.setVisibility(View.VISIBLE); rowsBox.setVisibility(View.VISIBLE);  rack(false); if (home.isEmpty()) return; rows = home; r = 0; for (Row x : rows) x.at = 0; cur = null; build(false); focus(0); }

  /** Speech never matches our own words, so anything that does is the screen being read back by mistake. */
  boolean onScreen(String t) { for (TextView v : new TextView[]{why, name, meta, where, hint, said}) if (t.contentEquals(v.getText())) return v != said || mode == Mode.ASK; return false; }

  long askedAt = -10000;

  /** A private key made once per install: only the app's own parts know it, so other apps on the TV can't drive the home screen. */
  static String gate(Context c) { android.content.SharedPreferences p = c.getSharedPreferences("gate", MODE_PRIVATE); String g = p.getString("k", null);
    if (g == null) { g = java.util.UUID.randomUUID().toString(); p.edit().putString("k", g).apply(); } return g; }
  void handle(Intent i) { if (i == null) return; String m = i.getStringExtra("mode"); if (m != null && !gate(this).equals(i.getStringExtra("gate"))) { Log.w(T, "ignored a command from outside the app: " + m); return; } if ("ask".equals(m)) { HomeService.setTakeover(this, true); String t = i.getStringExtra("text"); if (t == null) return; long now = SystemClock.elapsedRealtime(); if (now - askedAt < 4000 || onScreen(t)) { Log.w(T, "ignored second voice text: " + t); return; } askedAt = now; talk(t); } else if ("vinyl".equals(m)) { if (!vinyl.showing()) openVinyl(); } else if ("voices".equals(m)) voice.samples(); else if ("voice".equals(m)) voice.choose(i.getIntExtra("n", 0)); else if (m != null && m.startsWith("key:")) { int code = Integer.parseInt(m.substring(4)); long t0 = SystemClock.uptimeMillis(); dispatchKeyEvent(new KeyEvent(t0, t0, KeyEvent.ACTION_DOWN, code, 0)); dispatchKeyEvent(new KeyEvent(t0, t0, KeyEvent.ACTION_UP, code, 0)); } else if ("press".equals(m)) { if (listen.active()) listen.done(); else press(); } else if ("listen".equals(m)) { if (listen.active()) listen.done(); else hear(); } else if ("home".equals(m)) { if (listen != null) listen.stop(); if (ear != null) ear.setVisibility(View.GONE); cue("home"); goHome(); } else if ("wake".equals(m)) { greet = true; if (!plan.active()) reboot(); else if (plan.age() > 8000) { Log.w(T, "wake: splash stuck for " + plan.age() + "ms, finishing it"); plan.go(); } } else if ("detail".equals(m)) openDetail(); }

  @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); handle(i); }

  @Override protected void onResume() { super.onResume(); HomeService.setTakeover(this, true); if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > 600000 && mode == Mode.HOME && !home.isEmpty()) { stoppedAt = 0; morph.hide(); reboot(); return; } if (comeBack) { comeBack = false; veil.setVisibility(View.GONE); if (morph.isOpen()) { shrink(() -> { Title t = cur; cur = null; focus(0); }); return; } } if (mode == Mode.HOME && cur != null && !rows.isEmpty()) { Title t = cur; cur = null; focus(0); } }

  @Override protected void onStart() { super.onStart(); visible = true; shown = true; }

  @Override protected void onStop() { super.onStop(); visible = false; shown = false; leftAt = SystemClock.elapsedRealtime(); if (listen != null) listen.stop(); if (voice != null) voice.quiet(); if (ear != null) ear.setVisibility(View.GONE); if (island != null) island.settle(); if (arranging) endArrange(); stopVideo(); stoppedAt = SystemClock.elapsedRealtime(); }

  @Override protected void onDestroy() { super.onDestroy(); if (voice != null) voice.release(); if (player != null) player.release(); sound.release(); }

  /** Ambient light: the focused tile's colours, blurred and spread across the room, like a bias light behind the screen. */
  /** The clock and the glow belong to the app row on the home screen; browsing films hides them. */
  void homeExtras() { boolean on = mode == Mode.HOME && !rows.isEmpty() && rowAt().system; if (on) { trailerRun++; stopVideo(); } if (island != null) island.animate().alpha(mode == Mode.HOME && !on && !island.busy() ? 0f : 1f).setDuration(200).start(); if (more != null) more.animate().alpha(on && rows.size() > 1 ? 1f : 0f).setDuration(220).start(); if (island != null) island.media(mode == Mode.HOME); dots.animate().alpha(on ? 1f : 0f).setDuration(300).start(); if (!on) glow.animate().alpha(0f).setDuration(300).start(); }
  boolean blurred;
  /** Behind a cover-flow row the backdrop melts into a soft wash of the poster's colours, like the reference. */
  void blurRoom(boolean on) { if (on == blurred || android.os.Build.VERSION.SDK_INT < 31) return; blurred = on; android.graphics.RenderEffect fx = on ? android.graphics.RenderEffect.createBlurEffect(px(70), px(70), android.graphics.Shader.TileMode.CLAMP) : null; bgA.setRenderEffect(fx); bgB.setRenderEffect(fx); }
  void ambient() {
    try { if (rows.isEmpty() || mode != Mode.HOME) return; Row x = rowAt(); if (x.at >= x.cards.size()) return; View v = x.cards.get(x.at); if (v.getWidth() == 0) return;
      Bitmap b = Bitmap.createBitmap(Math.max(1, v.getWidth() / 8), Math.max(1, v.getHeight() / 8), Bitmap.Config.ARGB_8888); Canvas c = new Canvas(b); c.scale(1 / 8f, 1 / 8f); v.draw(c);
      Bitmap tiny = Bitmap.createScaledBitmap(b, 6, 3, true); BitmapDrawable d = new BitmapDrawable(getResources(), tiny); d.setFilterBitmap(true);
      glow.animate().cancel(); glow.setImageDrawable(d); glow.setAlpha(0f); glow.animate().alpha(.55f).setDuration(600).start();
    } catch (Exception e) { Log.w(T, "ambient: " + e); }
  }

  /** While a search runs, the bubble counts up; when results land it says how long it took. */
  final Object timerTok = new Object();
  void startTimer(int me) { searchStart = SystemClock.elapsedRealtime(); h.removeCallbacksAndMessages(timerTok); ear.setVisibility(View.GONE); island.thinking("0:00");
    Runnable tick = new Runnable() { public void run() { if (me != ask || searchStart == 0) return; island.sub(clockOf(SystemClock.elapsedRealtime() - searchStart)); h.postAtTime(this, timerTok, SystemClock.uptimeMillis() + 1000); } }; tick.run(); }
  void timerDone() { if (searchStart == 0) return; String took = clockOf(SystemClock.elapsedRealtime() - searchStart); searchStart = 0; h.removeCallbacksAndMessages(timerTok); island.found(took); }
  void timerStop() { searchStart = 0; h.removeCallbacksAndMessages(timerTok); ear.setVisibility(View.GONE); island.settle(); }
  static String clockOf(long ms) { long s2 = ms / 1000; return String.format(Locale.US, "%d:%02d", s2 / 60, s2 % 60); }

  /** The search pill at the top right: press up from the first row to reach it, then OK to type or dictate with the keyboard's mic. */
  void lightTop(boolean on) { topFocus = on; if (island != null) island.focus(on); if (!rows.isEmpty() && rowAt().system) magnify(rowAt()); if (lit != null && litRow != null) lift(lit, !on, litRow); lens.animate().alpha(on ? 0f : 1f).setDuration(160).start(); if (on) moved("row"); }

  /** Our own voice search, started by the remote's mic while this screen is up. */
  void hear() {
    if (mode == Mode.DETAIL || plan.active()) return; if (listen.active()) return;
    if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 7); return; }
    h.removeCallbacksAndMessages(ear); voice.quiet(); trailerRun++; island.animate().alpha(1f).setDuration(150).start(); ear.setVisibility(View.GONE); island.listening("Listening…"); stopVideo();
    listen.waitMs = quietMiss ? 5000 : 10000;
    listen.start(new Listen.Ear() {
      public void hearing(String soFar) { island.words(soFar); }
      public void level(float db) { island.level(db); }
      public void heard(String text) { quietMiss = false; long now = SystemClock.elapsedRealtime(); askedAt = now; if (Commands.weatherQuestion(text)) { Log.i(T, "weather question: " + text); island.message("Checking the weather…"); Api.weather(new Api.Done<String>() { public void ok(String b) { String line = Commands.weatherLine(b); Log.i(T, "weather answer: " + line); island.message(line); voice.say(line, null); } public void fail(String w) { island.message("I couldn't get the weather right now."); } }); return; }
        String done = Commands.run(MainActivity.this, text); if (done != null) { island.message(done); voice.say(done, null); return; } talk(text); }
      public void missed(String why) { if (quietMiss) { quietMiss = false; island.settle(); return; } cue("error"); island.message(why); }
    });
  }
  @Override public void onRequestPermissionsResult(int code, String[] p, int[] g) { super.onRequestPermissionsResult(code, p, g); if (code == 7 && g.length > 0 && g[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) hear(); }

  /** App tiles keep the order you give them. Long-press OK on one, move it with left and right, then press OK. */
  void ordered(List<Title> items) {
    String saved = getSharedPreferences(HomeService.PREFS, 0).getString("appOrder", ""); if (saved.isEmpty()) return;
    List<String> order = Arrays.asList(saved.split("\\|"));
    items.sort((a, b) -> { int i = order.indexOf(a.system), j = order.indexOf(b.system); return Integer.compare(i < 0 ? 999 : i, j < 0 ? 999 : j); });
  }
  /** The Reorder tile: pick a tile with OK, then move it. */
  void startChoosing() { Row x = rowAt(); if (!x.system) return; choosing = true; x.at = 0; cur = null; focus(0); ear.setText("Pick an app to move and press OK"); ear.setVisibility(View.VISIBLE); }
  /** "this morning", "this afternoon", "this evening" or "tonight", from the TV's clock. */
  static String partOfDay() { int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); return h >= 5 && h < 12 ? "this morning" : h >= 12 && h < 17 ? "this afternoon" : h >= 17 && h < 21 ? "this evening" : "tonight"; }
  /** The turntable covers the whole screen, so the island steps aside while it is open. */
  void openVinyl() { stopVideo(); island.setVisibility(View.GONE); vinyl.open(() -> { island.setVisibility(View.VISIBLE); focus(0); }); }
  Drawable vinylBanner() {
    int w = 320, hh = 180; Bitmap b = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(b);
    Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG); bg.setShader(new LinearGradient(0, 0, w, hh, 0xff2a2a2e, 0xff111113, Shader.TileMode.CLAMP)); c.drawRoundRect(new RectF(0, 0, w, hh), 18, 18, bg);
    Paint d = new Paint(Paint.ANTI_ALIAS_FLAG); d.setColor(0xff050506); c.drawCircle(130, 90, 70, d); d.setStyle(Paint.Style.STROKE); d.setStrokeWidth(1); d.setColor(0x26ffffff); for (int r = 30; r < 68; r += 5) c.drawCircle(130, 90, r, d);
    d.setStyle(Paint.Style.FILL); d.setColor(Tokens.COLOR_ACCENT); c.drawCircle(130, 90, 22, d); d.setColor(0xffe0e0e4); c.drawCircle(130, 90, 3, d);
    Paint arm = new Paint(Paint.ANTI_ALIAS_FLAG); arm.setColor(0xffc8c8cc); arm.setStrokeWidth(5); arm.setStrokeCap(Paint.Cap.ROUND); c.drawCircle(238, 38, 11, arm); c.drawLine(238, 38, 226, 118, arm); c.drawLine(226, 118, 206, 134, arm);
    Paint tx = new Paint(Paint.ANTI_ALIAS_FLAG); tx.setColor(SOFT); tx.setTextSize(24); tx.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); c.drawText("Turntable", 214, 166, tx);
    return new BitmapDrawable(getResources(), b);
  }
  Drawable arrangeBanner() {
    int w = 320, hh = 180; Bitmap b = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888); Canvas c = new Canvas(b);
    Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG); bg.setColor(Tokens.COLOR_WELL); c.drawRoundRect(new RectF(0, 0, w, hh), 18, 18, bg);
    Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG); ink.setColor(INK); ink.setStyle(Paint.Style.STROKE); ink.setStrokeWidth(7); ink.setStrokeCap(Paint.Cap.ROUND); ink.setStrokeJoin(Paint.Join.ROUND);
    float cy = 72; c.drawLine(118, cy - 16, 202, cy - 16, ink); c.drawLine(186, cy - 30, 202, cy - 16, ink); c.drawLine(186, cy - 2, 202, cy - 16, ink);
    c.drawLine(118, cy + 16, 202, cy + 16, ink); c.drawLine(134, cy + 2, 118, cy + 16, ink); c.drawLine(134, cy + 30, 118, cy + 16, ink);
    Paint tx = new Paint(Paint.ANTI_ALIAS_FLAG); tx.setColor(SOFT); tx.setTextSize(30); tx.setTextAlign(Paint.Align.CENTER); tx.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); c.drawText("Reorder", w / 2f, 146, tx);
    return new BitmapDrawable(getResources(), b);
  }
  void startArrange() { arranging = true; cue("select"); ear.setText("Move with ◀ ▶, then press OK"); ear.setVisibility(View.VISIBLE); }
  void endArrange() { arranging = false; if (ear != null) ear.setVisibility(View.GONE); Row x = rows.isEmpty() ? null : rowAt(); if (x == null || !x.system) return;
    StringBuilder b = new StringBuilder(); for (Title t : x.items) { if ("arrange".equals(t.system)) continue; if (b.length() > 0) b.append('|'); b.append(t.system); } getSharedPreferences(HomeService.PREFS, 0).edit().putString("appOrder", b.toString()).apply(); cue("select"); }
  void shift(int d) { Row x = rowAt(); int to = x.at + d; int last = x.items.size() - ("arrange".equals(x.items.get(x.items.size() - 1).system) ? 2 : 1); if (!x.system || to < 0 || to > last || "arrange".equals(x.items.get(x.at).system)) { bump(d, 0); return; }
    Collections.swap(x.items, x.at, to); x.at = to; build(false); cur = null; focus(0); moved("focus"); }

  @Override public boolean dispatchKeyEvent(KeyEvent e) {
    if (vinyl != null && vinyl.showing()) return vinyl.key(e);
    if (plan.active()) { if (plan.settled() && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) plan.stop(); return true; }
    int k = e.getKeyCode();
    boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER;
    if (e.getAction() == KeyEvent.ACTION_UP) { if (ok && !longDone && choosing) { choosing = false; if (cur != null && "arrange".equals(cur.system)) { ear.setVisibility(View.GONE); cue("back"); } else startArrange(); longDone = false; return true; }
    if (ok && !longDone) { if (listen != null && listen.active()) listen.done(); else if (mode == Mode.ASK && searchStart > 0) { ask++; timerStop(); Log.i(T, "search interrupted, listening again"); hear(); } else if (arranging) endArrange(); else if (mediaFocus) { dots.press(); cue("select"); } else if (topFocus && (mode == Mode.HOME || mode == Mode.RESULT)) { if (island.press()) { lightTop(false); hear(); } else cue("select"); } else press(); } longDone = false; return ok || super.dispatchKeyEvent(e); }
    if (k == KeyEvent.KEYCODE_BACK && voice != null && voice.speaking()) { voice.quiet(); ear.setVisibility(View.GONE); island.settle(); return true; }
    if (listen != null && listen.active() && k == KeyEvent.KEYCODE_BACK) { listen.stop(); voice.quiet(); ear.setVisibility(View.GONE); island.settle(); return true; }
    if (choosing && k == KeyEvent.KEYCODE_BACK) { choosing = false; ear.setVisibility(View.GONE); return true; }
    if (arranging) { if (k == KeyEvent.KEYCODE_BACK) { endArrange(); return true; } if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) { shift(k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1); return true; } if (!ok) return true; }
    if (ok) { if (e.getRepeatCount() > 8 && !longDone && (mode == Mode.HOME || mode == Mode.RESULT)) { longDone = true; if (cur != null && cur.system != null) { } else openDetail(); } return true; }
    if (k == KeyEvent.KEYCODE_BACK) { back(); return true; }
    if (veil.getVisibility() == View.VISIBLE) return true;
    if (mode == Mode.ASK) return true;
    if (mode == Mode.DETAIL) { int n = btns.getChildCount(); if (k == KeyEvent.KEYCODE_DPAD_RIGHT || k == KeyEvent.KEYCODE_DPAD_LEFT) { liftPill(btns.getChildAt(btn), false); btn = (btn + (k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : n - 1)) % n; liftPill(btns.getChildAt(btn), true); moveBtnLens(false); sound.play("focus", n > 1 ? btn / (n - 1f) : .5f, 0); } return true; }
    if (mediaFocus) { if (ok) return true; if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) { dots.move(k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1); moved("focus"); return true; }
      if (k == KeyEvent.KEYCODE_DPAD_UP) { mediaFocus = false; dots.focusMedia(false); lightTop(true); return true; } if (k == KeyEvent.KEYCODE_DPAD_DOWN || k == KeyEvent.KEYCODE_BACK) { mediaFocus = false; dots.focusMedia(false); moved("row"); focus(0); return true; } return true; }
    if (topFocus && (mode == Mode.HOME || mode == Mode.RESULT)) { if (ok) return true; if (k == KeyEvent.KEYCODE_DPAD_DOWN) { lightTop(false); return true; } if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) { island.move(k == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1); moved("focus"); return true; } return true; }
    if (rows.isEmpty()) return super.dispatchKeyEvent(e); Row x = rowAt();
    if (x.system && (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT)) { long now = SystemClock.uptimeMillis(); if (e.getRepeatCount() > 0 || now - lastDockMove < 170) return true; lastDockMove = now; }
    switch (k) {
      case KeyEvent.KEYCODE_DPAD_RIGHT: if (x.at < x.items.size() - 1) { x.at++; moved("focus"); focus(1); } else bump(1, 0); return true;
      case KeyEvent.KEYCODE_DPAD_LEFT: if (x.at > 0) { x.at--; moved("focus"); focus(-1); } else bump(-1, 0); return true;
      case KeyEvent.KEYCODE_DPAD_DOWN: if (r < rows.size() - 1) { r++; keepColumn(x); moved("row"); focus(0); } else bump(0, 1); return true;
      case KeyEvent.KEYCODE_DPAD_UP: if (r > 0) { r--; keepColumn(x); moved("row"); focus(0); } else if (mode == Mode.HOME && x.system && dots.hasMedia()) { mediaFocus = true; dots.focusMedia(true); moved("row"); } else if (mode == Mode.HOME || mode == Mode.RESULT) lightTop(true); else bump(0, -1); return true;
      default: return super.dispatchKeyEvent(e);
    }
  }

  /** In the results grid, up and down stay in the same column. */
  void keepColumn(Row from) { Row to = rowAt(); if (to.grid) to.at = Math.min(from.at, to.items.size() - 1); }

  void moved(String s) { Row x = rowAt(); int n = x.items.size(); sound.play(s, n > 1 ? x.at / (n - 1f) : .5f, r); }

  void bump(float dx, float dy) { wake(); moved("edge"); lean(dx, dy); }

  void press() { if (veil.getVisibility() == View.VISIBLE && home.isEmpty()) { recreate(); return; } cue("select"); if (mode == Mode.DETAIL) pressButton(); else if (mode != Mode.ASK && cur != null) launch(cur); }

  void back() { if (mode != Mode.HOME || r > 0) cue("back"); if (mode == Mode.DETAIL) closeDetail(); else if (mode == Mode.ASK || mode == Mode.RESULT) goHome(); else { Row x = rowAt(); if (!x.system && x.at > 0) { x.at = 0; cue("back"); moved("focus"); focus(-1); } else if (r > 0) { r = 0; focus(0); } } }
}
