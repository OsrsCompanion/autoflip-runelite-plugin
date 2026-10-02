package gg.autoflip;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoFlipScrollPositionTest
{
    @Test
    public void preservesMiddleScrollPositionAcrossRefresh()
    {
        assertFalse(AutoFlipScrollPosition.isAtBottom(240, 100, 1000));
        assertEquals(
            240,
            AutoFlipScrollPosition.restoredValue(240, false, 100, 1200)
        );
    }

    @Test
    public void followsBottomOnlyWhenViewerWasAlreadyAtBottom()
    {
        assertTrue(AutoFlipScrollPosition.isAtBottom(900, 100, 1000));
        assertEquals(
            1100,
            AutoFlipScrollPosition.restoredValue(900, true, 100, 1200)
        );
    }
}
