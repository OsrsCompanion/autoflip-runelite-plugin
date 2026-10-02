package gg.autoflip;

import java.awt.Rectangle;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class AutoFlipCardLayoutTest
{
    @Test
    public void sellTimingPanelStaysLeftOfSellBag()
    {
        Rectangle bag = new Rectangle(70, 48, 30, 35);
        Rectangle panel = AutoFlipCardLayout.compactTimingPanel(0, 0, 112, 110, bag, true);

        assertEquals(new Rectangle(2, 67, 64, 20), panel);
        assertFalse(panel.intersects(bag));
    }

    @Test
    public void buyTimingPanelStaysRightOfBuyBag()
    {
        Rectangle bag = new Rectangle(15, 48, 30, 35);
        Rectangle panel = AutoFlipCardLayout.compactTimingPanel(0, 0, 112, 110, bag, false);

        assertEquals(new Rectangle(49, 67, 61, 20), panel);
        assertFalse(panel.intersects(bag));
    }

    @Test
    public void raisesItemAndQuantitySevenPixels()
    {
        assertEquals(20, AutoFlipCardLayout.raisedY(27));
        assertEquals(45, AutoFlipCardLayout.raisedY(52));
    }
}
