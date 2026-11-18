package dev.stillframe;

import dev.stillframe.WatchFeed.Entry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Interleave providers at the same rank so the initial cards finish before a long feed's tail. */
final class PortraitWarmup {
    static final int VISIBLE_COUNT = 3;
    final List<Entry> first = new ArrayList<>();
    final List<Entry> rest = new ArrayList<>();
    final Set<String> keys = new HashSet<>();

    PortraitWarmup(Map<String, List<Entry>> source) {
        List<String> providers = new ArrayList<>(source.keySet());
        Collections.sort(providers);
        List<List<Entry>> feeds = new ArrayList<>();
        int ranks = 0;
        for (String provider : providers) {
            List<Entry> eligible = new ArrayList<>();
            for (Entry entry : source.get(provider)) {
                if (entry.portrait == null) continue;
                eligible.add(entry);
                keys.add(entry.portrait.key);
            }
            if (eligible.isEmpty()) continue;
            ranks = Math.max(ranks, eligible.size());
            feeds.add(eligible);
        }
        for (int rank = 0; rank < ranks; rank++) {
            List<Entry> output = rank < VISIBLE_COUNT ? first : rest;
            for (List<Entry> feed : feeds) {
                if (rank < feed.size()) output.add(feed.get(rank));
            }
        }
    }
}
