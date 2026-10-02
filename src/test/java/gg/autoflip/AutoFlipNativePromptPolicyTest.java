package gg.autoflip;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoFlipNativePromptPolicyTest
{
    @Test
    public void priceInjectionRequiresCurrentPricePrompt()
    {
        assertTrue(AutoFlipPlugin.isAutoFlipNativePricePromptText("Set a price for each item:"));
        assertFalse(AutoFlipPlugin.isAutoFlipNativePricePromptText("What would you like to buy?"));
        assertFalse(AutoFlipPlugin.isAutoFlipNativePricePromptText(""));
    }
}
