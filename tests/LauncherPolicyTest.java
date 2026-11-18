package dev.stillframe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LauncherPolicyTest {
    public static void main(String[] args) {
        List<String> pins = new ArrayList<>(Arrays.asList("netflix", "prime", "disney"));
        equal("prime", LauncherPolicy.provider("prime", pins));
        equal("netflix", LauncherPolicy.provider("removed", pins));
        equal("", LauncherPolicy.provider("removed", Collections.emptyList()));
        LauncherPolicy.move(pins, 0, 2);
        equal(Arrays.asList("prime", "disney", "netflix"), pins);
        LauncherPolicy.move(pins, 2, 3);
        equal(Arrays.asList("prime", "disney", "netflix"), pins);
        equal(0, LauncherPolicy.step(0, -1, 8));
        equal(7, LauncherPolicy.step(7, 1, 8));
        equal(2, LauncherPolicy.step(1, 1, 8));

        String google = "com.google.android.apps.tv.launcherx";
        String home = google + ".home.HomeActivity";
        equal(true, HomeOverrideService.shouldRedirect(google, home, 30L, -1L));
        equal(false, HomeOverrideService.shouldRedirect(google, home, 100L, 30L));
        equal(true, HomeOverrideService.shouldRedirect(google, home, 420L, 30L));
        equal(false, HomeOverrideService.shouldRedirect(google, google + ".SearchActivity", 420L, 30L));
        equal(false, HomeOverrideService.shouldRedirect("example.unrelated", home, 420L, 30L));
        equal(false, HomeOverrideService.shouldRedirect(google, null, 420L, 30L));
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
}
