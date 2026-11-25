package tv.home.app;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** The small corner mark on a tile: a bookmark for "saved for later", a tick for "seen". */
final class Badge extends Drawable {
  static final int SAVED = 0, SEEN = 1;
  private final int kind; private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), fg = new Paint(Paint.ANTI_ALIAS_FLAG); private final Path p = new Path();
  Badge(int kind, int color) { this.kind = kind; bg.setColor(color); fg.setColor(0xffffffff); fg.setStrokeCap(Paint.Cap.ROUND); fg.setStrokeJoin(Paint.Join.ROUND); }
  @Override public void draw(Canvas c) {
    Rect b = getBounds(); float s = Math.min(b.width(), b.height()), cx = b.exactCenterX(), cy = b.exactCenterY();
    c.drawCircle(cx, cy, s / 2f, bg); p.reset();
    if (kind == SAVED) {   // bookmark: a tall tab with a notch cut into the bottom
      float w = s * .30f, h = s * .42f, x0 = cx - w / 2, y0 = cy - h / 2; fg.setStyle(Paint.Style.FILL);
      p.moveTo(x0, y0); p.lineTo(x0 + w, y0); p.lineTo(x0 + w, y0 + h); p.lineTo(cx, y0 + h * .72f); p.lineTo(x0, y0 + h); p.close(); c.drawPath(p, fg);
    } else {               // tick
      fg.setStyle(Paint.Style.STROKE); fg.setStrokeWidth(s * .09f);
      p.moveTo(cx - s * .19f, cy + s * .01f); p.lineTo(cx - s * .05f, cy + s * .15f); p.lineTo(cx + s * .21f, cy - s * .13f); c.drawPath(p, fg);
    }
  }
  @Override public void setAlpha(int a) { bg.setAlpha(a); fg.setAlpha(a); } @Override public void setColorFilter(ColorFilter f) { fg.setColorFilter(f); }
  @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
