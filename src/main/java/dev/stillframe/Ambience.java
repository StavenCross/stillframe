package dev.stillframe;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;

import dev.stillframe.WallpaperFolders.Source;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/** Prepares each wallpaper and its accent before Home switches frames. Call on main. */
final class Ambience {
    private static final long FRAME_MILLIS = 60_000L;
    private static final String SELECTION = "selection";
    private static final String LOCAL = "local";
    private static final String SHARED_PREFIX = "shared:";
    private static final String BUNDLED = "Bundled wallpapers";
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private final Context context;
    private final WallpaperFolders folders;
    private final SharedPreferences preferences;
    interface Listener {
        void frame(Bitmap bitmap, Bitmap glass, int accent);
    }
    private final Listener onFrame;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable rotate = this::rotate;
    private boolean running;
    private int generation;
    private Future<?> work;
    private Frame current;
    private Frame prepared;
    private boolean preparedReady;
    private long nextFrameAt;
    private String folderLabel;

    Ambience(Context context, Listener onFrame) {
        this.context = context.getApplicationContext();
        this.onFrame = onFrame;
        folders = new WallpaperFolders(this.context);
        preferences = this.context.getSharedPreferences("wallpapers", Context.MODE_PRIVATE);
        folderLabel = defaultLabel(selection());
    }

    void start() {
        if (running) return;
        running = true;
        prepare(false);
    }

    void stop() {
        running = false;
        generation++;
        main.removeCallbacks(rotate);
        if (work != null) {
            work.cancel(true);
            work = null;
        }
        prepared = null;
        preparedReady = false;
    }

    void reload() {
        boolean wasRunning = running;
        stop();
        current = null;
        if (wasRunning) start();
    }

    String folderLabel() {
        return folderLabel;
    }

    void folder(Uri uri) {
        if (uri != null && !"content".equals(uri.getScheme())) return;
        if (uri != null) {
            try {
                context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // Providers without persistent access can still work with the current read grant.
            }
        }
        select(uri == null ? "" : uri.toString());
    }

    void useLocalFolder() {
        select(LOCAL);
    }

    void useSharedFolder(String path) {
        if (path != null && !path.isEmpty()) select(SHARED_PREFIX + path);
    }

    void sharedFolders(Consumer<List<String>> onFolders) {
        WallpaperFolders.INDEX_WORKER.execute(() -> {
            List<String> paths = folders.sharedPaths();
            main.post(() -> onFolders.accept(paths));
        });
    }

    boolean hasLocalFolder() {
        return folders.localFolder() != null;
    }

    String localFolderPath() {
        File folder = folders.localFolder();
        return folder == null ? "" : folder.getAbsolutePath();
    }

    private String selection() {
        return preferences.getString(SELECTION, "");
    }

    private void select(String next) {
        String previous = selection();
        preferences.edit().putString(SELECTION, next).apply();
        folderLabel = defaultLabel(next);
        reload();
        if (!previous.equals(next) && previous.startsWith("content:")) {
            try {
                context.getContentResolver().releasePersistableUriPermission(
                        Uri.parse(previous), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) {
                // The old provider may have already revoked or never offered persistence.
            }
        }
    }

    private static String defaultLabel(String selection) {
        if (selection.isEmpty()) return BUNDLED;
        if (LOCAL.equals(selection)) return "Wallpapers folder";
        if (selection.startsWith(SHARED_PREFIX)) {
            String path = selection.substring(SHARED_PREFIX.length());
            return "/".equals(path) ? "Shared storage" : path;
        }
        return "Selected folder";
    }

    private void prepare(boolean advance) {
        String selected = selection();
        Frame last = current;
        int requestGeneration = generation;
        DisplayMetrics display = context.getResources().getDisplayMetrics();
        int width = Math.max(1, display.widthPixels);
        int height = Math.max(1, display.heightPixels);
        double scale = Math.min(1, 1920.0 / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        work = WORKER.submit(() -> {
            Frame result = load(selected, last, advance, targetWidth, targetHeight);
            main.post(() -> {
                if (!running || requestGeneration != generation) return;
                if (!advance || SystemClock.uptimeMillis() >= nextFrameAt) {
                    show(result);
                } else {
                    prepared = result;
                    preparedReady = true;
                }
            });
        });
    }

    private void rotate() {
        if (preparedReady) show(prepared);
    }

    private void show(Frame frame) {
        prepared = null;
        preparedReady = false;
        if (frame != null) {
            folderLabel = frame.label;
            if (frame != current) {
                current = frame;
                onFrame.frame(frame.bitmap, frame.glass, frame.accent);
            }
        }
        // The next frame uses the time this one became visible, including any slow provider path.
        if (!running) return;
        nextFrameAt = SystemClock.uptimeMillis() + FRAME_MILLIS;
        main.removeCallbacks(rotate);
        main.postAtTime(rotate, nextFrameAt);
        prepare(true);
    }

    private Frame load(String selection, Frame last, boolean advance, int width, int height) {
        if (!selection.isEmpty()) {
            try {
                String label = defaultLabel(selection);
                List<Source> sources;
                if (LOCAL.equals(selection)) {
                    sources = folders.localSources();
                } else if (selection.startsWith(SHARED_PREFIX)) {
                    sources = folders.sharedSources(selection.substring(SHARED_PREFIX.length()));
                } else {
                    Uri tree = Uri.parse(selection);
                    label = folders.documentLabel(tree, label);
                    sources = folders.documentSources(tree);
                }
                Frame frame = choose(sources, last, advance, label, width, height);
                if (frame != null) return frame;
            } catch (RuntimeException ignored) {
                // A removed drive or revoked permission may invalidate a selected folder.
            }
        }
        try {
            String label = selection.isEmpty() ? BUNDLED : "Using bundled wallpapers · folder unavailable";
            return choose(folders.bundledSources(), last, advance, label, width, height);
        } catch (IOException ignored) {
            return null;
        }
    }

    private Frame choose(List<Source> sources, Frame last, boolean advance, String label, int width, int height) {
        sources.sort(Comparator.comparing((Source source) -> source.name).thenComparing(source -> source.id));
        int matching = -1;
        for (int index = 0; last != null && index < sources.size(); index++) {
            if (last.source.equals(sources.get(index).id)) {
                matching = index;
                break;
            }
        }
        int index = matching < 0 ? 0 : matching + (advance ? 1 : 0);
        for (int seen = 0; seen < sources.size() && !Thread.currentThread().isInterrupted(); seen++) {
            Source source = sources.get((index + seen) % sources.size());
            try {
                if (last != null && last.source.equals(source.id) && last.label.equals(label)) {
                    // A one-image folder stays still but a removed image must trigger fallback.
                    try (InputStream input = source.open.get()) {
                        if (input != null && input.read() >= 0) return last;
                    }
                } else {
                    WallpaperImage image = WallpaperImage.decode(source.decoder.get(), width, height);
                    return new Frame(source.id, image.bitmap, image.glass, image.accent, label);
                }
            } catch (IOException | RuntimeException ignored) {
                // One bad file does not invalidate the rest of the slideshow.
            }
        }
        return null;
    }

    private static final class Frame {
        final String source;
        final Bitmap bitmap, glass;
        final int accent;
        final String label;

        Frame(String source, Bitmap bitmap, Bitmap glass, int accent, String label) {
            this.source = source;
            this.bitmap = bitmap;
            this.glass = glass;
            this.accent = accent;
            this.label = label;
        }
    }
}
