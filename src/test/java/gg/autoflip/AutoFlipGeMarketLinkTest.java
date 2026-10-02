package gg.autoflip;

import java.awt.Rectangle;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoFlipGeMarketLinkTest
{
    @Test
    public void buildsEnvironmentAwareItemSlugUrl()
    {
        assertEquals(
            "https://www.autoflip.gg/osrs/market/item/steel-bar",
            AutoFlipGeMarketLink.buildUrl("https://www.autoflip.gg/", 2353, "Steel bar")
        );
        assertEquals(
            "https://autoflip.gg/osrs/market/item/cosmic-rune",
            AutoFlipGeMarketLink.buildUrl("https://autoflip.gg", 564, "Cosmic rune")
        );
    }

    @Test
    public void fallsBackToItemIdWhenNameIsUnavailable()
    {
        assertEquals(
            "https://www.autoflip.gg/osrs/market/item/2353",
            AutoFlipGeMarketLink.buildUrl("https://www.autoflip.gg", 2353, "")
        );
    }

    @Test
    public void anchorsIconInsideEachNativeSlot()
    {
        assertEquals(
            new Rectangle(180, 82, 16, 16),
            AutoFlipGeMarketLink.iconBounds(new Rectangle(100, 40, 101, 110), 0, 0, 101)
        );
    }

    @Test
    public void splitsLinksBetweenActiveOffersAndUnplacedRecommendations()
    {
        assertTrue(AutoFlipGeMarketLink.shouldShowOccupiedOfferLink(false));
        assertFalse(AutoFlipGeMarketLink.shouldShowOccupiedOfferLink(true));
        assertTrue(AutoFlipGeMarketLink.shouldShowRecommendationLink(true, 562));
        assertFalse(AutoFlipGeMarketLink.shouldShowRecommendationLink(true, 0));
        assertFalse(AutoFlipGeMarketLink.shouldShowRecommendationLink(false, 562));
    }
}
