package dev.stillframe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public final class MainActivity extends Activity
        implements HomeView.Actions, Drawers.Actions {
    private static final int TV_PERMISSION = 11;
    private final FocusTrace timing = new FocusTrace();
    private final DrawerPress drawerPress = new DrawerPress();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<String> pins = new ArrayList<>();
    private final Runnable tick = this::clock;
    private final Runnable saveProvider = this::persistProvider;
    private SharedPreferences preferences;
    private HomeView home;
    private Drawers drawers;
    private WatchFeed watch;
    private Ambience ambience;
    private Weather weather;
    private Setup setup;
    private CatalogLoader loader;
    private AppCatalog catalog, pendingCatalog;
    private List<WatchFeed.Entry> displayed;
    private String provider = "", dockFocus = "";
    private WatchFeed.Status displayedStatus;
    private boolean picking;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        timing.configure(getApplicationInfo(), getIntent());
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        preferences = getSharedPreferences("launcher", MODE_PRIVATE);
        provider = preferences.getString("provider", "");
        FrameLayout root = new FrameLayout(this);
        watch = new WatchFeed(this, this::watchChanged);
        home = new HomeView(this, watch, this);
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout overlays = new FrameLayout(this);
        root.addView(overlays, new FrameLayout.LayoutParams(-1, -1));
        drawers = new Drawers(this, overlays, this);
        loader = new CatalogLoader(this);
        ambience = new Ambience(this, (bitmap, blurred, accent) -> {
            home.wallpaper(bitmap, accent);
            drawers.theme(blurred, accent);
            setup.tint(accent);
        });
        weather = new Weather(this, home::weather);
        setup = new Setup(this, ambience, weather, this::showEdit);
        setContentView(root);
    }

    @Override protected void onStart() {
        super.onStart();
        loader.load(preferences.getString("icon_pack", ""), loaded -> catalogReady(loaded, false));
        watch.start();
        ambience.start();
        weather.start();
        clock();
    }

    @Override protected void onStop() {
        loader.invalidate();
        watch.stop();
        ambience.stop();
        weather.stop();
        main.removeCallbacks(tick);
        main.removeCallbacks(saveProvider);
        persistProvider();
        super.onStop();
    }
    private void clock() {
        home.clock(DateFormat.getTimeFormat(this).format(new Date()));
        main.postDelayed(tick, 60000 - System.currentTimeMillis() % 60000);
    }
    private void catalogReady(AppCatalog loaded, boolean chosen) {
        boolean first = catalog == null;
        boolean appSetChanged = first || !catalog.apps.keySet().equals(loaded.apps.keySet());
        boolean iconsChanged = first || !catalog.selectedPack.equals(loaded.selectedPack);
        if (!chosen && appSetChanged && drawers.active()) {
            pendingCatalog = loaded;
            return;
        }
        pendingCatalog = null;
        catalog = loaded;
        if (!appSetChanged && !iconsChanged) return;
        drawers.prepareApps(catalog.sorted());
        pins.clear();
        pins.addAll(PinPreferences.read(preferences, catalog.apps.keySet()));
        refreshProvider();
        renderDock();
        if (chosen && drawers.active() && !picking) showEdit();
        if (first && !drawers.active()) home.post(() -> { if (!drawers.active()) home.restoreDock(provider); });
    }

    private List<Drawers.App> pinnedApps() {
        List<Drawers.App> list = new ArrayList<>();
        for (String pkg : pins) {
            Drawers.App app = catalog.apps.get(pkg);
            if (app != null) list.add(app);
        }
        return list;
    }

    private void renderDock() {
        List<Drawers.App> apps = pinnedApps();
        apps.add(new Drawers.App(AppCatalog.APPS, "All Apps", null, 0xff456582));
        apps.add(new Drawers.App(AppCatalog.EDIT, "Edit Dock", null, 0xff456582));
        home.pins(apps);
    }

    private void persistPins() {
        PinPreferences.save(preferences, pins);
        refreshProvider();
        renderDock();
    }

    private void refreshProvider() {
        List<String> candidates = new ArrayList<>(pins);
        candidates.remove(AppCatalog.STORE);
        provider = LauncherPolicy.provider(provider, candidates);
        displayed = null;
        watchChanged();
    }

    private void persistProvider() {
        preferences.edit().putString("provider", provider).apply();
    }

    @Override public void highlight(String pkg) {
        if (pkg.startsWith(":") || AppCatalog.STORE.equals(pkg) || provider.equals(pkg)) return;
        provider = pkg;
        displayed = null;
        watchChanged();
        // Held D-pad navigation may change providers several times per second.
        main.removeCallbacks(saveProvider);
        main.postDelayed(saveProvider, 350);
    }

    private void watchChanged() {
        List<WatchFeed.Entry> rows = watch.forApp(provider);
        WatchFeed.Status status = watch.status();
        if (rows == displayed && status == displayedStatus) {
            home.updateArtwork();
        } else {
            displayed = rows;
            displayedStatus = status;
            Drawers.App app = catalog == null ? null : catalog.apps.get(provider);
            home.feed(provider, app == null ? "Continue Watching" : app.label, rows, status);
        }
    }

    @Override public void dockSelect(String pkg) {
        if (catalog == null) return;
        dockFocus = pkg;
        if (AppCatalog.APPS.equals(pkg)) {
            openOverlay();
            drawers.showApps(catalog.sorted(), false);
        } else if (AppCatalog.EDIT.equals(pkg)) {
            openOverlay();
            showEdit();
        } else {
            launch(pkg);
        }
    }

    @Override public void play(WatchFeed.Entry entry) {
        if (entry.open(this)) return;
        if (HidivePicks.isPick(entry)) launch(entry.pkg);
        else {
            AlertDialog dialog = StillframeDialogs.builder(this).setTitle("Continue watching")
                    .setMessage("Open the app to continue?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Open app", (d, button) -> launch(entry.pkg)).show();
            StillframeDialogs.style(dialog, setup.tint());
        }
    }
    @Override public void empty() {
        if (checkSelfPermission(WatchFeed.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            setup.tvListings(TV_PERMISSION);
        } else if (!provider.isEmpty()) launch(provider);
        else dockSelect(AppCatalog.APPS);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == TV_PERMISSION) watch.refresh();
        else setup.permissions(request, grants);
    }

    @Override public void settings() {
        startActivity(new Intent(Settings.ACTION_SETTINGS));
    }

    @Override public void weather() { setup.location(); }
    @Override public void icons() {
        setup.icons(catalog, selected -> {
            preferences.edit().putString("icon_pack", selected).apply();
            loader.load(selected, loaded -> catalogReady(loaded, true));
        });
    }
    @Override public void portraitPosters() {
        new PortraitPosterDialogs(this, setup.tint(), this::onPortraitSettingsChanged).show();
    }
    @Override public void folder() { setup.folder(); }
    @Override public void home() { setup.requestHome(); }
    private void onPortraitSettingsChanged() {
        watch.portraitSettingsChanged();
        showEdit();
    }
    @Override public void launch(String pkg) {
        if (catalog != null) catalog.launch(this, pkg);
    }
    private void openOverlay() {
        home.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }
    private void showEdit() {
        if (drawers.active() || AppCatalog.EDIT.equals(dockFocus))
            drawers.showEdit(pinnedApps(), weather.location(), ambience.folderLabel(), catalog.packLabel);
    }
    @Override public void addPicker() {
        picking = true;
        List<Drawers.App> unpinned = catalog.sorted();
        unpinned.removeIf(app -> pins.contains(app.pkg));
        drawers.showApps(unpinned, true);
    }

    @Override public void add(String pkg) {
        if (catalog.apps.containsKey(pkg) && !pins.contains(pkg)) {
            int storeIndex = pins.indexOf(AppCatalog.STORE);
            pins.add(storeIndex < 0 ? pins.size() : storeIndex, pkg);
            persistPins();
        }
        picking = false;
        showEdit();
    }

    @Override public void remove(String pkg) {
        if (pins.remove(pkg)) persistPins();
    }

    @Override public void move(int from, int to) {
        LauncherPolicy.move(pins, from, to);
        persistPins();
    }

    @Override public void closed() {
        if (picking) {
            picking = false;
            showEdit();
        } else {
            home.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            if (pendingCatalog != null) catalogReady(pendingCatalog, false);
            home.restoreDock(dockFocus);
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        setup.result(request, result, data);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (drawerPress.dispatch(event, drawers, home, timing)) return true;
        if (event.getAction() == KeyEvent.ACTION_DOWN && !drawers.active()) {
            long started = timing.begin();
            if (home.navigate(event.getKeyCode())) {
                timing.submitted(home, started);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        timing.configure(getApplicationInfo(), intent);
    }
    @Override public void onBackPressed() {
        if (drawers.back() || home.back()) return;
        super.onBackPressed();
    }
}
