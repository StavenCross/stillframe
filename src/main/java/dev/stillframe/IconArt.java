package dev.stillframe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

final class IconArt {
    private static final int SIZE = 160;

    /** Cache a bounded transparent square. Large pack bitmaps never enter the view hierarchy. */
    static Drawable resolve(Context context, Drawable installed, Drawable packed) {
        if (packed != null) {
            Drawable icon = raster(context, packed, true, true);
            if (icon != null) return icon;
        }
        if (Build.VERSION.SDK_INT >= 33 && installed instanceof AdaptiveIconDrawable) {
            Drawable mono = ((AdaptiveIconDrawable) installed).getMonochrome();
            Drawable icon = raster(context, mono, true, true);
            if (icon != null) return icon;
        }
        return raster(context, installed, false, false);
    }

    private static Drawable raster(Context context, Drawable source, boolean white, boolean trim) {
        if (source == null) return null;
        try {
            Bitmap raw = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(raw);
            int width = Math.max(1, source.getIntrinsicWidth());
            int height = Math.max(1, source.getIntrinsicHeight());
            Rect bounds = fitted(width, height, SIZE);
            Drawable independent = copy(source);
            independent.setBounds(bounds);
            if (white) independent.setTint(Color.WHITE);
            independent.draw(canvas);
            if (!trim) return new BitmapDrawable(context.getResources(), raw);

            Rect visible = alphaBounds(raw);
            if (visible.isEmpty()) return null;
            // Do not turn a fully opaque square from an icon pack into a white slab.
            if (visible.width() == SIZE && visible.height() == SIZE
                    && Color.alpha(raw.getPixel(0, 0)) > 190
                    && Color.alpha(raw.getPixel(SIZE - 1, SIZE - 1)) > 190) return null;
            Bitmap normalized = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
            new Canvas(normalized).drawBitmap(raw, visible, fitted(visible.width(), visible.height(), 142),
                    new Paint(Paint.FILTER_BITMAP_FLAG));
            return new BitmapDrawable(context.getResources(), normalized);
        } catch (RuntimeException invalidResource) {
            return null;
        }
    }

    static Drawable copy(Drawable source) {
        Drawable.ConstantState state = source.getConstantState();
        return state == null ? source : state.newDrawable().mutate();
    }

    private static Rect fitted(int width, int height, int edge) {
        float scale = edge / (float) Math.max(width, height);
        int w = Math.max(1, Math.round(width * scale)), h = Math.max(1, Math.round(height * scale));
        int x = (SIZE - w) / 2, y = (SIZE - h) / 2;
        return new Rect(x, y, x + w, y + h);
    }

    private static Rect alphaBounds(Bitmap bitmap) {
        int[] pixels = new int[SIZE * SIZE];
        bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        int l = SIZE, t = SIZE, r = 0, b = 0;
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            if (Color.alpha(pixels[y * SIZE + x]) < 24) continue;
            l = Math.min(l, x);
            t = Math.min(t, y);
            r = Math.max(r, x + 1);
            b = Math.max(b, y + 1);
        }
        return new Rect(l, t, r, b);
    }

    private IconArt() {}
}
