package dev.stillframe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Fetches title metadata and accepts only a defensible match and a 2:3 poster. */
final class TmdbCatalog {
    private static final String IMAGES = "https://image.tmdb.org/t/p/w500";
    private static final int SEARCH_PAGES = 3;
    private static final int EPISODE_SEARCH_PAGES = 8;
    private static final int MAX_EPISODE_CANDIDATES = 24;

    static final class Query {
        final String title, media, episodeTitle, externalId, alternateSeries, alternateEpisode;
        final int year, season, episode;

        Query(String title, String media, int year, int season, int episode,
              String episodeTitle, String externalId) {
            this(title, media, year, season, episode, episodeTitle, externalId, "", "");
        }

        Query(String title, String media, int year, int season, int episode, String episodeTitle,
              String externalId, String alternateSeries, String alternateEpisode) {
            this.title = title;
            this.media = media;
            this.year = year;
            this.season = season;
            this.episode = episode;
            this.episodeTitle = episodeTitle;
            this.externalId = externalId;
            this.alternateSeries = alternateSeries;
            this.alternateEpisode = alternateEpisode;
        }
    }

    static final class Unauthorized extends IOException {
        Unauthorized() { super("TMDB authorization failed"); }
    }

    static final class PosterResult {
        final String url;
        final boolean shared;

        PosterResult(String url, boolean shared) {
            this.url = url;
            this.shared = shared;
        }
    }

    private static final class Match {
        static final Match NONE = new Match(0, false);
        final int id;
        final boolean uniqueTitle;

        Match(int id, boolean uniqueTitle) {
            this.id = id;
            this.uniqueTitle = uniqueTitle;
        }
    }

    private final String language;
    private final TmdbTransport transport;

    private TmdbCatalog(String token, String language, BooleanSupplier active, int seconds) {
        this.language = language;
        transport = new TmdbTransport(token, active, seconds);
    }

    static String poster(Query query, String token, String language, BooleanSupplier active)
            throws IOException {
        PosterResult result = lookup(query, token, language, active);
        return result == null ? null : result.url;
    }

    static PosterResult lookup(Query query, String token, String language, BooleanSupplier active)
            throws IOException {
        if (!"movie".equals(query.media) && !"tv".equals(query.media)) return null;
        if (query.title == null || query.title.length() > 256
                || !TmdbMatching.sameTitle(query.title, query.title)) return null;
        if (token == null || !token.matches("[A-Za-z0-9._~=-]{20,4096}")) throw new Unauthorized();
        boolean alternate = "tv".equals(query.media) && !query.alternateSeries.isEmpty();
        TmdbCatalog catalog = new TmdbCatalog(token, TmdbMatching.language(language), active, alternate ? 24 : 12);
        int id = catalog.externalId(query);
        boolean verifiedId = id > 0;
        boolean uniqueTitle = false;
        if (id == 0) {
            Match match = catalog.findTitle(query);
            id = match.id;
            uniqueTitle = match.uniqueTitle;
        }
        if (id == 0 && alternate) id = catalog.findAlternateSeries(query);
        String url = id > 0 ? catalog.poster(query.media, id) : null;
        return url == null ? null : new PosterResult(url, reusableSeries(query, verifiedId, uniqueTitle));
    }

    static boolean reusableSeries(Query query, boolean verifiedId, boolean uniqueTitle) {
        // A title search must not populate an alias for an IMDb id that failed to resolve.
        return "tv".equals(query.media) && (verifiedId || uniqueTitle && query.externalId.isEmpty());
    }

    static boolean isApiKey(String credential) { return credential.matches("[0-9a-fA-F]{32}"); }

    static String apiRoute(String route, String credential) {
        if (!isApiKey(credential)) return route;
        return route + (route.contains("?") ? "&" : "?") + "api_key=" + credential;
    }

    static boolean isCredential(String value) {
        return isApiKey(value) || value.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    }

    private int externalId(Query query) throws IOException {
        if (query.externalId == null || !query.externalId.matches("tt[0-9]{7,12}")) return 0;
        JSONObject data = get("/find/" + query.externalId + "?external_source=imdb_id&language=" + language);
        if (data == null) return 0;
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        collectIds(data.optJSONArray(query.media + "_results"), "id", ids);
        if ("tv".equals(query.media)) collectIds(data.optJSONArray("tv_episode_results"), "show_id", ids);
        return ids.size() == 1 ? ids.iterator().next() : 0;
    }

    private static void collectIds(JSONArray result, String field, LinkedHashSet<Integer> ids) {
        if (result == null) return;
        for (int i = 0; i < result.length(); i++) {
            JSONObject candidate = result.optJSONObject(i);
            if (candidate != null && candidate.optInt(field) > 0)
                ids.add(candidate.optInt(field));
        }
    }

    private Match findTitle(Query query) throws IOException {
        List<Integer> matches = search(query, language);
        if (matches == null) return Match.NONE;
        String resultLanguage = language;
        if (matches.isEmpty() && !"en-US".equals(language)) {
            resultLanguage = "en-US";
            matches = search(query, resultLanguage);
        }
        if (matches == null || matches.isEmpty()) return Match.NONE;
        if (matches.size() == 1) return new Match(matches.get(0), true);
        if (!"tv".equals(query.media) || matches.size() > 3 || query.season < 0 || query.episode <= 0
                || !TmdbMatching.meaningfulEpisode(query.episodeTitle)) return Match.NONE;
        return new Match(verifyEpisode(matches, query, query.episodeTitle, resultLanguage, false), false);
    }

    private int findAlternateSeries(Query query) throws IOException {
        List<Integer> matches = search(query.alternateSeries, "tv", -1, language, EPISODE_SEARCH_PAGES);
        if (matches == null || matches.isEmpty() || matches.size() > MAX_EPISODE_CANDIDATES) return 0;
        // Compound provider titles may produce an exact series match for the prefix,
        // but even a single candidate must match the episode before we replace art.
        return verifyEpisode(matches, query, query.alternateEpisode, language,
                !TmdbMatching.meaningfulEpisode(query.alternateEpisode));
    }

    private int verifyEpisode(List<Integer> matches, Query query, String episodeTitle, String locale,
                              boolean uniqueCoordinates) throws IOException {
        int matched = 0;
        int existing = 0;
        for (int id : matches) {
            JSONObject detail = get("/tv/" + id + "/season/" + query.season + "/episode/"
                    + query.episode + "?language=" + locale);
            if (detail == null) continue;
            // A label such as "Episode 3" only verifies a match when the
            // supplied season and episode coordinates exist for one series.
            if (uniqueCoordinates && ++existing > 1) return 0;
            if (!TmdbMatching.sameTitle(episodeTitle, string(detail, "name"))) continue;
            if (matched != 0) return 0;
            matched = id;
        }
        return matched;
    }

    private List<Integer> search(Query query, String locale) throws IOException {
        return search(query.title, query.media, query.year, locale, SEARCH_PAGES);
    }

    private List<Integer> search(String title, String media, int year, String locale, int pageLimit)
            throws IOException {
        boolean movie = "movie".equals(media);
        String route = "/search/" + media + "?include_adult=false&language=" + locale
                + "&query=" + URLEncoder.encode(title, StandardCharsets.UTF_8.name());
        if (year >= 1000 && year <= 9999)
            route += (movie ? "&primary_release_year=" : "&first_air_date_year=") + year;
        List<TmdbMatching.Candidate> candidates = new ArrayList<>();
        int totalPages = 1;
        for (int page = 1; page <= totalPages; page++) {
            JSONObject data = get(route + "&page=" + page);
            if (data == null) return Collections.emptyList();
            totalPages = data.optInt("total_pages", -1);
            JSONArray results = data.optJSONArray("results");
            if (totalPages < 0 || results == null) throw new IOException("Invalid TMDB search response");
            if (totalPages > pageLimit) return null; // A partial search cannot establish uniqueness.
            for (int i = 0; i < results.length(); i++) {
                JSONObject result = results.optJSONObject(i);
                if (result == null) continue;
                candidates.add(new TmdbMatching.Candidate(result.optInt("id"),
                        string(result, movie ? "title" : "name"),
                        string(result, movie ? "original_title" : "original_name"),
                        year(string(result, movie ? "release_date" : "first_air_date"))));
            }
        }
        return TmdbMatching.matches(candidates, title, year);
    }

    private String poster(String media, int id) throws IOException {
        String preferred = language.substring(0, 2);
        String languages = "en".equals(preferred) ? "en,null" : preferred + ",en,null";
        JSONObject data = get("/" + media + "/" + id + "/images?include_image_language=" + languages);
        if (data == null) return null;
        JSONArray posters = data.optJSONArray("posters");
        if (posters == null) throw new IOException("Invalid TMDB poster response");
        List<TmdbMatching.Poster> candidates = new ArrayList<>();
        for (int i = 0; i < posters.length(); i++) {
            JSONObject poster = posters.optJSONObject(i);
            if (poster == null) continue;
            candidates.add(new TmdbMatching.Poster(string(poster, "file_path"),
                    string(poster, "iso_639_1"), poster.optInt("width"), poster.optInt("height"),
                    poster.optInt("vote_count")));
        }
        String best = TmdbMatching.bestPoster(candidates, preferred);
        return best == null ? null : IMAGES + best;
    }

    private static String string(JSONObject item, String field) {
        return item.isNull(field) ? "" : item.optString(field, "");
    }

    private static int year(String releaseDate) {
        return releaseDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                ? Integer.parseInt(releaseDate.substring(0, 4)) : -1;
    }

    private JSONObject get(String route) throws IOException { return transport.get(route); }
}
