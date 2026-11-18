package dev.stillframe;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

final class DockTile extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable icon;
    private final int color, glow;
    private Palette tint = Palette.DEFAULT;
    private Shader sheen;

    DockTile(Context context, Drawers.App app) {
        super(context);
        color = app.color;
        glow = AppGlow.hue(color);
        if (AppCatalog.APPS.equals(app.pkg)) icon = context.getDrawable(R.drawable.sf_apps);
        else if (AppCatalog.EDIT.equals(app.pkg)) icon = context.getDrawable(R.drawable.sf_edit);
        else if (app.icon != null) icon = IconArt.copy(app.icon);
        else if (AppCatalog.STORE.equals(app.pkg)) icon = context.getDrawable(R.drawable.sf_store);
        else icon = context.getDrawable(R.drawable.sf_apps);
        tintMark(false);
        setContentDescription(app.label);
        setFocusableInTouchMode(true);
        setDefaultFocusHighlightEnabled(false);
        setClickable(true);
    }

    void tint(Palette value) {
        if (tint != value) {
            tint = value;
            if (!isFocused()) tintMark(false);
            invalidate();
        }
    }

    private void tintMark(boolean focused) {
        if (icon == null) return;
        icon.setAlpha(focused ? 255 : 228);
        icon.setColorFilter(focused ? null : tint.mark);
    }

    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        sheen = new LinearGradient(0, 0, w * .25f, Math.max(1, h),
                0x16ffffff, 0x02ffffff, Shader.TileMode.CLAMP);
        boolean mark = icon instanceof android.graphics.drawable.BitmapDrawable;
        int inset = Math.round(Math.min(w, h) * (mark ? .09f : .20f));
        if (icon != null) icon.setBounds(inset, inset, w - inset, h - inset);
    }

    @Override protected void onFocusChanged(boolean focused, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        tintMark(focused);
        animate().cancel();
        if (focused && ValueAnimator.areAnimatorsEnabled()) {
            animate().scaleX(1.06f).scaleY(1.06f).setDuration(115).start();
        } else {
            setScaleX(1);
            setScaleY(1);
        }
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), radius = w * .14f;
        boolean focused = isFocused();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(focused ? color : tint.surface);
        canvas.drawRoundRect(1, 1, w - 1, h - 1, radius, radius, paint);
        if (!focused && sheen != null) {
            paint.setColor(Color.WHITE);
            paint.setShader(sheen);
            canvas.drawRoundRect(1, 1, w - 1, h - 1, radius, radius, paint);
            paint.setShader(null);
        }
        if (focused) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(3f, w * .046f));
            paint.setColor(AppGlow.alpha(glow, 0x54));
            canvas.drawRoundRect(2, 2, w - 2, h - 2, radius, radius, paint);
            paint.setStrokeWidth(Math.max(1f, w * .014f));
            paint.setColor(AppGlow.alpha(glow, 0xef));
            canvas.drawRoundRect(2, 2, w - 2, h - 2, radius, radius, paint);
        }
        if (icon != null) icon.draw(canvas);
    }

    // One immutable palette is shared across every tile at each wallpaper tick.
    static final class Palette {
        static final Palette DEFAULT = new Palette(0xff263949);
        final int accent, surface;
        final LightingColorFilter mark;

        Palette(int accent) {
            this.accent = accent;
            surface = DockPalette.surface(accent);
            mark = new LightingColorFilter(DockPalette.mark(accent), 0);
        }
    }
}
