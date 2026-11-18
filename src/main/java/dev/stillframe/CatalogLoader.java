package dev.stillframe;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class CatalogLoader {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Context context;
    private int request;

    CatalogLoader(Context context) { this.context = context.getApplicationContext(); }

    void load(String pack, Consumer<AppCatalog> ready) {
        int target = ++request;
        WORKER.execute(() -> {
            AppCatalog catalog = AppCatalog.read(context, pack);
            ui.post(() -> {
                if (target == request) ready.accept(catalog);
            });
        });
    }

    void invalidate() { request++; }
}
