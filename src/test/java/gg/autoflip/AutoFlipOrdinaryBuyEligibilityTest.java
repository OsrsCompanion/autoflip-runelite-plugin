package gg.autoflip;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoFlipOrdinaryBuyEligibilityTest
{
    @Test
    public void highlightsOnlySelectedBuyWithDifferentPrice()
    {
        assertTrue(AutoFlipOrdinaryBuyEligibility.isVisible(562, true, 104L,
            "Grand Exchange: Set up offer", true, false, false, 106));
        assertFalse(AutoFlipOrdinaryBuyEligibility.isVisible(562, true, 104L,
            "Grand Exchange: Set up offer", true, false, false, 104));
        assertFalse(AutoFlipOrdinaryBuyEligibility.isVisible(0, true, 104L,
            "Grand Exchange: Set up offer", true, false, false, 106));
        assertFalse(AutoFlipOrdinaryBuyEligibility.isVisible(562, true, 104L,
            "Grand Exchange: Set up offer", false, true, false, 106));
    }

    @Test
    public void keepsPricePromptActionsVisible()
    {
        assertTrue(AutoFlipOrdinaryBuyEligibility.isVisible(562, true, 104L,
            "Grand Exchange: Set up offer", true, false, true, 104));
    }

    @Test
    public void autofillOnlyTriggersForBlankPromptOncePerItem()
    {
        assertTrue(AutoFlipOrdinaryBuyEligibility.shouldAutoFillSuggestedPrice(
            562, 0, true, 0, 104L, false));
        assertFalse(AutoFlipOrdinaryBuyEligibility.shouldAutoFillSuggestedPrice(
            562, 562, true, 0, 104L, false));
        assertFalse(AutoFlipOrdinaryBuyEligibility.shouldAutoFillSuggestedPrice(
            562, 0, true, 104, 104L, false));
        assertFalse(AutoFlipOrdinaryBuyEligibility.shouldAutoFillSuggestedPrice(
            0, 0, true, 0, 104L, false));
        assertFalse(AutoFlipOrdinaryBuyEligibility.shouldAutoFillSuggestedPrice(
            562, 0, true, 0, 104L, true));
    }
}
