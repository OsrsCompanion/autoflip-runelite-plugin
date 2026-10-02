package gg.autoflip;

import java.awt.Rectangle;
import java.text.Normalizer;
import java.util.Locale;

final class AutoFlipGeMarketLink
{
    private static final int ICON_SIZE = 16;
    private static final int ICON_RIGHT_INSET = 5;
    private static final int ICON_TOP_OFFSET = 42;

    private AutoFlipGeMarketLink()
    {
    }

    static String buildUrl(String baseUrl, int itemId, String itemName)
    {
        String slug = toSlug(itemName);
        if (slug.isEmpty() && itemId > 0)
        {
            slug = String.valueOf(itemId);
        }

        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/"))
        {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/osrs/market/item/" + slug;
    }

    static Rectangle iconBounds(Rectangle slotBounds, int cardOffsetX, int cardOffsetY, int cardWidth)
    {
        if (slotBounds == null)
        {
            return null;
        }

        return new Rectangle(
            slotBounds.x + cardOffsetX + cardWidth - ICON_SIZE - ICON_RIGHT_INSET,
            slotBounds.y + cardOffsetY + ICON_TOP_OFFSET,
            ICON_SIZE,
            ICON_SIZE
        );
    }

    static boolean shouldShowOccupiedOfferLink(boolean optimizationActive)
    {
        return !optimizationActive;
    }

    static boolean shouldShowRecommendationLink(boolean optimizationActive, int itemId)
    {
        return optimizationActive && itemId > 0;
    }

    private static String toSlug(String itemName)
    {
        if (itemName == null)
        {
            return "";
        }

        String cleaned = Normalizer.normalize(itemName, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replace("&", " and ")
            .replace("'", "-")
            .replace("\u2019", "-")
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "")
            .replaceAll("-{2,}", "-");

        return cleaned == null ? "" : cleaned.trim();
    }
}
