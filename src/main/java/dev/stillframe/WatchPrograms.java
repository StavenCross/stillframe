package dev.stillframe;

import static android.media.tv.TvContract.WatchNextPrograms.*;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.media.tv.TvContract;
import android.os.Build;
import android.provider.BaseColumns;

import dev.stillframe.WatchFeed.Entry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Reads the provider's public program metadata and produces a package-isolated snapshot. */
final class WatchPrograms {
    private WatchPrograms() {}
    private static final String[] PROJECTION = {
        BaseColumns._ID, COLUMN_PACKAGE_NAME, COLUMN_TITLE, COLUMN_INTENT_URI,
        COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, COLUMN_WATCH_NEXT_TYPE,
        COLUMN_DURATION_MILLIS, COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
        COLUMN_SEASON_DISPLAY_NUMBER, COLUMN_EPISODE_DISPLAY_NUMBER, COLUMN_EPISODE_TITLE,
        COLUMN_CONTENT_RATING, COLUMN_CANONICAL_GENRE, COLUMN_RELEASE_DATE,
        COLUMN_SHORT_DESCRIPTION, COLUMN_LONG_DESCRIPTION, COLUMN_REVIEW_RATING,
        COLUMN_REVIEW_RATING_STYLE, COLUMN_POSTER_ART_URI, COLUMN_THUMBNAIL_URI,
        COLUMN_TYPE, COLUMN_CONTENT_ID, COLUMN_SERIES_ID
    };
    private static final String[] CORE_PROJECTION = {
        BaseColumns._ID, COLUMN_PACKAGE_NAME, COLUMN_TITLE, COLUMN_INTENT_URI,
        COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS, COLUMN_POSTER_ART_URI
    };
    private static final String[] IDENTITY_PROJECTION = {
        BaseColumns._ID, COLUMN_PACKAGE_NAME, COLUMN_TITLE
    };

    static Map<String, List<Entry>> read(Context context) {
        try {
            return read(context, Build.VERSION.SDK_INT >= 29
                    ? PROJECTION : Arrays.copyOf(PROJECTION, PROJECTION.length - 1));
        } catch (IllegalArgumentException | SQLiteException unsupportedColumn) {
            // Some providers reject a projection when an optional column is absent.
            try {
                return read(context, Arrays.copyOf(PROJECTION, PROJECTION.length - 3));
            } catch (IllegalArgumentException | SQLiteException unsupportedMetadata) {
                try {
                    return read(context, CORE_PROJECTION);
                } catch (IllegalArgumentException | SQLiteException unsupportedArtwork) {
                    return read(context, IDENTITY_PROJECTION);
                }
            }
        }
    }

    private static Map<String, List<Entry>> read(Context context, String[] projection) {
        List<Entry> entries = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(CONTENT_URI, projection, null, null, null)) {
            if (cursor == null) return null;
            while (cursor.moveToNext()) {
                Entry entry = readEntry(cursor);
                if (entry != null) entries.add(entry);
            }
        }
        return index(entries);
    }

    static Map<String, List<Entry>> index(List<Entry> entries) {
        Map<String, List<Entry>> byPackage = new HashMap<>();
        for (Entry entry : entries) {
            byPackage.computeIfAbsent(entry.pkg, ignored -> new ArrayList<>()).add(entry);
        }
        Comparator<Entry> order = Comparator.comparingLong((Entry entry) -> entry.engaged)
                .reversed().thenComparing(entry -> entry.id);
        byPackage.replaceAll((pkg, list) -> {
            list.sort(order);
            return Collections.unmodifiableList(list);
        });
        return Collections.unmodifiableMap(byPackage);
    }

    private static Entry readEntry(Cursor row) {
        String id = value(row, BaseColumns._ID);
        String pkg = value(row, COLUMN_PACKAGE_NAME);
        String title = value(row, COLUMN_TITLE);
        if (id.isEmpty() || pkg.isEmpty() || title.isEmpty()) return null;
        String season = value(row, COLUMN_SEASON_DISPLAY_NUMBER);
        String episode = value(row, COLUMN_EPISODE_DISPLAY_NUMBER);
        String episodeTitle = value(row, COLUMN_EPISODE_TITLE);
        if (episodeTitle.equalsIgnoreCase(title)) episodeTitle = "";
        String age = ageRating(value(row, COLUMN_CONTENT_RATING));
        String synopsis = value(row, COLUMN_SHORT_DESCRIPTION);
        if (synopsis.isEmpty()) synopsis = value(row, COLUMN_LONG_DESCRIPTION);
        long duration = number(row, COLUMN_DURATION_MILLIS);
        long position = number(row, COLUMN_LAST_PLAYBACK_POSITION_MILLIS);
        boolean next = number(row, COLUMN_WATCH_NEXT_TYPE) == WATCH_NEXT_TYPE_NEXT;
        float progress = next ? -1f : progress(duration, position);
        String time = next ? "Next episode" : progress < 0f ? "" : remaining(duration - position);
        PortraitIdentity identity = PortraitIdentity.read(pkg, title, (int) number(row, COLUMN_TYPE),
                value(row, COLUMN_RELEASE_DATE), season, episode, episodeTitle, value(row, COLUMN_CONTENT_ID),
                value(row, COLUMN_SERIES_ID));
        return new Entry(id, title, episodeChip(season, episode), episodeTitle, age,
                context(row), synopsis, review(row), time, pkg,
                progress, number(row, COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS),
                value(row, COLUMN_INTENT_URI), value(row, COLUMN_POSTER_ART_URI),
                value(row, COLUMN_THUMBNAIL_URI), identity);
    }

    static String episodeChip(String season, String episode) {
        return line(numberLabel(season, "S"), numberLabel(episode, "E"));
    }

    private static String numberLabel(String displayNumber, String prefix) {
        if (displayNumber.matches("-[0-9]+")) return "";
        return displayNumber.matches("[0-9]+[A-Za-z]?") ? prefix + displayNumber : displayNumber;
    }

    static float progress(long duration, long position) {
        return duration > 0 && position >= 0 && position <= duration ? (float) position / duration : -1f;
    }

    private static String remaining(long millis) {
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        if (millis % 60_000 != 0) minutes++;
        return minutes >= 60 ? (minutes / 60) + " hr " + (minutes % 60) + " min left"
                : minutes + " min left";
    }

    private static String context(Cursor row) {
        String genre = "";
        for (String canonical : TvContract.Programs.Genres.decode(value(row, COLUMN_CANONICAL_GENRE))) {
            if (!TvContract.Programs.Genres.isCanonical(canonical)) continue;
            String[] words = canonical.toLowerCase(Locale.ROOT).split("_");
            for (int i = 0; i < words.length; i++) {
                words[i] = Character.toUpperCase(words[i].charAt(0)) + words[i].substring(1);
            }
            genre = String.join(" ", words);
            break;
        }
        return line(genre, releaseYear(value(row, COLUMN_RELEASE_DATE)));
    }

    static String ageRating(String ratings) {
        for (String flattened : ratings.split(",")) {
            // The platform format is domain/system/main-rating followed by optional subratings.
            String[] fields = flattened.trim().split("/", 4);
            if (fields.length < 3 || fields[0].isEmpty() || fields[1].isEmpty()
                    || fields[2].isEmpty()) continue;
            String rating = fields[2];
            if (rating.startsWith(fields[1] + "_")) rating = rating.substring(fields[1].length() + 1);
            if (fields[1].equals("US_TV") && !rating.startsWith("TV-")) rating = "TV-" + rating;
            if (fields[1].equals("US_MV")) {
                if (rating.equals("PG13")) rating = "PG-13";
                if (rating.equals("NC17")) rating = "NC-17";
            }
            return rating.replace('_', '-');
        }
        return "";
    }

    static String releaseYear(String released) {
        if (released.matches("[1-9][0-9]{3}")) return released;
        if (!released.matches("[1-9][0-9]{3}-[0-9]{2}-[0-9]{2}([T ].*)?")) return "";
        try {
            return Integer.toString(java.time.LocalDate.parse(released.substring(0, 10)).getYear());
        } catch (java.time.DateTimeException invalid) {
            return "";
        }
    }

    private static String review(Cursor row) {
        return review(number(row, COLUMN_REVIEW_RATING_STYLE), value(row, COLUMN_REVIEW_RATING));
    }

    static String review(long style, String value) {
        if (style == REVIEW_RATING_STYLE_THUMBS_UP_DOWN && value.matches("[0-9]+,\\s*[0-9]+")) {
            String[] votes = value.split(",");
            return votes[0] + " likes · " + votes[1].trim() + " dislikes";
        }
        if (!value.matches("[0-9]+(\\.[0-9]+)?")) return "";
        double score = Double.parseDouble(value);
        if (style == REVIEW_RATING_STYLE_STARS && score <= 5) return value + " / 5";
        if (style == REVIEW_RATING_STYLE_PERCENTAGE && score <= 100) return value + "%";
        return "";
    }

    private static String line(String... parts) {
        List<String> present = new ArrayList<>();
        for (String part : parts) if (!part.isEmpty()) present.add(part);
        return String.join(" · ", present);
    }

    private static String value(Cursor row, String column) {
        int index = row.getColumnIndex(column);
        if (index < 0 || row.getType(index) == Cursor.FIELD_TYPE_BLOB) return "";
        String value = row.getString(index);
        return value == null ? "" : value.trim();
    }

    private static long number(Cursor row, String column) {
        try {
            return Long.parseLong(value(row, column));
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }

}
