package dev.stillframe;

final class DockPalette {
    static int surface(int accent) {
        int red = accent >>> 16 & 255, green = accent >>> 8 & 255, blue = accent & 255;
        int peak = Math.max(red, Math.max(green, blue));
        int light = Math.min(118, Math.max(65, peak + 15));
        // The backdrop samples a subdued accent. Increase its channel separation
        // so the hue remains visible after composition with the wallpaper.
        return 0x90000000 | tint(red, peak, light) << 16 | tint(green, peak, light) << 8
                | tint(blue, peak, light);
    }

    static int mark(int accent) {
        int red = accent >>> 16 & 255, green = accent >>> 8 & 255, blue = accent & 255;
        int peak = Math.max(red, Math.max(green, blue));
        return 0xff000000 | paper(red, peak) << 16 | paper(green, peak) << 8 | paper(blue, peak);
    }

    private static int tint(int channel, int peak, int light) {
        return Math.max(14, light - (peak - channel) * 7 / 4);
    }

    private static int paper(int channel, int peak) {
        return 255 - Math.min(31, peak - channel);
    }

    private DockPalette() {}
}
