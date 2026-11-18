package dev.stillframe;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;
import java.util.function.Consumer;

final class PosterTile extends FrameLayout {
    private final ImageView image;
    private final TextView title;
    private final GradientDrawable panel = new GradientDrawable();
    private final GradientDrawable stroke = new GradientDrawable();
    private final PosterPresentation presentation = new PosterPresentation();
    private final Runnable afterWait;
    private WatchFeed.Entry entry;
    private Bitmap shown;
    private int wellColor, ringWidth = 4;
    private float lift = 4;
    private boolean selected, prefersPortrait;
    private Runnable onFocus;

    PosterTile(Context context, Consumer<WatchFeed.Entry> play, Runnable refreshArtwork) {
        super(context);
        afterWait = () -> refreshArtwork.run();
        setFocusable(true);
        setDefaultFocusHighlightEnabled(false);
        setClipToOutline(true);
        panel.setColor(0x67131e29);
        setBackground(panel);
        stroke.setColor(Color.TRANSPARENT);
        setForeground(stroke);
        image = new ImageView(context);
        // The decoder normally crops to the card's aspect. Allow a slightly
        // wider crop for side lettering without trimming that extra margin.
        image.setScaleType(ImageView.ScaleType.FIT_XY);
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(image, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setArtTint(0xff273949);
        title = new TextView(context);
        title.setGravity(Gravity.START | Gravity.BOTTOM);
        title.setTextColor(0xfff3f7fb);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xae000000, 0xe6000000}));
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setIncludeFontPadding(false);
        title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(title);
        setOnClickListener(view -> { if (entry != null) play.accept(entry); });
        setOnFocusChangeListener((view, focused) -> {
            drawOutline();
            animate().cancel();
            float scale = focused ? 1.045f : 1f;
            if (ValueAnimator.areAnimatorsEnabled()) {
                animate().scaleX(scale).scaleY(scale).setDuration(focused ? 135 : 95).start();
            } else {
                setScaleX(scale);
                setScaleY(scale);
            }
            if (focused && onFocus != null) onFocus.run();
        });
        drawOutline();
    }

    static int layoutRow(FrameLayout row, List<PosterTile> cards, int width, int height, boolean portraits) {
        int side = cardWidth(width, height, portraits);
        int gap = Math.round(width * .018f);
        int cardHeight = portraits ? Math.round(side * 1.5f) : cardHeight(side, height);
        LayoutParams position = new LayoutParams(side * cards.size() + gap * (cards.size() - 1), cardHeight);
        position.leftMargin = Math.round(width * .357f);
        position.topMargin = Math.round(height * .206f);
        row.setLayoutParams(position);
        for (int i = 0; i < cards.size(); i++) {
            PosterTile tile = cards.get(i);
            LayoutParams bounds = new LayoutParams(side, cardHeight);
            bounds.leftMargin = i * (side + gap);
            tile.setLayoutParams(bounds);
            tile.size(side);
        }
        return cardHeight;
    }

    static int cardWidth(int width, int height, boolean portraits) {
        int standard = Math.round(width * .184f);
        return portraits ? Math.min(standard, Math.round(height * .32f)) : standard;
    }

    static int cardHeight(int width, int displayHeight) {
        return Math.round(Math.min(width * 1.12f, displayHeight * .48f));
    }

    static boolean showTitle(String publisher, boolean artworkLoaded) {
        // Watch Next has no field for lettering in artwork. Default to a label
        // except for publishers whose sampled art already includes the title.
        return !artworkLoaded || !(publisher.equals("com.netflix.ninja")
                || publisher.equals("com.amazon.amazonvideo.livingroom")
                || publisher.equals("com.wbd.stream"));
    }

    private void size(int side) {
        int pad = Math.max(6, Math.round(side * .067f));
        float corner = Math.max(8, side * .063f);
        panel.setCornerRadius(corner);
        stroke.setCornerRadius(corner);
        ringWidth = Math.max(2, Math.round(side * .016f));
        lift = Math.max(2, side * .018f);
        LayoutParams name = new LayoutParams(LayoutParams.MATCH_PARENT,
                Math.round(side * .38f), Gravity.BOTTOM);
        title.setLayoutParams(name);
        title.setPadding(pad, 0, pad, Math.round(side * .074f));
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, side * .086f);
        drawOutline();
    }

    WatchFeed.Entry entry() { return entry; }

    void bind(WatchFeed.Entry value, boolean highlighted, Runnable onFocus) {
        this.onFocus = onFocus;
        if (entry == null || !entry.pkg.equals(value.pkg) || !entry.id.equals(value.id)
                || !entry.title.equals(value.title) || !entry.episode.equals(value.episode)
                || !entry.episodeTitle.equals(value.episodeTitle)) {
            resetArtwork();
        }
        entry = value;
        if (!TextUtils.equals(title.getText(), value.title)) {
            setContentDescription(value.title);
            title.setText(value.title);
        }
        outline(highlighted);
    }

    void unbind() {
        if (entry == null) return;
        entry = null;
        onFocus = null;
        resetArtwork();
    }

    private void resetArtwork() {
        presentation.reset(SystemClock.uptimeMillis());
        removeCallbacks(afterWait);
        if (shown == null) return;
        image.animate().cancel();
        image.setImageBitmap(null);
        shown = null;
    }

    void outline(boolean previewed) {
        if (selected == previewed) return;
        selected = previewed;
        drawOutline();
    }

    private void drawOutline() {
        boolean focused = isFocused();
        stroke.setStroke(focused ? ringWidth : selected ? Math.max(1, ringWidth / 2) : 1,
                focused ? 0xffd5eafd : selected ? 0xff8aa6bf : 0x625d758f);
        setTranslationZ(focused ? lift : 0f);
        setTranslationY(focused ? -lift : 0);
    }

    void setArtTint(int tint) {
        if (wellColor == tint) return;
        wellColor = tint;
        panel.setColor(Color.argb(217, Color.red(tint) / 2, Color.green(tint) / 2, Color.blue(tint) / 2));
    }

    void updateArt(Bitmap portrait, Bitmap publisher, boolean pending, boolean preferPortrait) {
        if (prefersPortrait != preferPortrait) {
            prefersPortrait = preferPortrait;
            resetArtwork();
        }
        long now = SystemClock.uptimeMillis();
        PosterPresentation.Source source = presentation.select(now, portrait != null, publisher != null, pending);
        removeCallbacks(afterWait);
        switch (source) {
            case PORTRAIT:
                if (portrait != null) setArt(portrait, true);
                break;
            case PUBLISHER:
                if (publisher != null) setArt(publisher, false);
                break;
            case WAITING:
                setArt(null, false);
                if (publisher != null && pending) postDelayed(afterWait, presentation.remaining(now));
                break;
        }
    }

    private void setArt(Bitmap bitmap, boolean catalog) {
        if (shown != bitmap) {
            boolean reveal = shown == null && bitmap != null && ValueAnimator.areAnimatorsEnabled();
            shown = bitmap;
            image.animate().cancel();
            image.setImageBitmap(bitmap);
            image.setAlpha(reveal ? .62f : 1f);
            if (reveal) image.animate().alpha(1f).setDuration(125).start();
        }
        boolean titledPickArtwork = bitmap != null && HidivePicks.isPick(entry);
        int visibility = !catalog && !titledPickArtwork && showTitle(entry.pkg, bitmap != null)
                ? VISIBLE : INVISIBLE;
        if (title.getVisibility() != visibility) title.setVisibility(visibility);
    }

}
