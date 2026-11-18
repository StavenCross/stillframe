package dev.stillframe;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.ACTION_UP;
import static android.view.KeyEvent.KEYCODE_DPAD_CENTER;
import static android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
import static android.view.KeyEvent.KEYCODE_ENTER;

final class DrawerActivationTest {
    public static void main(String[] args) {
        DrawerPress press = new DrawerPress();
        expect(DrawerPress.Decision.PASS, press.select(KEYCODE_DPAD_CENTER, ACTION_DOWN, 0, 10, false));
        expect(DrawerPress.Decision.OPEN, press.select(KEYCODE_DPAD_CENTER, ACTION_DOWN, 0, 20, true));
        expect(DrawerPress.Decision.PASS, press.select(KEYCODE_DPAD_RIGHT, ACTION_DOWN, 0, 21, false));
        expect(DrawerPress.Decision.CONSUME, press.select(KEYCODE_DPAD_CENTER, ACTION_DOWN, 1, 20, false));
        expect(DrawerPress.Decision.CONSUME, press.select(KEYCODE_DPAD_CENTER, ACTION_UP, 0, 20, false));
        expect(DrawerPress.Decision.PASS, press.select(KEYCODE_DPAD_CENTER, ACTION_DOWN, 0, 30, false));
        expect(DrawerPress.Decision.OPEN, press.select(KEYCODE_ENTER, ACTION_DOWN, 0, 40, true));
        expect(DrawerPress.Decision.OPEN, press.select(KEYCODE_ENTER, ACTION_DOWN, 0, 50, true));
        expect(DrawerPress.Decision.CONSUME, press.select(KEYCODE_ENTER, ACTION_UP, 0, 50, false));
    }

    private static void expect(DrawerPress.Decision expected, DrawerPress.Decision actual) {
        if (actual != expected) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
