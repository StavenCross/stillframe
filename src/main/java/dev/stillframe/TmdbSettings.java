package dev.stillframe;

import android.content.Context;
import android.content.SharedPreferences;

final class TmdbSettings {
    private static final String TOKEN = "read_token";
    private static final String ENABLED = "enabled";

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("portrait_posters", Context.MODE_PRIVATE);
    }

    static boolean enabled(Context context) {
        return preferences(context).getBoolean(ENABLED, false) && hasToken(context);
    }

    static boolean hasToken(Context context) { return !token(context).isEmpty(); }

    static String token(Context context) { return preferences(context).getString(TOKEN, ""); }

    static void save(Context context, String token) {
        preferences(context).edit().putString(TOKEN, token.trim()).apply();
    }

    static void enabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean(ENABLED, enabled && hasToken(context)).apply();
    }

    static void clear(Context context) {
        preferences(context).edit().remove(TOKEN).remove(ENABLED).apply();
    }

    private TmdbSettings() {}
}
