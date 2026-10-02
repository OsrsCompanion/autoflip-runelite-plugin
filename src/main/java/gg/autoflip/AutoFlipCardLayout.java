package gg.autoflip;

import java.awt.Rectangle;

final class AutoFlipCardLayout
{
    private static final int PANEL_TOP = 67;
    private static final int PANEL_HEIGHT = 20;
    private static final int PANEL_EDGE_INSET = 2;
    private static final int BAG_GAP = 4;

    private AutoFlipCardLayout()
    {
    }

    static Rectangle compactTimingPanel(
        int cardX,
        int cardY,
        int cardWidth,
        int cardHeight,
        Rectangle nativeBagBounds,
        boolean sellPlan)
    {
        int y = cardY + Math.min(PANEL_TOP, Math.max(0, cardHeight - 1));
        int height = Math.max(1, Math.min(PANEL_HEIGHT, cardY + cardHeight - y));

        if (nativeBagBounds == null)
        {
            return new Rectangle(cardX + PANEL_EDGE_INSET, y, Math.max(1, cardWidth - 4), height);
        }

        if (sellPlan)
        {
            int x = cardX + PANEL_EDGE_INSET;
            int right = Math.max(x + 1, nativeBagBounds.x - BAG_GAP);
            return new Rectangle(x, y, Math.max(1, right - x), height);
        }

        int x = Math.min(cardX + cardWidth - 1, nativeBagBounds.x + nativeBagBounds.width + BAG_GAP);
        int right = Math.max(x + 1, cardX + cardWidth - PANEL_EDGE_INSET);
        return new Rectangle(x, y, Math.max(1, right - x), height);
    }

    static int raisedY(int configuredY)
    {
        return Math.max(0, configuredY - 7);
    }
}
