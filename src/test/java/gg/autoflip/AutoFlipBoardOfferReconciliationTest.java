package gg.autoflip;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AutoFlipBoardOfferReconciliationTest
{
    @Test
    public void inventorySellMovesDisplacedRecommendationToOfferedItemsPlannedSlot()
    {
        assertEquals(2, AutoFlipPlugin.resolveAutoFlipDisplacedRecommendationDestination(1, 2, false));
    }

    @Test
    public void explicitNativeSlotSellDoesNotReorderRecommendations()
    {
        assertEquals(-1, AutoFlipPlugin.resolveAutoFlipDisplacedRecommendationDestination(1, 2, true));
    }

    @Test
    public void matchingPhysicalAndPlannedSlotNeedsNoDisplacement()
    {
        assertEquals(-1, AutoFlipPlugin.resolveAutoFlipDisplacedRecommendationDestination(2, 2, false));
    }
}
