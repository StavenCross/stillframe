package dev.stillframe;

import android.view.View;

public final class DrawerFocusTest {
    public static void main(String[] args) {
        check(0, Drawers.gridTarget(0, 8, 6, View.FOCUS_LEFT), "left edge stays in row");
        check(5, Drawers.gridTarget(5, 8, 6, View.FOCUS_RIGHT), "right edge stays in row");
        check(7, Drawers.gridTarget(5, 8, 6, View.FOCUS_DOWN), "down reaches short row");
        check(1, Drawers.gridTarget(7, 8, 6, View.FOCUS_UP), "up returns to same column");
        check(7, Drawers.gridTarget(7, 8, 6, View.FOCUS_DOWN), "bottom retains focus");
        check(-1, Drawers.gridTarget(0, 8, 6, View.FOCUS_UP), "first row leads to search");
        check(5, Drawers.gridTarget(4, 6, 6, View.FOCUS_RIGHT), "last pin reaches add tile");
        check(-1, Drawers.gridTarget(0, 0, 6, View.FOCUS_DOWN), "empty grid has no target");
        check(5, DrawerViews.gridColumns(960), "target TV uses five columns");
        check(5, DrawerViews.gridColumns(1920), "larger TV retains five columns");
        check(3, DrawerViews.gridColumns(540), "compact layouts keep readable columns");
        int targetWidth = 960 - 2 * (24 + 22);
        int defaultRowWidth = 10 * (DrawerViews.pinCellWidth(targetWidth) + DrawerViews.PIN_GAP);
        if (defaultRowWidth > targetWidth) throw new AssertionError("default pins and Add must fit");
        check(60, DrawerViews.pinCellWidth(504), "narrow layouts preserve a minimum pin width");
        check(224, AppDrawer.viewportHeight(2, 540), "two rows have no trailing preview");
        check(326, AppDrawer.viewportHeight(4, 540), "viewport aligns three whole rows");
        check(102, AppDrawer.revealOffset(306, 408, 0, 306), "fourth row moves fully onscreen");
        check(0, AppDrawer.revealOffset(0, 102, 102, 306), "first row returns fully onscreen");
        check(102, AppDrawer.revealOffset(204, 306, 102, 306), "already visible rows do not scroll");
        check(3, AppDrawer.resultIndex(3, 8), "down from search restores the previous app");
        check(1, AppDrawer.resultIndex(3, 2), "return clamps to available search results");
        check(-1, AppDrawer.resultIndex(0, 0), "empty search keeps focus in the search field");
        int bright = GlassStyle.slate(0xfffaffff);
        if ((bright >>> 16 & 255) > 90 || (bright >>> 8 & 255) > 90 || (bright & 255) > 90)
            throw new AssertionError("Bright accents must still yield dark glass.");
        int warm = GlassStyle.slate(0xffff2400), cool = GlassStyle.slate(0xff0044ff);
        if ((warm >>> 16 & 255) <= (cool >>> 16 & 255) || (cool & 255) <= (warm & 255))
            throw new AssertionError("The sheet must retain the wallpaper hue.");
        check(16, DockPinMenu.left(40, 80, 244, 960, 16), "first pin keeps safe left margin");
        check(716, DockPinMenu.left(870, 72, 228, 960, 16), "last pin keeps safe right margin");
        check(366, DockPinMenu.left(440, 80, 228, 960, 16), "middle pin centers its menu");
        check(224, DockPinMenu.top(140, 76, 90, 540, 16, 8), "menu prefers below the pin");
        check(372, DockPinMenu.top(470, 40, 90, 540, 16, 8), "bottom pin opens above");
        check(16, DockPinMenu.top(110, 44, 100, 190, 16, 8), "tight frame clamps menu onscreen");
        DockPinMenu.Activation activation = new DockPinMenu.Activation(300);
        if (activation.select(false, 0, 120, false))
            throw new AssertionError("Releasing the opening hold must not remove a pin.");
        activation.select(true, 2, 120, false);
        if (activation.select(false, 0, 120, false))
            throw new AssertionError("The opening key repeat must not arm the action.");
        activation.select(true, 0, 420, false);
        activation.select(true, 1, 420, false);
        if (!activation.select(false, 0, 420, false))
            throw new AssertionError("A fresh Select press can remove the pin once.");
        if (activation.select(false, 0, 420, false))
            throw new AssertionError("A Select release cannot remove twice.");
        activation.select(true, 0, 500, false);
        if (activation.select(false, 0, 500, true))
            throw new AssertionError("A canceled Select press must not remove a pin.");
        System.out.println("Drawer focus checks passed");
    }

    private static void check(int expected, int actual, String name) {
        if (expected != actual) throw new AssertionError(name + ": " + actual + " != " + expected);
    }
}
