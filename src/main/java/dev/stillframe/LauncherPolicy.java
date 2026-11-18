package dev.stillframe;

import java.util.List;

final class LauncherPolicy {
    static String provider(String previous, List<String> candidates) {
        return candidates.contains(previous) ? previous : candidates.isEmpty() ? "" : candidates.get(0);
    }

    static void move(List<String> pins, int from, int to) {
        if (from >= 0 && to >= 0 && from < pins.size() && to < pins.size() && from != to) {
            pins.add(to, pins.remove(from));
        }
    }

    static int step(int index, int direction, int count) {
        return Math.max(0, Math.min(index + direction, count - 1));
    }

    private LauncherPolicy() {}
}
