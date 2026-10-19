package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS protects Amazon's launcher: it cannot be disabled, and set-home-activity is accepted but ignored, so this
 * launcher can never own Home. Instead this service watches for Amazon's home screen coming to the front (the Home
 * button, boot, an app exiting) and brings this launcher straight back over it. Amazon's home flashes briefly.
 *
 * The service only receives window-state events from Amazon's launcher package (res/xml/fire_home_service.xml), so
 * it does nothing while other apps are in use. Turning it off in Settings > Accessibility, or fire/restore.sh, gives
 * Amazon's home back.
 *
 * Enabled by fire/install.sh:
 *   settings put secure enabled_accessibility_services com.gathin.tvhome/.FireHomeService
 *   settings put secure accessibility_enabled 1
 */
public class FireHomeService extends AccessibilityService {

    static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    /** One launch per Home press: Amazon's launcher can post several window events while it opens. */
    private static final long DEBOUNCE_MS = 600;
    private long lastLaunch;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !AMAZON_LAUNCHER.contentEquals(pkg)) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastLaunch < DEBOUNCE_MS) return;
        lastLaunch = now;
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        } catch (RuntimeException ignored) {
            // Never crash the service: if the launch fails, Amazon's home simply stays up.
        }
    }

    @Override
    public void onInterrupt() {
    }
}
