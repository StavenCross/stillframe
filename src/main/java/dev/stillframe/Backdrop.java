package dev.stillframe;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.widget.FrameLayout;
import android.widget.ImageView;
import java.util.function.IntConsumer;

final class Backdrop extends FrameLayout {
    private final ImageView base;
    private final ImageView next;
    private final IntConsumer onTint;
    private ValueAnimator fade;
    private Bitmap shown;
    private int tint = 0xff263949;

    Backdrop(Context context, IntConsumer onTint) {
        super(context);
        this.onTint = onTint;
        setBackgroundColor(0xff090e17);
        base = layer(context);
        next = layer(context);
        next.setAlpha(0f);
    }

    private ImageView layer(Context context) {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);
        addView(view, new LayoutParams(-1, -1));
        return view;
    }

    void show(Bitmap bitmap, int targetTint) {
        if (bitmap == null || bitmap == shown) return;
        if (fade != null) fade.cancel();
        if (shown == null || !ValueAnimator.areAnimatorsEnabled()) {
            finish(bitmap, targetTint);
            return;
        }
        next.setImageBitmap(bitmap);
        next.setAlpha(0f);
        final int start = tint;
        fade = ValueAnimator.ofFloat(0f, 1f);
        fade.setDuration(600);
        fade.addUpdateListener(tick -> {
            float amount = (float) tick.getAnimatedValue();
            next.setAlpha(amount);
            tint = blend(start, targetTint, amount);
            onTint.accept(tint);
            if (amount >= 1f) finish(bitmap, targetTint);
        });
        fade.start();
    }

    private void finish(Bitmap bitmap, int value) {
        shown = bitmap;
        tint = value;
        base.setImageBitmap(bitmap);
        next.setAlpha(0f);
        next.setImageDrawable(null);
        onTint.accept(tint);
    }

    private static int blend(int a, int b, float fraction) {
        int rgb = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int from = (a >> shift) & 255, to = (b >> shift) & 255;
            rgb |= Math.round(from + (to - from) * fraction) << shift;
        }
        return rgb;
    }
}
