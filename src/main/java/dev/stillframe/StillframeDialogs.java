package dev.stillframe;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class StillframeDialogs {
    private static final int INSET = 24;
    private static final int GAP = 10;

    static AlertDialog.Builder builder(Activity activity) {
        return new AlertDialog.Builder(activity, R.style.StillframeDialogTheme);
    }

    static ListAdapter options(Activity activity, String[] labels, boolean checked) {
        int row = checked ? R.layout.sf_dialog_choice : R.layout.sf_dialog_row;
        return new ArrayAdapter<>(activity, row, android.R.id.text1, labels);
    }

    static LinearLayout content(Context context) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, INSET), dp(context, 6), dp(context, INSET), dp(context, 12));
        return content;
    }

    static LinearLayout field(EditText input) {
        Context context = input.getContext();
        input.setTextSize(17);
        input.setTextColor(GlassStyle.INK);
        input.setHintTextColor(GlassStyle.QUIET);
        input.setBackground(GlassStyle.focus(context, 0xff1e3042, 0xff2c455e, 10));
        input.setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8));
        input.setMinHeight(dp(context, 52));
        LinearLayout box = content(context);
        box.addView(input, -1, -2);
        return box;
    }

    static void style(AlertDialog dialog, int tint) {
        style(dialog, tint, 0);
    }

    static void style(AlertDialog dialog, int tint, int destructiveButton) {
        Window window = dialog.getWindow();
        if (window == null) return;
        Context context = dialog.getContext();
        int width = dp(context, DialogLayout.width(context.getResources().getConfiguration().screenWidthDp));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(.58f);
        if (!ValueAnimator.areAnimatorsEnabled()) window.setWindowAnimations(0);
        window.setBackgroundDrawable(GlassStyle.panel(context, tint, 22));
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);

        int titleId = context.getResources().getIdentifier("alertTitle", "id", "android");
        View title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title instanceof TextView) {
            TextView heading = (TextView) title;
            heading.setTextSize(23);
            heading.setTextColor(GlassStyle.INK);
            heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            heading.setSingleLine(false);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextSize(17);
            message.setTextColor(GlassStyle.QUIET);
            message.setLineSpacing(dp(context, 3), 1f);
        }
        styleList(dialog.getListView(), context);
        styleActions(dialog, context, width, destructiveButton);
    }

    private static void styleList(ListView list, Context context) {
        if (list == null) return;
        list.setPadding(dp(context, 18), dp(context, 6), dp(context, 18), dp(context, 4));
        list.setSelector(GlassStyle.rounded(context, 0xff36516d, 10, 0xff8bbce3));
        list.setDivider(new ColorDrawable(Color.TRANSPARENT));
        list.setDividerHeight(dp(context, 4));
        list.setVerticalScrollBarEnabled(false);
    }

    private static void styleActions(AlertDialog dialog, Context context, int width, int destructiveButton) {
        List<Button> buttons = new ArrayList<>(3);
        int[] order = {AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_POSITIVE};
        for (int which : order) {
            Button button = dialog.getButton(which);
            if (button == null || button.getVisibility() != View.VISIBLE) continue;
            styleButton(button, context, which == AlertDialog.BUTTON_POSITIVE, which == destructiveButton);
            buttons.add(button);
        }
        if (buttons.isEmpty() || !(buttons.get(0).getParent() instanceof ViewGroup)) return;
        ViewGroup nativeBar = (ViewGroup) buttons.get(0).getParent();
        for (Button button : buttons) if (button.getParent() != nativeBar) return;

        int[] widths = new int[buttons.size()];
        int padding = dp(context, 32);
        for (int index = 0; index < widths.length; index++) {
            Button button = buttons.get(index);
            widths[index] = Math.max(dp(context, 96),
                    (int) Math.ceil(button.getPaint().measureText(button.getText().toString())) + padding);
        }
        int gap = dp(context, GAP);
        int inset = dp(context, INSET);
        boolean stacked = DialogLayout.stacked(width - 2 * inset, gap, widths);
        View focused = dialog.getCurrentFocus();
        // Reuse Android's buttons and click handlers. Only replace the stock
        // action arrangement, which otherwise changes order on some TV builds.
        nativeBar.removeAllViews();
        nativeBar.setPadding(0, 0, 0, 0);
        LinearLayout footer = new LinearLayout(context);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setPadding(inset, dp(context, 12), inset, dp(context, 18));
        nativeBar.addView(footer, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        View divider = new View(context);
        divider.setBackgroundColor(0x24b9cfe6);
        footer.addView(divider, -1, dp(context, 1));
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams group = new LinearLayout.LayoutParams(-1, -2);
        group.topMargin = dp(context, 13);
        footer.addView(actions, group);
        for (int index = 0; index < widths.length; index++) {
            LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(stacked ? -1 : widths[index], -2);
            if (index > 0) {
                if (stacked) item.topMargin = gap;
                else item.setMarginStart(gap);
            }
            actions.addView(buttons.get(index), item);
        }
        if (buttons.contains(focused)) focused.requestFocus();
    }

    private static void styleButton(Button button, Context context, boolean primary, boolean destructive) {
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setEllipsize(null);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(16);
        button.setTextColor(GlassStyle.INK);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setDefaultFocusHighlightEnabled(false);
        int idle = destructive ? 0xff4d303a : primary ? 0xff34526e : 0xff26394d;
        int active = destructive ? 0xff714451 : 0xff466887;
        button.setBackground(GlassStyle.focus(context, idle, active, 10));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(context, 46));
        button.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
    }

    private static int dp(Context context, int value) { return GlassStyle.dp(context, value); }

    private StillframeDialogs() {}
}
