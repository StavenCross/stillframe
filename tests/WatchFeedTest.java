package dev.stillframe;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class WatchFeedTest {
    public static void main(String[] args) {
        WatchFeed.Entry oldest = entry("8", "example.fiction", 100);
        WatchFeed.Entry newest = entry("3", "example.fiction", 300);
        WatchFeed.Entry tied = entry("5", "example.fiction", 300);
        WatchFeed.Entry other = entry("2", "example.other", 400);
        Map<String, List<WatchFeed.Entry>> index = WatchPrograms.index(
                Arrays.asList(oldest, newest, other, tied));
        check(index.get("example.fiction").equals(Arrays.asList(newest, tied, oldest)),
                "provider feed sorts by engagement with stable ties");
        check(index.get("example.other").equals(Arrays.asList(other)), "publishers are isolated");
        check(index.get("example.missing") == null, "missing publisher cannot inherit a feed");
        try {
            index.get("example.fiction").clear();
            throw new AssertionError("published feed must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected because callers share the background index.
        }
        check(WatchPrograms.progress(1_000, 500) == 0.5f, "elapsed fraction");
        check(WatchPrograms.progress(1_000, 0) == 0f, "explicit zero position");
        check(WatchPrograms.progress(1_000, -1) == -1f, "missing position");
        check(WatchPrograms.progress(0, 1) == -1f, "invalid duration");
        check(WatchPrograms.progress(1_000, 1_001) == -1f, "position exceeds duration");
        check(WatchPrograms.ageRating("invalid/rating,example.rating/US_TV/US_TV_PG")
                .equals("TV-PG"), "malformed ratings do not hide later ratings");
        check(WatchPrograms.ageRating("example.rating/US_MV/US_MV_PG13")
                .equals("PG-13"), "cinema rating uses its recognizable form");
        check(WatchPrograms.ageRating("unknown,invalid/rating").isEmpty(), "ambiguous ratings stay blank");
        check(WatchPrograms.releaseYear("2024-02-29T00:00:00Z").equals("2024"), "release year");
        check(WatchPrograms.releaseYear("2023-02-29").isEmpty(), "invalid release date");
        check(WatchPrograms.review(0, "4.5").equals("4.5 / 5"), "styled stars");
        check(WatchPrograms.review(2, "85").equals("85%"), "styled percentage");
        check(WatchPrograms.review(2, "105").isEmpty(), "out-of-range percentage");
        check(WatchPrograms.review(-1, "85").isEmpty(), "untyped score");
        check(WatchPrograms.episodeChip("3", "1").equals("S3 · E1"), "compact episode index");
        check(WatchPrograms.episodeChip("", "04").equals("E04"), "episode without a season");
        check(WatchPrograms.episodeChip("Specials", "").equals("Specials"), "publisher display label");
        check(WatchPrograms.episodeChip("", "").isEmpty(), "missing episode index");
        check(PosterTile.cardHeight(352, 1080) > 352, "the TV card is taller than it is wide");
        check(PosterTile.cardHeight(352, 1080) < 1080 / 2, "cards leave room for the dock");
        check(Arrays.equals(ArtworkCrop.decodeSize(853, 480, 352, 410), new int[]{729, 410}),
                "wide art decodes to the height needed for a sharp full-bleed crop");
        check(Arrays.equals(ArtworkCrop.decodeSize(100, 56, 352, 410), new int[]{100, 56}),
                "tiny publisher images are never enlarged during decode");
        check(Arrays.equals(ArtworkCrop.decodeSize(2700, 100, 352, 410), new int[]{1056, 39}),
                "unusual aspect ratios keep intermediate decode work bounded");
        check(ArtworkCrop.offset(800, 350, .5f) == 225, "balanced art stays centered");
        check(ArtworkCrop.offset(800, 350, .75f) == 425, "off-center focal area can lead the crop");
        check(ArtworkCrop.offset(800, 350, 1f) == 450, "crop never goes past the image edge");
        check(ArtworkCrop.wordmarkWidth(352, 728) == 384, "give side lettering a small safe margin");
        check(ArtworkCrop.wordmarkWidth(352, 360) == 360, "safe margin remains inside the source");
        check(PosterTile.showTitle("com.crunchyroll.crunchyroid", true), "episode still gets a title");
        check(PosterTile.showTitle("example.unknown", true), "unknown publishers get a title by default");
        for (String branded : Arrays.asList("com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
                "com.wbd.stream")) {
            check(!PosterTile.showTitle(branded, true), "branded artwork can stand alone");
            check(PosterTile.showTitle(branded, false), "missing branded artwork shows the title");
        }
        int[] pixels = new int[40 * 24];
        check(ArtworkCrop.subjectX(pixels, 40, 24, 20) == .5f, "flat imagery stays centered");
        for (int y = 0; y < 24; y++)
            for (int x = 27; x < 36; x += 2) pixels[y * 40 + x] = 0xffffff;
        check(ArtworkCrop.subjectX(pixels, 40, 24, 20) > .62f, "focal crop follows off-center detail");
        int[] lettering = new int[96 * 54];
        for (int y = 20; y < 28; y++)
            for (int x = 65; x < 89; x += 2) lettering[y * 96 + x] = 0xffffff;
        check(ArtworkCrop.letteringX(lettering, 96, 54, 46) > .65f,
                "a publisher wordmark can draw the crop toward a side");
        PortraitIdentity episode = PortraitIdentity.read("example.publisher", "Fictional title", 3, "2025-10-21",
                "2", "4", "The Other Door", "tt7654321", "");
        check("tv".equals(episode.media) && episode.year == -1 && episode.season == 2 && episode.episode == 4,
                "episode metadata identifies the show without using its air date as the premiere");
        check(episode.externalId.isEmpty(), "an episode ID does not identify its series");
        PortraitIdentity movie = PortraitIdentity.read("example.publisher", "Fictional film", 0, "2024-03-01",
                "", "", "", "tt1234567", "");
        check("movie".equals(movie.media) && movie.year == 2024 && movie.externalId.equals("tt1234567"),
                "movie release year and external ID can refine the match");
        check(PortraitIdentity.read("example.publisher", "Fictional title", -1, "", "", "", "", "", "") == null,
                "untyped titles without episode evidence are skipped");
        PortraitIdentity compound = PortraitIdentity.read("com.cbs.ott", "Island Games 8 - Tide at Dawn",
                3, "", "8", "4", "", "", "");
        check(compound.alternateSeries.equals("Island Games") && compound.alternateEpisode.equals("Tide at Dawn"),
                "Paramount's season suffix and embedded episode title supply a verified-match candidate");
        PortraitIdentity external = PortraitIdentity.read("example.publisher", "Island Games 8 - Tide at Dawn",
                3, "", "8", "4", "", "", "");
        check(external.alternateSeries.isEmpty(), "only Paramount compound titles get a fallback");
        PortraitIdentity generic = PortraitIdentity.read("com.cbs.ott", "Island Games - Episode 4",
                3, "", "8", "4", "", "", "");
        check(generic.alternateSeries.isEmpty(), "generic episode names are insufficient evidence");
        PortraitIdentity numbered = PortraitIdentity.read("com.cbs.ott", "Island Games 8 - Episode 4",
                3, "", "8", "4", "", "", "");
        check(numbered.alternateSeries.equals("Island Games") && numbered.alternateEpisode.equals("Episode 4"),
                "a redundant labelled season and episode can request a unique catalog coordinate check");
        PortraitIdentity mismatch = PortraitIdentity.read("com.cbs.ott", "Island Games 8 - Episode 5",
                3, "", "8", "4", "", "", "");
        check(mismatch.alternateSeries.isEmpty(), "embedded episode number must agree with metadata");
        check(TmdbMatching.portrait(500, 750) && !TmdbMatching.portrait(853, 480),
                "validate the decoded poster aspect ratio");
        int portraitWidth = PosterTile.cardWidth(1920, 1080, true);
        check(TmdbMatching.portrait(portraitWidth, Math.round(portraitWidth * 1.5f)),
                "opt-in cards fit native portrait images");
        PosterPresentation presentation = new PosterPresentation();
        presentation.reset(100);
        check(presentation.select(120, false, true, true) == PosterPresentation.Source.WAITING,
                "hold the first art briefly while a portrait could still arrive");
        check(presentation.select(220, true, true, false) == PosterPresentation.Source.PORTRAIT,
                "a poster ready within the grace period is the first image");
        check(presentation.select(500, false, true, false) == PosterPresentation.Source.PORTRAIT,
                "a portrait stays selected if its cache entry is later evicted");
        presentation.reset(100);
        check(presentation.select(340, false, true, true) == PosterPresentation.Source.PUBLISHER,
                "choose available provider artwork when the grace period ends");
        check(presentation.select(500, true, true, false) == PosterPresentation.Source.PUBLISHER,
                "late portraits never replace an already visible provider image");
        presentation.reset(500);
        check(presentation.select(500, false, true, false) == PosterPresentation.Source.PUBLISHER,
                "known catalog misses show provider artwork immediately");
        presentation.reset(500);
        check(presentation.select(800, false, false, true) == PosterPresentation.Source.WAITING,
                "no image has been committed while both sources are unavailable");
        check(presentation.select(850, true, false, false) == PosterPresentation.Source.PORTRAIT,
                "the first real image may still arrive after the grace period");
        System.out.println("WatchFeed checks passed");
    }

    private static WatchFeed.Entry entry(String id, String pkg, long engaged) {
        return new WatchFeed.Entry(id, "Fictional title", "", "", "", "", "", "", "",
                pkg, -1f, engaged, "", "", "");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
