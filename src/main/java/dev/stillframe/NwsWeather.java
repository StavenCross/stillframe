package dev.stillframe;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

/** Station lookup and current observation conversion for the US weather service. */
final class NwsWeather {
    enum Icon { CLEAR, NIGHT, PARTLY_CLOUDY, CLOUD, RAIN, SNOW, THUNDER, FOG, WIND, THERMOMETER }

    static final long MAX_AGE_MILLIS = 2 * 60 * 60 * 1000L;
    private static final long FUTURE_TOLERANCE_MILLIS = 10 * 60 * 1000L;
    private static final int STATION_LIMIT = 4;
    private static final String ORIGIN = "https://api.weather.gov";
    private static final String USER_AGENT = "Stillframe/0.1 (dev.stillframe)";
    private static final int MAX_RESPONSE_BYTES = 128 * 1024;
    private static final Pattern STATION = Pattern.compile("[A-Za-z0-9-]{2,16}");
    private static final Pattern STATION_LIST = Pattern.compile("https://api\\.weather\\.gov/gridpoints/"
            + "[A-Za-z]{3}/-?[0-9]+,-?[0-9]+/stations");
    private static final Pattern POINT = Pattern.compile("-?[0-9]{1,3}\\.[0-9]{2},-?[0-9]{1,3}\\.[0-9]{2}");

    static List<String> stations(String point) throws IOException, JSONException {
        if (!POINT.matcher(point).matches()) throw new IOException("Invalid weather coordinates");
        JSONObject pointProperties = get(ORIGIN + "/points/" + point).getJSONObject("properties");
        String stationList = pointProperties.getString("observationStations");
        if (!STATION_LIST.matcher(stationList).matches()) throw new IOException("Invalid station list");
        JSONArray features = get(stationList + "?limit=" + STATION_LIMIT).getJSONArray("features");
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < features.length() && ids.size() < STATION_LIMIT; i++) {
            JSONObject station = features.getJSONObject(i).optJSONObject("properties");
            if (station != null) {
                String id = station.optString("stationIdentifier");
                if (STATION.matcher(id).matches() && !ids.contains(id)) ids.add(id);
            }
        }
        return ids;
    }

    static Weather.Reading current(List<String> stations) {
        return firstFresh(stations, station -> {
            if (!STATION.matcher(station).matches()) return null;
            JSONObject properties = get(ORIGIN + "/stations/" + station + "/observations/latest")
                    .getJSONObject("properties");
            JSONObject temperature = properties.optJSONObject("temperature");
            if (temperature == null) return null;
            Weather.Reading result = reading(temperature.optDouble("value"), temperature.optString("unitCode"),
                    properties.optString("textDescription"), properties.optString("icon"),
                    properties.optString("timestamp"), System.currentTimeMillis());
            return result == null ? null : result.atStation(station);
        });
    }

    @FunctionalInterface
    interface StationReader {
        Weather.Reading observe(String station) throws IOException, JSONException;
    }

    static Weather.Reading firstFresh(List<String> stations, StationReader reader) {
        for (String station : stations) {
            if (Thread.currentThread().isInterrupted()) return null;
            try {
                Weather.Reading result = reader.observe(station);
                if (result != null) return result;
            } catch (IOException | JSONException ignored) {
                // Individual stations can fail independently.
            }
        }
        return null;
    }

    static Weather.Reading reading(double value, String unit, String description,
                                   String iconUrl, String timestamp, long now) {
        if (!Double.isFinite(value)) return null;
        double fahrenheit;
        if ("wmoUnit:degC".equals(unit)) fahrenheit = value * 9 / 5 + 32;
        else if ("wmoUnit:degF".equals(unit)) fahrenheit = value;
        else return null;
        long observedAt;
        try {
            observedAt = Instant.parse(timestamp).toEpochMilli();
        } catch (DateTimeParseException | ArithmeticException invalid) {
            return null;
        }
        if (!fresh(observedAt, now)) return null;
        String condition = description.trim();
        if ("null".equalsIgnoreCase(condition)) condition = "";
        return new Weather.Reading(icon(condition, iconUrl), Math.round(fahrenheit) + "°F",
                condition, observedAt, "");
    }

    static boolean fresh(long observedAt, long now) {
        return observedAt > 0 && observedAt <= now + FUTURE_TOLERANCE_MILLIS
                && now - observedAt < MAX_AGE_MILLIS;
    }

    private static Icon icon(String description, String iconUrl) {
        String condition = description.toLowerCase(Locale.ROOT);
        if (condition.contains("thunder")) return Icon.THUNDER;
        if (condition.contains("freezing") || condition.contains("snow") || condition.contains("sleet")
                || condition.contains("ice") || condition.contains("hail")) return Icon.SNOW;
        if (condition.contains("rain") || condition.contains("drizzle") || condition.contains("shower"))
            return Icon.RAIN;
        if (condition.contains("fog") || condition.contains("mist") || condition.contains("haze")) return Icon.FOG;
        if (condition.contains("partly") || condition.contains("few clouds") || condition.contains("mostly clear"))
            return Icon.PARTLY_CLOUDY;
        if (condition.contains("cloud") || condition.contains("overcast")) return Icon.CLOUD;
        if (condition.contains("clear") || condition.contains("fair") || condition.contains("sunny"))
            return iconUrl.contains("/night/") ? Icon.NIGHT : Icon.CLEAR;
        if (condition.contains("wind") || condition.contains("breez")) return Icon.WIND;
        return Icon.THERMOMETER;
    }

    static JSONObject get(String url) throws IOException, JSONException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", url.startsWith(ORIGIN + "/") ? "application/geo+json" : "application/json");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        try {
            if (Thread.currentThread().isInterrupted() || connection.getResponseCode() != 200) {
                throw new IOException("Weather request failed");
            }
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream response = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int length;
                while ((length = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted() || response.size() + length > MAX_RESPONSE_BYTES) {
                        throw new IOException("Weather response interrupted or too large");
                    }
                    response.write(buffer, 0, length);
                }
                return new JSONObject(new String(response.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    private NwsWeather() {}
}
