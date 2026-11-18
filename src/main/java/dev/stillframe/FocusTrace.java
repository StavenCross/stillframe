package dev.stillframe;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;
import android.view.View;

/** Optional timing of the app-to-renderer path on development builds. */
final class FocusTrace {
    private boolean enabled;

    void configure(ApplicationInfo info, Intent intent) {
        enabled = Build.VERSION.SDK_INT >= 29
                && (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
                && intent != null && intent.getBooleanExtra("profile_focus", false);
    }

    long begin() { return enabled ? System.nanoTime() : 0; }

    void submitted(View view, long started) {
        submitted(view, started, "frame_submit_us");
    }

    void drawerSubmitted(View view, long started) {
        submitted(view, started, "drawer_submit_us");
    }

    private void submitted(View view, long started, String metric) {
        if (started == 0 || Build.VERSION.SDK_INT < 29 || !view.isHardwareAccelerated()) return;
        view.getViewTreeObserver().registerFrameCommitCallback(() -> {
            long elapsed = (System.nanoTime() - started) / 1000;
            Log.d("StillframeFocus", metric + "=" + elapsed);
        });
    }
}
