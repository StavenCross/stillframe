package dev.stillframe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;

final class GlassStyle {
    static final int INK = 0xfff7f9fe;
    static final int QUIET = 0xffc2cede;
    static final int NEUTRAL = 0xff32475e;
    private static final int RIM = 0x57cce2fd;
    private static final int FOCUS = 0xfffcfdff;
    private static final int MOVE = 0xff9de0ff;

    static int dp(Context context, int dp) {
        return Math.round(context.getResources().getDisplayMetrics().density * dp);
    }

    static int slate(int tint) {
        int red = (tint >> 16 & 0xff) / 5 + 15;
        int green = (tint >> 8 & 0xff) / 5 + 22;
        int blue = (tint & 0xff) / 5 + 32;
        return 0xff000000 | red << 16 | green << 8 | blue;
    }

    private static int alpha(int color, int alpha) {
        return color & 0x00ffffff | alpha << 24;
    }

    static GradientDrawable rounded(Context context, int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radius));
        if (stroke != 0) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    static GradientDrawable panel(Context context, int tint, int radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{slate(tint), 0xff151e2c});
        drawable.setCornerRadius(dp(context, radius));
        drawable.setStroke(dp(context, 1), RIM);
        return drawable;
    }

    static StateListDrawable focus(Context context, int idle, int active, int radius) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_selected}, state(context, active, radius, MOVE));
        states.addState(new int[]{android.R.attr.state_focused}, state(context, active, radius, FOCUS));
        states.addState(new int[]{android.R.attr.state_pressed}, state(context, active, radius, FOCUS));
        states.addState(new int[0], rounded(context, idle, radius, idle == 0 ? 0 : 0x40b8cbe5));
        return states;
    }

    static StateListDrawable appTile(Context context, int idle, int brand, int radius) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_selected}, appGlow(context, brand, radius, MOVE));
        states.addState(new int[]{android.R.attr.state_focused},
                appGlow(context, brand, radius, AppGlow.hue(brand)));
        states.addState(new int[0], rounded(context, idle, radius, 0));
        return states;
    }

    private static Drawable appGlow(Context context, int brand, int radius, int edge) {
        GradientDrawable halo = rounded(context, brand, radius, 0);
        halo.setStroke(dp(context, 3), AppGlow.alpha(edge, 0x58));
        GradientDrawable line = rounded(context, 0, radius, 0);
        line.setStroke(dp(context, 1), AppGlow.alpha(edge, 0xe9));
        return new LayerDrawable(new Drawable[]{halo, line});
    }

    private static Drawable state(Context context, int fill, int radius, int outline) {
        GradientDrawable drawable = rounded(context, fill, radius, 0);
        drawable.setStroke(dp(context, 2), outline);
        return drawable;
    }

    static Drawable sheet(View host, View sheet, Bitmap bitmap, int tint) {
        if (bitmap == null || bitmap.isRecycled()) return panel(sheet.getContext(), tint, 24);
        return new FrostedSheet(host, sheet, bitmap, tint);
    }

    private static final class FrostedSheet extends Drawable {
        private final View host;
        private final View sheet;
        private final Bitmap wallpaper;
        private final int tint;
        private final int radius;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Path clip = new Path();
        private final RectF surface = new RectF();
        private final RectF backdrop = new RectF();
        private android.graphics.Shader sheen;

        FrostedSheet(View host, View sheet, Bitmap wallpaper, int tint) {
            this.host = host;
            this.sheet = sheet;
            this.wallpaper = wallpaper;
            this.tint = tint;
            radius = dp(sheet.getContext(), 24);
        }

        @Override protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            sheen = new android.graphics.LinearGradient(0, 0, 0, Math.max(1, bounds.height()),
                    0x22e8f5ff, 0x04e8f5ff, android.graphics.Shader.TileMode.CLAMP);
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            surface.set(bounds);
            clip.rewind();
            clip.addRoundRect(surface, radius, radius, Path.Direction.CW);
            int restore = canvas.save();
            canvas.clipPath(clip);
            if (!wallpaper.isRecycled() && host.getWidth() > 0 && host.getHeight() > 0) {
                // Sample the same center crop as Home, in screen coordinates.
                float scale = Math.max((float) host.getWidth() / wallpaper.getWidth(),
                        (float) host.getHeight() / wallpaper.getHeight());
                float width = wallpaper.getWidth() * scale;
                float height = wallpaper.getHeight() * scale;
                float left = (host.getWidth() - width) / 2 - sheet.getLeft();
                float top = (host.getHeight() - height) / 2 - sheet.getTop();
                backdrop.set(left, top, left + width, top + height);
                paint.setColor(INK);
                canvas.drawBitmap(wallpaper, null, backdrop, paint);
            }
            canvas.drawColor(alpha(slate(tint), 0xa1));
            paint.setColor(INK);
            paint.setShader(sheen);
            canvas.drawRoundRect(surface, radius, radius, paint);
            paint.setShader(null);
            paint.setColor(RIM);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(sheet.getContext(), 2));
            canvas.drawRoundRect(surface, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.restoreToCount(restore);
        }

        @Override public void getOutline(Outline outline) {
            outline.setRoundRect(getBounds(), radius);
        }

        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private GlassStyle() {}
}
