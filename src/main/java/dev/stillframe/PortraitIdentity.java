package dev.stillframe;

import static android.media.tv.TvContract.WatchNextPrograms.TYPE_MOVIE;
import static android.media.tv.TvContract.WatchNextPrograms.TYPE_TV_EPISODE;
import static android.media.tv.TvContract.WatchNextPrograms.TYPE_TV_SEASON;
import static android.media.tv.TvContract.WatchNextPrograms.TYPE_TV_SERIES;

final class PortraitIdentity {
    final String title, media, episodeTitle, externalId, key, alternateSeries, alternateEpisode, seriesKey;
    final int year, season, episode;

    private PortraitIdentity(String publisher, String title, String media, int year, int season, int episode,
                             String episodeTitle, String externalId, String alternateSeries,
                             String alternateEpisode) {
        this.title = title;
        this.media = media;
        this.year = year;
        this.season = season;
        this.episode = episode;
        this.episodeTitle = episodeTitle;
        this.externalId = externalId;
        this.alternateSeries = alternateSeries;
        this.alternateEpisode = alternateEpisode;
        String primaryKey = String.join("\0", media, title, Integer.toString(year), Integer.toString(season),
                Integer.toString(episode), episodeTitle, externalId);
        key = alternateSeries.isEmpty() ? primaryKey : String.join("\0", primaryKey, alternateSeries, alternateEpisode);
        seriesKey = !"tv".equals(media) ? "" : !externalId.isEmpty() ? "series-id\0" + externalId
                : String.join("\0", "series-title", publisher, title, Integer.toString(year));
    }

    static PortraitIdentity read(String publisher, String title, int type, String date, String seasonNumber,
                                 String episodeNumber, String episodeTitle, String contentId, String seriesId) {
        if (title.isEmpty() || title.length() > 250) return null;
        int season = numeric(seasonNumber), episode = numeric(episodeNumber);
        boolean movie = type == TYPE_MOVIE;
        boolean tv = type == TYPE_TV_SERIES || type == TYPE_TV_SEASON || type == TYPE_TV_EPISODE
                || (type == -1 && (season >= 0 || episode >= 0 || !episodeTitle.isEmpty()));
        if (!movie && !tv) return null;
        // Watch Next dates can refer to an episode or season. They only safely
        // disambiguate a film or an entry explicitly identified as a series.
        int year = movie || type == TYPE_TV_SERIES ? numeric(WatchPrograms.releaseYear(date)) : -1;
        String externalId = imdb(movie || type == TYPE_TV_SERIES ? contentId : seriesId);
        String alternateSeries = "", alternateEpisode = "";
        if ("com.cbs.ott".equals(publisher) && type == TYPE_TV_EPISODE && season >= 0 && episode > 0) {
            int divider = title.indexOf(" - ");
            if (divider > 0) {
                String series = title.substring(0, divider).trim();
                String suffix = title.substring(divider + 3).trim();
                String seasonSuffix = " " + season;
                boolean labelledSeason = season > 0 && series.endsWith(seasonSuffix);
                boolean confirmedNumbers = labelledSeason && TmdbMatching.sameTitle(suffix, "Episode " + episode);
                if ((TmdbMatching.meaningfulEpisode(suffix) || confirmedNumbers)
                        && (episodeTitle.isEmpty() || TmdbMatching.sameTitle(suffix, episodeTitle))) {
                    alternateSeries = labelledSeason ? series.substring(0, series.length() - seasonSuffix.length())
                            : series;
                    alternateEpisode = suffix;
                }
            }
        }
        return new PortraitIdentity(publisher, title, movie ? "movie" : "tv", year, season, episode,
                movie ? "" : episodeTitle, externalId, alternateSeries, alternateEpisode);
    }

    TmdbCatalog.Query query() {
        return new TmdbCatalog.Query(title, media, year, season, episode, episodeTitle, externalId,
                alternateSeries, alternateEpisode);
    }

    private static int numeric(String part) {
        return part.matches("[0-9]{1,4}") ? Integer.parseInt(part) : -1;
    }

    private static String imdb(String id) { return id.matches("tt[0-9]{7,12}") ? id : ""; }
}
