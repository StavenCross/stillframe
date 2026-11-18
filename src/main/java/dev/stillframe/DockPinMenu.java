package dev.stillframe;

import android.animation.ValueAnimator;
import android.graphics.drawable.StateListDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.transition.Fade;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

final class DockPinMenu {
    private static final int REMOVE_INK = 0xffffdad3;

    static PopupWindow show(DrawerViews views, View anchor, String appName, Runnable action, Runnable dismiss) {
        View window = views.activity().getWindow().getDecorView();
        int margin = views.dp(16), gap = views.dp(8);
        int width = Math.min(views.dp(174), Math.max(1, window.getWidth() - 2 * margin));
        int height = Math.min(views.dp(60), Math.max(1, window.getHeight() - 2 * margin));
        int[] location = new int[2];
        anchor.getLocationInWindow(location);
        int x = left(location[0], anchor.getWidth(), width, window.getWidth(), margin);
        int y = top(location[1], anchor.getHeight(), height, window.getHeight(), margin, gap);

        LinearLayout content = views.row();
        content.setPadding(views.dp(12), views.dp(7), views.dp(7), views.dp(7));
        TextView label = views.text(appName, 14, GlassStyle.QUIET, true);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setPadding(0, 0, views.dp(8), 0);
        content.addView(label, new LinearLayout.LayoutParams(0, -1, 1));
        ImageView remove = removeAction(views, action);
        LinearLayout.LayoutParams actionSize = new LinearLayout.LayoutParams(views.dp(46), -1);
        content.addView(remove, actionSize);

        PopupWindow popup = new PopupWindow(content, width, height, true);
        popup.setBackgroundDrawable(GlassStyle.panel(views.activity(), views.tint(), 17));
        popup.setElevation(views.dp(12));
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setAnimationStyle(0);
        if (ValueAnimator.areAnimatorsEnabled()) popup.setEnterTransition(new Fade(Fade.IN).setDuration(100));
        popup.setOnDismissListener(() -> {
            dismiss.run();
            if (anchor.isAttachedToWindow()) anchor.requestFocus();
        });
        popup.showAtLocation(anchor, Gravity.TOP | Gravity.LEFT, x, y);
        remove.requestFocus();
        return popup;
    }

    private static ImageView removeAction(DrawerViews views, Runnable action) {
        ImageView remove = views.image(R.drawable.sf_remove, 12);
        remove.setColorFilter(REMOVE_INK);
        remove.setContentDescription("Remove from dock");
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},
                GlassStyle.rounded(views.activity(), 0xff50343d, 10, 0xffcf918c));
        background.addState(new int[0], GlassStyle.rounded(views.activity(), 0x553d2630, 10, 0));
        remove.setBackground(background);
        remove.setFocusableInTouchMode(true);
        remove.setDefaultFocusHighlightEnabled(false);
        remove.setOnClickListener(view -> action.run());
        Activation activation = new Activation(SystemClock.uptimeMillis());
        remove.setOnKeyListener((view, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER
                    && keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
            int state = event.getAction();
            if ((state == KeyEvent.ACTION_DOWN || state == KeyEvent.ACTION_UP)
                    && activation.select(state == KeyEvent.ACTION_DOWN, event.getRepeatCount(),
                            event.getDownTime(), event.isCanceled())) view.performClick();
            return true;
        });
        return remove;
    }

    static int left(int anchorLeft, int anchorWidth, int width, int screenWidth, int margin) {
        return clamp(anchorLeft + (anchorWidth - width) / 2, width, screenWidth, margin);
    }

    static int top(int anchorTop, int anchorHeight, int height, int screenHeight, int margin, int gap) {
        int below = anchorTop + anchorHeight + gap;
        int preferred = below + height <= screenHeight - margin ? below : anchorTop - gap - height;
        return clamp(preferred, height, screenHeight, margin);
    }

    private static int clamp(int value, int size, int screenSize, int margin) {
        return Math.max(margin, Math.min(value, screenSize - margin - size));
    }

    static final class Activation {
        private final long openedAt;
        private long armedPress = -1;

        Activation(long openedAt) { this.openedAt = openedAt; }

        boolean select(boolean down, int repeat, long downTime, boolean canceled) {
            if (down) {
                // The Select press that opened the popup still owns its release.
                if (repeat == 0 && downTime >= openedAt && !canceled) armedPress = downTime;
                return false;
            }
            boolean trigger = armedPress >= 0 && armedPress == downTime && !canceled;
            armedPress = -1;
            return trigger;
        }
    }

    private DockPinMenu() {}
}
