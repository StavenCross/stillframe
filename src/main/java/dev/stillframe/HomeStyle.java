package dev.stillframe;

import android.graphics.Color;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

final class HomeStyle {
    static void place(View view, double x, double y, double width, double height,
                      int w, int h, double font) {
        FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(
                (int) (w * width), (int) (h * height));
        layout.leftMargin = (int) (w * x);
        layout.topMargin = (int) (h * y);
        view.setLayoutParams(layout);
        if (font > 0) ((TextView) view).setTextSize(
                TypedValue.COMPLEX_UNIT_PX, (float)(w * font));
    }

    static GradientDrawable frame(boolean active, int fill) {
        GradientDrawable style = new GradientDrawable();
        style.setCornerRadius(14);
        style.setColor(fill);
        style.setStroke(active ? 4 : 1, active ? Color.WHITE : 0x60cad9e7);
        return style;
    }

    static TextView label(Context context, String value) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(0xffeff6ff);
        view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private HomeStyle() {}
}
