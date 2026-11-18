# Portrait posters

Stillframe can replace Continue Watching artwork with 2:3 movie and series
posters from [TMDB](https://www.themoviedb.org). The streaming provider
continues to supply the Watch Next entry, progress and playback link.

## Set up on the TV

1. Sign in to [TMDB API settings](https://www.themoviedb.org/settings/api) and
   request developer API access for noncommercial use.
2. Copy the **API Read Access Token** or **v3 API Key** shown in the account's
   API settings.
3. In Stillframe open **Edit Dock → Portrait posters → Set up TMDB**. Enter
   the credential and choose **Save and turn on**.

The credential is stored in app-private settings on the TV. The APK and
repository contain no credentials. Local development credentials can be
kept in the ignored `.env.local` file as `TMDB_READ_ACCESS_TOKEN` or
`TMDB_API_KEY`. The build does not read that file. Use the same menu to
turn the feature off or replace the credential. **Remove TMDB credential**
also clears Stillframe's downloaded portrait cache.
TMDB's approved logo and required notice appear in **About and credits**.

## Matching and fallback

The viewer opts in before Stillframe sends title searches to TMDB. Lookups
use the movie or series type, a supported external identifier when available,
and a movie release year or episode details when those fields can help
distinguish exact titles. Ambiguous titles keep the streaming app's artwork.
When a Paramount+ episode combines the series and episode names in its
title, Stillframe can search by the series portion. It verifies the
episode name against the published season and episode number before using
the poster. A generic label such as “Episode 3” also requires the season
number in the provider title and a single series with that episode in TMDB.
Stillframe does not send viewing progress, account profiles, or resume links
to TMDB.

The launcher uses a TMDB poster after validating a 2:3 image at decode.
It checks the local cache independently of any running catalog request.
A new card can wait up to 240 ms for a pending portrait before choosing
available provider artwork. Once an image appears, the card keeps that
source for the current visit. A portrait downloaded after that can appear
the next time you visit the card.

The background queue warms the first three eligible entries across all
providers first, then continues through the rest of the feed. Visible
cards take priority if you navigate ahead. Verified series IDs and
independently unique series matches can reuse their posters across episodes.
Definitive misses are cached for 12 hours. TMDB rate-limit responses pause
network lookups for the time specified in `Retry-After` when supplied.
The app-private disk cache is capped at 24 MiB and expires poster images
after 14 days.

Run the local host checks with `./test.sh`. Authenticated testing on the
streamer requires a valid credential configured in the app.
