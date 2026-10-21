package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so MediaSessionManager.getActiveSessions lets Home read what is playing (NowPlaying): Android ties
 * that to an enabled notification listener. It overrides nothing, so no notification is ever read. Enabled over
 * ADB, see my-tv/README.md; without it the now-playing card simply stays hidden.
 */
public class MediaListener extends NotificationListenerService {
}
