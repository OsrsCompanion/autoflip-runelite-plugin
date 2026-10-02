package gg.autoflip;

final class AutoFlipScrollPosition
{
    private static final int BOTTOM_TOLERANCE_PX = 2;

    private AutoFlipScrollPosition()
    {
    }

    static boolean isAtBottom(int value, int extent, int maximum)
    {
        return value + extent >= maximum - BOTTOM_TOLERANCE_PX;
    }

    static int restoredValue(
        int oldValue,
        boolean wasAtBottom,
        int newExtent,
        int newMaximum)
    {
        int largestValue = Math.max(0, newMaximum - newExtent);
        return wasAtBottom
            ? largestValue
            : Math.max(0, Math.min(oldValue, largestValue));
    }
}
