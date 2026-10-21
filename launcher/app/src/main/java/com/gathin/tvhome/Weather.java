package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The weather card beside the now-playing card: Open-Meteo's current temperature, sky, feel, wind and humidity,
 * and today's low and high, drawn with a WeatherIcon over glass tinted by the weather, for config.xml latitude / longitude (no API key, no account; only the coordinates go out). Off unless
 * weather_card is true and both coordinates are set. Fetches only while Home is started, at most every 30 minutes;
 * with nothing fetched yet the card stays hidden, and a failed fetch is tried again a minute later.
 */
final class Weather {

    private static final String TAG = "TvHome.Weather";
    private static final long REFRESH_MS = 30 * 60_000L;
    /** After a failed fetch (Open-Meteo answers 503 now and then): try again soon instead of in half an hour. */
    private static final long RETRY_MS = 60_000L;
    private static final int TIMEOUT_MS = 10_000;

    private final View card;
    private final TextView temp, city, sky, range, feels, wind, humidity;
    private final WeatherIcon icon = new WeatherIcon();
    /** Over the card's glass: the weather's colour from the left edge, fading out across the card. */
    private final GradientDrawable glow =
            new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0, 0});
    private int glowColor;
    private final float density;
    private final String url;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService fetcher = Executors.newSingleThreadExecutor();
    private final Runnable refresh = this::refresh;
    private boolean started, wanted, loading, loaded;
    private long fetchedAt;

    /** @param card the weatherCard view; null, or the feature off, makes every call a no-op. */
    Weather(Activity activity, View card) {
        String lat = activity.getString(R.string.latitude).trim();
        String lon = activity.getString(R.string.longitude).trim();
        boolean on = card != null && activity.getResources().getBoolean(R.bool.weather_card)
                && !lat.isEmpty() && !lon.isEmpty();
        this.card = on ? card : null;
        density = activity.getResources().getDisplayMetrics().density;
        url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day"
                + "&daily=temperature_2m_max,temperature_2m_min"
                + "&timezone=auto&forecast_days=1";
        if (this.card == null) {
            temp = city = sky = range = feels = wind = humidity = null;
            return;
        }
        temp = card.findViewById(R.id.weatherTemp);
        city = card.findViewById(R.id.weatherCity);
        sky = card.findViewById(R.id.weatherSky);
        range = card.findViewById(R.id.weatherRange);
        feels = card.findViewById(R.id.weatherFeels);
        wind = card.findViewById(R.id.weatherWind);
        humidity = card.findViewById(R.id.weatherHumidity);
        ((ImageView) card.findViewById(R.id.weatherIcon)).setImageDrawable(icon);
        glow.setCornerRadius(24 * density);
        card.setBackground(new LayerDrawable(new Drawable[] {card.getBackground(), glow}));
        city.setText(activity.getString(R.string.weather_city));
        city.setVisibility(TextUtils.isEmpty(city.getText()) ? View.INVISIBLE : View.VISIBLE);
        card.setAlpha(0f);
    }

    void start() {
        if (card == null) return;
        started = true;
        long age = SystemClock.elapsedRealtime() - fetchedAt;
        if (!loaded || age >= REFRESH_MS) refresh();
        else handler.postDelayed(refresh, REFRESH_MS - age);
        updateVisibility();
    }

    void stop() {
        if (card == null) return;
        started = false;
        handler.removeCallbacks(refresh);
        updateVisibility();
    }

    void close() {
        handler.removeCallbacks(refresh);
        fetcher.shutdownNow();
    }

    /** The dock's top is at dockTop (root coordinates): sit level with the now-playing card, bottoms aligned. */
    void showAbove(float dockTop) {
        if (card == null) return;
        wanted = true;
        place(dockTop);
        updateVisibility();
    }

    void hide() {
        if (card == null) return;
        wanted = false;
        updateVisibility();
    }

    private void place(float dockTop) {
        if (card.getHeight() == 0) {
            card.post(() -> place(dockTop));
            return;
        }
        // Same gap above the dock band as NowPlaying.place.
        card.setY(dockTop - 48 * density - card.getHeight());
    }

    private void updateVisibility() {
        boolean show = wanted && started && loaded;
        if (show && card.getVisibility() != View.VISIBLE) {
            card.setVisibility(View.VISIBLE);
            card.animate().cancel();
            card.animate().alpha(1f).setDuration(220).start();
        } else if (!show && card.getVisibility() == View.VISIBLE) {
            card.animate().cancel();
            card.animate().alpha(0f).setDuration(160)
                    .withEndAction(() -> card.setVisibility(View.GONE)).start();
        }
    }

    private void refresh() {
        handler.removeCallbacks(refresh);
        if (!started || loading) return;
        loading = true;
        fetcher.execute(() -> {
            JSONObject result = fetch();
            handler.post(() -> {
                loading = false;
                boolean ok = result != null && show(result);
                if (ok) {
                    loaded = true;
                    fetchedAt = SystemClock.elapsedRealtime();
                    updateVisibility();
                }
                // A failure keeps what is shown and tries again in a minute.
                if (started) handler.postDelayed(refresh, ok ? REFRESH_MS : RETRY_MS);
            });
        });
    }

    private JSONObject fetch() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP " + connection.getResponseCode());
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream body = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) > 0) body.write(buffer, 0, n);
                return new JSONObject(body.toString(StandardCharsets.UTF_8.name()));
            }
        } catch (Exception e) {
            Log.w(TAG, "fetch failed: " + e);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** False when the answer lacks what the card shows, so a bad answer never replaces a good one. */
    private boolean show(JSONObject json) {
        try {
            JSONObject current = json.getJSONObject("current");
            JSONObject daily = json.getJSONObject("daily");
            int now = (int) Math.round(current.getDouble("temperature_2m"));
            int low = (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0));
            int high = (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0));
            WeatherIcon.Sky kind = sky(current.getInt("weather_code"));
            boolean night = current.optInt("is_day", 1) == 0;
            temp.setText(String.format(Locale.ROOT, "%d°", now));
            sky.setText(skyText(kind));
            range.setText(card.getContext().getString(R.string.weather_range, low, high));
            detail(feels, R.string.weather_feels, current, "apparent_temperature");
            detail(wind, R.string.weather_wind, current, "wind_speed_10m");
            detail(humidity, R.string.weather_humidity, current, "relative_humidity_2m");
            icon.set(kind, night);
            tint(glowColor(kind, night));
            return true;
        } catch (Exception e) {
            Log.w(TAG, "unexpected answer: " + e);
            return false;
        }
    }

    /** One of the small lines on the right, rounded; hidden when the answer lacks that field. */
    private void detail(TextView view, int format, JSONObject current, String field) {
        if (current.isNull(field)) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setText(card.getContext().getString(format, (int) Math.round(current.optDouble(field))));
        view.setVisibility(View.VISIBLE);
    }

    /** Fades the card's tint to this colour, so a change of weather shifts the card's mood instead of jumping. */
    private void tint(int color) {
        int from = glowColor;
        glowColor = color;
        if (from == color) return;
        ValueAnimator fade = ValueAnimator.ofArgb(from, color);
        fade.setDuration(from == 0 ? 0 : 900);
        fade.addUpdateListener(a -> glow.setColors(new int[] {(int) a.getAnimatedValue(), 0}));
        fade.start();
    }

    /** The tint for this sky: warm for sun, deep blue for a clear night, grey for cloud, blue for rain. */
    private static int glowColor(WeatherIcon.Sky kind, boolean night) {
        switch (kind) {
            case CLEAR: return night ? 0x664A5BFF : 0x66FFA83A;
            case PARTLY: return night ? 0x555A4FE0 : 0x55F2B544;
            case FOG: return 0x4D9AA5B1;
            case DRIZZLE: return 0x555AA9E6;
            case RAIN: return 0x662F80ED;
            case SNOW: return 0x55BFE3FF;
            case STORM: return 0x668E5CFF;
            default: return 0x4D8A9BB0;
        }
    }

    /** WMO weather interpretation codes, as Open-Meteo documents them, grouped. */
    private static WeatherIcon.Sky sky(int code) {
        if (code == 0) return WeatherIcon.Sky.CLEAR;
        if (code <= 2) return WeatherIcon.Sky.PARTLY;
        if (code == 45 || code == 48) return WeatherIcon.Sky.FOG;
        if (code >= 51 && code <= 57) return WeatherIcon.Sky.DRIZZLE;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return WeatherIcon.Sky.RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return WeatherIcon.Sky.SNOW;
        if (code >= 95) return WeatherIcon.Sky.STORM;
        return WeatherIcon.Sky.CLOUDY;
    }

    private static int skyText(WeatherIcon.Sky kind) {
        switch (kind) {
            case CLEAR: return R.string.weather_clear;
            case PARTLY: return R.string.weather_partly_cloudy;
            case FOG: return R.string.weather_fog;
            case DRIZZLE: return R.string.weather_drizzle;
            case RAIN: return R.string.weather_rain;
            case SNOW: return R.string.weather_snow;
            case STORM: return R.string.weather_storm;
            default: return R.string.weather_overcast;
        }
    }
}
