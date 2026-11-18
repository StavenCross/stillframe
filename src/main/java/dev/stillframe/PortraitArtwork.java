package dev.stillframe;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.SystemClock;
import android.util.LruCache;
import android.widget.Toast;
import dev.stillframe.WatchFeed.Entry;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Lookups are serial and bounded. Disk hits can resolve independently of slow TMDB requests. */
final class PortraitArtwork {
    private static final long RETRY_MS = TimeUnit.MINUTES.toMillis(1);
    private final Handler main;
    private final Context context;
    private final Runnable changed;
    private final PortraitPosterStore store;
    private final String language = Locale.getDefault().toLanguageTag();
    private final ThreadPoolExecutor disk = worker();
    private final ThreadPoolExecutor network = worker();
    private final LruCache<String, Bitmap> images = new LruCache<String, Bitmap>(18 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private final Map<String, Long> retry = new HashMap<>();
    private final Set<String> diskMiss = new HashSet<>();
    private final Set<String> done = new HashSet<>();
    private final Runnable runNext = this::next;
    private List<Entry> visible = Collections.emptyList();
    private PortraitWarmup plan = new PortraitWarmup(Collections.emptyMap());
    private String token = "";
    private int width, height;
    private volatile int generation;
    private boolean reading, fetching, unauthorized, purge;
    private long backoff;

    PortraitArtwork(Context context, Handler main, Runnable changed) {
        this.main = main;
        this.context = context;
        this.changed = changed;
        store = new PortraitPosterStore(context.getCacheDir());
        disk.execute(store::prune);
    }

    private static ThreadPoolExecutor worker() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1));
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    void configure(String nextToken) {
        if (token.equals(nextToken)) return;
        token = nextToken;
        cancel();
        unauthorized = false;
        backoff = 0;
        retry.clear();
        if (!enabled()) images.evictAll();
    }

    boolean enabled() { return !token.isEmpty(); }

    void clearStoredArt() {
        images.evictAll();
        purge = true;
        next();
    }

    void cancel() {
        generation++;
        main.removeCallbacks(runNext);
        visible = Collections.emptyList();
        plan = new PortraitWarmup(Collections.emptyMap());
        diskMiss.clear();
        done.clear();
    }

    void size(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        cancel();
        images.evictAll();
    }

    void warm(List<Entry> row) {
        if (!enabled()) return;
        visible = row.subList(0, Math.min(PortraitWarmup.VISIBLE_COUNT, row.size()));
        next();
    }

    void prefetch(Map<String, List<Entry>> rows) {
        if (!enabled()) return;
        plan = new PortraitWarmup(rows);
        diskMiss.retainAll(plan.keys);
        done.retainAll(plan.keys);
        retry.keySet().retainAll(plan.keys);
        main.removeCallbacks(runNext);
        main.postDelayed(runNext, 120);
    }

    Bitmap cached(Entry entry) {
        return enabled() && entry.portrait != null ? image(entry.portrait) : null;
    }

    boolean pending(Entry entry) {
        if (!enabled() || width < 1 || height < 1 || entry.portrait == null || cached(entry) != null) return false;
        long now = SystemClock.uptimeMillis();
        String key = entry.portrait.key;
        if (retry.getOrDefault(key, 0L) > now) return false;
        return !diskMiss.contains(key) || (!unauthorized && now >= backoff);
    }

    private Bitmap image(PortraitIdentity identity) {
        Bitmap picture = images.get(identity.key);
        return picture != null || identity.seriesKey.isEmpty() ? picture : images.get(identity.seriesKey);
    }

    private void next() {
        if (purge) {
            if (!reading) {
                purge = false;
                reading = true;
                disk.execute(() -> {
                    try {
                        store.clear();
                    } finally {
                        main.post(() -> {
                            reading = false;
                            next();
                        });
                    }
                });
            }
            return;
        }
        if (!enabled() || width < 1 || height < 1 || (visible.isEmpty() && plan.first.isEmpty())) return;
        long now = SystemClock.uptimeMillis();
        boolean firstDone = true;
        for (Entry entry : plan.first) {
            if (image(entry.portrait) != null) done.add(entry.portrait.key);
            else if (!done.contains(entry.portrait.key)) firstDone = false;
        }
        List<Entry> phase = firstDone ? plan.rest : plan.first;
        if (!reading) {
            Entry candidate = choose(visible, phase, now, false);
            if (candidate != null) readDisk(candidate.portrait);
        }
        if (!fetching && !unauthorized) {
            if (now < backoff) {
                main.removeCallbacks(runNext);
                main.postAtTime(runNext, backoff);
            } else {
                Entry candidate = choose(visible, phase, now, true);
                if (candidate != null) fetch(candidate.portrait);
            }
        }
    }

    private Entry choose(List<Entry> visible, List<Entry> plan, long now, boolean onNetwork) {
        Entry result = candidate(visible, now, onNetwork, false);
        return result != null ? result : candidate(plan, now, onNetwork, true);
    }

    private Entry candidate(List<Entry> entries, long now, boolean onNetwork, boolean background) {
        for (Entry entry : entries) {
            PortraitIdentity identity = entry.portrait;
            if (identity == null || (background && done.contains(identity.key))) continue;
            if (image(identity) != null) {
                done.add(identity.key);
                continue;
            }
            if (retry.getOrDefault(identity.key, 0L) > now) continue;
            if (onNetwork == diskMiss.contains(identity.key)) return entry;
        }
        return null;
    }

    private void readDisk(PortraitIdentity identity) {
        int requested = generation, w = width, h = height;
        reading = true;
        disk.execute(() -> {
            Bitmap exact = store.cached(diskKey(identity.key), w, h);
            Bitmap series = exact == null && !identity.seriesKey.isEmpty()
                    ? store.cached(diskKey(identity.seriesKey), w, h) : null;
            long miss = exact == null && series == null ? store.knownMiss(diskKey(identity.key)) : 0;
            main.post(() -> {
                reading = false;
                if (requested == generation) {
                    if (exact != null || series != null) {
                        ready(identity, exact != null ? exact : series, series != null);
                    } else {
                        diskMiss.add(identity.key);
                        if (miss > 0) {
                            retry.put(identity.key, SystemClock.uptimeMillis() + miss);
                            done.add(identity.key);
                        }
                    }
                    if (exact != null || series != null || miss > 0 || unauthorized
                            || SystemClock.uptimeMillis() < backoff) notifyVisible(identity);
                }
                next();
            });
        });
    }

    private void fetch(PortraitIdentity identity) {
        int requested = generation, w = width, h = height;
        String credential = token;
        fetching = true;
        network.execute(() -> {
            Bitmap poster = null;
            long delay = PortraitPosterStore.MISS_AGE, cooldown = 0;
            boolean denied = false, shared = false;
            try {
                if (generation == requested) {
                    TmdbCatalog.PosterResult result = TmdbCatalog.lookup(identity.query(), credential, language,
                            () -> generation == requested);
                    if (result != null && generation == requested) {
                        shared = result.shared && !identity.seriesKey.isEmpty();
                        poster = store.download(result.url, diskKey(shared ? identity.seriesKey : identity.key),
                                w, h, () -> generation == requested);
                    }
                    if (poster == null) store.rememberMiss(diskKey(identity.key), () -> generation == requested);
                }
            } catch (TmdbCatalog.Unauthorized failure) {
                denied = true;
            } catch (TmdbTransport.RateLimited failure) {
                cooldown = failure.delay;
                delay = cooldown;
            } catch (IOException | RuntimeException failure) {
                delay = RETRY_MS;
                cooldown = RETRY_MS;
            } finally {
                Bitmap result = poster;
                long wait = delay, quiet = cooldown;
                boolean authFailed = denied, series = shared;
                main.post(() -> complete(requested, identity, result, series, wait, quiet, authFailed));
            }
        });
    }

    private void complete(int requested, PortraitIdentity identity, Bitmap poster, boolean shared,
                          long delay, long cooldown, boolean denied) {
        fetching = false;
        if (requested == generation) {
            long now = SystemClock.uptimeMillis();
            unauthorized = denied;
            if (denied) Toast.makeText(context, "TMDB rejected the credential. Replace it in Portrait posters.",
                    Toast.LENGTH_LONG).show();
            if (poster != null) ready(identity, poster, shared);
            else retry.put(identity.key, deadline(now, delay));
            if (cooldown > 0) backoff = deadline(now, cooldown);
            if (cooldown == 0 && !denied) done.add(identity.key);
            if (cooldown > 0 || denied) changed.run();
            else notifyVisible(identity);
        }
        next();
    }

    private static long deadline(long now, long delay) {
        return now + Math.min(Long.MAX_VALUE - now, delay);
    }

    private void ready(PortraitIdentity identity, Bitmap bitmap, boolean shared) {
        // Touch the first three and focused cards before tail inserts so they are last to evict.
        for (Entry entry : plan.first) cached(entry);
        for (Entry entry : visible) cached(entry);
        images.put(shared ? identity.seriesKey : identity.key, bitmap);
        diskMiss.remove(identity.key);
        done.add(identity.key);
    }

    private void notifyVisible(PortraitIdentity identity) {
        boolean shared = !identity.seriesKey.isEmpty() && images.get(identity.seriesKey) != null;
        for (Entry entry : visible) {
            PortraitIdentity current = entry.portrait;
            if (current != null && (identity.key.equals(current.key)
                    || shared && identity.seriesKey.equals(current.seriesKey))) {
                changed.run();
                return;
            }
        }
    }

    private String diskKey(String key) { return language + "\0" + key; }
}
