package tv.home.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.format.DateFormat;
import android.view.View;
import java.util.Calendar;

/** A dot-matrix clock that sits quietly in the background. */
final class PixelClock extends View {
  /** 5x7 digits, one string per row, '#' is a lit dot. */
  private static final String[][] DIGITS = {
    {".###.","#...#","#..##","#.#.#","##..#","#...#",".###."}, {"..#..",".##..","..#..","..#..","..#..","..#..",".###."},
    {".###.","#...#","....#","...#.","..#..",".#...","#####"}, {"#####","...#.","..#..","...#.","....#","#...#",".###."},
    {"...#.","..##.",".#.#.","#..#.","#####","...#.","...#."}, {"#####","#....","####.","....#","....#","#...#",".###."},
    {"..##.",".#...","#....","####.","#...#","#...#",".###."}, {"#####","....#","...#.","..#..",".#...",".#...",".#..."},
    {".###.","#...#","#...#",".###.","#...#","#...#",".###."}, {".###.","#...#","#...#",".####","....#","...#.",".##.."}};
  private static final String[] COLON = {".",".","#",".","#",".","."};
  private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG), off = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float pitch;
  private final Runnable tick = new Runnable() { public void run() { invalidate(); postDelayed(this, 60000 - System.currentTimeMillis() % 60000 + 50); } };

  PixelClock(Context c, float pitchPx) { super(c); pitch = pitchPx; lit.setColor(Tokens.COLOR_INK); off.setColor(0x26f4f1ea); }

  /** Columns across: four digits of 5, a colon of 1, and gaps of 1 between. */
  private static final int COLS = 5 * 4 + 1 + 5;
  @Override protected void onMeasure(int w, int h) { setMeasuredDimension(Math.round(COLS * pitch), Math.round(7 * pitch)); }
  @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); post(tick); }
  @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }

  @Override protected void onDraw(Canvas c) {
    Calendar now = Calendar.getInstance(); int hr = now.get(DateFormat.is24HourFormat(getContext()) ? Calendar.HOUR_OF_DAY : Calendar.HOUR); if (!DateFormat.is24HourFormat(getContext()) && hr == 0) hr = 12;
    int mi = now.get(Calendar.MINUTE); String[] blank = {".....",".....",".....",".....",".....",".....","....."}; String[][] parts = {hr < 10 && !DateFormat.is24HourFormat(getContext()) ? blank : DIGITS[hr / 10], DIGITS[hr % 10], {COLON[0],COLON[1],COLON[2],COLON[3],COLON[4],COLON[5],COLON[6]}, DIGITS[mi / 10], DIGITS[mi % 10]};
    float r = pitch * .36f; int col = 0;
    for (String[] g : parts) { int w = g[0].length();
      for (int y = 0; y < 7; y++) for (int x = 0; x < w; x++) c.drawCircle((col + x + .5f) * pitch, (y + .5f) * pitch, r, g[y].charAt(x) == '#' ? lit : off);
      col += w + 1; }
  }
}
