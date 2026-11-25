package tv.home.app;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;
import java.util.*;

/**
 * The screen draws itself. A ring of dots opens into the grid, the real layout appears as a blueprint, and a scan
 * line resolves it into the interface. The real UI is fully laid out underneath: this view only covers what the
 * scan line hasn't reached. Everything is two batched draws plus the line, so it stays light on this GPU.
 */
final class Blueprint extends View {
  static final class Part { final RectF box; final String label; final float radius; final boolean text; int at;
    Part(RectF box, String label, float radius, boolean text) { this.box = box; this.label = label; this.radius = radius; this.text = text; } }
  interface Source { List<Part> parts(); }

  private final float u; private final float[] field; private float far;
  private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), mark = new Paint(Paint.ANTI_ALIAS_FLAG), word = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(), fade = new Paint();
  private final Matrix shift = new Matrix(); private final LinearGradient edge;
  private boolean active, brief; private long began; private long go = -1; private Source source; private Runnable done, onGo; private List<Part> parts; private float[] segs = new float[0]; private int segCount;

  Blueprint(Context c, float unit, Typeface mono) {
    super(c); u = unit; setVisibility(GONE);
    List<float[]> p = new ArrayList<>(); int gap = Tokens.BOOT_DOT_GAP, W = Tokens.CANVAS_WIDTH, H = Tokens.CANVAS_HEIGHT;
    for (int y = gap / 2; y < H; y += gap) for (int x = gap / 2; x < W; x += gap) p.add(new float[]{x * u, y * u, (float) Math.hypot(x - W / 2f, y - H / 2f)});
    p.sort((a, b) -> Float.compare(a[2], b[2])); far = p.get(p.size() - 1)[2]; field = new float[p.size() * 2]; for (int i = 0; i < p.size(); i++) { field[i * 2] = p.get(i)[0]; field[i * 2 + 1] = p.get(i)[1]; }
    int ink = Tokens.COLOR_INK & 0xffffff;
    dot.setColor(0x47000000 | ink); dot.setStrokeWidth(2 * u); dot.setStrokeCap(Paint.Cap.SQUARE);
    line.setColor(0xb8000000 | ink); line.setStrokeWidth(1.5f * u); mark.setColor(Tokens.COLOR_INK); mark.setStrokeWidth(2 * u); mark.setStyle(Paint.Style.STROKE);
    word.setColor(0xcc000000 | ink); word.setTextSize(Tokens.CARD_LABEL_SIZE * u); word.setTypeface(mono); fill.setColor(Tokens.COLOR_ROOM);
    int room = Tokens.COLOR_ROOM & 0xffffff; edge = new LinearGradient(0, 0, 0, Tokens.BOOT_FEATHER * u, room, 0xff000000 | room, Shader.TileMode.CLAMP); fade.setShader(edge);
  }

  Warp warp;
  boolean active() { return active; }
  /** How long the current splash has been up. */
  long age() { return active ? SystemClock.uptimeMillis() - began : 0; }

  /** The press that woke the TV can arrive just after the splash starts, so it mustn't count as a skip. */
  boolean settled() { return SystemClock.uptimeMillis() - began > 1200; }

  /** Full sequence. The ring holds until go() says the screen underneath is ready. */
  void start(Source s, Runnable onGo, Runnable done) { begin(false, s, onGo, done); go = -1; }

  /** A short pass over the lower screen, for picks. */
  void brief(Source s, Runnable done) { begin(true, s, null, done); go = began; }

  void go() { if (warp != null && !brief) warp.go(); if (active && go < 0) { go = Math.max(SystemClock.uptimeMillis(), began + (brief ? Tokens.BOOT_RING_MS : 2500)); if (onGo != null) onGo.run(); } }

  private void begin(boolean b, Source s, Runnable g, Runnable d) { brief = b; source = s; onGo = g; done = d; parts = null; segCount = 0; active = true; began = SystemClock.uptimeMillis(); setVisibility(VISIBLE); bringToFront(); setTranslationZ(2000); if (!b && warp != null) { setVisibility(GONE); warp.start(this::stop); } invalidate(); }

  void stop() { if (!active) return; active = false; if (warp != null) warp.stop(); setVisibility(GONE); parts = null; Runnable d = done; done = null; if (d != null) d.run(); }

  private static float ease(float x) { x = Math.min(Math.max(x, 0), 1); return 1 - (1 - x) * (1 - x) * (1 - x); }
  private static float inOut(float x) { x = Math.min(Math.max(x, 0), 1); return x < .5f ? 4 * x * x * x : 1 - (float) Math.pow(-2 * x + 2, 3) / 2; }

  private void trace() {
    parts = source.parts(); List<float[]> s = new ArrayList<>(); float gap = Tokens.BOOT_LINE_GAP * u;
    for (Part p : parts) {
      p.at = s.size(); RectF b = p.box;
      if (p.text) { float y = b.centerY(); for (float x = b.left; x < b.right; x += gap) s.add(new float[]{x, y, x + 1.5f * u, y}); continue; }
      float r = Math.min(p.radius, b.height() / 2);
      run(s, b.left + r, b.top, b.right - r, b.top, gap); arc(s, b.right - r, b.top + r, r, -90); run(s, b.right, b.top + r, b.right, b.bottom - r, gap); arc(s, b.right - r, b.bottom - r, r, 0);
      run(s, b.right - r, b.bottom, b.left + r, b.bottom, gap); arc(s, b.left + r, b.bottom - r, r, 90); run(s, b.left, b.bottom - r, b.left, b.top + r, gap); arc(s, b.left + r, b.top + r, r, 180);
    }
    segCount = s.size(); segs = new float[segCount * 4]; for (int i = 0; i < segCount; i++) System.arraycopy(s.get(i), 0, segs, i * 4, 4);
  }

  private static void run(List<float[]> s, float x0, float y0, float x1, float y1, float gap) { int n = Math.max(1, Math.round((float) Math.hypot(x1 - x0, y1 - y0) / gap)); for (int i = 0; i < n; i++) { float a = i / (float) n, b = (i + .45f) / n; s.add(new float[]{x0 + (x1 - x0) * a, y0 + (y1 - y0) * a, x0 + (x1 - x0) * b, y0 + (y1 - y0) * b}); } }

  private static void arc(List<float[]> s, float cx, float cy, float r, float deg) { if (r <= 0) return; for (int i = 0; i < 4; i++) { double a = Math.toRadians(deg + i * 22.5), b = a + Math.PI / 14; s.add(new float[]{cx + r * (float) Math.cos(a), cy + r * (float) Math.sin(a), cx + r * (float) Math.cos(b), cy + r * (float) Math.sin(b)}); } }

  // ---- Matrix rain in LED dots: columns of 3x5 dot digits fall from the top and flicker as they go ----
  private static final String[] DIG = {"111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001", "111100111001111", "111100111101111", "111001010010010", "111101111101111", "111101111001111"};
  private static final int CELL = 30, PITCH = 6; private float[][] pts; private int[] counts; private final Paint rp = new Paint();
  private int col(int i, int k) { int h = i * 73856093 ^ k * 19349663; h ^= h >>> 13; h *= 0x5bd1e995; h ^= h >>> 15; return h & 0x7fffffff; }
  private void rain(Canvas c, long now, float W, float H) {
    float cell = CELL * u, pitch = PITCH * u, el = (now - began) / 1000f; int cols = (int) (W / cell) + 1, rows = (int) (H / cell) + 2;
    if (pts == null) { pts = new float[5][cols * 22 * 15 * 2]; counts = new int[5]; rp.setStrokeCap(Paint.Cap.ROUND); rp.setStrokeWidth(3.2f * u); }
    Arrays.fill(counts, 0);
    for (int i = 0; i < cols; i++) {
      int seed = col(i, 7); float speed = 9 + (seed % 9), len = 8 + (seed >> 4) % 14, start = ((seed >> 9) % 1400) / 1000f;
      float head = (el - start) * speed; if (head < 0) continue; float span = rows + len, h = head % span;
      for (int r = 0; r < len; r++) { int row = (int) h - r; if (row < 0 || row > rows) continue;
        int lvl = r == 0 ? 0 : r < 3 ? 1 : r < len * .45f ? 2 : r < len * .75f ? 3 : 4;
        String g = DIG[col(i * 131 + row, (int) (el * 9) + (r == 0 ? 0 : row)) % 10]; float x0 = i * cell + (cell - 2 * pitch) / 2, y0 = row * cell + (cell - 4 * pitch) / 2;
        float[] arr = pts[lvl]; int n = counts[lvl];
        for (int q = 0; q < 15; q++) if (g.charAt(q) == '1' && n + 2 <= arr.length) { arr[n++] = x0 + (q % 3) * pitch; arr[n++] = y0 + (q / 3) * pitch; }
        counts[lvl] = n; } }
    int[] tint = {0xffffffff, 0xffffc48a, 0xd9ff9a3c, 0x80f5f5f7, 0x33f5f5f7};
    for (int l = 4; l >= 0; l--) if (counts[l] > 0) { rp.setColor(tint[l]); c.drawPoints(pts[l], 0, counts[l], rp); }
  }

  // ---- warp tunnel: glyph streams on the tunnel walls rush towards the viewer, then the centre opens onto the app ----
  private static final int N = 820; private float[] th, rad, zz; private int[] gl; private boolean[] streak; private long last; private final Path hole = new Path();
  private float[][] tp = new float[4][96 * 60 * 15 * 2]; private int[] tc = new int[4]; private float[] lines = new float[N * 4]; private final Paint tpt = new Paint(), tln = new Paint(Paint.ANTI_ALIAS_FLAG), cover = new Paint();
  private boolean tunnel(Canvas c, long now, float W, float H) {
    if (th == null) { th = new float[1]; tpt.setStrokeCap(Paint.Cap.ROUND); tln.setStrokeCap(Paint.Cap.ROUND); cover.setColor(0xff030305); last = now; }
    float el = now - began; last = now;
    float revealAt = go < 0 ? Float.MAX_VALUE : go - began + 500, rv = Math.max(0, Math.min(1, (el - revealAt) / 750f));
    if (rv >= 1) { stop(); return false; }
    float cx = W / 2, cy = H / 2, maxR = (float) Math.hypot(W, H) * .56f, R0 = Math.min(W, H) * .018f;
    // how far we've travelled: slow at first, then pulled in faster and faster
    float sec = el / 1000f, travel = sec * .55f + 1.4f * sec * sec * .25f + rv * 2.2f;
    hole.reset(); float hr = (float) Math.hypot(W, H) * .6f * rv * rv; if (hr > 0) hole.addCircle(cx, cy, hr, Path.Direction.CW);
    int save = c.save(); if (hr > 0) c.clipOutPath(hole);
    c.drawRect(0, 0, W, H, cover);
    Arrays.fill(tc, 0);
    // walls: characters on a polar grid, turned to run along their spoke, spaced geometrically in depth
    final int A = 80; final float Q = 1.14f; int K = (int) Math.ceil(Math.log(maxR / R0) / Math.log(Q)) + 1; float ph = (travel * 6f) % 1f; int base = (int) Math.floor(travel * 6f);
    for (int a = 0; a < A; a++) {
      double ang = (a + .5) * Math.PI * 2 / A; float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
      float jit = (col(a, 29) % 1000) / 1000f; float pa = ph + jit; int sh = pa >= 1 ? 1 : 0; pa -= sh;
      for (int k = 0; k < K; k++) { float r = R0 * (float) Math.pow(Q, k + pa); if (r > maxR) break; int id = k - base - sh;
        int hsh = col(a * 1031 + id, 5); if (hsh % 9 == 0) continue;
        float fr = r / maxR, pitch = Math.max(.9f * u, r * .0105f);
        int b = fr < .12f ? 0 : fr < .3f ? 1 : fr < .6f ? 2 : 3;
        String g = DIG[col(a + id * 17, (int) (el / 110) + (hsh & 7)) % 10]; float[] arr = tp[b]; int n = tc[b];
        float ox = cx + ca * r, oy = cy + sa * r;
        for (int q2 = 0; q2 < 15; q2++) if (g.charAt(q2) == '1' && n + 2 <= arr.length) { float lx = (q2 % 3 - 1) * pitch, ly = (q2 / 3 - 2) * pitch; arr[n++] = ox + ca * ly - sa * lx; arr[n++] = oy + sa * ly + ca * lx; }
        tc[b] = n; } }
    int[] colr = {0x55ff9a3c, 0x99ff9a3c, 0xccffae5c, 0xf2ffd3a6}; float[] sz = {1.1f, 1.9f, 3.1f, 4.8f};
    for (int b = 0; b < 4; b++) if (tc[b] > 0) { tpt.setColor(colr[b]); tpt.setStrokeWidth(sz[b] * u); c.drawPoints(tp[b], 0, tc[b], tpt); }
    // neon dashes flying outward along the spokes
    int ln = 0; final int M = 90;
    for (int m = 0; m < M; m++) { int hsh = col(m, 11); double ang = ((hsh % A) + .5) * Math.PI * 2 / A; float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
      float f = ((travel * (5f + (hsh >> 8) % 4) + (hsh >> 12) % 100 / 10f) % (float) K), r = R0 * (float) Math.pow(Q, f); if (r > maxR || r < R0 * 3) continue;
      float len = r * .16f; if (ln + 4 <= lines.length) { lines[ln++] = cx + ca * r; lines[ln++] = cy + sa * r; lines[ln++] = cx + ca * (r + len); lines[ln++] = cy + sa * (r + len); } }
    if (ln > 0) { tln.setColor(0x40ff9a3c); tln.setStrokeWidth(14f * u); c.drawLines(lines, 0, ln, tln); tln.setColor(0x99ffb46e); tln.setStrokeWidth(6f * u); c.drawLines(lines, 0, ln, tln); tln.setColor(0xfffff4e8); tln.setStrokeWidth(2.4f * u); c.drawLines(lines, 0, ln, tln); }
    Paint shade = new Paint(); shade.setShader(new RadialGradient(cx, cy, Math.min(W, H) * .22f, new int[]{0xff030305, 0x00030305}, null, Shader.TileMode.CLAMP)); c.drawCircle(cx, cy, Math.min(W, H) * .22f, shade);
    c.restoreToCount(save);
    if (hr > 0) { Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG); ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(6 * u); ring.setColor(0x99ffb46e); c.drawCircle(cx, cy, hr, ring); }
    return true;
  }

  @Override protected void onDraw(Canvas c) {
    if (!active) return;
    long now = SystemClock.uptimeMillis(); float W = getWidth(), H = getHeight();
    int ring = Tokens.BOOT_RING_MS, fieldMs = Tokens.BOOT_FIELD_MS, mapMs = brief ? Tokens.BOOT_SHORT_MS / 2 : Tokens.BOOT_MAP_MS, scanMs = brief ? Tokens.BOOT_SHORT_MS : Tokens.BOOT_SCAN_MS;
    float t = go < 0 ? Math.min(now - began, ring) : ring + now - go - (brief ? ring : 0) + 0f;
    if (go >= 0 && !brief) t = ring + (now - go);
    float lead = brief ? 200 : ring + fieldMs / 2f, scanAt = lead + mapMs + (brief ? 0 : Tokens.BOOT_HOLD_MS);
    if (t > scanAt + scanMs) { stop(); return; }
    if (brief && parts == null && t >= lead) trace();
    if (!brief) { if (warp == null && tunnel(c, now, W, H)) invalidate(); return; }
    float top = brief ? (Tokens.HOME_INFO_BOTTOM - 400) * u : 0, y = top + inOut((t - scanAt) / scanMs) * (H - top), f = Tokens.BOOT_FEATHER * u;
    c.drawRect(0, y, W, H, fill);
    if (y > top) { shift.setTranslate(0, y - f); edge.setLocalMatrix(shift); c.drawRect(0, y - f, W, y, fade); }
    int save = c.save(); c.clipRect(0, y, W, H);
    if (!brief) rain(c, now, W, H);
    if (false) {
      float spin = (now - began) / 2600f, open = 1 - ease((t - ring) / (fieldMs * .7f));
      if (open > 0) { float r = Tokens.BOOT_RING_RADIUS * u * (1 + (1 - open) * 1.2f) * ease(Math.max(now - began, 1) / (ring / 1.4f)); for (int i = 0; i < Tokens.BOOT_RING_DOTS; i++) { double a = i * 2 * Math.PI / Tokens.BOOT_RING_DOTS + spin; boolean big = i % 9 == 0; mark.setStyle(Paint.Style.FILL); mark.setAlpha(Math.round(open * (big ? 230 : 128))); c.drawCircle(W / 2 + r * (float) Math.cos(a), H / 2 + r * (float) Math.sin(a), (big ? 3.5f : 2f) * u, mark); } mark.setStyle(Paint.Style.STROKE); mark.setAlpha(255); }
      float wave = ease((t - ring * .8f) / fieldMs) * 1.02f; int n = Math.round(field.length / 2f * wave * wave); if (n > 0) c.drawPoints(field, 0, Math.min(n, field.length / 2) * 2, dot);
    }
    if (parts != null && segCount > 0) {
      int n = Math.min(segCount, Math.round(ease((t - lead) / mapMs) * segCount)); if (n > 0) c.drawLines(segs, 0, n * 4, line);
      if (!brief) { Part cur = null; for (Part p : parts) if (p.label != null && p.at <= n) cur = p;
        if (cur != null) { float m = 10 * u, e = 14 * u, x0 = cur.box.left - m, y0 = cur.box.top - m, x1 = cur.box.right + m, y1 = cur.box.bottom + m;
          float[][] k = {{x0, y0, 1, 1}, {x1, y0, -1, 1}, {x0, y1, 1, -1}, {x1, y1, -1, -1}}; for (float[] q : k) { c.drawLine(q[0], q[1], q[0] + q[2] * e, q[1], mark); c.drawLine(q[0], q[1], q[0], q[1] + q[3] * e, mark); }
          c.drawText(cur.label + " " + Math.round(cur.box.width() / u) + "×" + Math.round(cur.box.height() / u), Math.min(x0, W - 340 * u), Math.max(y0 - 12 * u, 30 * u), word); } }
    }
    c.restoreToCount(save);
    if (t >= scanAt) { for (float x = 0; x < W; x += 10 * u) c.drawLine(x, y, x + 3 * u, y, mark); mark.setStyle(Paint.Style.FILL); c.drawCircle(Tokens.SAFE_LEFT * u, y, 4 * u, mark); c.drawCircle(W - Tokens.SAFE_RIGHT * u, y, 4 * u, mark); mark.setStyle(Paint.Style.STROKE); }
    invalidate();
  }
}
