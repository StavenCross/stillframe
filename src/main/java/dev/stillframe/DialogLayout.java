package dev.stillframe;

final class DialogLayout {
    private DialogLayout() {}

    static int width(int screenWidth) {
        return Math.max(1, Math.min(560, screenWidth - 48));
    }

    static boolean stacked(int availableWidth, int gap, int[] actionWidths) {
        long required = (long) Math.max(0, actionWidths.length - 1) * gap;
        for (int width : actionWidths) required += width;
        return required > availableWidth;
    }
}
