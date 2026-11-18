package dev.stillframe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static dev.stillframe.HomeStyle.place;

final class HomeView extends FrameLayout {
    interface Actions {
        void highlight(String pkg);
        void dockSelect(String pkg);
        void play(WatchFeed.Entry entry);
        void empty();
        void settings();
    }

    private static final int VISIBLE_CARDS = 3;
    private final Actions actions;
    private final WatchFeed feed;
    private final Backdrop backdrop;
    private final FeaturePanel details;
    private final HomeHeader header;
    private final TextView heading, empty;
    private final FrameLayout posters;
    private final HorizontalScrollView dockScroll;
    private final LinearLayout dock;
    private final List<PosterTile> slots = new ArrayList<>();
    private final List<DockTile> dockTiles = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private List<WatchFeed.Entry> entries = Collections.emptyList();
    private String provider = "", providerLabel = "Continue Watching", returnKey = "";
    private int selected, firstVisible;
    private DockTile.Palette dockTint = DockTile.Palette.DEFAULT;
    private WatchFeed.Status feedStatus = WatchFeed.Status.LOADING;
    private boolean providerPending, portraitLayout;
    private final Runnable presentProvider = this::presentSelectedProvider;
    HomeView(Context context, WatchFeed feed, Actions actions) {
        super(context);
        this.feed = feed;
        this.actions = actions;
        setClipChildren(false);
        backdrop = new Backdrop(context, this::applyTint);
        addView(backdrop, new LayoutParams(-1, -1));
        header = new HomeHeader(context, actions::settings);
        addView(header);
        heading = HomeStyle.label(context, "Continue Watching");
        addView(heading);
        details = new FeaturePanel(context, () -> { if (!providerPending) actions.empty(); });
        empty = details.emptyAction();
        empty.setVisibility(INVISIBLE);
        addView(details);
        posters = new FrameLayout(context);
        posters.setClipChildren(false);
        addView(posters);
        for (int i = 0; i < VISIBLE_CARDS; i++) {
            PosterTile slot = new PosterTile(context, entry -> {
                if (!providerPending && provider.equals(entry.pkg)) actions.play(entry);
            }, this::updateArtwork);
            slot.setVisibility(INVISIBLE);
            slots.add(slot);
            posters.addView(slot);
        }
        dockScroll = new HorizontalScrollView(context);
        dockScroll.setHorizontalScrollBarEnabled(false);
        dockScroll.setClipChildren(false);
        dockScroll.setClipToPadding(false);
        dockScroll.setSmoothScrollingEnabled(false);
        dock = new LinearLayout(context);
        dock.setGravity(Gravity.CENTER);
        dock.setClipChildren(false);
        dockScroll.addView(dock, new HorizontalScrollView.LayoutParams(-2, -1));
        addView(dockScroll);
    }

    void wallpaper(Bitmap bitmap, int color) { backdrop.show(bitmap, color); }
    void clock(String value) { header.clock(value); }
    void weather(Weather.Reading value) { header.weather(value); }

    void pins(List<Drawers.App> apps) {
        String previous = focusedKey();
        dock.removeAllViews();
        dockTiles.clear();
        keys.clear();
        for (Drawers.App app : apps) {
            keys.add(app.pkg);
            DockTile tile = new DockTile(getContext(), app);
            tile.tint(dockTint);
            tile.setOnClickListener(view -> actions.dockSelect(app.pkg));
            tile.setOnFocusChangeListener((view, focused) -> {
                if (focused) {
                    returnKey = app.pkg;
                    actions.highlight(app.pkg);
                }
            });
            dock.addView(tile);
            dockTiles.add(tile);
        }
        sizeTiles(getWidth(), getHeight());
        if (!previous.isEmpty()) restoreDock(previous);
    }

    void feed(String pkg, String label, List<WatchFeed.Entry> rows, WatchFeed.Status status) {
        boolean changed = !provider.equals(pkg);
        String oldId = !changed && !entries.isEmpty() ? entries.get(selected).id : "";
        provider = pkg;
        providerLabel = label;
        entries = rows;
        feedStatus = status;
        if (changed) firstVisible = 0;
        selected = 0;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).id.equals(oldId)) { selected = i; break; }
        if (selected < firstVisible || selected > firstVisible + VISIBLE_CARDS - 1) firstVisible = Math.max(0, selected - VISIBLE_CARDS + 1);
        firstVisible = Math.min(firstVisible, Math.max(0, rows.size() - VISIBLE_CARDS));
        if (changed && isLaidOut()) {
            // Draw dock focus first. A burst of keys needs only the final provider's layout.
            providerPending = true;
            removeCallbacks(presentProvider);
            postDelayed(presentProvider, 25);
        } else if (!providerPending) render();
    }
    private void presentSelectedProvider() {
        removeCallbacks(presentProvider);
        providerPending = false;
        render();
    }

    private void render() {
        ensurePortraitLayout();
        boolean posterFocused = slots.contains(findFocus()) || findFocus() == empty;
        heading.setVisibility(entries.isEmpty() ? INVISIBLE : VISIBLE);
        String label = !entries.isEmpty() && HidivePicks.isPick(entries.get(0))
                ? "Popular on HIDIVE" : "Continue Watching";
        if (!label.contentEquals(heading.getText())) heading.setText(label);
        showDetails();
        if (entries.isEmpty()) empty.setVisibility(VISIBLE);
        if (entries.isEmpty() && posterFocused) empty.requestFocus();
        warmVisible();
        for (int i = 0; i < slots.size(); i++) {
            int index = firstVisible + i;
            PosterTile slot = slots.get(i);
            if (index < entries.size()) {
                WatchFeed.Entry entry = entries.get(index);
                slot.bind(entry, selected == index, () -> selectPoster(index));
                applyArtwork(slot);
            } else slot.unbind();
            slot.setVisibility(index < entries.size() ? VISIBLE : INVISIBLE);
        }
        if (!entries.isEmpty()) {
            if (posterFocused) slots.get(selected - firstVisible).requestFocus();
            empty.setVisibility(GONE);
        }
    }

    private void selectPoster(int index) {
        if (index >= entries.size()) return;
        selected = index;
        for (int i = 0; i < slots.size(); i++) slots.get(i).outline(firstVisible + i == index);
        showDetails();
    }
    private void showDetails() {
        details.show(entries.isEmpty() ? null : entries.get(selected), providerLabel, provider,
                feedStatus, slots.contains(findFocus()));
    }

    private void warmVisible() {
        feed.warm(entries.subList(firstVisible, Math.min(firstVisible + VISIBLE_CARDS + 1, entries.size())));
    }

    void updateArtwork() {
        ensurePortraitLayout();
        if (providerPending) return;
        if (portraitLayout) warmVisible();
        for (PosterTile slot : slots) if (slot.entry() != null) applyArtwork(slot);
    }
    private void ensurePortraitLayout() {
        if (portraitLayout != feed.portraitsEnabled() && getWidth() > 0 && getHeight() > 0)
            layoutPosters(getWidth(), getHeight());
    }
    private void applyArtwork(PosterTile slot) {
        Bitmap portrait = feed.portrait(slot.entry());
        slot.updateArt(portrait, portrait == null ? feed.artwork(slot.entry()) : null,
                portrait == null && feed.portraitPending(slot.entry()), portraitLayout);
    }

    private void applyTint(int color) {
        if (color != dockTint.accent) dockTint = new DockTile.Palette(color);
        int tint = 0xff000000 | (Math.min(105, Color.red(color) + 23) << 16)
                | (Math.min(105, Color.green(color) + 23) << 8) | Math.min(105, Color.blue(color) + 26);
        for (DockTile tile : dockTiles) tile.tint(dockTint);
        for (PosterTile slot : slots) slot.setArtTint(tint);
    }

    String focusedKey() {
        int index = dockTiles.indexOf(findFocus());
        return index >= 0 ? keys.get(index) : "";
    }

    boolean appsFocused() { return AppCatalog.APPS.equals(focusedKey()); }
    void openApps() { actions.dockSelect(AppCatalog.APPS); }

    void restoreDock(String pkg) {
        int index = keys.indexOf(pkg);
        if (index < 0) index = keys.indexOf(provider);
        if (index < 0) index = 0;
        if (index < dockTiles.size()) dockTiles.get(index).requestFocus();
    }

    boolean navigate(int key) {
        View focus = findFocus();
        int index = dockTiles.indexOf(focus);
        boolean left = key == KeyEvent.KEYCODE_DPAD_LEFT, right = key == KeyEvent.KEYCODE_DPAD_RIGHT;
        boolean up = key == KeyEvent.KEYCODE_DPAD_UP, down = key == KeyEvent.KEYCODE_DPAD_DOWN;
        if (!(left || right || up || down)) return false;
        if (index >= 0) {
            if (left || right) dockTiles.get(LauncherPolicy.step(index, left ? -1 : 1, keys.size())).requestFocus();
            else if (up) {
                if (providerPending) presentSelectedProvider();
                if (entries.isEmpty()) empty.requestFocus();
                else slots.get(selected - firstVisible).requestFocus();
            }
            return true;
        }
        if (slots.contains(focus) || focus == empty) {
            if (down) restoreDock(provider);
            else if (up) header.settings.requestFocus();
            else if ((left || right) && !entries.isEmpty()) {
                selected = LauncherPolicy.step(selected, left ? -1 : 1, entries.size());
                if (selected < firstVisible) firstVisible = selected;
                if (selected > firstVisible + VISIBLE_CARDS - 1) firstVisible = selected - VISIBLE_CARDS + 1;
                render();
            }
            return true;
        }
        if (header.contains(focus)) {
            if (down) {
                if (providerPending) presentSelectedProvider();
                if (entries.isEmpty()) empty.requestFocus();
                else slots.get(selected - firstVisible).requestFocus();
            } else if (right) header.settings.requestFocus();
            return true;
        }
        restoreDock(returnKey);
        return true;
    }

    boolean back() {
        View focused = findFocus();
        if (slots.contains(focused) || focused == empty || header.contains(focused)) {
            restoreDock(header.contains(focused) ? returnKey : provider);
            return true;
        }
        return false;
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        place(header, 0, 0, 1, .12, w, h, 0);
        header.size(w, h);
        place(heading, .357, .146, .60, .043, w, h, .016);
        details.layoutAt(w, h);
        layoutPosters(w, h);
        updateArtwork();
        place(dockScroll, .045, .824, .91, .135, w, h, 0);
        sizeTiles(w, h);
    }
    private void layoutPosters(int w, int h) {
        portraitLayout = feed.portraitsEnabled();
        int height = PosterTile.layoutRow(posters, slots, w, h, portraitLayout);
        feed.artworkSize(PosterTile.cardWidth(w, h, portraitLayout), height);
        if (!providerPending) warmVisible();
    }
    private void sizeTiles(int w, int h) {
        if (w <= 0 || h <= 0) return;
        int tile = (int) Math.min(w * .071, h * .111);
        int gap = (int) Math.min(w * .014, (w * .90 - 11 * tile) / 11);
        dock.setMinimumWidth((int) (w * .91));
        for (DockTile view : dockTiles) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(tile, tile);
            p.setMargins(gap / 2, 0, gap / 2, 0);
            view.setLayoutParams(p);
        }
    }
}
