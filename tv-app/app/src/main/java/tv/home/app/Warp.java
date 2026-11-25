package tv.home.app;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.util.Arrays;

/**
 * The boot: flying into a tunnel whose walls are streams of pixel digits, with smooth light streaks rushing past.
 * It draws on its own thread, so it keeps moving while the app loads underneath. When the app is ready, the centre
 * opens and the home screen shows through.
 */
final class Warp extends SurfaceView implements SurfaceHolder.Callback {
  private static final String[] DIG = {"111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001", "111100111001111", "111100111101111", "111001010010010", "111101111101111", "111101111001111"};
  private final float u; private final Handler main = new Handler(Looper.getMainLooper());
  Runnable onStart, onOpen, onStuck; private int frames; private long fpsAt; private volatile boolean opened; private volatile boolean running, surface; private volatile long began, goAt = -1; private Runnable done; private Thread thread;
  private final float[][] tp = new float[4][80 * 64 * 15 * 2]; private final int[] tc = new int[4]; private final float[][] seg = new float[3][120 * 4];
  private final Paint dots = new Paint(), line = new Paint(Paint.ANTI_ALIAS_FLAG), cover = new Paint(), clear = new Paint(), ring = new Paint(Paint.ANTI_ALIAS_FLAG), shade = new Paint(); private final Path hole = new Path();

  Warp(Context c, float u) {
    super(c); this.u = u; setZOrderOnTop(true); getHolder().setFormat(PixelFormat.TRANSLUCENT); getHolder().setFixedSize(1280, 720); getHolder().addCallback(this);   // drawn at 720p, scaled up by the TV hardware: a steady frame rate on slow TV GPUs setVisibility(GONE);
    dots.setStrokeCap(Paint.Cap.SQUARE); dots.setAntiAlias(false); line.setStrokeCap(Paint.Cap.ROUND); cover.setColor(0xff04060d); clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    ring.setStyle(Paint.Style.STROKE); ring.setColor(0x998fb8ff);
    final java.io.File cache = new java.io.File(c.getCacheDir(), "warp_walls_v1.png");
    new Thread(() -> {   // built once and saved, so later boots load it instantly; frames never wait for it
      try { if (cache.exists()) { Bitmap b = BitmapFactory.decodeFile(cache.getAbsolutePath()); if (b != null) { wallsReadyAt = SystemClock.uptimeMillis(); walls = b; prepVignette(1280, 720); return; } } } catch (Throwable ignored) {}
      buildWalls(1280, 720); wallsReadyAt = SystemClock.uptimeMillis();
      try (java.io.FileOutputStream o = new java.io.FileOutputStream(cache)) { walls.compress(Bitmap.CompressFormat.PNG, 100, o); } catch (Throwable ignored) {}
    }, "warp-walls").start();
  }
  boolean running() { return running; }
  void start(Runnable onDone) { android.util.Log.i(HomeService.T, "warp start"); done = onDone; began = SystemClock.uptimeMillis(); lastAt = 0; virt = 0; goAt = -1; opened = false; running = true; setVisibility(VISIBLE); if (onStart != null) onStart.run(); kick(); }
  /** The app underneath is ready: keep flying a little longer, then open up. */
  void go() { if (goAt < 0) goAt = Math.max(SystemClock.uptimeMillis(), began + 2400); }
  /** Stopped early (a key press, or the app leaving): hide the tunnel and always bring the screen back. */
  void stop() { running = false; main.post(() -> { setVisibility(GONE); if (!opened) { opened = true; if (onOpen != null) onOpen.run(); } }); }

  @Override public void surfaceCreated(SurfaceHolder h) { surface = true; kick(); }
  @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {}
  @Override public void surfaceDestroyed(SurfaceHolder h) { surface = false; }

  private void kick() {
    if (!running || !surface || (thread != null && thread.isAlive())) return;
    thread = new Thread(() -> {
      android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);   // ahead of the home screen loading in the background
      while (running && surface) {
        Canvas c = null; boolean more = true;
        long f0 = SystemClock.uptimeMillis(); try { c = getHolder().lockHardwareCanvas(); if (c != null) more = frame(c); } catch (Exception ignored) {}
        finally { if (c != null) try { getHolder().unlockCanvasAndPost(c); } catch (Exception ignored) {} }
        frames++; if (f0 - fpsAt >= 1000) { android.util.Log.i(HomeService.T, "warp fps " + frames + " draw " + (SystemClock.uptimeMillis() - f0) + "ms"); frames = 0; fpsAt = f0; }
        if (!more) { running = false; main.post(() -> { if (!opened && onOpen != null) onOpen.run(); setVisibility(GONE); Runnable d = done; done = null; if (d != null) d.run(); }); }
      }
    }, "warp");
    thread.start();
  }

  private static int hash(int i, int k) { int h = i * 73856093 ^ k * 19349663; h ^= h >>> 13; h *= 0x5bd1e995; h ^= h >>> 15; return h & 0x7fffffff; }

  // The walls are drawn once into a big image. The tunnel repeats itself every three rings, so zooming into that image
  // forever looks like falling in without end, and each frame is just one image draw plus the streaks.
  private static final float Q = 1.15f; private static final int P = 3; private static final float F = (float) Math.pow(Q, P);
  private volatile Bitmap walls; private long lastAt, virt; private volatile long wallsReadyAt; private final Matrix zoom = new Matrix(); private final Paint img = new Paint(Paint.FILTER_BITMAP_FLAG), vign = new Paint();
  private void buildWalls(int W, int H) {
    int bw = Math.round(W * F), bh = Math.round(H * F); walls = Bitmap.createBitmap(bw, bh, Bitmap.Config.RGB_565); Canvas g = new Canvas(walls); g.drawColor(0xff04060d);
    float cx = bw / 2f, cy = bh / 2f, maxR = (float) Math.hypot(bw, bh) / 2f, R0 = Math.min(W, H) * .018f; Paint d = new Paint(Paint.ANTI_ALIAS_FLAG); final int A = 72;
    for (int a = 0; a < A; a++) { double ang = (a + .5) * Math.PI * 2 / A; float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang), jit = (hash(a, 29) % 1000) / 1000f;
      for (int k = 0; ; k++) { float r = R0 * (float) Math.pow(Q, k + jit); if (r > maxR) break; int key = k % P; int hs = hash(a * 1031 + key, 5); if (hs % 9 == 0) continue;
        float pitch = r * .0105f, rad = Math.max(.6f, pitch * .36f); d.setColor(hs % 5 == 0 ? 0xffdbe9ff : hs % 3 == 0 ? 0xff8fb6ff : 0xff5b86f5); float ox = cx + ca * r, oy = cy + sa * r;
        if (pitch < 1.4f) { g.drawCircle(ox, oy, rad, d); continue; }
        String gl = DIG[hash(a + key * 17, 9) % 10];
        for (int q = 0; q < 15; q++) if (gl.charAt(q) == '1') { float lx = (q % 3 - 1) * pitch, ly = (q / 3 - 2) * pitch; g.drawCircle(ox + ca * ly - sa * lx, oy + sa * ly + ca * lx, rad, d); } } }
    prepVignette(W, H);
  }
  private void prepVignette(int W, int H) {
    vign.setShader(new RadialGradient(W / 2f, H / 2f, (float) Math.hypot(W, H) * .5f, new int[]{0xff04060d, 0xb304060d, 0x3304060d, 0x00000000, 0x6604060d}, new float[]{0f, .1f, .22f, .55f, 1f}, Shader.TileMode.CLAMP));
  }

  private boolean frame(Canvas c) {
    long now = SystemClock.uptimeMillis(); if (lastAt == 0) lastAt = now; virt += Math.min(now - lastAt, 34); lastAt = now; int Wi = c.getWidth(), Hi = c.getHeight(); final float u = Wi / 1920f; float W = Wi, H = Hi, sec = virt / 1000f;

    // safety net: if nobody says "ready" (the TV went off and on mid-boot, say), open up by ourselves after 10 seconds
    if (goAt < 0 && now - began > 10000) { goAt = now; android.util.Log.w(HomeService.T, "warp: no go after 10s, opening anyway"); if (onStuck != null) main.post(onStuck); }
    float rv = goAt < 0 ? 0 : Math.max(0, Math.min(1, (now - goAt - 300) / 750f)); if (rv >= 1) return false;
    if (goAt >= 0 && now > goAt && !opened) { opened = true; if (onOpen != null) main.post(onOpen); }
    float cx = W / 2, cy = H / 2, R0 = Math.min(W, H) * .018f, maxR = (float) Math.hypot(W, H) * .56f;
    // rings passed per second: quick from the start and quicker as we're pulled in
    float travel = sec * 2.2f + sec * sec * .35f + rv * rv * 4f, ph = travel % P, sc = (float) Math.pow(Q, ph) / F;
    c.drawPaint(clear);
    hole.reset(); float hr = (float) Math.hypot(W, H) * .6f * rv * rv; if (hr > 0) hole.addCircle(cx, cy, hr, Path.Direction.CW);
    int save = c.save(); if (hr > 0) c.clipOutPath(hole);
    Bitmap wl = walls; if (wl != null) { float k = Wi / 1280f; img.setAlpha((int) (255 * Math.min(1f, (now - wallsReadyAt) / 300f))); zoom.reset(); zoom.postTranslate(-wl.getWidth() / 2f, -wl.getHeight() / 2f); zoom.postScale(sc * k, sc * k); zoom.postTranslate(cx, cy); c.drawBitmap(wl, zoom, img); }
    if (vign.getShader() != null) c.drawRect(0, 0, W, H, vign);
    // light streaks rushing outward, each fading from a dim tail to a bright head
    int[] ln = new int[3]; final int M = 110, A = 72; float K = (float) (Math.log(maxR / R0) / Math.log(Q));
    for (int m = 0; m < M; m++) { int hs = hash(m, 11); double ang = ((hs % A) + .5) * Math.PI * 2 / A; float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
      float f = (travel * (1.4f + (hs >> 8) % 4 * .35f) + (hs >> 12) % 100 / 4f) % K, r = R0 * (float) Math.pow(Q, f); if (r < R0 * 4) continue;
      float len = r * .22f;
      for (int s2 = 0; s2 < 3; s2++) { float a0 = r + len * s2 / 3f, a1 = r + len * (s2 + 1) / 3f; float[] arr = seg[s2]; if (ln[s2] + 4 <= arr.length) { arr[ln[s2]++] = cx + ca * a0; arr[ln[s2]++] = cy + sa * a0; arr[ln[s2]++] = cx + ca * a1; arr[ln[s2]++] = cy + sa * a1; } } }
    int[][] tone = {{0x143d6bff, 0x333d6bff, 0x556f9dff}, {0x403d6bff, 0x806f9dff, 0xccb5d3ff}, {0x666f9dff, 0xccc9defe, 0xffffffff}}; float[] width = {10f, 5f, 2.4f};
    for (int layer = 1; layer < 3; layer++) { line.setStrokeWidth(width[layer] * u * (layer == 1 ? 1.4f : 1f)); for (int s2 = 0; s2 < 3; s2++) if (ln[s2] > 0) { line.setColor(tone[s2][layer]); c.drawLines(seg[s2], 0, ln[s2], line); } }
    c.restoreToCount(save);
    if (hr > 0) { ring.setStrokeWidth(6 * u); c.drawCircle(cx, cy, hr, ring); }
    return true;
  }
}
