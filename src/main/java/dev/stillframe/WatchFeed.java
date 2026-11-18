package dev.stillframe;

import static android.media.tv.TvContract.WatchNextPrograms.CONTENT_URI;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Main-thread view of the TV provider, with independently bounded scan and artwork work. */
public final class WatchFeed {
    public static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    public enum Status { LOADING, READY, PERMISSION_REQUIRED, UNAVAILABLE }
    private final Context context;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) { refresh(); }
    };
    private final ThreadPoolExecutor scans = new ThreadPoolExecutor(1, 1, 15,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
    private final WatchArtwork artwork;
    private final PortraitArtwork portraits;
    private final List<Entry> hidivePicks = HidivePicks.select(new Random());
    private Map<String, List<Entry>> items = Collections.emptyMap();
    private Status status;
    private boolean running, observing, scanning, scanAgain;
    private int generation;

    public WatchFeed(Context context, Runnable changed) {
        this.context = context.getApplicationContext();
        this.changed = changed;
        artwork = new WatchArtwork(this.context, main, this::dispatchChange);
        portraits = new PortraitArtwork(this.context, main, this::dispatchChange);
        scans.allowCoreThreadTimeOut(true);
        status = hasPermission() ? Status.LOADING : Status.PERMISSION_REQUIRED;
    }

    public void start() {
        if (running) return;
        running = true;
        configurePortraits();
        refresh();
    }

    public void stop() {
        if (!running) return;
        running = false;
        generation++;
        scanAgain = false;
        if (observing) context.getContentResolver().unregisterContentObserver(observer);
        observing = false;
        artwork.cancelPending();
        portraits.cancel();
    }

    public Status status() { return status; }

    public boolean permitted() { return status == Status.READY; }

    public List<Entry> forApp(String pkg) {
        return HidivePicks.forApp(pkg, items.get(pkg), hidivePicks);
    }

    public void refresh() {
        if (!running) return;
        if (scanning) {
            scanAgain = true;
            return;
        }
        scanning = true;
        int requested = generation;
        scans.execute(() -> {
            boolean granted = hasPermission();
            boolean rejected = false;
            Map<String, List<Entry>> result = null;
            try {
                if (granted) result = WatchPrograms.read(context);
            } catch (SecurityException denied) {
                rejected = true;
            } catch (RuntimeException unavailable) {
                // A transient vendor failure can leave the last good snapshot usable.
            }
            boolean clearSnapshot = !granted || rejected;
            Map<String, List<Entry>> snapshot = result;
            Status next = !granted ? Status.PERMISSION_REQUIRED
                    : snapshot == null ? Status.UNAVAILABLE : Status.READY;
            main.post(() -> finishScan(requested, next, clearSnapshot, snapshot));
        });
    }

    private void finishScan(int requested, Status next, boolean clearSnapshot,
                            Map<String, List<Entry>> result) {
        scanning = false;
        if (!running) return;
        if (requested == generation) {
            status = next;
            if (clearSnapshot) {
                items = Collections.emptyMap();
                artwork.clear();
                portraits.cancel();
            } else if (result != null) {
                items = result;
            }
            if (next == Status.READY && !observing) {
                try {
                    context.getContentResolver().registerContentObserver(CONTENT_URI, true, observer);
                    observing = true;
                    // Close the gap between querying and registering the observer.
                    scanAgain = true;
                } catch (RuntimeException unavailable) {
                    // Resume and explicit refresh still work without observers.
                }
            }
            prefetch();
            dispatchChange();
        }
        if (scanAgain) {
            scanAgain = false;
            refresh();
        }
    }

    private boolean hasPermission() {
        return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    private void dispatchChange() {
        if (running) changed.run();
    }

    public void warm(List<Entry> entries) {
        if (!running) return;
        if (status != Status.PERMISSION_REQUIRED || entries.isEmpty() || HidivePicks.isPick(entries.get(0))) {
            artwork.warm(entries);
        }
        if (status != Status.PERMISSION_REQUIRED) portraits.warm(entries);
    }

    public Bitmap artwork(Entry entry) {
        return running && (status != Status.PERMISSION_REQUIRED || HidivePicks.isPick(entry))
                ? artwork.artwork(entry) : null;
    }

    public void artworkSize(int width, int height) {
        artwork.size(width, height);
        portraits.size(width, height);
        if (running) prefetch();
    }

    Bitmap portrait(Entry entry) {
        return running && status != Status.PERMISSION_REQUIRED ? portraits.cached(entry) : null;
    }

    boolean portraitPending(Entry entry) {
        return running && status != Status.PERMISSION_REQUIRED && portraits.pending(entry);
    }

    boolean portraitsEnabled() { return portraits.enabled(); }

    void portraitSettingsChanged() {
        configurePortraits();
        if (!TmdbSettings.hasToken(context)) portraits.clearStoredArt();
        dispatchChange();
        if (running) prefetch();
    }

    private void configurePortraits() {
        portraits.configure(TmdbSettings.enabled(context) ? TmdbSettings.token(context) : "");
    }

    private void prefetch() {
        Map<String, List<Entry>> artworkRows = new HashMap<>(items);
        artworkRows.put(AppCatalog.HIDIVE, forApp(AppCatalog.HIDIVE));
        artwork.prefetch(artworkRows);
        if (status == Status.READY) portraits.prefetch(items);
    }

    public static final class Entry {
        public final String id, title, episode, episodeTitle, ageRating, context, synopsis, review, time, pkg;
        public final float progress;
        final long engaged;
        private final String link;
        final String poster, thumbnail;
        final PortraitIdentity portrait;

        Entry(String id, String title, String episode, String episodeTitle, String ageRating,
              String context, String synopsis, String review, String time, String pkg,
              float progress, long engaged, String link, String poster, String thumbnail) {
            this(id, title, episode, episodeTitle, ageRating, context, synopsis, review, time, pkg,
                    progress, engaged, link, poster, thumbnail, null);
        }

        Entry(String id, String title, String episode, String episodeTitle, String ageRating,
              String context, String synopsis, String review, String time, String pkg,
              float progress, long engaged, String link, String poster, String thumbnail,
              PortraitIdentity portrait) {
            this.id = id;
            this.title = title;
            this.episode = episode;
            this.episodeTitle = episodeTitle;
            this.ageRating = ageRating;
            this.context = context;
            this.synopsis = synopsis;
            this.review = review;
            this.time = time;
            this.pkg = pkg;
            this.progress = progress;
            this.engaged = engaged;
            this.link = link;
            this.poster = poster;
            this.thumbnail = thumbnail;
            this.portrait = portrait;
        }

        public boolean open(Activity activity) {
            if (link.isEmpty()) return false;
            try {
                Intent intent = Intent.parseUri(link, 0);
                ComponentName component = intent.getComponent();
                String target = intent.getPackage();
                String scheme = intent.getScheme();
                if (intent.getSelector() != null || (target != null && !pkg.equals(target))
                        || (component != null && !pkg.equals(component.getPackageName()))
                        || "file".equalsIgnoreCase(scheme) || "content".equalsIgnoreCase(scheme)
                        || (scheme == null && component == null && target == null)) return false;
                // Keep publisher extras for playback while removing caller-controlled launch flags.
                intent.setPackage(pkg);
                intent.setFlags(0);
                intent.setClipData(null);
                activity.startActivity(intent);
                return true;
            } catch (java.net.URISyntaxException | ActivityNotFoundException | SecurityException
                    | IllegalArgumentException invalid) {
                return false;
            }
        }
    }
}
