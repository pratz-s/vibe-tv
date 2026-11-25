package tv.home.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Two glass spheres circling each other with a soft white core where they overlap: the "thinking" mark. */
final class Orbs extends View {
  private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG), core = new Paint(Paint.ANTI_ALIAS_FLAG); private final long t0 = System.currentTimeMillis();
  Orbs(Context c, float u) { super(c); setLayerType(LAYER_TYPE_SOFTWARE, null); rim.setStyle(Paint.Style.STROKE); rim.setStrokeWidth(2 * u); rim.setColor(0x59ffffff); core.setMaskFilter(new BlurMaskFilter(10 * u, BlurMaskFilter.Blur.NORMAL)); }
  @Override protected void onDraw(Canvas c) {
    float w = getWidth(), H = getHeight(), R = H * .32f, t = (System.currentTimeMillis() - t0) / 1000f, a = t * 1.6f, cx = w / 2, cy = H / 2, d = R * .55f;
    float x1 = cx + d * (float) Math.cos(a), y1 = cy + d * .6f * (float) Math.sin(a), x2 = cx - d * (float) Math.cos(a), y2 = cy - d * .6f * (float) Math.sin(a);
    for (float[] o : new float[][]{{x1, y1}, {x2, y2}}) { glass.setShader(new RadialGradient(o[0] - R * .3f, o[1] - R * .35f, R * 1.3f, new int[]{0x66ffffff, 0x1affffff, 0x08ffffff}, new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP)); c.drawCircle(o[0], o[1], R, glass); c.drawCircle(o[0], o[1], R, rim); }
    float pulse = .75f + .25f * (float) Math.sin(t * 3f);
    core.setShader(new RadialGradient((x1 + x2) / 2, (y1 + y2) / 2, R * .7f, new int[]{(int) (0xe6 * pulse) << 24 | 0xffffff, 0x00ffffff}, null, Shader.TileMode.CLAMP)); c.drawCircle((x1 + x2) / 2, (y1 + y2) / 2, R * .7f, core);
    if (getVisibility() == VISIBLE) postInvalidateOnAnimation();
  }
}
