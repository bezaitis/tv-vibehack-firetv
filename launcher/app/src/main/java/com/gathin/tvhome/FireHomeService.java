package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Fire OS protects Amazon's launcher: it cannot be disabled, and set-home-activity is accepted but ignored, so this
 * launcher can never own Home. Instead this service watches for Amazon's home screen coming to the front (the Home
 * button, boot, an app exiting) and brings this launcher straight back over it. Amazon's home flashes briefly.
 *
 * Amazon's launcher package also holds other screens: Settings (the system Settings intent resolves to
 * com.amazon.tv.launcher/.ui.SettingsActivity), the app library and the profile picker. Those must stay usable, so
 * the service only reacts to the package's HOME activity; any other activity of that package is left alone. A
 * window it cannot name (a dialog or a plain view class) is treated as home, as before.
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

    /**
     * Set by the launcher just before it opens one of Amazon's own screens (Settings, Network), which can show
     * Amazon's home for a moment on the way. Same process, so a static is enough.
     */
    private static volatile long suppressUntil;

    static void suppress(long ms) {
        suppressUntil = SystemClock.uptimeMillis() + ms;
    }

    /** Activities of Amazon's launcher that answer HOME, and every activity it has; empty if unreadable. */
    private final Set<String> homeActivities = new HashSet<>();
    private final Set<String> launcherActivities = new HashSet<>();

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        loadActivities();
    }

    private void loadActivities() {
        homeActivities.clear();
        launcherActivities.clear();
        PackageManager pm = getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(AMAZON_LAUNCHER, PackageManager.GET_ACTIVITIES);
            if (info.activities != null) {
                for (ActivityInfo a : info.activities) addNames(launcherActivities, a);
            }
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(AMAZON_LAUNCHER);
            for (ResolveInfo ri : pm.queryIntentActivities(home, PackageManager.MATCH_DISABLED_COMPONENTS)) {
                if (ri.activityInfo != null) addNames(homeActivities, ri.activityInfo);
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // Unreadable: both sets stay empty and every window counts as home (the original behaviour).
        }
        if (homeActivities.isEmpty()) launcherActivities.clear();   // can't tell home apart: don't filter at all
    }

    private static void addNames(Set<String> set, ActivityInfo a) {
        set.add(a.name);
        if (a.targetActivity != null) set.add(a.targetActivity);   // an alias reports its target class
    }

    /** True unless the window is a named activity of Amazon's launcher other than its home screen. */
    private boolean isAmazonHome(CharSequence className) {
        if (className == null) return true;
        String name = className.toString();
        if (homeActivities.contains(name)) return true;
        return !launcherActivities.contains(name);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !AMAZON_LAUNCHER.contentEquals(pkg)) return;
        if (!isAmazonHome(event.getClassName())) return;   // Settings, app library, profiles: leave them be
        long now = SystemClock.uptimeMillis();
        if (now < suppressUntil) return;   // the launcher just opened an Amazon screen on purpose
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
