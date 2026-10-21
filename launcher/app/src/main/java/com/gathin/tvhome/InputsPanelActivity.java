package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

/**
 * The Inputs menu floating over whatever is playing, for the remote's Input button where nothing answers
 * VIEW_INPUTS (the panel lived in the stock launcher: Hisense A4, Android 9). Translucent and in a task of its
 * own, like QuickSettingsActivity, so the HDMI picture keeps playing behind it and Back returns to it. A card at
 * the right edge, with the input playing now ticked and focused. Pressing Input again closes the panel.
 */
public final class InputsPanelActivity extends Activity {

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        InputsMenu menu = new InputsMenu(this, Config.load(this));
        if (menu.sourcePanelOnly()) {
            menu.openSourcePanel();
            finish();
            return;
        }
        setContentView(R.layout.activity_inputs_panel);
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        LinearLayout rows = findViewById(R.id.rows);
        if (!menu.fill(rows, this::close, true)) {
            finish();
            return;
        }
        menu.focusRow(rows).requestFocus();
        // Slides in from the right edge, like a sidebar.
        View panel = findViewById(R.id.panel);
        panel.setAlpha(0f);
        panel.setTranslationX(40 * getResources().getDisplayMetrics().density);
        panel.animate().alpha(1f).translationX(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    /** Closes without the exit animation, which would flash over the input taking the screen. */
    private void close() {
        finish();
        overridePendingTransition(0, 0);
    }

    /** Input pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (the input just picked, Home, an app) closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }
}
