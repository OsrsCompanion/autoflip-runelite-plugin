package gg.autoflip;

final class AutoFlipOrdinarySellEligibility
{
    private AutoFlipOrdinarySellEligibility()
    {
    }

    static boolean isVisible(
        int itemId,
        boolean autoFlipInventoryItem,
        boolean apiPriceReady,
        long cachedSellPriceGp,
        String geHeaderText,
        boolean pricePromptOpen,
        boolean verboseLogging)
    {
        boolean visible = itemId > 0
            && cachedSellPriceGp > 0L
            && geHeaderText != null
            && geHeaderText.startsWith("Grand Exchange")
            && (pricePromptOpen || geHeaderText.startsWith("Grand Exchange: Set up offer"));

        return visible;
    }

    static boolean shouldRenderSetupFlow(
        boolean autoFlipOverlayActive,
        boolean guidedSetup,
        boolean ordinarySellPrice)
    {
        return (autoFlipOverlayActive && guidedSetup) || ordinarySellPrice;
    }
}
