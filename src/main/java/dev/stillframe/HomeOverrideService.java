package dev.stillframe;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

public final class HomeOverrideService extends AccessibilityService {
    private static final String GOOGLE_TV = "com.google.android.apps.tv.launcherx";
    private static final String GOOGLE_HOME = GOOGLE_TV + ".home.HomeActivity";
    private static final long DUPLICATE_WINDOW_MS = 250;

    private long lastRedirect = -1;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        long now = SystemClock.uptimeMillis();
        if (!shouldRedirect(event.getPackageName(), event.getClassName(), now, lastRedirect)) return;
        lastRedirect = now;
        startActivity(new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setClass(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    static boolean shouldRedirect(CharSequence publisher, CharSequence window, long now, long previous) {
        // Only the stock Home activity should redirect. Other Google TV windows can stay open.
        return publisher != null && GOOGLE_TV.contentEquals(publisher)
                && window != null && GOOGLE_HOME.contentEquals(window)
                && (previous < 0 || now - previous >= DUPLICATE_WINDOW_MS);
    }

    @Override public void onInterrupt() {}
}
