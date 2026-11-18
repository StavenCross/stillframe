package dev.stillframe;

import java.util.Arrays;

public final class GlassPixelsTest {
    public static void main(String[] args) {
        int[] flat = new int[49];
        Arrays.fill(flat, 0xff1e2b40);
        if (!Arrays.equals(flat, GlassPixels.blur(flat, 7, 7, 2)))
            throw new AssertionError("Even wallpaper colors must stay even.");
        int[] point = new int[49];
        Arrays.fill(point, 0xff000000);
        point[24] = 0xffffffff;
        int[] spread = GlassPixels.blur(point, 7, 7, 2);
        if ((spread[24] & 255) >= 255 || (spread[23] & 255) == 0 || spread[24] != spread[25])
            throw new AssertionError("Blur must spread light evenly without preserving a hotspot.");
        if (point[24] != 0xffffffff) throw new AssertionError("Original pixels must not be changed.");
    }
}
