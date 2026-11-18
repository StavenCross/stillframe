package dev.stillframe;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class PinPreferences {
    static List<String> read(SharedPreferences preferences, Set<String> installed) {
        List<String> pins = new ArrayList<>();
        String saved = preferences.getString("pins", null);
        if (saved == null) {
            for (String pkg : AppCatalog.DEFAULTS) if (installed.contains(pkg)) pins.add(pkg);
            if (installed.contains(AppCatalog.STORE)) pins.add(AppCatalog.STORE);
        } else {
            try {
                JSONArray array = new JSONArray(saved);
                for (int i = 0; i < array.length(); i++) {
                    String pkg = array.getString(i);
                    if (installed.contains(pkg) && !pins.contains(pkg)) pins.add(pkg);
                }
            } catch (JSONException ignored) {
                // A damaged pin list is recoverable through the picker.
            }
        }
        return pins;
    }

    static void save(SharedPreferences preferences, List<String> pins) {
        preferences.edit().putString("pins", new JSONArray(pins).toString()).apply();
    }

    private PinPreferences() {}
}
