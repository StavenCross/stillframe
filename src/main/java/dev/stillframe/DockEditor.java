package dev.stillframe;

import android.animation.ValueAnimator;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DockEditor {
    static final String ADD = ":add";
    private static final String WEATHER = ":weather";
    private static final String FOLDER = ":folder";
    private static final String ICONS = ":icons";
    private static final String POSTERS = ":posters";
    private static final String HOME = ":home";
    private static final String DONE = ":done";
    private final DrawerViews views;
    private final Drawers.Actions actions;
    private final ArrayList<Drawers.App> pins;
    private final ArrayList<DrawerViews.IconTile> tiles = new ArrayList<>();
    private final LinearLayout row;
    private final HorizontalScrollView scroller;
    private final TextView hint;
    private final View weather;
    private final View folder;
    private final View icons;
    private final View posters;
    private final View home;
    private final View done;
    private final int cellWidth;
    private DrawerViews.IconTile lastTile;
    private DrawerViews.IconTile picked;
    private PopupWindow menu;
    private String focused = "";

    DockEditor(DrawerViews views, LinearLayout panel, Drawers.Actions actions, Runnable close,
               List<Drawers.App> apps, String weatherLocation, String folderLabel,
               String iconPackLabel, String target, String priorPin) {
        this.views = views;
        this.actions = actions;
        pins = new ArrayList<>(apps);
        row = views.row();
        scroller = views.horizontalScroll();
        cellWidth = DrawerViews.pinCellWidth(
                views.activity().getResources().getConfiguration().screenWidthDp - 92);
        scroller.addView(row, -2, -1);
        panel.addView(views.text("Pinned apps", 16, GlassStyle.QUIET, true), -1, views.dp(26));
        panel.addView(scroller, -1, views.dp(82));
        hint = views.text("", 14, GlassStyle.QUIET, false);
        panel.addView(hint, -1, views.dp(27));
        weather = setting(panel, WEATHER, R.drawable.sf_light_mode, "Weather location",
                value(weatherLocation, "Not set"), actions::weather);
        folder = setting(panel, FOLDER, R.drawable.sf_folder, "Slideshow folder",
                value(folderLabel, "Bundled wallpapers"), actions::folder);
        icons = setting(panel, ICONS, R.drawable.sf_apps, "App icons",
                value(iconPackLabel, "Default"), actions::icons);
        posters = setting(panel, POSTERS, R.drawable.sf_poster, "Portrait posters",
                TmdbSettings.enabled(views.activity()) ? "On" : "Off", actions::portraitPosters);
        home = views.button("Set as Home", false, actions::home);
        done = views.button("Done", true, close);
        LinearLayout footer = views.row();
        footer.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        panel.addView(footer, -1, views.dp(53));
        footer.addView(home, views.dp(136), views.dp(40));
        LinearLayout.LayoutParams doneLayout = new LinearLayout.LayoutParams(views.dp(110), views.dp(40));
        doneLayout.leftMargin = views.dp(8);
        footer.addView(done, doneLayout);
        watchFocus(home, HOME);
        watchFocus(done, DONE);
        views.arrows(weather, dir -> focusTarget(dir, lastTile, weather, folder, weather));
        views.arrows(folder, dir -> focusTarget(dir, weather, folder, icons, folder));
        views.arrows(icons, dir -> focusTarget(dir, folder, icons, posters, icons));
        views.arrows(posters, dir -> focusTarget(dir, icons, posters, done, posters));
        views.arrows(home, dir -> focusTarget(dir, posters, done, home, home));
        views.arrows(done, dir -> focusTarget(dir, posters, done, done, home));

        render(0, isSetting(target) ? priorPin : target);
        if (isSetting(target)) control(target).requestFocus();
    }

    String focusKey() {
        return focused;
    }

    String pinKey() {
        return lastTile == null ? "" : (String) lastTile.getTag();
    }

    boolean back() {
        if (menu != null) dismissMenu();
        else if (picked != null) pick(null);
        else return false;
        return true;
    }

    void dismissMenu() {
        if (menu != null) menu.dismiss();
    }

    private View setting(LinearLayout panel, String key, int vector, String label,
                         String value, Runnable click) {
        View view = views.setting(vector, label, value, click);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, views.dp(42));
        layout.bottomMargin = views.dp(5);
        panel.addView(view, layout);
        watchFocus(view, key);
        return view;
    }

    private void watchFocus(View view, String key) {
        view.setOnFocusChangeListener((v, active) -> {
            views.focusScale(v, active);
            if (active) focused = key;
        });
    }

    private void render(int fallbackIndex, String target) {
        row.removeAllViews();
        tiles.clear();
        for (Drawers.App app : pins) {
            DrawerViews.IconTile tile = views.tile(app, true);
            tile.setOnClickListener(v -> pick(picked == tile ? null : tile));
            tile.setOnLongClickListener(v -> {
                pick(null);
                removeMenu(app, tile);
                return true;
            });
            addTile(tile, app.pkg);
        }
        DrawerViews.IconTile add = views.addTile();
        add.setOnClickListener(v -> actions.addPicker());
        addTile(add, ADD);
        lastTile = tiles.get(Math.min(fallbackIndex, tiles.size() - 1));
        for (DrawerViews.IconTile tile : tiles) if (target.equals(tile.getTag())) lastTile = tile;
        lastTile.requestFocus();
        pick(null);
    }

    private void addTile(DrawerViews.IconTile tile, String key) {
        tiles.add(tile);
        tile.setTag(key);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(views.dp(cellWidth), -1);
        layout.rightMargin = views.dp(DrawerViews.PIN_GAP);
        row.addView(tile, layout);
        tile.setOnFocusChangeListener((v, active) -> {
            views.focusScale(v, active);
            if (active) {
                focused = key;
                lastTile = tile;
            }
        });
        views.arrows(tile, direction -> {
            int index = tiles.indexOf(tile);
            if (picked == tile) {
                if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
                    moveTile(index, Drawers.gridTarget(index, pins.size(), pins.size(), direction));
                }
                return tile;
            }
            if (direction == View.FOCUS_DOWN) return weather;
            int target = Drawers.gridTarget(index, tiles.size(), tiles.size(), direction);
            return target < 0 ? tile : tiles.get(target);
        });
    }

    private void pick(DrawerViews.IconTile tile) {
        boolean changedMode = (picked == null) != (tile == null);
        if (picked != null) picked.moveState(false, false, false);
        picked = tile;
        if (picked != null) updateMoveState();
        if (pins.isEmpty()) hint.setText("Add apps to your dock");
        else hint.setText(picked == null ? "Select to move   ·   Hold Select to remove"
                : "Left or Right to move   ·   Select to place");
        if (changedMode && ValueAnimator.areAnimatorsEnabled()) {
            hint.animate().cancel();
            hint.setAlpha(.68f);
            hint.animate().alpha(1f).setDuration(145).start();
        }
    }

    private void updateMoveState() {
        int index = tiles.indexOf(picked);
        picked.moveState(true, index > 0, index < pins.size() - 1);
    }

    private void moveTile(int from, int to) {
        if (from == to) return;
        actions.move(from, to);
        Collections.swap(pins, from, to);
        Collections.swap(tiles, from, to);
        // Moving the neighboring view keeps the active tile attached to the focus chain.
        View neighbor = row.getChildAt(to);
        row.removeViewAt(to);
        row.addView(neighbor, from);
        views.revealPin(scroller, to, cellWidth);
        updateMoveState();
        if (ValueAnimator.areAnimatorsEnabled()) {
            neighbor.animate().cancel();
            neighbor.setTranslationX((to - from) * (views.dp(cellWidth) + views.dp(DrawerViews.PIN_GAP)));
            neighbor.animate().translationX(0).setDuration(125).start();
        }
    }

    private void removeMenu(Drawers.App app, View anchor) {
        menu = DockPinMenu.show(views, anchor, app.label, () -> {
            int index = pins.indexOf(app);
            if (index < 0) return;
            actions.remove(app.pkg);
            pins.remove(index);
            dismissMenu();
            render(index, "");
        }, () -> menu = null);
    }

    private boolean isSetting(String key) {
        return control(key) != null;
    }

    private View control(String key) {
        switch (key) {
            case WEATHER: return weather;
            case FOLDER: return folder;
            case ICONS: return icons;
            case POSTERS: return posters;
            case HOME: return home;
            case DONE: return done;
            default: return null;
        }
    }

    private static View focusTarget(int direction, View up, View right, View down, View left) {
        switch (direction) {
            case View.FOCUS_UP: return up;
            case View.FOCUS_RIGHT: return right;
            case View.FOCUS_DOWN: return down;
            default: return left;
        }
    }

    private static String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
