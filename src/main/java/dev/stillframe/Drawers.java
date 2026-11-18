package dev.stillframe;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import java.util.List;
import java.util.Collections;

final class Drawers {
    interface Actions {
        void add(String pkg);
        void addPicker();
        void move(int from, int to);
        void remove(String pkg);
        void launch(String pkg);
        void weather();
        void folder();
        default void icons() {}
        default void portraitPosters() {}
        void home();
        void closed();
    }

    static final class App {
        public final String pkg;
        public final String label;
        public final Drawable icon;
        public final int color;

        App(String pkg, String label, Drawable icon, int color) {
            this.pkg = pkg;
            this.label = label;
            this.icon = icon;
            this.color = color;
        }
    }

    private final FrameLayout host;
    private final Actions actions;
    private final DrawerViews views;
    private final DecelerateInterpolator enterEasing = new DecelerateInterpolator();
    private final Runnable warmApps = this::warmApps;
    private LinearLayout panel;
    private AppDrawer appDrawer;
    private AppDrawer preparedApps;
    private List<App> allApps = Collections.emptyList();
    private DockEditor editor;
    private Bitmap blur;
    private int tint = GlassStyle.NEUTRAL;
    private boolean returningFromPicker;

    Drawers(Activity activity, FrameLayout overlayHost, Actions actions) {
        host = overlayHost;
        this.actions = actions;
        views = new DrawerViews(activity);
        host.setVisibility(View.GONE);
    }

    void theme(Bitmap blurredWallpaper, int tint) {
        blur = blurredWallpaper;
        this.tint = tint;
        views.tint(tint);
        if (panel != null) panel.setBackground(GlassStyle.sheet(host, panel, blur, tint));
    }

    boolean active() {
        return panel != null;
    }

    boolean back() {
        if (!active()) return false;
        if (editor == null || !editor.back()) close();
        return true;
    }

    void showApps(List<App> apps, boolean adding) {
        returningFromPicker = adding;
        open(adding ? "Add app" : "All Apps", true);
        if (adding) {
            appDrawer = new AppDrawer(views, apps, true, pkg -> {
                appDrawer.hideKeyboard();
                actions.add(pkg);
            });
        } else {
            if (preparedApps == null || !preparedApps.matches(apps)) preparedApps = createApps(apps);
            appDrawer = preparedApps;
        }
        appDrawer.attach(panel);
    }

    void prepareApps(List<App> apps) {
        allApps = apps;
        preparedApps = null;
        scheduleWarmup();
    }

    private void scheduleWarmup() {
        host.removeCallbacks(warmApps);
        host.postDelayed(warmApps, 260);
    }

    private void warmApps() {
        Looper.myQueue().addIdleHandler(() -> {
            if (!active() && host.isAttachedToWindow()) {
                if (preparedApps == null && !allApps.isEmpty()) preparedApps = createApps(allApps);
                if (preparedApps != null) {
                    preparedApps.reset();
                    preparedApps.premeasure();
                }
            }
            return false;
        });
    }

    private AppDrawer createApps(List<App> apps) {
        return new AppDrawer(views, apps, false, pkg -> {
            close();
            actions.launch(pkg);
        });
    }

    void showEdit(List<App> pins, String weatherLocation, String folderLabel) {
        showEdit(pins, weatherLocation, folderLabel, null);
    }

    void showEdit(List<App> pins, String weatherLocation, String folderLabel, String iconPackLabel) {
        String target = returningFromPicker ? DockEditor.ADD : editor == null ? "" : editor.focusKey();
        String lastPin = editor == null ? "" : editor.pinKey();
        returningFromPicker = false;
        open("Edit Dock", editor == null);
        editor = new DockEditor(views, panel, actions, this::close, pins, weatherLocation,
                folderLabel, iconPackLabel, target, lastPin);
    }

    private void open(String title, boolean animate) {
        clear();
        host.setVisibility(View.VISIBLE);
        host.setBackgroundColor(0x88040a12);
        host.setOnClickListener(v -> back());
        host.setFocusable(false);
        panel = views.column();
        panel.setClickable(true);
        panel.setFocusable(false);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setPadding(views.dp(22), views.dp(16), views.dp(22), views.dp(16));
        panel.setBackground(GlassStyle.sheet(host, panel, blur, tint));
        panel.setElevation(views.dp(16));
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        layout.setMargins(views.dp(24), 0, views.dp(24), views.dp(14));
        host.addView(panel, layout);
        panel.addView(views.text(title, 27, GlassStyle.INK, true), -1, views.dp(44));
        if (animate && ValueAnimator.areAnimatorsEnabled()) {
            panel.setTranslationY(views.dp(7));
            panel.animate().translationY(0).setInterpolator(enterEasing).setDuration(115).start();
        }
    }

    private void clear() {
        if (editor != null) editor.dismissMenu();
        if (appDrawer != null) appDrawer.detach(panel);
        if (panel != null) {
            panel.animate().cancel();
            host.removeView(panel);
        }
        panel = null;
        appDrawer = null;
        editor = null;
    }

    private void close() {
        clear();
        host.setVisibility(View.GONE);
        scheduleWarmup();
        actions.closed();
    }

    static int gridTarget(int index, int size, int columns, int direction) {
        if (size == 0) return -1;
        switch (direction) {
            case View.FOCUS_LEFT: return index % columns == 0 ? index : index - 1;
            case View.FOCUS_RIGHT: return Math.min(index + 1, Math.min(size - 1, index - index % columns + columns - 1));
            case View.FOCUS_UP: return index < columns ? -1 : index - columns;
            case View.FOCUS_DOWN: return index - index % columns + columns >= size
                    ? index : Math.min(index + columns, size - 1);
            default: return index;
        }
    }

    static int direction(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return View.FOCUS_RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            default: return 0;
        }
    }
}
