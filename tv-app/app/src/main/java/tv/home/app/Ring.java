package tv.home.app;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Focus outline: the orange accent over a faint warm wash. */
final class Ring extends Drawable {
  private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG), wash = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final float radius, width; private final RectF r = new RectF();
  Ring(float radius, float width) { this.radius = radius; this.width = width;
    edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(width); edge.setColor(Tokens.COLOR_ACCENT);
    wash.setStyle(Paint.Style.FILL); wash.setColor(0x1aff9a3c); }
  @Override public void draw(Canvas c) { Rect b = getBounds(); float i = width / 2; r.set(b.left + i, b.top + i, b.right - i, b.bottom - i); c.drawRoundRect(r, radius, radius, wash); c.drawRoundRect(r, radius, radius, edge); }
  @Override public void setAlpha(int a) { edge.setAlpha(a); wash.setAlpha(a); invalidateSelf(); }
  @Override public void setColorFilter(ColorFilter f) { edge.setColorFilter(f); }
  @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
