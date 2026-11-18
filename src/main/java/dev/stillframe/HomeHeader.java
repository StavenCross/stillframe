package dev.stillframe;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import static dev.stillframe.HomeStyle.place;

final class HomeHeader extends FrameLayout {
    private static final int FOREGROUND = 0xffeff6ff;
    final ImageView settings;
    private final TextView wordmark, time, temperature;
    private final LinearLayout weather;
    private final WeatherIcon weatherIcon;

    HomeHeader(Context context, Runnable showSettings) {
        super(context);
        wordmark = text("", Gravity.CENTER_VERTICAL);
        wordmark.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        wordmark.setLetterSpacing(.13f);
        SpannableString identity = new SpannableString("STILLFRAME");
        identity.setSpan(new StyleSpan(Typeface.BOLD), "STILL".length(), identity.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        wordmark.setText(identity);
        time = text("", Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        addView(wordmark);
        addView(time);
        weather = new LinearLayout(context);
        weather.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        weather.setScreenReaderFocusable(true);
        weather.setVisibility(GONE);
        weatherIcon = new WeatherIcon(context, FOREGROUND);
        weatherIcon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        weather.addView(weatherIcon);
        temperature = text("", Gravity.CENTER_VERTICAL);
        temperature.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        weather.addView(temperature);
        addView(weather);

        settings = new ImageView(context);
        settings.setImageResource(R.drawable.sf_settings);
        settings.setColorFilter(FOREGROUND);
        settings.setScaleType(ImageView.ScaleType.FIT_CENTER);
        settings.setContentDescription("Android settings");
        settings.setFocusable(true);
        settings.setOnClickListener(button -> showSettings.run());
        settings.setDefaultFocusHighlightEnabled(false);
        settings.setOnFocusChangeListener((button, focused) -> {
            settings.setColorFilter(focused ? 0xff9cdaff : FOREGROUND);
            settings.setScaleX(focused ? 1.10f : 1f);
            settings.setScaleY(focused ? 1.10f : 1f);
        });
        addView(settings);
    }

    void size(int width, int height) {
        place(wordmark, .05, .045, .33, .057, width, height, .019);
        place(time, .873, .04, .077, .065, width, height, .016);
        place(settings, .823, .04, .044, .065, width, height, 0);
        int horizontalInset = (int) (width * .012);
        int verticalInset = (int) (height * .017);
        settings.setPadding(horizontalInset, verticalInset, horizontalInset, verticalInset);
        place(weather, .69, .04, .121, .065, width, height, 0);
        temperature.setTextSize(TypedValue.COMPLEX_UNIT_PX, width * .016f);
        int iconSize = Math.max(1, (int) (height * .034));
        LinearLayout.LayoutParams iconLayout = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLayout.rightMargin = (int) (width * .005);
        weatherIcon.setLayoutParams(iconLayout);
    }

    boolean contains(View focused) { return focused == settings; }

    void clock(String value) { time.setText(value); }

    void weather(Weather.Reading value) {
        weather.setVisibility(value == null ? GONE : VISIBLE);
        if (value != null) {
            weatherIcon.condition(value.icon);
            temperature.setText(value.temperature);
            weather.setContentDescription(value.condition.isEmpty() ? value.temperature
                    : value.condition + ", " + value.temperature);
        }
    }

    private TextView text(String value, int gravity) {
        TextView view = new TextView(getContext());
        view.setSingleLine();
        view.setText(value);
        view.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        view.setTextColor(FOREGROUND);
        view.setGravity(gravity);
        return view;
    }
}
