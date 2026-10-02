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

        if (verboseLogging)
        {
            System.out.println(
                "AUTOFLIP_ORDINARY_SELL_VISIBILITY"
                    + " item_id=" + itemId
                    + " autoFlipInventoryItem=" + autoFlipInventoryItem
                    + " apiPriceReady=" + apiPriceReady
                    + " cachedSellPriceGp=" + cachedSellPriceGp
                    + " geHeaderText=" + String.valueOf(geHeaderText)
                    + " pricePromptOpen=" + pricePromptOpen
                    + " visible=" + visible
            );
        }

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
