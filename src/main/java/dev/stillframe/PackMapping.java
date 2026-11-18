package dev.stillframe;

import java.util.HashMap;
import java.util.Map;

final class PackMapping {
    private final Map<String, String> activities = new HashMap<>();
    private final Map<String, String> defaults = new HashMap<>();
    private final Map<String, String> firstByPackage = new HashMap<>();

    void add(String component, String drawable) {
        if (component == null || drawable == null || !drawable.matches("[a-zA-Z0-9_]+")) return;
        String value = component.trim();
        if (value.startsWith("ComponentInfo{") && value.endsWith("}"))
            value = value.substring(14, value.length() - 1);
        int slash = value.indexOf('/');
        if (slash <= 0) return;
        String pkg = value.substring(0, slash), activity = value.substring(slash + 1);
        if (activity.isEmpty()) defaults.putIfAbsent(pkg, drawable);
        else activities.putIfAbsent(key(pkg, activity), drawable);
        firstByPackage.putIfAbsent(pkg, drawable);
    }

    String find(String pkg, String activity) {
        String exact = activities.get(key(pkg, activity));
        if (exact != null) return exact;
        String preferred = defaults.get(pkg);
        return preferred == null ? firstByPackage.get(pkg) : preferred;
    }

    private static String key(String pkg, String activity) {
        return pkg + "/" + (activity.startsWith(".") ? pkg + activity : activity);
    }
}
