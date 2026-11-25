package tv.home.app;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.util.Random;

/**
 * Tiles made of pixels. dissolve(): a tile breaks into pixels that crumble and fall off the screen (removed from a list).
 * form(): pixels fly in from above and assemble into the tile, then the real poster settles in (added to a list).
 * The tile is drawn once into a bitmap and cut into small squares, so it looks like the poster itself coming apart.
 */
final class PixelFx extends View {
  private static final long DISSOLVE_MS = 1100, FORM_MS = 950;
  private final boolean forming; private final int cols, rows; private final float cell;
  private final int[] color; private final float[] delay, vx, vy, sx, sy; private final Paint p = new Paint();
  private final long start = SystemClock.uptimeMillis(); private final Runnable done; private final View target; private boolean finished;

  private PixelFx(Context c, View target, Bitmap snap, float cell, boolean forming, Runnable done) {
    super(c); this.target = target; this.cell = cell; this.forming = forming; this.done = done;
    cols = Math.max(1, (int) Math.ceil(snap.getWidth() / cell)); rows = Math.max(1, (int) Math.ceil(snap.getHeight() / cell)); int n = cols * rows;
    color = new int[n]; delay = new float[n]; vx = new float[n]; vy = new float[n]; sx = new float[n]; sy = new float[n]; Random r = new Random();
    for (int j = 0; j < rows; j++) for (int i = 0; i < cols; i++) { int k = j * cols + i;
      int px = Math.min(snap.getWidth() - 1, (int) ((i + .5f) * cell)), py = Math.min(snap.getHeight() - 1, (int) ((j + .5f) * cell)); color[k] = snap.getPixel(px, py);
      if (forming) { delay[k] = .45f * r.nextFloat() + .2f * (1f - j / (float) rows); sx[k] = (r.nextFloat() - .5f) * cell * 30; sy[k] = -(260 + r.nextFloat() * 520) * cell / 10f; }
      // dissolving starts at the top-right corner and sweeps across, each pixel with its own push and fall
      else { delay[k] = .35f * ((cols - i) / (float) cols * .5f + j / (float) rows * .5f) + .15f * r.nextFloat(); vx[k] = (r.nextFloat() - .5f) * cell * 9; vy[k] = -r.nextFloat() * cell * 6; } }
    snap.recycle(); setWillNotDraw(false);
  }

  @Override protected void onDraw(Canvas c) {
    float t = (SystemClock.uptimeMillis() - start) / (float) (forming ? FORM_MS : DISSOLVE_MS), g = cell * 140; boolean alive = false;
    for (int j = 0; j < rows; j++) for (int i = 0; i < cols; i++) { int k = j * cols + i; float x0 = i * cell, y0 = j * cell;
      float e = Math.max(0, Math.min(1, (t - delay[k]) / .55f));
      if (forming) { if (e < 1) alive = true; float q = 1 - (1 - e) * (1 - e) * (1 - e); float x = x0 + sx[k] * (1 - q), y = y0 + sy[k] * (1 - q); p.setColor(color[k]); p.setAlpha((int) (255 * Math.min(1, e * 2.5f) * Color.alpha(color[k]) / 255f)); c.drawRect(x, y, x + cell * .92f, y + cell * .92f, p); }
      else { if (e <= 0) { p.setColor(color[k]); c.drawRect(x0, y0, x0 + cell, y0 + cell, p); alive = true; continue; } if (e >= 1) continue; alive = true; float s = e * .9f;
        float x = x0 + vx[k] * s, y = y0 + vy[k] * s + g * s * s, size = cell * (1 - .45f * e); p.setColor(color[k]); p.setAlpha((int) (255 * (1 - e * e) * Color.alpha(color[k]) / 255f)); c.drawRect(x, y, x + size, y + size, p); } }
    if (alive && t < 2.2f) { postInvalidateOnAnimation(); return; }
    if (finished) return; finished = true;
    if (forming) target.animate().alpha(1f).setDuration(180).withEndAction(this::remove).start(); else remove();
  }
  private void remove() { ViewGroup v = (ViewGroup) getParent(); if (v != null) v.removeView(this); if (done != null) done.run(); }

  private static void run(ViewGroup stage, View target, boolean forming, Runnable done) {
    if (target == null || target.getWidth() == 0 || stage == null) { if (forming && target != null) target.setAlpha(1f); if (done != null) done.run(); return; }
    Bitmap snap; try { snap = Bitmap.createBitmap(target.getWidth(), target.getHeight(), Bitmap.Config.ARGB_8888); float a = target.getAlpha(); target.setAlpha(1f); target.draw(new Canvas(snap)); target.setAlpha(a); }
    catch (Throwable e) { if (forming) target.setAlpha(1f); if (done != null) done.run(); return; }
    int[] a = new int[2], b = new int[2]; target.getLocationOnScreen(a); stage.getLocationOnScreen(b);
    float cell = Math.max(6f, target.getWidth() / 34f);
    PixelFx fx = new PixelFx(stage.getContext(), target, snap, cell, forming, done);
    FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(target.getWidth(), target.getHeight()); lp.leftMargin = a[0] - b[0]; lp.topMargin = a[1] - b[1];
    fx.setScaleX(target.getScaleX()); fx.setScaleY(target.getScaleY()); fx.setPivotX(target.getPivotX()); fx.setPivotY(target.getPivotY()); fx.setTranslationZ(3000);
    target.setAlpha(0f); stage.addView(fx, lp);
  }
  /** The tile breaks into pixels that fall away; done() runs when they're gone. */
  static void dissolve(ViewGroup stage, View tile, Runnable done) { run(stage, tile, false, done); }
  /** Pixels fly in and form the tile, then the real tile fades in under them. */
  static void form(ViewGroup stage, View tile) { run(stage, tile, true, null); }
}
