package dev.stillframe;

public final class DialogLayoutTest {
    public static void main(String[] args) {
        check(DialogLayout.width(960) == 560, "wide screens center a moderate dialog");
        check(DialogLayout.width(360) == 312, "narrow screens retain outside breathing room");
        check(DialogLayout.width(42) > 0, "a temporarily tiny display still has a valid width");
        check(!DialogLayout.stacked(512, 10, new int[]{138, 96, 156}),
                "three typical TV actions fit horizontally");
        check(DialogLayout.stacked(270, 10, new int[]{138, 96, 156}),
                "stack long actions before labels can clip");
        check(DialogLayout.stacked(240, 10, new int[]{285}),
                "one oversized action wraps to the available width");
        check(!DialogLayout.stacked(250, 10, new int[]{120, 120}),
                "an exact fit stays in a row");
        System.out.println("Dialog sizing checks passed");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
