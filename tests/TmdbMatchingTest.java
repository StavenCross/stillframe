package dev.stillframe;

import java.io.File;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class TmdbMatchingTest {
    public static void main(String[] args) throws Exception {
        check(TmdbMatching.sameTitle(" L’Aube – Partie I ", "L'aube partie I"),
                "normalize casing and typographic punctuation");
        check(TmdbMatching.sameTitle("春の食卓", "春の食卓"), "compare non-Latin titles");
        check(!TmdbMatching.sameTitle("", "…"), "empty normalized titles cannot match");
        check(!TmdbMatching.sameTitle("Signal", "Signals"), "require a whole title match");
        List<TmdbMatching.Candidate> feed = Arrays.asList(
                new TmdbMatching.Candidate(1, "Glass Harbor", "Port de verre", 1996),
                new TmdbMatching.Candidate(2, "Glass Harbor", "Glass Harbor", 2020),
                new TmdbMatching.Candidate(3, "Harbor", "Harbor", 2020));
        check(TmdbMatching.matches(feed, "Glass Harbor", -1).equals(Arrays.asList(1, 2)),
                "preserve remake ambiguity");
        check(TmdbMatching.matches(feed, "Glass Harbor", 2020).equals(Arrays.asList(2)),
                "specific year distinguishes same-title releases");
        check(TmdbMatching.matches(feed, "Port de verre", 1996).equals(Arrays.asList(1)),
                "an exact original title can identify the localized record");
        check(!TmdbMatching.meaningfulEpisode("Episode #2"), "generic episode titles convey no identity");
        check(!TmdbMatching.meaningfulEpisode("Pilot"), "a common pilot label cannot distinguish a remake");
        check(TmdbMatching.meaningfulEpisode("The Bell at Dawn"), "distinct episode titles can corroborate");
        check(TmdbMatching.portrait(667, 1000), "allow pixel rounding around 2:3");
        check(!TmdbMatching.portrait(900, 1600), "other portrait ratios cannot qualify as 2:3");
        check(!TmdbMatching.portrait(1920, 1080), "reject landscape art");
        check(TmdbMatching.posterPath("/portraitA123.jpg"), "allow ordinary CDN paths");
        check(!TmdbMatching.posterPath("//other.example/a.jpg"), "reject a new host");
        check(!TmdbMatching.posterPath("/../other.jpg"), "reject path traversal");
        check(TmdbMatching.language("fr-CA").equals("fr-CA"), "keep a valid locale");
        check(TmdbMatching.language("zh-Hant-TW").equals("zh-TW"), "keep the language and region on script-specific TVs");
        check(TmdbMatching.language("es-419").equals("es"), "TMDB locales use a country abbreviation");
        check(TmdbMatching.language("xx&query=test").equals("en-US"), "reject query syntax in locale");
        List<TmdbMatching.Poster> posters = Arrays.asList(
                new TmdbMatching.Poster("/wide.jpg", "fr", 1600, 900, 19),
                new TmdbMatching.Poster("/other.jpg", "ja", 600, 900, 22),
                new TmdbMatching.Poster("/neutral.jpg", "", 600, 900, 21),
                new TmdbMatching.Poster("/english.jpg", "en", 600, 900, 20),
                new TmdbMatching.Poster("/small.jpg", "fr", 200, 300, 18),
                new TmdbMatching.Poster("/french.jpg", "fr", 600, 900, 1));
        check("/french.jpg".equals(TmdbMatching.bestPoster(posters, "fr")),
                "prefer a usable-size portrait in the chosen language");
        check("/english.jpg".equals(TmdbMatching.bestPoster(posters, "de")),
                "known-language fallback keeps a readable wordmark");
        String demoKey = "0123456789abcdef0123456789abcdef";
        check(TmdbCatalog.isApiKey(demoKey), "recognize a TMDB v3 API key");
        check(!TmdbCatalog.isApiKey(demoKey + ".example"), "bearer tokens use a different authentication path");
        check(TmdbCatalog.apiRoute("/configuration", demoKey).equals("/configuration?api_key=" + demoKey),
                "authenticate routes without existing parameters");
        check(TmdbCatalog.apiRoute("/search/tv?query=demo", demoKey)
                .equals("/search/tv?query=demo&api_key=" + demoKey), "preserve the search parameters");
        check(TmdbCatalog.apiRoute("/configuration", "fictional.bearer.token").equals("/configuration"),
                "bearer credentials stay out of request URLs");
        TmdbCatalog.Query query = new TmdbCatalog.Query("Glass Harbor", "tv", -1, -1, -1, "", "");
        try {
            TmdbCatalog.poster(query, "invalid\nheader", "en-US", () -> true);
            throw new AssertionError("malformed token cannot become an HTTP header");
        } catch (TmdbCatalog.Unauthorized expected) {
            // A malformed stored credential fails without starting a network request.
        }
        try {
            TmdbCatalog.poster(query, "fictional-token-no-request", "en-US", () -> false);
            throw new AssertionError("a disabled catalog job must stop before opening a connection");
        } catch (InterruptedIOException expected) {
            // The consent check comes before any request in a multi-step lookup.
        }
        check(TmdbTransport.retryDelay("90", 0) == 90_000, "respect Retry-After seconds");
        check(TmdbTransport.retryDelay("Thu, 01 Jan 1970 00:02:00 GMT", 30_000) == 90_000,
                "respect a Retry-After date");
        check(TmdbTransport.retryDelay("unusable", 0) == 60_000, "use short backoff without a valid hint");
        PortraitIdentity episodeA = PortraitIdentity.read("example.publisher", "Glass Harbor", 3, "",
                "1", "2", "After Sunrise", "", "");
        PortraitIdentity episodeB = PortraitIdentity.read("example.publisher", "Glass Harbor", 3, "",
                "1", "3", "After Sunset", "", "");
        PortraitIdentity unrelated = PortraitIdentity.read("another.publisher", "Glass Harbor", 3, "",
                "1", "3", "After Sunset", "", "");
        check(!episodeA.key.equals(episodeB.key) && episodeA.seriesKey.equals(episodeB.seriesKey),
                "preserve an episode key alongside potential proven series reuse");
        check(!episodeB.seriesKey.equals(unrelated.seriesKey), "title-based aliases are scoped to the publisher");
        check(!TmdbCatalog.reusableSeries(episodeA.query(), false, false),
                "an episode-only match cannot enable reuse");
        check(TmdbCatalog.reusableSeries(episodeA.query(), false, true),
                "an independently unique exact series title can enable reuse");
        PortraitIdentity externalSeries = PortraitIdentity.read("example.publisher", "Glass Harbor", 3, "",
                "1", "2", "After Sunrise", "", "tt1234567");
        check(TmdbCatalog.reusableSeries(externalSeries.query(), true, false), "an exact IMDb ID can enable reuse");
        check(!TmdbCatalog.reusableSeries(externalSeries.query(), false, true),
                "a failed IMDb ID cannot be overwritten by title-based reuse");
        checkWarmupOrder();
        checkPersistentMiss();
        System.out.println("TMDB matching checks passed");
    }

    private static void checkWarmupOrder() {
        List<WatchFeed.Entry> a = Arrays.asList(entry("a1", "a"), entry("a2", "a"), entry("a3", "a"),
                entry("a4", "a"), entry("a5", "a"));
        List<WatchFeed.Entry> b = Arrays.asList(entry("b1", "b"), entry("b2", "b"), entry("b3", "b"),
                entry("b4", "b"));
        PortraitWarmup plan = new PortraitWarmup(Map.of("a", a, "b", b));
        check(plan.first.stream().map(e -> e.id).collect(Collectors.toList())
                .equals(Arrays.asList("a1", "b1", "a2", "b2", "a3", "b3")),
                "the first three cards across every provider precede the tail");
        check(plan.rest.stream().map(e -> e.id).collect(Collectors.toList())
                .equals(Arrays.asList("a4", "b4", "a5")), "the warmup covers entries past the visible rows");
    }

    private static WatchFeed.Entry entry(String title, String publisher) {
        PortraitIdentity identity = PortraitIdentity.read(publisher, title, 1, "", "", "", "", "", "");
        return new WatchFeed.Entry(title, title, "", "", "", "", "", "", "", publisher,
                0f, 0L, "", "", "", identity);
    }

    private static void checkPersistentMiss() throws Exception {
        File cache = Files.createTempDirectory("stillframe-test-cache").toFile();
        PortraitPosterStore store = new PortraitPosterStore(cache);
        File directory = new File(cache, "portrait-posters-v1");
        try {
            store.rememberMiss("fictional episode", () -> false);
            check(store.knownMiss("fictional episode") == 0, "a canceled job cannot create a cached miss");
            store.rememberMiss("fictional episode", () -> true);
            PortraitPosterStore reopened = new PortraitPosterStore(cache);
            check(reopened.knownMiss("fictional episode") > 0, "known misses survive a cold cache open");
            File[] markers = directory.listFiles();
            check(markers != null && markers.length == 1 && markers[0].setLastModified(
                    System.currentTimeMillis() - PortraitPosterStore.MISS_AGE - 60_000),
                    "age the synthetic miss past its TTL");
            check(reopened.knownMiss("fictional episode") == 0, "expired misses are retried");
            reopened.prune();
            check(directory.listFiles().length == 0, "expired misses are pruned");
        } finally {
            store.clear();
            Files.deleteIfExists(directory.toPath());
            Files.deleteIfExists(cache.toPath());
        }
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
