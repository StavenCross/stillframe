package dev.stillframe;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class HidivePicksTest {
    public static void main(String[] args) {
        Set<Set<String>> selections = new HashSet<>();
        Random random = new Random(0);
        for (int draw = 0; draw < 20; draw++) {
            List<WatchFeed.Entry> picks = HidivePicks.select(random);
            Set<String> ids = new HashSet<>();
            for (WatchFeed.Entry pick : picks) {
                if (!HidivePicks.isPick(pick) || pick.title.isEmpty()
                        || !pick.poster.startsWith("https://") || pick.progress >= 0
                        || !ids.add(pick.id)) throw new AssertionError("Invalid HIDIVE pick");
            }
            if (ids.size() != 3) throw new AssertionError("Expected three distinct shows");
            selections.add(ids);
        }
        if (selections.size() < 2) throw new AssertionError("Picks should vary");
        List<WatchFeed.Entry> picks = HidivePicks.select(new Random(0));
        WatchFeed.Entry watchNext = new WatchFeed.Entry("7", "Saved title", "", "", "", "", "", "",
                "", AppCatalog.HIDIVE, .5f, 100, "", "", "");
        List<WatchFeed.Entry> real = List.of(watchNext);
        if (HidivePicks.forApp(AppCatalog.HIDIVE, real, picks) != real
                || HidivePicks.forApp(AppCatalog.HIDIVE, List.of(), picks) != picks
                || !HidivePicks.forApp("another.app", null, picks).isEmpty()) {
            throw new AssertionError("Only an empty HIDIVE feed should be replaced");
        }
        System.out.println("HIDIVE picks passed");
    }
}
