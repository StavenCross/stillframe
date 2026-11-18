package dev.stillframe;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lists the image sources the launcher can access on this TV. */
final class WallpaperFolders {
    static final ExecutorService INDEX_WORKER = Executors.newSingleThreadExecutor();
    private static final Pattern LEGACY_ROOT = Pattern.compile("^/storage/(?:emulated/[0-9]+|[^/]+)/(.+)$");
    private static final Uri EXTERNAL_IMAGES = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

    private final Context context;
    private final ContentResolver resolver;

    WallpaperFolders(Context context) {
        this.context = context;
        resolver = context.getContentResolver();
    }

    File localFolder() {
        File root = context.getExternalFilesDir(null);
        return root == null ? null : new File(root, "Wallpapers");
    }

    List<Source> localSources() {
        List<Source> sources = new ArrayList<>();
        File folder = localFolder();
        if (folder == null) return sources;
        if (!folder.isDirectory() && !folder.mkdirs()) return sources;
        File[] files = folder.listFiles(File::isFile);
        if (files != null) {
            for (File file : files) {
                sources.add(new Source(file.getName(), file.getPath() + "@" + file.lastModified(),
                        () -> new FileInputStream(file), () -> ImageDecoder.createSource(file)));
            }
        }
        return sources;
    }

    List<Source> bundledSources() throws IOException {
        List<Source> sources = new ArrayList<>();
        String[] assets = context.getAssets().list("wallpaper");
        if (assets != null) {
            for (String asset : assets) {
                String path = "wallpaper/" + asset;
                sources.add(new Source(asset, path, () -> context.getAssets().open(path),
                        () -> ImageDecoder.createSource(context.getAssets(), path)));
            }
        }
        return sources;
    }

    String documentLabel(Uri tree, String fallback) {
        Uri folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        try (Cursor row = resolver.query(folder, new String[]{Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (row != null && row.moveToFirst()) {
                String name = row.getString(0);
                if (name != null && !name.isEmpty()) return name;
            }
        } catch (RuntimeException ignored) {
            // Folder metadata is optional. The provider may still be able to list images.
        }
        return fallback;
    }

    List<Source> documentSources(Uri tree) {
        List<Source> sources = new ArrayList<>();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
        String[] columns = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
                Document.COLUMN_LAST_MODIFIED};
        try (Cursor rows = resolver.query(children, columns, null, null, null)) {
            while (rows != null && rows.moveToNext() && !Thread.currentThread().isInterrupted()) {
                String id = rows.getString(0);
                if (id == null || Document.MIME_TYPE_DIR.equals(rows.getString(2))) continue;
                Uri media = DocumentsContract.buildDocumentUriUsingTree(tree, id);
                String name = rows.getString(1);
                sources.add(mediaSource(name == null ? id : name, media, rows.getLong(3)));
            }
        }
        return sources;
    }

    List<String> sharedPaths() {
        Set<String> paths = new TreeSet<>();
        try (Cursor rows = resolver.query(EXTERNAL_IMAGES, new String[]{pathColumn()}, null, null, null)) {
            while (rows != null && rows.moveToNext()) {
                String path = pathFromIndex(rows.getString(0));
                if (path != null) paths.add(path);
            }
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(paths));
    }

    List<Source> sharedSources(String folder) {
        List<Source> sources = new ArrayList<>();
        String path = pathColumn();
        String[] columns = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_MODIFIED, path};
        String selection = null;
        String[] values = null;
        if (Build.VERSION.SDK_INT >= 29) {
            if ("/".equals(folder)) {
                selection = path + " IS NULL OR " + path + " IN ('', '/')";
            } else {
                selection = path + " = ?";
                values = new String[]{folder};
            }
        }
        try (Cursor rows = resolver.query(EXTERNAL_IMAGES, columns, selection, values, null)) {
            while (rows != null && rows.moveToNext() && !Thread.currentThread().isInterrupted()) {
                if (!folder.equals(pathFromIndex(rows.getString(3)))) continue;
                long id = rows.getLong(0);
                String name = rows.getString(1);
                sources.add(mediaSource(name == null ? Long.toString(id) : name,
                        ContentUris.withAppendedId(EXTERNAL_IMAGES, id), rows.getLong(2)));
            }
        }
        return sources;
    }

    @SuppressWarnings("deprecation") // Android 9 indexed images expose their folder only through the legacy path.
    private static String pathColumn() {
        return Build.VERSION.SDK_INT >= 29 ? MediaStore.MediaColumns.RELATIVE_PATH : MediaStore.MediaColumns.DATA;
    }

    private static String pathFromIndex(String value) {
        if (Build.VERSION.SDK_INT < 29) return legacyRelativePath(value);
        return value == null || value.isEmpty() ? "/" : value;
    }

    static String legacyRelativePath(String value) {
        if (value == null) return null;
        Matcher root = LEGACY_ROOT.matcher(value);
        if (!root.matches()) return null;
        String relative = root.group(1);
        int slash = relative.lastIndexOf('/');
        return slash < 0 ? "/" : relative.substring(0, slash + 1);
    }

    private Source mediaSource(String name, Uri media, long modified) {
        return new Source(name, media + "@" + modified, () -> resolver.openInputStream(media),
                () -> ImageDecoder.createSource(resolver, media));
    }

    @FunctionalInterface
    interface Opener<T> {
        T get() throws IOException;
    }

    static final class Source {
        final String name;
        final String id;
        final Opener<InputStream> open;
        final Opener<ImageDecoder.Source> decoder;

        Source(String name, String id, Opener<InputStream> open, Opener<ImageDecoder.Source> decoder) {
            this.name = name;
            this.id = id;
            this.open = open;
            this.decoder = decoder;
        }
    }
}
