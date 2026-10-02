package gg.autoflip;

final class AutoFlipOrdinaryBuyEligibility
{
    private AutoFlipOrdinaryBuyEligibility()
    {
    }

    static boolean shouldAutoFillSuggestedPrice(int itemId, int autoFillItemId, boolean pricePromptOpen,
        int currentPriceGp, long cachedBuyPriceGp, boolean manualChoiceMade)
    {
        return itemId > 0
            && cachedBuyPriceGp > 0L
            && pricePromptOpen
            && !manualChoiceMade
            && currentPriceGp <= 0
            && autoFillItemId != itemId;
    }

    static boolean isVisible(int itemId, boolean apiReady, long cachedBuyPriceGp,
        String geHeaderText, boolean buyOfferVisible, boolean sellOfferVisible,
        boolean pricePromptOpen, int currentPriceGp)
    {
        return itemId > 0
            && apiReady
            && cachedBuyPriceGp > 0L
            && geHeaderText != null
            && geHeaderText.startsWith("Grand Exchange: Set up offer")
            && buyOfferVisible
            && !sellOfferVisible
            && (pricePromptOpen || currentPriceGp <= 0 || currentPriceGp != cachedBuyPriceGp);
    }
}
