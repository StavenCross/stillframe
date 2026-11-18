package dev.stillframe;

final class ArtworkCrop {
    static int[] decodeSize(int width, int height, int cardWidth, int cardHeight) {
        double cover = Math.max((double) cardWidth / width, (double) cardHeight / height);
        // Keep intermediate decodes bounded for panorama and banner artwork.
        double limit = Math.min(3.0 * cardWidth / width, 3.0 * cardHeight / height);
        double scale = Math.min(1.0, Math.min(cover, limit));
        return new int[]{Math.max(1, (int) Math.round(width * scale)),
                Math.max(1, (int) Math.round(height * scale))};
    }

    static int offset(int extent, int visible, float subject) {
        return Math.max(0, Math.min(extent - visible, Math.round(extent * subject - visible * .5f)));
    }

    static int wordmarkWidth(int visible, int extent) {
        return Math.min(extent, Math.round(visible * 1.09f));
    }

    static float subjectX(int[] pixels, int width, int height, int visible) {
        if (visible >= width || visible < 1) return .5f;
        int[] detail = new int[width];
        // Favor detail in the body of the image over corner badges and borders.
        for (int y = height / 6; y < height - height / 8; y++) {
            for (int x = 1; x < width; x++) {
                int index = y * width + x;
                detail[x] += Math.abs(luma(pixels[index]) - luma(pixels[index - 1]));
            }
        }
        int total = 0;
        for (int column : detail) total += column;
        int centered = (width - visible) / 2;
        int best = centered;
        double bestScore = Double.NEGATIVE_INFINITY;
        int running = 0;
        for (int x = 0; x < visible; x++) running += detail[x];
        for (int left = 0; left <= width - visible; left++) {
            double score = running - total * .52 * Math.abs(left - centered) / width;
            if (score > bestScore) {
                best = left;
                bestScore = score;
            }
            if (left < width - visible) running += detail[left + visible] - detail[left];
        }
        return total == 0 ? .5f : (best + visible * .5f) / width;
    }

    static float letteringX(int[] pixels, int width, int height, int visible) {
        int firstRow = height / 5, rows = height * 3 / 4 - firstRow;
        int band = Math.max(1, height / 6);
        int[][] prefixes = new int[rows][width + 1];
        for (int y = 0; y < rows; y++) {
            int row = (firstRow + y) * width;
            for (int x = 1; x < width - 1; x++) {
                int stroke = Math.abs(2 * luma(pixels[row + x])
                        - luma(pixels[row + x - 1]) - luma(pixels[row + x + 1]));
                prefixes[y][x + 1] = prefixes[y][x] + Math.max(0, stroke - 10);
            }
            prefixes[y][width] = prefixes[y][width - 1];
        }

        // Lettering often produces a band of sharp strokes amid smoother rows.
        int center = (width - visible) / 2;
        double bestScore = 0;
        double[] scores = new double[width - visible + 1];
        for (int left = 0; left <= width - visible; left++) {
            int total = 0, rolling = 0, peak = 0;
            for (int y = 0; y < rows; y++) {
                int strength = prefixes[y][left + visible] - prefixes[y][left];
                total += strength;
                rolling += strength;
                if (y >= band) rolling -= prefixes[y - band][left + visible] - prefixes[y - band][left];
                peak = Math.max(peak, rolling);
            }
            scores[left] = 2 * peak - (double) total * band / rows;
            bestScore = Math.max(bestScore, scores[left]);
        }
        if (bestScore == 0) return Float.NaN;
        // Break near ties toward the middle to keep more of the composition.
        int best = -1;
        for (int left = 0; left < scores.length; left++) {
            if (scores[left] >= bestScore * .985
                    && (best < 0 || Math.abs(left - center) < Math.abs(best - center))) best = left;
        }
        return (best + visible * .5f) / width;
    }

    private static int luma(int color) {
        return ((color >>> 16 & 255) * 2 + (color >>> 8 & 255) * 5 + (color & 255)) / 8;
    }

    private ArtworkCrop() {}
}
