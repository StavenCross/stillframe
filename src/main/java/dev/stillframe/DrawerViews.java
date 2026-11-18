package dev.stillframe;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.IntFunction;

final class DrawerViews {
    static final int PIN_GAP = 6;
    static final int PIN_SIDE = 54;
    static final int GRID_SIDE = 64;
    private static final int ACCENT = 0xff4f6a8b;
    private final Activity activity;
    private int tint = GlassStyle.NEUTRAL;

    DrawerViews(Activity activity) {
        this.activity = activity;
    }

    Activity activity() { return activity; }
    int dp(int dp) { return GlassStyle.dp(activity, dp); }
    void tint(int tint) { this.tint = tint; }
    int tint() { return tint; }

    static int gridColumns(int screenWidth) {
        return Math.max(2, Math.min(5, (screenWidth - 92) / 148));
    }

    static int pinCellWidth(int contentWidth) {
        return Math.max(60, Math.min(82, contentWidth / 10 - PIN_GAP));
    }

    LinearLayout column() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    LinearLayout row() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setClipChildren(false);
        layout.setClipToPadding(false);
        return layout;
    }

    TextView text(String content, int size, int color, boolean semibold) {
        TextView view = new TextView(activity);
        view.setText(content);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTypeface(Typeface.create(semibold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return view;
    }

    ImageView image(int resource, int padding) {
        ImageView view = new ImageView(activity);
        view.setImageResource(resource);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        return view;
    }

    private void control(View view) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setDefaultFocusHighlightEnabled(false);
        view.setOnFocusChangeListener(this::focusScale);
    }

    void focusScale(View view, boolean focused) {
        if (view instanceof IconTile) {
            ((IconTile) view).focusAppearance(focused);
            return;
        }
        // A full-width setting has its own focus outline and should stay aligned.
        if (view instanceof LinearLayout) return;
        view.animate().cancel();
        if (ValueAnimator.areAnimatorsEnabled()) {
            float scale = focused ? 1.035f : 1f;
            view.animate().scaleX(scale).scaleY(scale).setDuration(110).start();
        } else {
            view.setScaleX(1);
            view.setScaleY(1);
        }
    }

    TextView button(String title, boolean primary, Runnable click) {
        TextView button = text(title, 16, GlassStyle.INK, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setSingleLine();
        button.setBackground(GlassStyle.focus(activity, primary ? 0xff426182 : 0, ACCENT, 10));
        control(button);
        button.setOnClickListener(v -> click.run());
        return button;
    }

    View setting(int vector, String label, String value, Runnable click) {
        LinearLayout control = row();
        control.setPadding(dp(12), 0, dp(12), 0);
        control.addView(image(vector, 1), dp(22), dp(22));
        TextView title = text(label, 16, GlassStyle.INK, true);
        LinearLayout.LayoutParams titleLayout = new LinearLayout.LayoutParams(0, -1, 1);
        titleLayout.leftMargin = dp(13);
        control.addView(title, titleLayout);
        if (value != null && !value.isEmpty()) {
            TextView detail = text(value, 15, GlassStyle.QUIET, false);
            detail.setMaxWidth(dp(340));
            detail.setSingleLine(true);
            detail.setEllipsize(TextUtils.TruncateAt.END);
            control.addView(detail, -2, -1);
        }
        LinearLayout.LayoutParams chevron = new LinearLayout.LayoutParams(dp(20), dp(20));
        chevron.leftMargin = dp(11);
        control.addView(image(R.drawable.sf_chevron, 2), chevron);
        control.setContentDescription(value == null || value.isEmpty() ? label : label + ", " + value);
        control.setBackground(GlassStyle.focus(activity, 0x43354b65, ACCENT, 10));
        control(control);
        control.setOnClickListener(v -> click.run());
        return control;
    }

    IconTile tile(Drawers.App app, boolean pinned) {
        Drawable.ConstantState state = app.icon == null ? null : app.icon.getConstantState();
        Drawable icon = state == null ? app.icon : state.newDrawable(activity.getResources()).mutate();
        return new IconTile(app.label, icon, app.color, pinned ? PIN_SIDE : GRID_SIDE, pinned);
    }

    IconTile addTile() {
        return new IconTile("Add app", activity.getDrawable(R.drawable.sf_add), 0xff496587, PIN_SIDE, false);
    }

    void rowItem(LinearLayout row, View view) {
        row.addView(view, new LinearLayout.LayoutParams(0, -1, 1));
    }

    HorizontalScrollView horizontalScroll() {
        HorizontalScrollView scroller = new HorizontalScrollView(activity);
        scroller.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        scroller.setClipToPadding(false);
        scroller.setSmoothScrollingEnabled(false);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setHorizontalFadingEdgeEnabled(true);
        scroller.setFadingEdgeLength(dp(12));
        scroller.setFocusable(false);
        return scroller;
    }

    void revealPin(HorizontalScrollView scroller, int index, int cell) {
        int left = index * (dp(cell) + dp(PIN_GAP));
        int offset = scroller.getScrollX();
        if (left < offset) scroller.scrollTo(left, 0);
        else if (left + dp(cell) > offset + scroller.getWidth()) {
            scroller.scrollTo(left + dp(cell) - scroller.getWidth(), 0);
        }
    }

    void arrows(View view, IntFunction<View> resolver) {
        view.setOnKeyListener((v, keyCode, event) -> {
            int direction = Drawers.direction(keyCode);
            if (direction == 0) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) resolver.apply(direction).requestFocus();
            return true;
        });
    }

    final class IconTile extends LinearLayout {
        private final FrameLayout square;
        private final ImageView left;
        private final ImageView right;
        private final TextView label;

        IconTile(String name, Drawable drawable, int color, int side, boolean movable) {
            super(activity);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);
            setClipChildren(false);
            FrameLayout glyphRow = new FrameLayout(activity);
            glyphRow.setClipChildren(false);
            glyphRow.setDuplicateParentStateEnabled(true);
            addView(glyphRow, -1, dp(side));
            square = new FrameLayout(activity);
            square.setBackground(GlassStyle.appTile(activity, GlassStyle.slate(tint),
                    color == 0 ? ACCENT : color, 12));
            square.setDuplicateParentStateEnabled(true);
            glyphRow.addView(square, new FrameLayout.LayoutParams(dp(side), dp(side), Gravity.CENTER));
            if (drawable == null) {
                String fallback = name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1));
                TextView mark = text(fallback, 25, GlassStyle.INK, true);
                mark.setGravity(Gravity.CENTER);
                square.addView(mark, -1, -1);
            } else {
                ImageView mark = new ImageView(activity);
                mark.setImageDrawable(IconArt.copy(drawable));
                mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
                mark.setPadding(dp(8), dp(8), dp(8), dp(8));
                square.addView(mark, -1, -1);
            }
            left = movable ? moveArrow(R.drawable.sf_arrow_left) : null;
            right = movable ? moveArrow(R.drawable.sf_arrow_right) : null;
            if (movable) {
                glyphRow.addView(left, new FrameLayout.LayoutParams(dp(15), dp(18),
                        Gravity.CENTER_VERTICAL | Gravity.LEFT));
                glyphRow.addView(right, new FrameLayout.LayoutParams(dp(15), dp(18),
                        Gravity.CENTER_VERTICAL | Gravity.RIGHT));
            }
            label = text(name, 13, GlassStyle.INK, false);
            label.setAlpha(.86f);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            addView(label, -1, dp(24));
            setContentDescription(name);
            control(this);
        }

        private void focusAppearance(boolean focused) {
            square.animate().cancel();
            label.animate().cancel();
            float opacity = focused ? 1f : .86f;
            if (ValueAnimator.areAnimatorsEnabled()) {
                float scale = focused ? 1.045f : 1f;
                square.animate().scaleX(scale).scaleY(scale).translationY(focused ? -dp(2) : 0)
                        .setDuration(110).start();
                label.animate().alpha(opacity).setDuration(110).start();
            } else {
                square.setScaleX(1);
                square.setScaleY(1);
                square.setTranslationY(0);
                label.setAlpha(opacity);
            }
        }

        private ImageView moveArrow(int resource) {
            ImageView arrow = image(resource, 1);
            arrow.setBackground(GlassStyle.rounded(activity, 0xff20374a, 5, 0x9cc3e9ff));
            arrow.setVisibility(INVISIBLE);
            return arrow;
        }

        void moveState(boolean picked, boolean canLeft, boolean canRight) {
            setSelected(picked);
            if (left == null) return;
            left.setVisibility(picked ? VISIBLE : INVISIBLE);
            right.setVisibility(picked ? VISIBLE : INVISIBLE);
            left.setAlpha(canLeft ? 1f : .45f);
            right.setAlpha(canRight ? 1f : .45f);
        }
    }
}
