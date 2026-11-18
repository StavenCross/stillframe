package dev.stillframe;

final class GlassPixels {
    static int[] blur(int[] pixels, int width, int height, int radius) {
        int[] horizontal = new int[pixels.length], output = new int[pixels.length];
        pass(pixels, horizontal, width, height, 1, width, radius);
        pass(horizontal, output, height, width, width, 1, radius);
        return output;
    }

    private static void pass(int[] input, int[] output, int length, int lines,
                             int step, int stride, int radius) {
        int count = 2 * radius + 1;
        for (int line = 0; line < lines; line++) {
            int start = line * stride, red = 0, green = 0, blue = 0;
            for (int offset = -radius; offset <= radius; offset++) {
                int p = input[start + clamp(offset, length) * step];
                red += p >>> 16 & 255;
                green += p >>> 8 & 255;
                blue += p & 255;
            }
            for (int index = 0; index < length; index++) {
                output[start + index * step] =
                        0xff000000 | ((red / count) << 16) | ((green / count) << 8) | blue / count;
                int remove = input[start + clamp(index - radius, length) * step];
                int add = input[start + clamp(index + radius + 1, length) * step];
                red += (add >>> 16 & 255) - (remove >>> 16 & 255);
                green += (add >>> 8 & 255) - (remove >>> 8 & 255);
                blue += (add & 255) - (remove & 255);
            }
        }
    }

    private static int clamp(int n, int length) { return Math.max(0, Math.min(n, length - 1)); }

    private GlassPixels() {}
}
