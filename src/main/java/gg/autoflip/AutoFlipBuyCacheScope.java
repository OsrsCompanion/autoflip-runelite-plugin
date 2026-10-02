package gg.autoflip;

import java.util.Set;

final class AutoFlipBuyCacheScope
{
    private AutoFlipBuyCacheScope()
    {
    }

    static boolean isAuthorized(int itemId, Set<Integer> explicitlyRequestedBuyItemIds)
    {
        return itemId > 0
            && explicitlyRequestedBuyItemIds != null
            && explicitlyRequestedBuyItemIds.contains(itemId);
    }
}
