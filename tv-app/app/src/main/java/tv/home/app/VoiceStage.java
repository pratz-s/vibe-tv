package tv.home.app;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The full-screen voice moment: the room darkens, glowing white bars move with the person's voice and their words
 * appear underneath. While the engine thinks, two glass orbs drift around each other with "Thinking…".
 */
final class VoiceStage extends FrameLayout {
  private final float u; private final Wave wave; private final Orbs orbs; private final TextView words, status, hint; private final View stop; private final LinearLayout thinkRow;
  private long shownAt;

  VoiceStage(Context c, float u, Typeface sans, Typeface sansLight) {
    super(c); this.u = u; setVisibility(GONE); setAlpha(0f); setClickable(false);
    setBackground(new Room());
    wave = new Wave(c); addView(wave, new LayoutParams(px(620), px(300), Gravity.CENTER)); ((LayoutParams) wave.getLayoutParams()).topMargin = -px(110);
    words = text(c, sansLight, 58, 0xfff5f5f7); words.setGravity(Gravity.CENTER); words.setMaxLines(2); words.setEllipsize(TextUtils.TruncateAt.START);
    LayoutParams wp = new LayoutParams(px(1400), LayoutParams.WRAP_CONTENT, Gravity.CENTER); wp.topMargin = px(170); addView(words, wp);
    thinkRow = new LinearLayout(c); thinkRow.setOrientation(LinearLayout.HORIZONTAL); thinkRow.setGravity(Gravity.CENTER_VERTICAL); thinkRow.setVisibility(GONE);
    orbs = new Orbs(c); thinkRow.addView(orbs, new LinearLayout.LayoutParams(px(150), px(130)));
    LinearLayout col = new LinearLayout(c); col.setOrientation(LinearLayout.VERTICAL);
    status = text(c, sansLight, 64, 0xffd9d9de); status.setText("Thinking…"); col.addView(status);
    TextView sub = text(c, sansLight, 30, 0x99f5f5f7); sub.setTag("sub"); col.addView(sub);
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2); cp.leftMargin = px(36); thinkRow.addView(col, cp);
    addView(thinkRow, new LayoutParams(-2, -2, Gravity.CENTER));
    stop = new StopButton(c); LayoutParams sp = new LayoutParams(px(110), px(110), Gravity.BOTTOM | Gravity.END); sp.rightMargin = px(Tokens.SAFE_RIGHT + 20); sp.bottomMargin = px(Tokens.SAFE_BOTTOM + 20); addView(stop, sp);
    hint = text(c, sans, 26, 0x80f5f5f7); hint.setText("OK to finish   ·   Back to cancel");
    LayoutParams hp = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); hp.bottomMargin = px(Tokens.SAFE_BOTTOM + 52); addView(hint, hp);
  }

  private int px(float v) { return Math.round(v * u); }
  private TextView text(Context c, Typeface f, float size, int color) { TextView t = new TextView(c); t.setTypeface(f); t.setTextColor(color); t.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * u); t.setIncludeFontPadding(false); t.setLineSpacing(size * u * .2f, 1f); return t; }

  boolean showing() { return getVisibility() == VISIBLE; }
  private void appear() { if (getVisibility() != VISIBLE) { setVisibility(VISIBLE); shownAt = System.currentTimeMillis(); } animate().cancel(); animate().alpha(1f).setDuration(260).start(); }

  /** Listening: bars move with the voice, the words follow. */
  void listening(String prompt) { appear(); thinkRow.setVisibility(GONE); wave.setVisibility(VISIBLE); wave.listening(true); words.setVisibility(VISIBLE); words.setTextColor(0x99f5f5f7); words.setText(prompt); stop.setVisibility(VISIBLE); hint.setVisibility(VISIBLE); }
  void words(String soFar) { words.setTextColor(0xfff5f5f7); words.setText(soFar); }
  void level(float rmsDb) { wave.level(rmsDb); }

  /** Speaking: the bars breathe while the TV talks. */
  void speaking(String text) { appear(); thinkRow.setVisibility(GONE); wave.setVisibility(VISIBLE); wave.listening(false); words.setVisibility(VISIBLE); words.setTextColor(0xfff5f5f7); words.setText(text); stop.setVisibility(GONE); hint.setVisibility(GONE); }

  /** Thinking: two orbs and a timer. */
  void thinking(String said) { appear(); wave.setVisibility(GONE); words.setVisibility(GONE); stop.setVisibility(GONE); hint.setVisibility(VISIBLE); hint.setText("OK to change your request"); thinkRow.setVisibility(VISIBLE); status.setText("Thinking…"); sub(said); }
  void sub(String s) { ((TextView) thinkRow.findViewWithTag("sub")).setText(s); }
  void found(String took) { status.setText("Found it"); sub("in " + took); postDelayed(this::hide, 1100); }
  void message(String m) { appear(); wave.setVisibility(GONE); thinkRow.setVisibility(GONE); stop.setVisibility(GONE); hint.setVisibility(GONE); words.setVisibility(VISIBLE); words.setTextColor(0xccf5f5f7); words.setText(m); postDelayed(this::hide, 2600); }
  void hide() { if (getVisibility() != VISIBLE) return; wave.listening(false); hint.setText("OK to finish   ·   Back to cancel"); animate().cancel(); animate().alpha(0f).setDuration(240).withEndAction(() -> { setVisibility(GONE); wave.setVisibility(GONE); }).start(); }

  /** Near-black room with a faint cool light from the top left, like the references. */
  private final class Room extends android.graphics.drawable.Drawable {
    private final Paint p = new Paint(); private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    @Override protected void onBoundsChange(Rect b) { glow.setShader(new RadialGradient(b.width() * .12f, b.height() * .2f, b.width() * .55f, new int[]{0x2e8f9bb3, 0x00000000}, null, Shader.TileMode.CLAMP)); }
    @Override public void draw(Canvas c) { Rect b = getBounds(); p.setColor(0xeb050506); c.drawRect(b, p); c.drawRect(b, glow); }
    @Override public void setAlpha(int a) {} @Override public void setColorFilter(ColorFilter f) {} @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
  }

  /** Glowing capsules: a faint full-height track behind each, a bright bar that follows the voice. */
  private final class Wave extends View {
    private static final int N = 9; private final float[] h = new float[N], target = new float[N]; private final float[] shape = {.28f, .55f, .8f, 1f, .7f, .9f, .6f, .4f, .22f};
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG), halo = new Paint(Paint.ANTI_ALIAS_FLAG), track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean live; private float lvl; private long t0 = System.currentTimeMillis(); private final RectF r = new RectF();
    Wave(Context c) { super(c); setLayerType(LAYER_TYPE_SOFTWARE, null); bar.setColor(0xfff5f5f7); halo.setColor(0x99ffffff); halo.setMaskFilter(new BlurMaskFilter(px(22), BlurMaskFilter.Blur.NORMAL)); track.setColor(0x1fffffff); for (int i = 0; i < N; i++) h[i] = .15f; }
    void listening(boolean on) { live = on; lvl = 0; invalidate(); }
    void level(float db) { lvl = Math.max(0f, Math.min(1f, (db + 1f) / 10f)); }
    @Override protected void onDraw(Canvas c) {
      float w = getWidth(), H = getHeight(), slot = w / N, bw = slot * .52f, t = (System.currentTimeMillis() - t0) / 1000f;
      for (int i = 0; i < N; i++) {
        float idle = .16f + .1f * (float) Math.sin(t * 2.2f + i * .8f);
        float wob = .75f + .25f * (float) Math.sin(t * 9f + i * 1.7f);
        target[i] = live ? Math.max(idle, lvl * shape[i] * wob) : idle;
        h[i] += (target[i] - h[i]) * .25f;
        float cx = slot * (i + .5f), th = H * .78f * Math.max(.6f, shape[i]);
        r.set(cx - bw / 2, (H - th) / 2, cx + bw / 2, (H + th) / 2); c.drawRoundRect(r, bw / 2, bw / 2, track);
        float bh = Math.max(bw, H * .9f * h[i]); r.set(cx - bw / 2, (H - bh) / 2, cx + bw / 2, (H + bh) / 2);
        c.drawRoundRect(r, bw / 2, bw / 2, halo); c.drawRoundRect(r, bw / 2, bw / 2, bar);
      }
      if (getVisibility() == VISIBLE && VoiceStage.this.getVisibility() == VISIBLE) postInvalidateOnAnimation();
    }
  }

  /** Two glass spheres circling each other, with a soft white core where they overlap. */
  private final class Orbs extends View {
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG), core = new Paint(Paint.ANTI_ALIAS_FLAG); private final long t0 = System.currentTimeMillis();
    Orbs(Context c) { super(c); setLayerType(LAYER_TYPE_SOFTWARE, null); rim.setStyle(Paint.Style.STROKE); rim.setStrokeWidth(px(2)); rim.setColor(0x59ffffff); core.setMaskFilter(new BlurMaskFilter(px(14), BlurMaskFilter.Blur.NORMAL)); }
    @Override protected void onDraw(Canvas c) {
      float w = getWidth(), H = getHeight(), R = H * .3f, t = (System.currentTimeMillis() - t0) / 1000f, a = t * 1.6f;
      float cx = w / 2, cy = H / 2, d = R * .55f;
      float x1 = cx + d * (float) Math.cos(a), y1 = cy + d * .6f * (float) Math.sin(a), x2 = cx - d * (float) Math.cos(a), y2 = cy - d * .6f * (float) Math.sin(a);
      for (float[] o : new float[][]{{x1, y1}, {x2, y2}}) { glass.setShader(new RadialGradient(o[0] - R * .3f, o[1] - R * .35f, R * 1.3f, new int[]{0x66ffffff, 0x1affffff, 0x08ffffff}, new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP)); c.drawCircle(o[0], o[1], R, glass); c.drawCircle(o[0], o[1], R, rim); }
      float pulse = .75f + .25f * (float) Math.sin(t * 3f);
      core.setShader(new RadialGradient((x1 + x2) / 2, (y1 + y2) / 2, R * .7f, new int[]{(int) (0xe6 * pulse) << 24 | 0xffffff, 0x00ffffff}, null, Shader.TileMode.CLAMP));
      c.drawCircle((x1 + x2) / 2, (y1 + y2) / 2, R * .7f, core);
      if (getVisibility() == VISIBLE && VoiceStage.this.getVisibility() == VISIBLE) postInvalidateOnAnimation();
    }
  }

  /** Orange ring with a rounded square: press OK to stop listening. */
  private final class StopButton extends View {
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG), sq = new Paint(Paint.ANTI_ALIAS_FLAG); private final RectF r = new RectF();
    StopButton(Context c) { super(c); ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(px(6)); ring.setColor(Tokens.COLOR_ACCENT); sq.setColor(Tokens.COLOR_ACCENT); }
    @Override protected void onDraw(Canvas c) { float w = getWidth(), s = w * .3f; c.drawCircle(w / 2, w / 2, w / 2 - px(5), ring); r.set(w / 2 - s / 2, w / 2 - s / 2, w / 2 + s / 2, w / 2 + s / 2); c.drawRoundRect(r, s * .22f, s * .22f, sq); }
  }
}
