package gg.autoflip;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoFlipInventoryLifecyclePolicyTest
{
    @Test
    public void soldCardsSortAfterActiveHolds()
    {
        assertEquals(0, AutoFlipInventoryLifecyclePolicy.displayRank("HOLD"));
        assertEquals(1, AutoFlipInventoryLifecyclePolicy.displayRank("SOLD"));
    }

    @Test
    public void soldCardsAreSessionOnly()
    {
        assertTrue(AutoFlipInventoryLifecyclePolicy.retainAfterRestart("HOLD"));
        assertFalse(AutoFlipInventoryLifecyclePolicy.retainAfterRestart("SOLD"));
    }
}
