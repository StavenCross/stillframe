package dev.stillframe;

import android.view.KeyEvent;

/** Consume the whole Select press that opens All Apps, including its release. */
final class DrawerPress {
    enum Decision { PASS, OPEN, CONSUME }
    private long capturedTime = -1;

    boolean dispatch(KeyEvent event, Drawers drawers, HomeView home, FocusTrace timing) {
        if (!isSelect(event.getKeyCode())) return false;
        Decision decision = select(event.getKeyCode(), event.getAction(), event.getRepeatCount(),
                event.getDownTime(), !drawers.active() && home.appsFocused());
        if (decision == Decision.OPEN) {
            long started = timing.begin();
            home.openApps();
            timing.drawerSubmitted(home, started);
        }
        return decision != Decision.PASS;
    }

    Decision select(int key, int action, int repeat, long downTime, boolean canOpen) {
        if (!isSelect(key)) return Decision.PASS;
        if (downTime == capturedTime) {
            if (action == KeyEvent.ACTION_UP) capturedTime = -1;
            return Decision.CONSUME;
        }
        if (action != KeyEvent.ACTION_DOWN || repeat != 0) return Decision.PASS;
        capturedTime = canOpen ? downTime : -1;
        return canOpen ? Decision.OPEN : Decision.PASS;
    }

    private static boolean isSelect(int key) {
        return key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }
}
