package gg.autoflip;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoFlipOrdinarySellEligibilityTest
{
    @Test
    public void allowsCachedOrdinarySellWithoutAutoFlipOverlay()
    {
        assertTrue(
            AutoFlipOrdinarySellEligibility.isVisible(
                564,
                false,
                true,
                123L,
                "Grand Exchange: Set up offer",
                true,
                false
            )
        );
    }

    @Test
    public void rejectsMissingPriceOrClosedPrompt()
    {
        assertTrue(
            AutoFlipOrdinarySellEligibility.isVisible(
                564,
                true,
                true,
                123L,
                "Grand Exchange: Set up offer",
                true,
                false
            )
        );
        assertFalse(
            AutoFlipOrdinarySellEligibility.isVisible(
                564,
                false,
                false,
                0L,
                "Grand Exchange: Set up offer",
                true,
                false
            )
        );
        assertTrue(
            AutoFlipOrdinarySellEligibility.isVisible(
                564,
                false,
                false,
                33L,
                "Grand Exchange: Set up offer",
                true,
                false
            )
        );
        assertFalse(
            AutoFlipOrdinarySellEligibility.isVisible(
                564,
                false,
                true,
                123L,
                "Grand Exchange",
                false,
                false
            )
        );
    }

    @Test
    public void rendersOrdinaryPriceMarkerWhileAutoFlipOverlayIsClosed()
    {
        assertTrue(
            AutoFlipOrdinarySellEligibility.shouldRenderSetupFlow(
                false,
                false,
                true
            )
        );
    }
}
