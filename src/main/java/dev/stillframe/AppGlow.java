package dev.stillframe;

final class AppGlow {
    static int hue(int color) {
        int red = light(color >>> 16 & 255);
        int green = light(color >>> 8 & 255);
        int blue = light(color & 255);
        return 0xff000000 | red << 16 | green << 8 | blue;
    }

    private static int light(int channel) { return channel + (255 - channel) / 4; }

    static int alpha(int color, int opacity) { return (color & 0xffffff) | opacity << 24; }

    private AppGlow() {}
}
