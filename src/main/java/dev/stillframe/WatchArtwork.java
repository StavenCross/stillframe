package dev.stillframe;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.util.LruCache;
import dev.stillframe.WatchFeed.Entry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.HttpsURLConnection;

/** Loads only display-sized artwork and deprioritizes work for a previous provider. */
final class WatchArtwork {
    private static final int ARTWORK_CACHE_BYTES = 24 * 1024 * 1024;
    private static final int PREFETCH_BUDGET_BYTES = 16 * 1024 * 1024;
    private static final int PREFETCH_PER_APP = 4;

    private final Context context;
    private final Handler main;
    private final Runnable artworkChanged;
    private final ThreadPoolExecutor artwork = new ThreadPoolExecutor(2, 2, 15,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(12));
    private final Map<String, Runnable> pending = new HashMap<>();
    private final LruCache<String, Bitmap> images = new LruCache<String, Bitmap>(ARTWORK_CACHE_BYTES) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getAllocationByteCount(); }
    };
    private final LruCache<String, Long> failures = new LruCache<>(64);
    private final ArrayDeque<Entry> prefetchQueue = new ArrayDeque<>();
    private final Runnable continuePrefetch = this::prefetchNext;
    private List<String> planKeys = Collections.emptyList();
    private String prefetchKey;
    private boolean prefetchVisible;
    private String warmPackage = "";
    private int generation, width, height;

    WatchArtwork(Context context, Handler main, Runnable artworkChanged) {
        this.context = context;
        this.main = main;
        this.artworkChanged = artworkChanged;
        artwork.allowCoreThreadTimeOut(true);
    }

    void cancelPending() {
        generation++;
        artwork.getQueue().clear();
        pending.clear();
        prefetchQueue.clear();
        planKeys = Collections.emptyList();
        prefetchKey = null;
        prefetchVisible = false;
        warmPackage = "";
        main.removeCallbacks(artworkChanged);
        main.removeCallbacks(continuePrefetch);
    }

    void clear() {
        cancelPending();
        images.evictAll();
        failures.evictAll();
    }

    void size(int width, int height) {
        if (this.width == width && this.height == height) return;
        this.width = width;
        this.height = height;
        cancelPending();
    }

    void warm(List<Entry> entries) {
        String pkg = entries.isEmpty() ? "" : entries.get(0).pkg;
        if (!warmPackage.equals(pkg)) {
            warmPackage = pkg;
            pending.entrySet().removeIf(task -> !task.getKey().equals(prefetchKey)
                    && artwork.remove(task.getValue()));
        }
        for (int i = 0; i < Math.min(6, entries.size()); i++) artwork(entries.get(i));
        prefetchNext();
    }

    Bitmap artwork(Entry entry) {
        if (width <= 0 || height <= 0 || !hasArtwork(entry)) return null;
        String key = key(entry);
        Bitmap image = images.get(key);
        if (image != null) return image;
        if (key.equals(prefetchKey)) prefetchVisible = true;
        if (mayLoad(key)) enqueue(entry, key);
        return null;
    }

    void prefetch(Map<String, List<Entry>> entries) {
        if (width <= 0 || height <= 0) return;
        long estimatedBytes = (long) width * height * 4;
        int capacity = (int) Math.max(1, Math.min(64, PREFETCH_BUDGET_BYTES / estimatedBytes));
        List<Entry> plan = prefetchPlan(entries, capacity);
        List<String> keys = new ArrayList<>(plan.size());
        for (Entry entry : plan) keys.add(key(entry));
        if (keys.equals(planKeys)) return;
        planKeys = keys;
        prefetchQueue.clear();
        prefetchQueue.addAll(plan);
        // Give the first layout a chance to request the currently selected row.
        main.removeCallbacks(continuePrefetch);
        main.postDelayed(continuePrefetch, 100);
    }

    static List<Entry> prefetchPlan(Map<String, List<Entry>> entries, int limit) {
        if (limit <= 0) return Collections.emptyList();
        List<String> packages = new ArrayList<>(entries.keySet());
        Collections.sort(packages);
        List<List<Entry>> perApp = new ArrayList<>();
        for (String pkg : packages) {
            List<Entry> eligible = new ArrayList<>(PREFETCH_PER_APP);
            for (Entry entry : entries.get(pkg)) {
                if (hasArtwork(entry)) eligible.add(entry);
                if (eligible.size() == PREFETCH_PER_APP) break;
            }
            if (!eligible.isEmpty()) perApp.add(eligible);
        }
        List<Entry> plan = new ArrayList<>();
        // Cache the newest card for each app before filling the rest of any row.
        for (int rank = 0; rank < PREFETCH_PER_APP; rank++) {
            for (List<Entry> row : perApp) {
                if (row.size() > rank) plan.add(row.get(rank));
                if (plan.size() == limit) return plan;
            }
        }
        return plan;
    }

    private void prefetchNext() {
        if (!pending.isEmpty()) return;
        while (!prefetchQueue.isEmpty()) {
            Entry entry = prefetchQueue.removeFirst();
            String key = key(entry);
            if (images.get(key) == null && mayLoad(key) && enqueue(entry, key)) {
                prefetchKey = key;
                prefetchVisible = false;
                return;
            }
        }
    }

    private static boolean hasArtwork(Entry entry) {
        return !entry.poster.isEmpty() || !entry.thumbnail.isEmpty();
    }

    private String key(Entry entry) {
        // Include the slot dimensions so density changes cannot reuse an undersized decode.
        return width + "x" + height + ":" + entry.pkg + ":" + entry.poster.length() + ":"
                + entry.poster + entry.thumbnail;
    }

    private boolean mayLoad(String key) {
        if (pending.containsKey(key)) return false;
        Long retryAt = failures.get(key);
        return retryAt == null || SystemClock.uptimeMillis() >= retryAt;
    }

    private boolean enqueue(Entry entry, String key) {
        int requested = generation;
        int slotWidth = width;
        int slotHeight = height;
        Runnable load = () -> {
            Bitmap poster = decode(entry.poster, entry.pkg, slotWidth, slotHeight);
            Bitmap loaded = poster == null ? decode(entry.thumbnail, entry.pkg, slotWidth, slotHeight) : poster;
            main.post(() -> {
                if (generation != requested || pending.remove(key) == null) return;
                boolean notify = true;
                if (key.equals(prefetchKey)) {
                    notify = prefetchVisible;
                    prefetchKey = null;
                    prefetchVisible = false;
                }
                if (loaded == null) {
                    failures.put(key, SystemClock.uptimeMillis() + 30_000);
                } else {
                    images.put(key, loaded);
                    if (notify) {
                        main.removeCallbacks(artworkChanged);
                        main.postDelayed(artworkChanged, 32);
                    }
                }
                prefetchNext();
            });
        };
        pending.put(key, load);
        try {
            artwork.execute(load);
            return true;
        } catch (RejectedExecutionException full) {
            pending.remove(key);
            return false;
        }
    }

    private Bitmap decode(String source, String publisher, int slotWidth, int slotHeight) {
        if (source.isEmpty()) return null;
        try {
            Uri uri = Uri.parse(source);
            byte[] bytes;
            if ("https".equals(uri.getScheme())) {
                HttpsURLConnection connection = (HttpsURLConnection) new URL(source).openConnection();
                try {
                    connection.setConnectTimeout(2000);
                    connection.setReadTimeout(2000);
                    connection.setInstanceFollowRedirects(false);
                    if (connection.getResponseCode() != 200) return null;
                    bytes = readImage(connection.getInputStream());
                } finally {
                    connection.disconnect();
                }
            } else if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())
                    || ContentResolver.SCHEME_ANDROID_RESOURCE.equals(uri.getScheme())) {
                bytes = readImage(context.getContentResolver().openInputStream(uri));
            } else {
                return null;
            }
            Bitmap bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes)),
                    (decoder, info, imageSource) -> {
                        int[] size = ArtworkCrop.decodeSize(info.getSize().getWidth(), info.getSize().getHeight(),
                                slotWidth, slotHeight);
                        decoder.setTargetSize(size[0], size[1]);
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    });
            return crop(bitmap, publisher, slotWidth, slotHeight);
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    private static Bitmap crop(Bitmap bitmap, String publisher, int slotWidth, int slotHeight) {
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int keptWidth = Math.max(1, Math.min(width, Math.round(height * ((float) slotWidth / slotHeight))));
        int keptHeight = Math.max(1, Math.min(height, Math.round(width * ((float) slotHeight / slotWidth))));
        if (keptWidth == width && keptHeight == height) return bitmap;
        float subject = .5f;
        // Netflix's sampled wordmarks cluster around the center. For other art,
        // estimate where the visual detail is concentrated before cropping.
        if (keptWidth < width && !"com.netflix.ninja".equals(publisher)) {
            float fraction = (float) keptWidth / width;
            subject = ArtworkCrop.subjectX(sample(bitmap, 40, 24), 40, 24, Math.round(40 * fraction));
            if ("com.amazon.amazonvideo.livingroom".equals(publisher) || "com.wbd.stream".equals(publisher)) {
                float lettering = ArtworkCrop.letteringX(sample(bitmap, 96, 54), 96, 54,
                        Math.max(1, Math.round(96 * fraction)));
                if (Math.abs(lettering - subject) > .14f) {
                    subject = lettering;
                    keptWidth = ArtworkCrop.wordmarkWidth(keptWidth, width);
                }
            }
        }
        return Bitmap.createBitmap(bitmap, ArtworkCrop.offset(width, keptWidth, subject),
                ArtworkCrop.offset(height, keptHeight, .46f), keptWidth, keptHeight);
    }

    private static int[] sample(Bitmap source, int width, int height) {
        Bitmap reduced = Bitmap.createScaledBitmap(source, width, height, true);
        int[] pixels = new int[width * height];
        reduced.getPixels(pixels, 0, width, 0, 0, width, height);
        return pixels;
    }

    private static byte[] readImage(InputStream source) throws IOException {
        if (source == null) throw new IOException("Artwork is unavailable");
        long deadline = SystemClock.uptimeMillis() + 4000;
        try (InputStream input = source; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1; ) {
                if (bytes.size() + count > 6 * 1024 * 1024 || SystemClock.uptimeMillis() > deadline) {
                    throw new IOException("Artwork exceeds download limits");
                }
                bytes.write(buffer, 0, count);
            }
            return bytes.toByteArray();
        }
    }

}
