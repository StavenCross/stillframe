package dev.stillframe;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** The home screen's optional current weather. Call its lifecycle and settings methods on main. */
final class Weather {
    static final String PRIVACY = "Weather supports US addresses and ZIP codes. This TV saves your entry."
            + " Zippopotam.us receives the five-digit ZIP. Android's geocoder may send address text to its service."
            + " The National Weather Service receives coordinates rounded to 0.01°.";
    private static final String LOCATION = "location";
    private static final String POINT = "nws_point";
    private static final String ICON = "nws_icon";
    private static final String TEMPERATURE = "nws_temperature";
    private static final String CONDITION = "nws_condition";
    private static final String OBSERVED_AT = "nws_observed_at";
    private static final String CHECKED_AT = "nws_checked_at";
    private static final String STATIONS = "nws_stations";
    private static final String STATIONS_CHECKED_AT = "nws_stations_checked_at";
    private static final long REFRESH_MILLIS = 30 * 60 * 1000L;
    private static final long RETRY_MILLIS = 5 * 60 * 1000L;
    private static final long STATION_CACHE_MILLIS = 24 * 60 * 60 * 1000L;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    static final class Reading {
        final NwsWeather.Icon icon;
        final String temperature;
        final String condition;
        final long observedAt;
        final String station;

        Reading(NwsWeather.Icon icon, String temperature, String condition, long observedAt, String station) {
            this.icon = icon;
            this.temperature = temperature;
            this.condition = condition;
            this.observedAt = observedAt;
            this.station = station;
        }

        Reading atStation(String id) {
            return new Reading(icon, temperature, condition, observedAt, id);
        }
    }

    private final Context context;
    private final SharedPreferences preferences;
    private final Consumer<Reading> onReading;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refresh = this::request;
    private final Runnable expire = () -> show(null);
    private boolean running;
    private int generation;
    private Future<?> work;

    Weather(Context context, Consumer<Reading> onReading) {
        this.context = context.getApplicationContext();
        this.onReading = onReading;
        preferences = this.context.getSharedPreferences("weather", Context.MODE_PRIVATE);
    }

    String location() {
        return preferences.getString(LOCATION, "");
    }

    void location(String value) {
        String location = value == null ? "" : value.trim();
        if (location.equals(location())) return;
        cancelPending();
        preferences.edit().clear().putString(LOCATION, location).apply();
        if (running) {
            show(null);
            request();
        }
    }

    void start() {
        if (running) return;
        running = true;
        Reading cached = cachedReading();
        show(cached);
        if (location().isEmpty()) return;
        long age = System.currentTimeMillis() - preferences.getLong(CHECKED_AT, 0);
        if (cached == null || age < 0 || age >= REFRESH_MILLIS) {
            request();
        } else {
            main.postDelayed(refresh, REFRESH_MILLIS - age);
        }
    }

    private Reading cachedReading() {
        long observedAt = preferences.getLong(OBSERVED_AT, 0);
        String temperature = preferences.getString(TEMPERATURE, "");
        if (temperature.isEmpty() || !NwsWeather.fresh(observedAt, System.currentTimeMillis())) return null;
        NwsWeather.Icon icon;
        try {
            icon = NwsWeather.Icon.valueOf(preferences.getString(ICON, "THERMOMETER"));
        } catch (IllegalArgumentException invalid) {
            icon = NwsWeather.Icon.THERMOMETER;
        }
        return new Reading(icon, temperature, preferences.getString(CONDITION, ""), observedAt, "");
    }

    private void show(Reading reading) {
        main.removeCallbacks(expire);
        onReading.accept(reading);
        if (reading != null) {
            long remaining = reading.observedAt + NwsWeather.MAX_AGE_MILLIS - System.currentTimeMillis();
            main.postDelayed(expire, Math.max(0, remaining));
        }
    }

    void stop() {
        running = false;
        cancelPending();
    }

    private void cancelPending() {
        generation++;
        main.removeCallbacks(refresh);
        main.removeCallbacks(expire);
        if (work != null) {
            work.cancel(true);
            work = null;
        }
    }

    private void request() {
        String query = location();
        if (!running || query.isEmpty()) return;
        String cachedPoint = preferences.getString(POINT, "");
        String cachedStations = preferences.getString(STATIONS, "");
        long cachedStationTime = preferences.getLong(STATIONS_CHECKED_AT, 0);
        int requestGeneration = generation;
        work = WORKER.submit(() -> {
            String point = cachedPoint;
            List<String> stations = new ArrayList<>();
            if (!cachedStations.isEmpty()) stations.addAll(Arrays.asList(cachedStations.split(",")));
            long stationTime = cachedStationTime;
            Reading result = null;
            try {
                if (point.isEmpty()) point = geocode(query);
                if (point != null && !Thread.currentThread().isInterrupted()) {
                    long age = System.currentTimeMillis() - stationTime;
                    if (stations.isEmpty() || age < 0 || age >= STATION_CACHE_MILLIS) {
                        try {
                            List<String> discovered = NwsWeather.stations(point);
                            if (!discovered.isEmpty()) {
                                stations = discovered;
                                stationTime = System.currentTimeMillis();
                            }
                        } catch (IOException | JSONException ignored) {
                            // A saved station can still answer when discovery is temporarily unavailable.
                        }
                    }
                    result = NwsWeather.current(stations);
                    if (result != null && stations.remove(result.station)) stations.add(0, result.station);
                }
            } catch (IOException | JSONException | RuntimeException ignored) {
                // A lookup failure leaves the last reading visible until its observation expires.
            }
            String resolvedPoint = point;
            String resolvedStations = String.join(",", stations);
            long resolvedStationTime = stationTime;
            Reading fetched = result;
            main.post(() -> {
                if (!running || requestGeneration != generation) return;
                SharedPreferences.Editor changes = preferences.edit();
                if (resolvedPoint != null && !resolvedPoint.isEmpty()) changes.putString(POINT, resolvedPoint);
                if (!resolvedStations.isEmpty()) {
                    changes.putString(STATIONS, resolvedStations).putLong(STATIONS_CHECKED_AT, resolvedStationTime);
                }
                if (fetched != null) {
                    changes.putString(ICON, fetched.icon.name()).putString(TEMPERATURE, fetched.temperature)
                            .putString(CONDITION, fetched.condition).putLong(OBSERVED_AT, fetched.observedAt)
                            .putLong(CHECKED_AT, System.currentTimeMillis());
                }
                changes.apply();
                main.postDelayed(refresh, fetched == null ? RETRY_MILLIS : REFRESH_MILLIS);
                if (fetched != null) show(fetched);
            });
        });
    }

    private String geocode(String query) throws IOException, JSONException {
        String zip = zipCode(query);
        if (zip != null) {
            JSONObject place = NwsWeather.get("https://api.zippopotam.us/us/" + zip)
                    .getJSONArray("places").getJSONObject(0);
            return roundedPoint(place.getDouble("latitude"), place.getDouble("longitude"));
        }
        return addressPoint(query);
    }

    @SuppressWarnings("deprecation") // The synchronous call is isolated on the weather worker.
    private String addressPoint(String query) throws IOException {
        if (!Geocoder.isPresent()) return null;
        List<Address> addresses = new Geocoder(context, Locale.getDefault()).getFromLocationName(query, 1);
        if (addresses == null || addresses.isEmpty()) return null;
        Address address = addresses.get(0);
        return "US".equalsIgnoreCase(address.getCountryCode()) && address.hasLatitude() && address.hasLongitude()
                ? roundedPoint(address.getLatitude(), address.getLongitude()) : null;
    }

    static String zipCode(String input) {
        return input.matches("[0-9]{5}(-[0-9]{4})?") ? input.substring(0, 5) : null;
    }

    static String roundedPoint(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return null;
        return String.format(Locale.ROOT, "%.2f,%.2f", latitude, longitude);
    }

}
