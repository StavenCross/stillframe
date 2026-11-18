package dev.stillframe;

import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.net.ssl.HttpsURLConnection;

/** App-private disk cache and image download. All file and image work runs off the UI thread. */
final class PortraitPosterStore {
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;
    private static final long MAX_CACHE_BYTES = 24L * 1024 * 1024;
    private static final int MAX_CACHE_ENTRIES = 128;
    private static final long MAX_AGE = TimeUnit.DAYS.toMillis(14);
    static final long MISS_AGE = TimeUnit.HOURS.toMillis(12);
    private static final Object DISK_WRITES = new Object();
    private final File directory;

    PortraitPosterStore(File cache) {
        directory = new File(cache, "portrait-posters-v1");
    }

    Bitmap cached(String key, int width, int height) {
        File file = file(key, ".poster");
        try {
            if (file.length() <= 0 || file.length() > MAX_IMAGE_BYTES
                    || System.currentTimeMillis() - file.lastModified() > MAX_AGE) return null;
            return decode(Files.readAllBytes(file.toPath()), width, height);
        } catch (IOException | RuntimeException miss) {
            return null;
        }
    }

    long knownMiss(String key) {
        try {
            long written = file(key, ".miss").lastModified();
            long age = System.currentTimeMillis() - written;
            return written > 0 && age >= 0 && age < MISS_AGE ? MISS_AGE - age : 0;
        } catch (RuntimeException unavailable) {
            return 0;
        }
    }

    void rememberMiss(String key, BooleanSupplier active) {
        synchronized (DISK_WRITES) {
            if (!active.getAsBoolean()) return;
            try {
                if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) return;
                File marker = file(key, ".miss");
                if (!marker.createNewFile()) marker.setLastModified(System.currentTimeMillis());
                trim();
            } catch (IOException | RuntimeException unavailable) {
                // The short in-memory miss still prevents request loops.
            }
        }
    }

    Bitmap download(String uri, String key, int width, int height, BooleanSupplier active) throws IOException {
        URL url = new URL(uri);
        if (!"https".equals(url.getProtocol()) || !"image.tmdb.org".equals(url.getHost())
                || url.getUserInfo() != null || url.getPort() != -1 || !url.getPath().startsWith("/t/p/")) {
            throw new IOException("Unexpected portrait image location");
        }
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        try {
            if (!active.getAsBoolean()) return null;
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(2500);
            connection.setReadTimeout(2500);
            int response = connection.getResponseCode();
            if (response == 429) throw new TmdbTransport.RateLimited(connection.getHeaderField("Retry-After"));
            if (response == 404 || response == 410) return null;
            if (response != 200 || connection.getContentLength() > MAX_IMAGE_BYTES) {
                throw new IOException("Portrait image is unavailable");
            }
            byte[] bytes;
            try (InputStream input = connection.getInputStream()) {
                bytes = readImage(input, active);
            }
            if (!active.getAsBoolean()) return null;
            Bitmap poster = decode(bytes, width, height);
            if (poster != null) {
                synchronized (DISK_WRITES) {
                    if (active.getAsBoolean()) save(key, bytes);
                }
            }
            return poster;
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readImage(InputStream input, BooleanSupplier active) throws IOException {
        long deadline = SystemClock.uptimeMillis() + 4000;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int count; (count = input.read(buffer)) != -1; ) {
            if (!active.getAsBoolean() || bytes.size() + count > MAX_IMAGE_BYTES
                    || SystemClock.uptimeMillis() > deadline) {
                throw new IOException("Portrait image exceeds download limits");
            }
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }

    void clear() {
        synchronized (DISK_WRITES) {
            File[] files = directory.listFiles((dir, name) -> cacheEntry(name) || name.endsWith(".tmp"));
            if (files != null) for (File file : files) file.delete();
        }
    }

    void prune() {
        synchronized (DISK_WRITES) {
            File[] interrupted = directory.listFiles((dir, name) -> name.endsWith(".tmp"));
            if (interrupted != null) for (File file : interrupted) file.delete();
            trim();
        }
    }

    private static Bitmap decode(byte[] bytes, int width, int height) throws IOException {
        try {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes)),
                    (decoder, info, source) -> {
                        int sourceWidth = info.getSize().getWidth(), sourceHeight = info.getSize().getHeight();
                        if (!TmdbMatching.portrait(sourceWidth, sourceHeight)) {
                            throw new IllegalArgumentException("Expected a 2:3 portrait");
                        }
                        int[] size = ArtworkCrop.decodeSize(sourceWidth, sourceHeight, width, height);
                        decoder.setTargetSize(size[0], size[1]);
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    });
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private void save(String key, byte[] bytes) {
        // Use a unique temporary file so two launcher tasks can share the cache.
        File temporary = null;
        try {
            if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) return;
            temporary = File.createTempFile("image", ".tmp", directory);
            Files.write(temporary.toPath(), bytes);
            if (temporary.renameTo(file(key, ".poster"))) {
                file(key, ".miss").delete();
                trim();
            }
        } catch (IOException | RuntimeException unavailable) {
            // The decoded bitmap remains usable without disk space.
        } finally {
            if (temporary != null && temporary.exists()) temporary.delete();
        }
    }

    private void trim() {
        File[] files = directory.listFiles((dir, name) -> cacheEntry(name));
        if (files == null) return;
        long size = 0;
        for (File file : files) size += file.length();
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        long now = System.currentTimeMillis();
        int count = files.length;
        for (File file : files) {
            long maxAge = file.getName().endsWith(".miss") ? MISS_AGE : MAX_AGE;
            if (count <= MAX_CACHE_ENTRIES && size <= MAX_CACHE_BYTES && now - file.lastModified() <= maxAge)
                continue;
            long length = file.length();
            if (file.delete()) {
                size -= length;
                count--;
            }
        }
    }

    private static boolean cacheEntry(String name) { return name.endsWith(".poster") || name.endsWith(".miss"); }

    private File file(String key, String extension) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder(64);
            for (byte value : bytes) {
                name.append(Character.forDigit(value >>> 4 & 15, 16));
                name.append(Character.forDigit(value & 15, 16));
            }
            return new File(directory, name + extension);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
