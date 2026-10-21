package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.content.pm.ResolveInfo;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

/**
 * The now-playing card above the dock: artwork, title, artist and previous / play-pause / next for the media
 * session that is playing (Spotify, or any app with a session). With nothing playing it offers the music app
 * (config.xml music_app): Play sends it the play media button, which resumes where it left off, and opens the
 * app when that starts nothing. Sessions come from MediaListener where Android lets it be enabled; where it
 * does not (low-RAM TVs keep notification access for the system's own listener), the card connects to the music
 * app's MediaBrowserService instead and follows that app only.
 * Listens only while Home is started, like the rest of Home.
 */
final class NowPlaying {

    /** What Home does around the card. */
    interface Host {
        void focusTopBar();

        void focusDock();

        /** A card button took focus: the tile highlight steps aside. */
        void onCardFocused();
    }

    /** Play sent to a music app that was not running: open it if no session has started by then. */
    private static final long OPEN_IF_SILENT_MS = 2500L;
    /** Spotify reports "stopped" while it changes track; the card keeps showing it as playing meanwhile. */
    private static final long SKIP_GRACE_MS = 3000L;
    /** Spotify's TV app puts itself in front when Play starts it; Home comes back once it is playing. */
    private static final long BRING_BACK_WINDOW_MS = 6000L;
    private static final long BRING_BACK_DELAY_MS = 4000L;

    private final Activity activity;
    private final View card;
    private final ImageView art;
    private final TextView appName;
    private final TextView title;
    private final TextView artist;
    private final ImageView prev;
    private final ImageView play;
    private final ImageView next;
    private final Host host;
    private final String musicApp;
    private final float density;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MediaSessionManager sessions;
    private final ComponentName listener;

    private MediaController controller;
    /** The music app's browser service, used where reading every session is not allowed. */
    private MediaBrowser browser;
    /** There is something to show: a session, or an installed music app to start. */
    private boolean available;
    /** Home wants the card: the dock is on screen with its shelf space free. */
    private boolean wanted;
    private boolean started;
    private long skippingUntil;
    /** Play was just pressed: if another app covers Home before this time, Home returns (bringHomeBack). */
    private long bringBackUntil;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsChanged = this::pick;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            render();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            render();
        }

        @Override
        public void onSessionDestroyed() {
            setController(null);
            render();
        }
    };

    private final Runnable openIfSilent = new Runnable() {
        @Override
        public void run() {
            if (controller == null || !isPlaying()) openMusicApp();
        }
    };

    /** card is the inflated now_playing view, or null in layouts without it: then this does nothing. */
    NowPlaying(Activity activity, View card, String musicApp, Host host) {
        this.activity = activity;
        this.card = card;
        this.host = host;
        this.musicApp = musicApp == null ? "" : musicApp;
        density = activity.getResources().getDisplayMetrics().density;
        sessions = (MediaSessionManager) activity.getSystemService(Context.MEDIA_SESSION_SERVICE);
        listener = new ComponentName(activity, MediaListener.class);
        if (card == null) {
            art = null;
            appName = title = artist = null;
            prev = play = next = null;
            return;
        }
        art = card.findViewById(R.id.npArt);
        appName = card.findViewById(R.id.npApp);
        title = card.findViewById(R.id.npTitle);
        artist = card.findViewById(R.id.npArtist);
        prev = card.findViewById(R.id.npPrev);
        play = card.findViewById(R.id.npPlay);
        next = card.findViewById(R.id.npNext);
        float radius = 12 * density;
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        art.setClipToOutline(true);
        card.setAlpha(0f);
        card.setVisibility(View.GONE);

        prev.setOnClickListener(v -> skip(false));
        next.setOnClickListener(v -> skip(true));
        play.setOnClickListener(v -> playPause());
        View.OnKeyListener keys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    host.focusTopBar();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    host.focusDock();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return v == prev;   // the row's ends keep focus instead of jumping to a tile
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    return v == next;
                default:
                    return false;
            }
        };
        View.OnFocusChangeListener focus = (v, has) -> {
            if (has) host.onCardFocused();
        };
        for (View button : new View[]{prev, play, next}) {
            button.setOnKeyListener(keys);
            button.setOnFocusChangeListener(focus);
        }
    }

    void start() {
        if (card == null || started) return;
        started = true;
        List<MediaController> list;
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler);
            list = sessions.getActiveSessions(listener);
        } catch (SecurityException notAllowed) {
            // MediaListener is not enabled (and cannot be on low-RAM TVs): follow the music app through its
            // browser service. Until it answers, the card offers the app.
            render();
            connectBrowser();
            return;
        }
        pick(list);
    }

    private void connectBrowser() {
        ComponentName service = browserService();
        if (service == null) return;
        browser = new MediaBrowser(activity, service, new MediaBrowser.ConnectionCallback() {
            @Override
            public void onConnected() {
                if (browser == null) return;
                setController(new MediaController(activity, browser.getSessionToken()));
                render();
            }

            @Override
            public void onConnectionSuspended() {
                setController(null);
                render();
            }

            @Override
            public void onConnectionFailed() {
                Log.w("TvHome.NowPlaying", musicApp + " refused its media browser connection");
            }
        }, null);
        browser.connect();
    }

    private ComponentName browserService() {
        if (musicApp.isEmpty()) return null;
        Intent find = new Intent("android.media.browse.MediaBrowserService").setPackage(musicApp);
        List<ResolveInfo> found = activity.getPackageManager().queryIntentServices(find, 0);
        if (found.isEmpty()) return null;
        return new ComponentName(found.get(0).serviceInfo.packageName, found.get(0).serviceInfo.name);
    }

    void stop() {
        if (card == null || !started) return;
        started = false;
        if (SystemClock.uptimeMillis() < bringBackUntil) {
            bringBackUntil = 0;
            handler.postDelayed(this::bringHomeBack, BRING_BACK_DELAY_MS);
        }
        handler.removeCallbacks(openIfSilent);
        sessions.removeOnActiveSessionsChangedListener(sessionsChanged);
        setController(null);
        if (browser != null) {
            browser.disconnect();
            browser = null;
        }
    }

    /** The dock is in place with its top at dockTop (root coordinates): the card sits just above it. */
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

    /** Up from the dock or Down from the top bar: focus the card's play button if the card is up. */
    boolean focus() {
        if (card == null || card.getVisibility() != View.VISIBLE) return false;
        return play.requestFocus();
    }

    private void place(float dockTop) {
        if (card.getHeight() == 0) {
            card.post(() -> place(dockTop));
            return;
        }
        // The dock band reaches 20dp above its tiles (DockDecoration); keep a clear gap above the band.
        card.setY(dockTop - 48 * density - card.getHeight());
    }

    private void updateVisibility() {
        boolean show = wanted && available && started;
        if (show && card.getVisibility() != View.VISIBLE) {
            card.setVisibility(View.VISIBLE);
            card.animate().cancel();
            card.animate().alpha(1f).setDuration(220).start();
        } else if (!show && card.getVisibility() == View.VISIBLE) {
            if (card.hasFocus()) host.focusDock();
            card.animate().cancel();
            card.animate().alpha(0f).setDuration(160)
                    .withEndAction(() -> card.setVisibility(View.GONE)).start();
        }
    }

    /** The session that is playing; else the music app's; else the most recent one. */
    private void pick(List<MediaController> list) {
        if (list == null) list = Collections.emptyList();
        MediaController best = null;
        for (MediaController c : list) {
            PlaybackState s = c.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                best = c;
                break;
            }
        }
        if (best == null) {
            for (MediaController c : list) {
                if (musicApp.equals(c.getPackageName())) {
                    best = c;
                    break;
                }
            }
        }
        if (best == null && !list.isEmpty()) best = list.get(0);
        setController(best);
        render();
    }

    private void setController(MediaController c) {
        if (controller != null) controller.unregisterCallback(callback);
        controller = c;
        if (c != null) c.registerCallback(callback, handler);
    }

    private boolean isPlaying() {
        if (SystemClock.uptimeMillis() < skippingUntil) return true;
        PlaybackState s = controller == null ? null : controller.getPlaybackState();
        if (s == null) return false;
        switch (s.getState()) {
            case PlaybackState.STATE_PLAYING:
            case PlaybackState.STATE_BUFFERING:
            case PlaybackState.STATE_CONNECTING:
            case PlaybackState.STATE_SKIPPING_TO_NEXT:
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM:
                return true;
            default:
                return false;
        }
    }

    /** The music app took the screen to start playing: put Home back in front, the music keeps going. */
    private void bringHomeBack() {
        if (started) return;   // Home is already back
        try {
            activity.startActivity(new Intent(activity, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    private void render() {
        if (card == null) return;
        MediaMetadata m = controller == null ? null : controller.getMetadata();
        CharSequence trackTitle = m == null ? null : m.getText(MediaMetadata.METADATA_KEY_TITLE);
        if (controller != null && !TextUtils.isEmpty(trackTitle)) {
            handler.removeCallbacks(openIfSilent);
            CharSequence by = m.getText(MediaMetadata.METADATA_KEY_ARTIST);
            if (TextUtils.isEmpty(by)) by = m.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            title.setText(trackTitle);
            artist.setText(by);
            artist.setVisibility(TextUtils.isEmpty(by) ? View.GONE : View.VISIBLE);
            appName.setText(appLabel(controller.getPackageName()));
            Bitmap picture = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (picture == null) picture = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (picture == null) picture = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            if (picture != null) art.setImageBitmap(picture);
            else art.setImageDrawable(appIcon(controller.getPackageName()));
            available = true;
        } else if (installed(musicApp)) {
            // Nothing playing: offer the music app, Play resumes it.
            appName.setText(appLabel(musicApp));
            title.setText(R.string.now_playing_idle);
            artist.setVisibility(View.GONE);
            art.setImageDrawable(appIcon(musicApp));
            available = true;
        } else {
            available = false;
        }
        boolean playing = isPlaying();
        play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        play.setContentDescription(activity.getString(playing ? R.string.media_pause : R.string.media_play));
        updateVisibility();
    }

    private void playPause() {
        if (controller != null && controller.getMetadata() != null) {
            if (isPlaying()) {
                skippingUntil = 0;
                controller.getTransportControls().pause();
            } else {
                bringBackUntil = SystemClock.uptimeMillis() + BRING_BACK_WINDOW_MS;
                controller.getTransportControls().play();
            }
            return;
        }
        // No session: wake the music app with the play media button, the way a remote's Play key would.
        if (!sendMediaButton(KeyEvent.KEYCODE_MEDIA_PLAY)) {
            openMusicApp();
            return;
        }
        handler.removeCallbacks(openIfSilent);
        handler.postDelayed(openIfSilent, OPEN_IF_SILENT_MS);
    }

    private void skip(boolean forward) {
        if (controller == null) {
            openMusicApp();
            return;
        }
        if (isPlaying()) skippingUntil = SystemClock.uptimeMillis() + SKIP_GRACE_MS;
        if (forward) controller.getTransportControls().skipToNext();
        else controller.getTransportControls().skipToPrevious();
        handler.postDelayed(this::render, SKIP_GRACE_MS + 100);
    }

    private boolean sendMediaButton(int keyCode) {
        if (!installed(musicApp)) return false;
        long now = SystemClock.uptimeMillis();
        for (int action : new int[]{KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP}) {
            Intent button = new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(musicApp)
                    .putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(now, now, action, keyCode, 0));
            activity.sendBroadcast(button);
        }
        return true;
    }

    private void openMusicApp() {
        PackageManager pm = activity.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(musicApp);
        if (launch == null) launch = pm.getLaunchIntentForPackage(musicApp);
        if (launch == null) return;
        try {
            activity.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException ignored) {
        }
    }

    private boolean installed(String pkg) {
        if (pkg.isEmpty()) return false;
        try {
            return activity.getPackageManager().getApplicationInfo(pkg, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private CharSequence appLabel(String pkg) {
        try {
            PackageManager pm = activity.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private Drawable appIcon(String pkg) {
        try {
            return activity.getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
