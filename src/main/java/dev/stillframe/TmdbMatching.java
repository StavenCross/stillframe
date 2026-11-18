package dev.stillframe;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.IllformedLocaleException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

final class TmdbMatching {
    private TmdbMatching() {}

    static final class Candidate {
        final int id, year;
        final String title, originalTitle;

        Candidate(int id, String title, String originalTitle, int year) {
            this.id = id;
            this.title = title;
            this.originalTitle = originalTitle;
            this.year = year;
        }
    }

    static final class Poster {
        final String path, language;
        final int width, height, votes;

        Poster(String path, String language, int width, int height, int votes) {
            this.path = path;
            this.language = language;
            this.width = width;
            this.height = height;
            this.votes = votes;
        }
    }

    static List<Integer> matches(List<Candidate> candidates, String title, int year) {
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        for (Candidate candidate : candidates) {
            if (candidate.id > 0 && (year < 0 || year == candidate.year)
                    && (sameTitle(title, candidate.title) || sameTitle(title, candidate.originalTitle))) {
                ids.add(candidate.id);
            }
        }
        return new ArrayList<>(ids);
    }

    static boolean sameTitle(String left, String right) {
        String normalized = normalizedTitle(left);
        return !normalized.isEmpty() && normalized.equals(normalizedTitle(right));
    }

    static boolean meaningfulEpisode(String title) {
        String normalized = normalizedTitle(title);
        return !normalized.isEmpty() && !"pilot".equals(normalized)
                && !normalized.matches("(?:episode|ep|chapter|part)? *[0-9]+[a-z]?")
                && !normalized.matches("s[0-9]+ *e[0-9]+");
    }

    private static String normalizedTitle(String title) {
        if (title == null) return "";
        String input = Normalizer.normalize(title, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(input.length());
        boolean space = false;
        for (int i = 0; i < input.length(); ) {
            int cp = input.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\'' || cp == '’') continue;
            if (Character.isLetterOrDigit(cp) || Character.getType(cp) == Character.NON_SPACING_MARK) {
                if (space) normalized.append(' ');
                normalized.appendCodePoint(cp);
                space = false;
            } else if (normalized.length() > 0) {
                space = true;
            }
        }
        return normalized.toString();
    }

    static boolean portrait(int width, int height) {
        return width > 0 && height > width && Math.abs(3L * width - 2L * height) <= 3;
    }

    static boolean posterPath(String path) {
        return path != null && path.matches("/[A-Za-z0-9_-]{1,96}\\.(?:jpg|jpeg|png|webp)");
    }

    static String bestPoster(List<Poster> posters, String language) {
        Poster best = null;
        int bestLanguage = -1, bestWidth = 0;
        for (Poster poster : posters) {
            if (!portrait(poster.width, poster.height) || !posterPath(poster.path)) continue;
            int priority = language.equals(poster.language) ? 3 : "en".equals(poster.language) ? 2
                    : poster.language.isEmpty() ? 1 : 0;
            if (priority == 0) continue;
            int usefulWidth = Math.min(poster.width, 500);
            if (priority > bestLanguage || (priority == bestLanguage && (usefulWidth > bestWidth
                    || (usefulWidth == bestWidth && poster.votes > best.votes)))) {
                best = poster;
                bestLanguage = priority;
                bestWidth = usefulWidth;
            }
        }
        return best == null ? null : best.path;
    }

    static String language(String locale) {
        if (locale == null || locale.isEmpty()) return "en-US";
        try {
            Locale parsed = new Locale.Builder().setLanguageTag(locale).build();
            String language = parsed.getLanguage(), country = parsed.getCountry();
            if (!language.matches("[a-z]{2}")) return "en-US";
            return country.matches("[A-Z]{2}") ? language + "-" + country : language;
        } catch (IllformedLocaleException malformed) {
            return "en-US";
        }
    }
}
