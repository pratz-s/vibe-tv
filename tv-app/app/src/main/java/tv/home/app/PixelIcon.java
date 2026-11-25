package tv.home.app;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;

/**
 * A dock icon drawn as LED dots, in the same language as the weather card: dark glass, a full grid of faint dots,
 * and the app's mark lit in its brand colour. When selected it gets a soft colour wash and a ripple of light
 * spreading out from the centre of the mark.
 */
final class PixelIcon extends View {
  static final int GRID = 19;
  private final boolean[][] mark; private final int color; private final boolean sparkle; private boolean on; private long since;
  private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG), halo = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), wash = new Paint(Paint.ANTI_ALIAS_FLAG), edge = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF box = new RectF(); private final Path clip = new Path();

  PixelIcon(Context c, String app) { super(c); mark = mask(app); color = brand(app); sparkle = app.equals("hotstar"); fill.setColor(0xc70e0e11); edge.setStyle(Paint.Style.STROKE); }
  void select(boolean s) { if (s == on) return; on = s; since = SystemClock.uptimeMillis(); invalidate(); }

  static int brand(String app) { switch (app) { case "prime": return 0xff3496ff; case "hotstar": return 0xffb068ff; case "youtube": return 0xffff3a3a; case "netflix": return 0xffff3030; case "spotify": return 0xff28e16e; case "stremio": return 0xff9678ff; default: return Tokens.COLOR_ACCENT; } }

  @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
    box.set(1, 1, w - 1, h - 1); float R = w * .26f; clip.reset(); clip.addRoundRect(box, R, R, Path.Direction.CW);
    wash.setShader(new RadialGradient(w * .9f, 0, w * 1.1f, new int[]{(color & 0xffffff) | 0x4d000000, (color & 0xffffff) | 0x12000000, color & 0xffffff}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP));
    sheen.setShader(new LinearGradient(0, 0, 0, h * .45f, 0x0dffffff, 0x00ffffff, Shader.TileMode.CLAMP));
  }

  @Override protected void onDraw(Canvas c) {
    float w = getWidth(), h = getHeight(), R = w * .26f, t = (SystemClock.uptimeMillis() - since) / 1000f;
    c.drawPath(clip, fill); c.save(); c.clipPath(clip); if (on) c.drawRect(box, wash); c.drawRect(0, 0, w, h * .45f, sheen); c.restore();
    edge.setStrokeWidth(on ? 2f : 1.5f); edge.setShader(new LinearGradient(0, 0, 0, h, on ? 0x80ffffff : 0x33ffffff, on ? 0x33ffffff : 0x12ffffff, Shader.TileMode.CLAMP)); c.drawRoundRect(box, R, R, edge);
    float pitch = w * .86f / GRID, ox = w * .07f, oy = h * .07f, r = pitch * .34f, mid = GRID / 2f, wave = (t * 7f) % (GRID * 1.2f);
    for (int j = 0; j < GRID; j++) for (int i = 0; i < GRID; i++) {
      float cx = ox + (i + .5f) * pitch, cy = oy + (j + .5f) * pitch;
      if (!mark[j][i]) { dot.setColor(0x0dffffff); c.drawCircle(cx, cy, r * .66f, dot); continue; }
      float boost = on ? Math.max(0, 1 - Math.abs((float) Math.hypot(i - mid, j - mid) - wave) / 1.6f) : 0;
      int baseCol = sparkle ? (rim(i, j) ? 0xffe8b450 : 0xfff5f4fa) : color; int col = mix(baseCol, 0xffffffff, boost * .7f);
      halo.setColor((col & 0xffffff) | ((int) ((on ? 0x55 : 0x33) * (1 + boost)) << 24)); c.drawCircle(cx, cy, r * (on ? 1.9f + boost * .4f : 1.55f), halo);
      dot.setColor(col); c.drawCircle(cx, cy, r * (1 + boost * .15f), dot);
    }
    if (on) postInvalidateOnAnimation();
  }
  /** Gold along the lower-right edges only (where light catches the reference's metal), so thin rays stay white. */
  private boolean on(int i, int j) { return i >= 0 && j >= 0 && i < GRID && j < GRID && mark[j][i]; }
  private boolean rim(int i, int j) { return (!on(i + 1, j) && on(i - 1, j)) || (!on(i, j + 1) && on(i, j - 1)); }
  private static int mix(int a, int b, float f) { return Color.rgb((int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f), (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f), (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f)); }

  /** Each mark as dots: the Amazon arrow is placed by hand; the rest are drawn as shapes and sampled onto the grid. */
  static boolean[][] mask(String app) {
    boolean[][] m = new boolean[GRID][GRID];
    if (app.equals("prime")) { for (int x = 3; x <= 14; x++) { int y = Math.round(11 - (float) Math.pow(x - 8.5, 2) / 11f); m[y][x] = true; m[y + 1][x] = true; }
      int[][] head = {{12, 6}, {13, 6}, {14, 6}, {15, 6}, {15, 7}, {15, 8}, {15, 9}, {14, 7}}; for (int[] p : head) m[p[1]][p[0]] = true; return m; }
    int s = 240; Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ALPHA_8); Canvas g = new Canvas(b); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(0xffffffff);
    Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG); cut.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    switch (app) {
      case "hotstar": { float[][] pts = {{.47f, .07f}, {.52f, .40f}, {.93f, .08f}, {.58f, .47f}, {.80f, .56f}, {.55f, .56f}, {.47f, .95f}, {.42f, .60f}, {.22f, .70f}, {.38f, .52f}, {.05f, .40f}, {.40f, .43f}, {.30f, .22f}, {.44f, .38f}};
        Path st = new Path(); for (int i = 0; i < pts.length; i++) { if (i == 0) st.moveTo(pts[i][0] * s, pts[i][1] * s); else st.lineTo(pts[i][0] * s, pts[i][1] * s); } st.close(); g.drawPath(st, p); break; }
      case "youtube": { g.drawRoundRect(s * .12f, s * .24f, s * .88f, s * .76f, s * .14f, s * .14f, p); Path tri = new Path(); tri.moveTo(s * .4f, s * .35f); tri.lineTo(s * .64f, s * .5f); tri.lineTo(s * .4f, s * .65f); tri.close(); g.drawPath(tri, cut); break; }
      case "netflix": { g.drawRect(s * .28f, s * .14f, s * .43f, s * .86f, p); g.drawRect(s * .57f, s * .14f, s * .72f, s * .86f, p); Path d = new Path(); d.moveTo(s * .28f, s * .14f); d.lineTo(s * .43f, s * .14f); d.lineTo(s * .72f, s * .86f); d.lineTo(s * .57f, s * .86f); d.close(); g.drawPath(d, p); break; }
      case "spotify": { g.drawCircle(s / 2f, s / 2f, s * .4f, p); Paint arc = new Paint(cut); arc.setStyle(Paint.Style.STROKE); arc.setStrokeCap(Paint.Cap.ROUND);
        float[][] arcs = {{.36f, .08f}, {.51f, .07f}, {.64f, .06f}}; for (float[] a : arcs) { arc.setStrokeWidth(s * a[1]); Path q = new Path(); q.moveTo(s * .26f, s * a[0]); q.quadTo(s * .5f, s * (a[0] - .08f), s * .74f, s * (a[0] + .03f)); g.drawPath(q, arc); } break; }
      case "stremio": { Path d = new Path(); d.moveTo(s / 2f, s * .1f); d.lineTo(s * .9f, s / 2f); d.lineTo(s / 2f, s * .9f); d.lineTo(s * .1f, s / 2f); d.close(); g.drawPath(d, p); Path tri = new Path(); tri.moveTo(s * .4f, s * .33f); tri.lineTo(s * .67f, s * .5f); tri.lineTo(s * .4f, s * .67f); tri.close(); g.drawPath(tri, cut); break; }
      default: { p.setTextSize(s * .7f); p.setTextAlign(Paint.Align.CENTER); g.drawText(app.isEmpty() ? "?" : app.substring(0, 1).toUpperCase(), s / 2f, s * .72f, p); }
    }
    float cell = s / (float) GRID;
    for (int j = 0; j < GRID; j++) for (int i = 0; i < GRID; i++) { int sum = 0, n = 0; for (float y = j * cell; y < (j + 1) * cell; y += 2) for (float x = i * cell; x < (i + 1) * cell; x += 2) { sum += Color.alpha(b.getPixel((int) x, (int) y)); n++; } m[j][i] = sum / (float) n / 255f > (app.equals("hotstar") ? .28f : .35f); }
    return m;
  }

  /** A faint dot grid on dark, shown in a poster's place until its artwork arrives. */
  static Drawable placeholderTile(int pitch) {
    int n = Math.max(6, pitch); Bitmap tile = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888); Canvas g = new Canvas(tile); g.drawColor(0xff121216); Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(0x1fffffff); g.drawCircle(n / 2f, n / 2f, Math.max(1.2f, n * .1f), p);
    android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(null, tile); d.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT); return d;
  }
  /** The loading card: the dot grid breathes gently (no glow), in a soft wave across the row, until its artwork arrives. */
  static Drawable placeholder(int pitch) { return new Skeleton(placeholderTile(pitch)); }
  static final class Skeleton extends Drawable {
    private final Drawable tile; private int alpha = 255; private final Paint base = new Paint();
    Skeleton(Drawable tile) { this.tile = tile; }
    @Override protected void onBoundsChange(android.graphics.Rect b) { tile.setBounds(b); }
    @Override public void draw(Canvas c) {
      Object cb = getCallback(); boolean loaded = cb instanceof android.widget.ImageView && ((android.widget.ImageView) cb).getDrawable() != null;
      if (loaded) { tile.setAlpha(alpha); tile.draw(c); return; }          // artwork has arrived: stop breathing, cost nothing
      float off = cb instanceof View ? ((View) cb).getX() / 900f : 0f;     // cards further right breathe a beat later: a wave
      double ph = (SystemClock.uptimeMillis() / 1500.0 - off) * Math.PI * 2; float k = .5f + .5f * (float) Math.sin(ph);
      base.setColor(0xff0b0b0e); base.setAlpha(alpha); c.drawRect(getBounds(), base);   // solid base: the selection glass never shows through
      tile.setAlpha((int) (alpha * (.35f + .45f * k))); tile.draw(c);
      scheduleSelf(this::invalidateSelf, SystemClock.uptimeMillis() + 33);
    }
    @Override public void setAlpha(int a) { alpha = a; }
    @Override public void setColorFilter(android.graphics.ColorFilter f) { tile.setColorFilter(f); }
    @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
  }
  /** The home screen's surface: a deep navy gradient with a very faint dot grid, drawn once. */
  static Drawable room(Context c, int w, int h) {
    Bitmap b = Bitmap.createBitmap(Math.max(1, w / 2), Math.max(1, h / 2), Bitmap.Config.ARGB_8888); Canvas g = new Canvas(b); float W = b.getWidth(), H = b.getHeight();
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setShader(new LinearGradient(0, 0, W * .6f, H, new int[]{0xff0b1224, 0xff07070a, 0xff0c0814}, new float[]{0, .55f, 1}, Shader.TileMode.CLAMP)); g.drawRect(0, 0, W, H, p); p.setShader(null);
    float gp = W / 90f; p.setColor(0x09ffffff); for (float y = gp / 2; y < H; y += gp) for (float x = gp / 2; x < W; x += gp) g.drawCircle(x, y, Math.max(.8f, gp * .12f), p);
    android.graphics.drawable.BitmapDrawable d = new android.graphics.drawable.BitmapDrawable(c.getResources(), b); d.setFilterBitmap(true); return d;
  }
}
