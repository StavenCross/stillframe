package dev.stillframe;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

final class AppDrawer {
    private static final int ROW_HEIGHT = 102;
    private static final int VIEWPORT_PADDING = 10;
    private final DrawerViews views;
    private final Activity activity;
    private final EditText search;
    private final ScrollView scroll;
    private final LinearLayout content, rows;
    private final ArrayList<View> tiles = new ArrayList<>();
    private final List<Drawers.App> apps;
    private final boolean adding;
    private final Consumer<String> select;
    private final int columns;
    private int previousResult;

    AppDrawer(DrawerViews views, List<Drawers.App> apps, boolean adding, Consumer<String> select) {
        this.views = views;
        activity = views.activity();
        content = views.column();
        search = new EditText(activity);
        scroll = new ScrollView(activity);
        rows = views.column();
        this.apps = new ArrayList<>(apps);
        this.adding = adding;
        this.select = select;
        columns = DrawerViews.gridColumns(activity.getResources().getConfiguration().screenWidthDp);
        int height = activity.getResources().getConfiguration().screenHeightDp;
        configureSearch();
        content.addView(search, -1, views.dp(46));

        scroll.setFocusable(false);
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setScrollbarFadingEnabled(false);
        scroll.setScrollBarSize(views.dp(3));
        if (Build.VERSION.SDK_INT >= 29) {
            scroll.setVerticalScrollbarThumbDrawable(GlassStyle.rounded(activity, 0x80a8bfdb, 2, 0));
        }
        scroll.setPadding(0, views.dp(VIEWPORT_PADDING), 0, views.dp(VIEWPORT_PADDING));
        scroll.addView(rows, -1, -2);
        int totalRows = (apps.size() + columns - 1) / columns;
        // Keep the scrolling grid in its own render clip, below the fixed search field.
        FrameLayout viewport = new FrameLayout(activity) {
            @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
                setClipBounds(new Rect(0, 0, w, h));
            }
        };
        viewport.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        content.addView(viewport, -1, views.dp(viewportHeight(totalRows, height)));
        render("");
    }

    boolean matches(List<Drawers.App> available) {
        if (apps.size() != available.size()) return false;
        for (int i = 0; i < apps.size(); i++) {
            Drawers.App old = apps.get(i), current = available.get(i);
            if (!old.pkg.equals(current.pkg) || !old.label.equals(current.label)) return false;
        }
        return true;
    }

    void premeasure() {
        int width = activity.getWindow().getDecorView().getWidth() - views.dp(92);
        if (width <= 0) return;
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    }

    void attach(LinearLayout panel) {
        reset();
        panel.addView(content, -1, -2);
        if (tiles.isEmpty()) search.requestFocus();
        else tiles.get(0).requestFocus();
    }

    void detach(LinearLayout panel) {
        if (search.hasFocus()) hideKeyboard();
        panel.removeView(content);
    }

    void reset() {
        if (search.length() != 0) search.setText("");
        previousResult = 0;
        scroll.scrollTo(0, 0);
    }

    private void configureSearch() {
        search.setHint("Search apps");
        search.setTextColor(GlassStyle.INK);
        search.setHintTextColor(GlassStyle.QUIET);
        search.setTextSize(16);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        search.setBackground(GlassStyle.focus(activity, 0x63415a76, 0xff344d69, 11));
        search.setPadding(views.dp(14), 0, views.dp(14), 0);
        Drawable lens = activity.getDrawable(R.drawable.sf_search);
        if (lens != null) lens.setBounds(0, 0, views.dp(20), views.dp(20));
        search.setCompoundDrawablesRelative(lens, null, null, null);
        search.setCompoundDrawablePadding(views.dp(10));
        search.setOnKeyListener((v, key, event) -> {
            if (key == KeyEvent.KEYCODE_DPAD_CENTER) {
                if (event.getAction() == KeyEvent.ACTION_UP) showKeyboard();
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && search.getMovementMethod() != null) {
                    search.getMovementMethod().onKeyDown(search, search.getText(), key, event);
                }
                return true;
            }
            if (key == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (key != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) focusResults();
            return true;
        });
        search.setOnEditorActionListener((v, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            focusResults();
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                render(s.toString());
            }
            @Override public void afterTextChanged(Editable editable) {}
        });
    }

    private void render(String query) {
        String filter = query.trim().toLowerCase(Locale.ROOT);
        previousResult = 0;
        rows.removeAllViews();
        tiles.clear();
        LinearLayout currentRow = null;
        for (Drawers.App app : apps) {
            if (!app.label.toLowerCase(Locale.ROOT).contains(filter)) continue;
            int index = tiles.size();
            if (index % columns == 0) {
                currentRow = views.row();
                rows.addView(currentRow, -1, views.dp(ROW_HEIGHT));
            }
            View tile = views.tile(app, false);
            tile.setOnClickListener(v -> select.accept(app.pkg));
            tile.setOnFocusChangeListener((view, focused) -> {
                views.focusScale(view, focused);
                if (focused) previousResult = index;
            });
            views.arrows(tile, direction -> {
                int target = Drawers.gridTarget(index, tiles.size(), columns, direction);
                if (target < 0) return search;
                View next = tiles.get(target);
                if (target != index) scroll.post(() -> revealRow(target, next));
                return next;
            });
            tiles.add(tile);
            views.rowItem(currentRow, tile);
        }
        int rowsCount = (tiles.size() + columns - 1) / columns;
        if (rowsCount == 0) {
            String empty = filter.isEmpty()
                    ? (adding ? "All installed apps are already pinned." : "No launchable apps.")
                    : "No apps match your search.";
            rows.addView(views.text(empty, 17, GlassStyle.QUIET, false), -1, views.dp(74));
        } else {
            for (int column = tiles.size() % columns; column > 0 && column < columns; column++) {
                views.rowItem(currentRow, new View(activity));
            }
        }
        scroll.scrollTo(0, 0);
    }

    static int viewportHeight(int rows, int screenHeight) {
        if (rows == 0) return 94;
        int available = Math.max(ROW_HEIGHT, screenHeight - 155 - 2 * VIEWPORT_PADDING);
        int visibleRows = Math.min(3, Math.min(rows, available / ROW_HEIGHT));
        return visibleRows * ROW_HEIGHT + 2 * VIEWPORT_PADDING;
    }

    static int revealOffset(int top, int bottom, int offset, int visible) {
        if (top < offset) return top;
        if (bottom > offset + visible) return bottom - visible;
        return offset;
    }

    private void revealRow(int index, View tile) {
        if (!tile.hasFocus() || index >= tiles.size() || tiles.get(index) != tile) return;
        View row = rows.getChildAt(index / columns);
        if (row == null) return;
        int visible = scroll.getHeight() - scroll.getPaddingTop() - scroll.getPaddingBottom();
        if (visible <= 0) return;
        int offset = revealOffset(row.getTop(), row.getBottom(), scroll.getScrollY(), visible);
        scroll.scrollTo(0, offset);
    }

    static int resultIndex(int previous, int size) {
        return size == 0 ? -1 : Math.min(previous, size - 1);
    }

    private void focusResults() {
        int index = resultIndex(previousResult, tiles.size());
        if (index < 0) return;
        hideKeyboard();
        tiles.get(index).requestFocus();
    }

    void hideKeyboard() {
        InputMethodManager input = activity.getSystemService(InputMethodManager.class);
        if (input != null && search.getWindowToken() != null) input.hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    private void showKeyboard() {
        InputMethodManager input = activity.getSystemService(InputMethodManager.class);
        if (input != null) input.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
    }
}
