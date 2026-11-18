package dev.stillframe;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import javax.net.ssl.HttpsURLConnection;

/** Applies one deadline across a catalog lookup and honors TMDB rate-limit hints. */
final class TmdbTransport {
    private static final String API = "https://api.themoviedb.org/3";
    private static final int MAX_RESPONSE = 1024 * 1024;
    private final String token;
    private final BooleanSupplier active;
    private final long deadline;

    TmdbTransport(String token, BooleanSupplier active, int seconds) {
        this.token = token;
        this.active = active;
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
    }

    static final class RateLimited extends IOException {
        final long delay;

        RateLimited(String header) {
            super("TMDB rate limit");
            delay = retryDelay(header, System.currentTimeMillis());
        }
    }

    static long retryDelay(String header, long now) {
        if (header != null) {
            try {
                long seconds = Long.parseLong(header.trim());
                if (seconds >= 0) return Math.max(1000L, TimeUnit.SECONDS.toMillis(seconds));
            } catch (NumberFormatException secondsInvalid) {
                try {
                    long date = ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant().toEpochMilli();
                    return Math.max(1000L, date - now);
                } catch (DateTimeParseException dateInvalid) {
                    // An absent or malformed hint uses a conservative default.
                }
            }
        }
        return TimeUnit.MINUTES.toMillis(1);
    }

    JSONObject get(String route) throws IOException {
        int remaining = remainingMillis();
        HttpsURLConnection connection = (HttpsURLConnection) new URL(API + TmdbCatalog.apiRoute(route, token)).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(Math.min(2500, remaining));
            connection.setReadTimeout(Math.min(2500, remaining));
            connection.setRequestProperty("Accept", "application/json");
            if (!TmdbCatalog.isApiKey(token)) connection.setRequestProperty("Authorization", "Bearer " + token);
            remainingMillis();
            int status = connection.getResponseCode();
            if (status == 401 || status == 403) throw new TmdbCatalog.Unauthorized();
            if (status == 429) throw new RateLimited(connection.getHeaderField("Retry-After"));
            if (status == 404) return null;
            if (status != 200) throw new IOException("TMDB request failed");
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream response = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) != -1; ) {
                    remainingMillis();
                    if (response.size() + count > MAX_RESPONSE) throw new IOException("TMDB response too large");
                    response.write(buffer, 0, count);
                }
                return new JSONObject(new String(response.toByteArray(), StandardCharsets.UTF_8));
            }
        } catch (JSONException malformed) {
            throw new IOException("Invalid TMDB response");
        } finally {
            connection.disconnect();
        }
    }

    private int remainingMillis() throws InterruptedIOException {
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remaining < 1 || Thread.currentThread().isInterrupted() || !active.getAsBoolean())
            throw new InterruptedIOException("TMDB request canceled or timed out");
        return (int) remaining;
    }
}
