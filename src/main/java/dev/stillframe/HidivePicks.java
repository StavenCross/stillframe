package dev.stillframe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class HidivePicks {
    private static final String ID_PREFIX = "hidive-pick:";
    private static final List<WatchFeed.Entry> CATALOG = Arrays.asList(
            show("34391", "Oshi No Ko", "Drama · 2023",
                    "An idol's twin children grow up inside the entertainment industry.",
                    "qoW507a8RoGbpILCxt2N87USn8H"),
            show("19920", "The Dangers in My Heart", "Romance · 2023",
                    "A withdrawn middle school student grows closer to an outgoing classmate.",
                    "bC8Bx0VHLZzvPCXzuDnvTIbzVIU"),
            show("39506", "Reincarnated as a Sword", "Fantasy · 2022",
                    "A sentient sword teams up with an aspiring adventurer named Fran.",
                    "yZ3VZVc7iyF7KtIjERBGEedVXqA"),
            show("19079", "Call of the Night", "Supernatural · 2022",
                    "A sleepless teenager meets a vampire and discovers a new life after dark.",
                    "rDhwDG2ii8c3nCcfUNp6mogGQ93"));

    private HidivePicks() {}

    static List<WatchFeed.Entry> select(Random random) {
        List<WatchFeed.Entry> shuffled = new ArrayList<>(CATALOG);
        Collections.shuffle(shuffled, random);
        return Collections.unmodifiableList(shuffled.subList(0, 3));
    }

    static List<WatchFeed.Entry> forApp(String pkg, List<WatchFeed.Entry> rows, List<WatchFeed.Entry> picks) {
        if (rows != null && !rows.isEmpty()) return rows;
        return AppCatalog.HIDIVE.equals(pkg) ? picks : Collections.emptyList();
    }

    static boolean isPick(WatchFeed.Entry entry) {
        return AppCatalog.HIDIVE.equals(entry.pkg) && entry.id.startsWith(ID_PREFIX);
    }

    private static WatchFeed.Entry show(String season, String title, String context, String synopsis, String poster) {
        return new WatchFeed.Entry(ID_PREFIX + season, title, "", "", "", context, synopsis, "", "",
                AppCatalog.HIDIVE, -1f, 0, "https://www.hidive.com/season/" + season,
                "https://image.tmdb.org/t/p/w500/" + poster + ".jpg", "");
    }
}
