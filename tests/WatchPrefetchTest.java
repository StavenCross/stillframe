package dev.stillframe;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WatchPrefetchTest {
    public static void main(String[] args) {
        WatchFeed.Entry a1 = entry("example.first", "a1", true);
        WatchFeed.Entry a2 = entry("example.first", "a2", true);
        WatchFeed.Entry a3 = entry("example.first", "a3", true);
        WatchFeed.Entry a4 = entry("example.first", "a4", true);
        WatchFeed.Entry a5 = entry("example.first", "a5", true);
        WatchFeed.Entry b1 = entry("example.second", "b1", true);
        WatchFeed.Entry b2 = entry("example.second", "b2", true);
        WatchFeed.Entry blank = entry("example.second", "blank", false);
        Map<String, List<WatchFeed.Entry>> rows = new LinkedHashMap<>();
        rows.put("example.first", Arrays.asList(a1, a2, a3, a4, a5));
        rows.put("example.second", Arrays.asList(blank, b1, b2));
        rows.put("example.empty", Collections.emptyList());
        equal(Arrays.asList(a1, b1), WatchArtwork.prefetchPlan(rows, 2));
        equal(Arrays.asList(a1, b1, a2, b2, a3, a4), WatchArtwork.prefetchPlan(rows, 40));
        equal(Collections.emptyList(), WatchArtwork.prefetchPlan(rows, 0));
        System.out.println("Watch artwork prefetch checks passed");
    }

    private static WatchFeed.Entry entry(String pkg, String id, boolean hasArt) {
        return new WatchFeed.Entry(id, "Fictional title", "", "", "", "", "", "", "",
                pkg, -1f, 0, "", hasArt ? "https://example.invalid/" + id : "", "");
    }

    private static void equal(List<WatchFeed.Entry> expected, List<WatchFeed.Entry> actual) {
        if (!expected.equals(actual)) throw new AssertionError("Unexpected prefetch order");
    }
}
