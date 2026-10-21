package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The weather card's picture of the sky: a sun or a moon, clouds, and rain, drizzle, snow, fog or lightning under
 * them. Drawn in a 24 x 24 unit box scaled into the bounds, so one instance serves any size; {@link #set} switches
 * the picture without allocating.
 */
final class WeatherIcon extends Drawable {

    enum Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

    private static final float BOX = 24f;

    private final Paint sun = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rays = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint moon = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cloud = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkCloud = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint drop = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flake = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mist = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bolt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path cloudPath = new Path(), moonPath = new Path(), cut = new Path(), boltPath = new Path();

    private Sky sky = Sky.CLEAR;
    private boolean night;
    private int alpha = 255;

    WeatherIcon() {
        sun.setShader(new RadialGradient(10f, 9f, 6f, 0xFFFFE28A, 0xFFFFB02E, Shader.TileMode.CLAMP));
        sunGlow.setShader(new RadialGradient(12f, 12f, 11f, 0x66FFC94D, 0x00FFC94D, Shader.TileMode.CLAMP));
        rays.setColor(0xFFFFC94D);
        rays.setStyle(Paint.Style.STROKE);
        rays.setStrokeCap(Paint.Cap.ROUND);
        rays.setStrokeWidth(1.4f);
        moon.setShader(new LinearGradient(6f, 4f, 18f, 18f, 0xFFF4F1FF, 0xFFB9B3F2, Shader.TileMode.CLAMP));
        cloud.setShader(new LinearGradient(0f, 9f, 0f, 19f, 0xFFFFFFFF, 0xFFC9D3E0, Shader.TileMode.CLAMP));
        darkCloud.setShader(new LinearGradient(0f, 7f, 0f, 17f, 0xFF9AA6B8, 0xFF5E6878, Shader.TileMode.CLAMP));
        drop.setColor(0xFF6EC1FF);
        drop.setStyle(Paint.Style.STROKE);
        drop.setStrokeCap(Paint.Cap.ROUND);
        drop.setStrokeWidth(1.5f);
        flake.setColor(0xFFFFFFFF);
        mist.setColor(0xCCDDE3EA);
        mist.setStyle(Paint.Style.STROKE);
        mist.setStrokeCap(Paint.Cap.ROUND);
        mist.setStrokeWidth(1.6f);
        bolt.setColor(0xFFFFD43B);

        // A cloud as the union of three puffs and a flat base.
        cloudPath.addCircle(9f, 13.5f, 4f, Path.Direction.CW);
        cloudPath.addCircle(14f, 11.5f, 5f, Path.Direction.CW);
        cloudPath.addCircle(18.5f, 14.5f, 3.2f, Path.Direction.CW);
        cloudPath.addRoundRect(5f, 13.5f, 21.7f, 17.7f, 2.1f, 2.1f, Path.Direction.CW);
        cloudPath.op(new Path(), Path.Op.UNION);
        // A crescent: a disc with an offset disc taken out of it.
        moonPath.addCircle(11f, 11f, 6.5f, Path.Direction.CW);
        cut.addCircle(14.5f, 8.5f, 5.5f, Path.Direction.CW);
        moonPath.op(cut, Path.Op.DIFFERENCE);
        boltPath.moveTo(13.5f, 15f);
        boltPath.lineTo(10.5f, 19.5f);
        boltPath.lineTo(12.6f, 19.5f);
        boltPath.lineTo(11.2f, 23.2f);
        boltPath.lineTo(15.4f, 18.2f);
        boltPath.lineTo(13.2f, 18.2f);
        boltPath.lineTo(14.8f, 15f);
        boltPath.close();
    }

    void set(Sky sky, boolean night) {
        if (this.sky == sky && this.night == night) return;
        this.sky = sky;
        this.night = night;
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas c) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        int save = c.save();
        c.translate(b.left, b.top);
        float scale = Math.min(b.width(), b.height()) / BOX;
        c.scale(scale, scale);
        if (alpha < 255) c.saveLayerAlpha(0, 0, BOX, BOX, alpha);
        switch (sky) {
            case CLEAR:
                drawBody(c, 0f, 0f, 1f);
                break;
            case PARTLY:
                drawBody(c, -3.5f, -3.5f, 0.75f);
                c.drawPath(cloudPath, cloud);
                break;
            case CLOUDY:
                c.save();
                c.translate(-2.5f, -4f);
                c.scale(0.8f, 0.8f, 12f, 12f);
                c.drawPath(cloudPath, darkCloud);
                c.restore();
                c.drawPath(cloudPath, cloud);
                break;
            case FOG:
                c.save();
                c.translate(0f, -3f);
                c.drawPath(cloudPath, cloud);
                c.restore();
                c.drawLine(4f, 18f, 20f, 18f, mist);
                c.drawLine(6f, 21f, 18f, 21f, mist);
                break;
            case DRIZZLE:
            case RAIN:
                c.save();
                c.translate(0f, -3.5f);
                c.drawPath(cloudPath, sky == Sky.RAIN ? darkCloud : cloud);
                c.restore();
                float len = sky == Sky.RAIN ? 3.2f : 1.4f;
                for (int i = 0; i < 3; i++) {
                    float x = 8.5f + i * 4f, y = 17f + (i % 2) * 1.6f;
                    c.drawLine(x, y, x - len * 0.4f, y + len, drop);
                }
                break;
            case SNOW:
                c.save();
                c.translate(0f, -3.5f);
                c.drawPath(cloudPath, cloud);
                c.restore();
                for (int i = 0; i < 3; i++) {
                    c.drawCircle(8.5f + i * 4f, 18.5f + (i % 2) * 2f, 1.2f, flake);
                }
                break;
            case STORM:
                c.save();
                c.translate(0f, -4f);
                c.drawPath(cloudPath, darkCloud);
                c.restore();
                c.drawPath(boltPath, bolt);
                break;
        }
        if (alpha < 255) c.restore();
        c.restoreToCount(save);
    }

    /** The sun (rays and a glow) or, at night, the moon, shifted by dx / dy and scaled about the box's centre. */
    private void drawBody(Canvas c, float dx, float dy, float size) {
        c.save();
        c.translate(dx, dy);
        c.scale(size, size, 12f, 12f);
        if (night) {
            c.drawPath(moonPath, moon);
        } else {
            c.drawCircle(12f, 12f, 11f, sunGlow);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                c.drawLine(12f + cos * 7.4f, 12f + sin * 7.4f, 12f + cos * 9.6f, 12f + sin * 9.6f, rays);
            }
            c.drawCircle(12f, 12f, 5.4f, sun);
        }
        c.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
