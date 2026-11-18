package dev.stillframe;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

final class FeaturePanel extends LinearLayout {
    private final TextView title, episode, program, rating, score, context, synopsis, time, emptyAction;
    private final LinearLayout chips, playback;
    private final ProgressBar progress;
    private String displayed = "";

    FeaturePanel(Context context, Runnable onEmpty) {
        super(context);
        setOrientation(VERTICAL);
        title = textView(3, 0xfff4f8ff, "sans-serif-medium");
        title.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
        row(title);
        episode = textView(2, 0xffdce8f2, "sans-serif");
        row(episode);
        chips = horizontal();
        program = chip();
        rating = chip();
        score = chip();
        this.context = textView(1, 0xffb7c9d8, "sans-serif");
        row(this.context);
        synopsis = textView(3, 0xffdfe9f1, "sans-serif");
        row(synopsis);
        playback = horizontal();
        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(ColorStateList.valueOf(0xffe5edf8));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(0xff586777));
        playback.addView(progress, new LayoutParams(0, 6, 1));
        time = textView(1, 0xffdce8f2, "sans-serif-medium");
        playback.addView(time, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        emptyAction = textView(1, 0xfff4f8ff, "sans-serif-medium");
        emptyAction.setFocusable(true);
        emptyAction.setDefaultFocusHighlightEnabled(false);
        emptyAction.setGravity(Gravity.CENTER_VERTICAL);
        emptyAction.setOnClickListener(view -> onEmpty.run());
        GradientDrawable idle = HomeStyle.frame(false, 0xc5253545);
        GradientDrawable focused = HomeStyle.frame(true, 0xfa384e66);
        emptyAction.setBackground(idle);
        emptyAction.setOnFocusChangeListener((view, active) -> emptyAction.setBackground(active ? focused : idle));
        addView(emptyAction, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    TextView emptyAction() { return emptyAction; }

    private void row(TextView view) {
        addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView chip() {
        TextView view = textView(1, 0xffdfebf7, "sans-serif-medium");
        GradientDrawable fill = new GradientDrawable();
        fill.setCornerRadius(12);
        fill.setColor(0xa24b5b6e);
        view.setBackground(fill);
        chips.addView(view, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return view;
    }

    private TextView textView(int maxLines, int color, String family) {
        TextView view = new TextView(getContext());
        view.setMaxLines(maxLines);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(family, Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        view.setShadowLayer(2f, 0, 1f, 0xb0000000);
        return view;
    }

    void layoutAt(int width, int height) {
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(Math.round(width * .285f),
                LayoutParams.WRAP_CONTENT);
        position.leftMargin = Math.round(width * .047f);
        position.topMargin = Math.round(height * .213f);
        setLayoutParams(position);
        int pad = Math.round(width * .012f);
        setPadding(0, Math.round(width * .008f), pad, Math.round(width * .01f));
        size(title, width * .029f, width * .006f);
        size(episode, width * .016f, width * .006f);
        size(context, width * .013f, width * .005f);
        size(synopsis, width * .014f, width * .009f);
        synopsis.setLineSpacing(width * .0015f, 1f);
        size(time, width * .0135f, 0);
        chipSize(program, width);
        chipSize(rating, width);
        chipSize(score, width);
        LayoutParams chipRow = (LayoutParams) chips.getLayoutParams();
        chipRow.bottomMargin = Math.round(width * .006f);
        chips.setLayoutParams(chipRow);
        LayoutParams track = (LayoutParams) progress.getLayoutParams();
        track.height = Math.max(4, Math.round(width * .0033f));
        track.setMarginEnd(pad / 2);
        progress.setLayoutParams(track);
        LayoutParams action = (LayoutParams) emptyAction.getLayoutParams();
        action.topMargin = Math.round(width * .005f);
        action.height = Math.round(width * .036f);
        emptyAction.setLayoutParams(action);
        emptyAction.setMaxWidth(Math.round(width * .24f));
        emptyAction.setPadding(pad, 0, pad, 0);
        emptyAction.setTextSize(TypedValue.COMPLEX_UNIT_PX, width * .015f);
    }

    private void chipSize(TextView view, int width) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, width * .012f);
        view.setMaxWidth(Math.round(width * .082f));
        int horizontal = Math.round(width * .006f);
        int vertical = Math.round(width * .0037f);
        view.setPadding(horizontal, vertical, horizontal, vertical);
        LayoutParams layout = (LayoutParams) view.getLayoutParams();
        layout.setMarginEnd(Math.round(width * .0035f));
        view.setLayoutParams(layout);
    }

    private void size(TextView view, float px, float gap) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        view.setPadding(0, 0, 0, (int) gap);
    }

    void show(WatchFeed.Entry entry, String providerLabel, String provider, WatchFeed.Status status,
              boolean animateTitle) {
        String identity = entry == null ? "" : entry.pkg + "/" + entry.id;
        if (!displayed.equals(identity)) {
            displayed = identity;
            title.animate().cancel();
            boolean reveal = animateTitle && entry != null && isLaidOut() && ValueAnimator.areAnimatorsEnabled();
            title.setAlpha(reveal ? .84f : 1f);
            if (reveal) title.animate().alpha(1f).setDuration(115).start();
        }
        if (entry == null) {
            setText(title, providerLabel);
            setText(episode, emptyMessage(status, provider));
            String action = status == WatchFeed.Status.PERMISSION_REQUIRED ? "Enable Watch Next"
                    : provider.isEmpty() ? "Browse apps" : "Open " + providerLabel;
            if (!TextUtils.equals(emptyAction.getText(), action)) emptyAction.setText(action);
            showChips("", "", "");
            setText(context, "");
            setText(synopsis, "");
            playback.setVisibility(GONE);
            return;
        }
        setText(title, entry.title);
        setText(episode, entry.episodeTitle);
        showChips(entry.episode, entry.ageRating, entry.review);
        setText(context, entry.context);
        setText(synopsis, entry.synopsis);
        progress.setVisibility(entry.progress < 0 ? GONE : VISIBLE);
        if (entry.progress >= 0) progress.setProgress(Math.round(entry.progress * 1000));
        setText(time, entry.time);
        playback.setVisibility(entry.progress >= 0 || !entry.time.isEmpty() ? VISIBLE : GONE);
    }

    static String emptyMessage(WatchFeed.Status status, String provider) {
        switch (status) {
            case LOADING: return "Loading Continue Watching…";
            case PERMISSION_REQUIRED: return "Allow TV listings to show your titles here.";
            case UNAVAILABLE: return "Continue Watching is unavailable.";
            case READY:
                return provider.isEmpty() ? "Pin an app to see its titles here." : "No Continue Watching titles yet.";
            default: throw new AssertionError(status);
        }
    }

    private void showChips(String episodeNumber, String ageRating, String review) {
        setText(program, episodeNumber);
        setText(rating, ageRating);
        setText(score, review);
        chips.setVisibility(episodeNumber.isEmpty() && ageRating.isEmpty() && review.isEmpty() ? GONE : VISIBLE);
    }

    private void setText(TextView view, String value) {
        if (!TextUtils.equals(view.getText(), value)) view.setText(value);
        view.setVisibility(TextUtils.isEmpty(value) ? GONE : VISIBLE);
    }
}
