package dev.stillframe;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.graphics.Rect;

import java.io.IOException;

/** Sizes a wallpaper for the display and derives a muted accent off the UI thread. */
final class WallpaperImage {
    final Bitmap bitmap, glass;
    final int accent;

    private WallpaperImage(Bitmap bitmap, Bitmap glass, int accent) {
        this.bitmap = bitmap;
        this.glass = glass;
        this.accent = accent;
    }

    static WallpaperImage decode(ImageDecoder.Source source, int width, int height) throws IOException {
        Bitmap bitmap = ImageDecoder.decodeBitmap(source, (decoder, info, decodedSource) -> {
            Geometry size = geometry(info.getSize().getWidth(), info.getSize().getHeight(), width, height);
            int left = (size.width - size.cropWidth) / 2;
            int top = (size.height - size.cropHeight) / 2;
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            decoder.setTargetSize(size.width, size.height);
            decoder.setCrop(new Rect(left, top, left + size.cropWidth, top + size.cropHeight));
        });
        return new WallpaperImage(bitmap, glass(bitmap), accent(bitmap));
    }

    private static Bitmap glass(Bitmap source) {
        int width = 160, height = Math.max(1, Math.min(120,
                Math.round(width * source.getHeight() / (float) source.getWidth())));
        Bitmap small = Bitmap.createScaledBitmap(source, width, height, true);
        int[] pixels = new int[width * height];
        small.getPixels(pixels, 0, width, 0, 0, width, height);
        return Bitmap.createBitmap(GlassPixels.blur(pixels, width, height, 7),
                width, height, Bitmap.Config.ARGB_8888);
    }

    static Geometry geometry(int sourceWidth, int sourceHeight, int targetWidth, int targetHeight) {
        double widthScale = targetWidth / (double) sourceWidth;
        double heightScale = targetHeight / (double) sourceHeight;
        double cover = Math.max(widthScale, heightScale);
        // Extremely tall or wide files may need to be enlarged at draw time.
        double bounded = Math.min(1, Math.min(cover, 2 * Math.min(widthScale, heightScale)));
        int width = Math.max(1, (int) Math.round(sourceWidth * bounded));
        int height = Math.max(1, (int) Math.round(sourceHeight * bounded));
        int cropWidth = Math.max(1, Math.min(width, (int) Math.round(height * (targetWidth / (double) targetHeight))));
        int cropHeight = Math.max(1, Math.min(height, (int) Math.round(width * (targetHeight / (double) targetWidth))));
        return new Geometry(width, height, cropWidth, cropHeight);
    }

    private static int accent(Bitmap bitmap) {
        Bitmap preview = Bitmap.createScaledBitmap(bitmap, 12, 8, true);
        int[] pixels = new int[96];
        preview.getPixels(pixels, 0, 12, 0, 0, 12, 8);
        if (preview != bitmap) preview.recycle();
        float[] hsv = new float[3];
        double x = 0, y = 0, weight = 0;
        for (int pixel : pixels) {
            Color.colorToHSV(pixel, hsv);
            double contribution = hsv[1] * hsv[2] * Color.alpha(pixel) / 255;
            double angle = Math.toRadians(hsv[0]);
            x += Math.cos(angle) * contribution;
            y += Math.sin(angle) * contribution;
            weight += contribution;
        }
        float hue = (float) ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
        float saturation = weight >= 2 ? .33f : .13f;
        return Color.HSVToColor(new float[]{weight >= 2 ? hue : 215, saturation, .36f});
    }

    static final class Geometry {
        final int width;
        final int height;
        final int cropWidth;
        final int cropHeight;

        Geometry(int width, int height, int cropWidth, int cropHeight) {
            this.width = width;
            this.height = height;
            this.cropWidth = cropWidth;
            this.cropHeight = cropHeight;
        }
    }
}
