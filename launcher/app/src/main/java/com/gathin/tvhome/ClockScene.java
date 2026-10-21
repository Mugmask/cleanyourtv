package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.widget.ImageView;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * The clock screensaver's scene (layout dream_clock): the theme's wallpaper, still, with the clock and date in
 * the bottom right corner. Without a wallpaper, black under a slow ambient glow
 * that shifts colour every minute. The clock glides a few pixels every minute, so nothing burns in while it stays
 * where you look for it.
 * Shared by ClockDream (the system screensaver) and ClockActivity (Home left idle).
 */
final class ClockScene {

    private static final long MOVE_MS = 60_000L;
    /** How far the clock may glide from its corner, as a fraction of the screen: about 20 px at 1080p. */
    private static final float NUDGE = 0.01f;
    /** The clock's distance from the bottom right corner, as a fraction of the screen. */
    private static final float MARGIN = 0.05f;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final View content;
    private final GradientDrawable glow;
    private int colorIndex;
    private int glowColor;
    /** The wallpaper behind the clock, or null: then the ambient glow. */
    private final ImageView photo;
    /** The date line, or null in layouts that keep a TextClock for it. */
    private final TextView date;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            moveClock();
            updateDate();
            if (photo == null) shiftGlow();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    /** root is the inflated dream_clock layout. */
    ClockScene(View root) {
        content = root.findViewById(R.id.dreamContent);
        View ambient = root.findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);
        date = root.findViewById(R.id.dreamDate);
        updateDate();
        ImageView view = root.findViewById(R.id.dreamPhoto);
        Bitmap picture = view == null ? null : loadWallpaper(root);
        if (picture != null) {
            view.setImageBitmap(picture);
            view.setVisibility(View.VISIBLE);
            root.findViewById(R.id.dreamShade).setVisibility(View.VISIBLE);
            ambient.setVisibility(View.GONE);
            photo = view;
        } else {
            photo = null;
        }
    }

    /** "Mié 21 Oct": short day and month without the locale's dots, each word capitalised. */
    private void updateDate() {
        if (date != null) date.setText(shortDate(new Date(), Locale.getDefault()));
    }

    static String shortDate(Date when, Locale locale) {
        String raw = new SimpleDateFormat("EEE d MMM", locale).format(when).replace(".", "");
        StringBuilder out = new StringBuilder(raw.length());
        boolean start = true;
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            out.appendCodePoint(start ? Character.toTitleCase(cp) : cp);
            start = Character.isWhitespace(cp);
            i += Character.charCount(cp);
        }
        return out.toString().trim();
    }

    /** The theme's first wallpaper, decoded near screen size in RGB_565 like Home's (about 4 MB at 1080p). */
    private static Bitmap loadWallpaper(View root) {
        Config cfg = Config.load(root.getContext());
        if (!cfg.wallpaperEnabled || cfg.wallpapers.isEmpty()) return null;
        File file = cfg.wallpapers.get(0);
        DisplayMetrics metrics = root.getResources().getDisplayMetrics();
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;
            int sample = WallpaperSize.sampleSize(options.outWidth, options.outHeight,
                    metrics.widthPixels, metrics.heightPixels);
            if (!WallpaperSize.accepted(options.outWidth / sample, options.outHeight / sample)) return null;
            options.inJustDecodeBounds = false;
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    void start() {
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, MOVE_MS);
    }

    void stop() {
        handler.removeCallbacks(tick);
    }

    /** A slow glide to the next spot, never a jump. */
    private void moveClock() {
        float[] at = nextSpot();
        content.animate().x(at[0]).y(at[1]).setDuration(4000).start();
        centreGlow(at[0], at[1]);
    }

    private void placeClock() {
        float[] at = nextSpot();
        content.setX(at[0]);
        content.setY(at[1]);
        centreGlow(at[0], at[1]);
    }

    /** The bottom right corner, MARGIN in from the edges and nudged by up to NUDGE in each direction. */
    private float[] nextSpot() {
        View parent = (View) content.getParent();
        float x = parent.getWidth() * (1 - MARGIN) - content.getWidth()
                + parent.getWidth() * NUDGE * (2 * random.nextFloat() - 1);
        float y = parent.getHeight() * (1 - MARGIN) - content.getHeight()
                + parent.getHeight() * NUDGE * (2 * random.nextFloat() - 1);
        return new float[]{Math.max(0, x), Math.max(0, y)};
    }

    /** The glow behind the clock. */
    private void centreGlow(float x, float y) {
        View parent = (View) content.getParent();
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void shiftGlow() {
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, PALETTE[colorIndex]);
        anim.setDuration(8000);
        anim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        anim.start();
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
