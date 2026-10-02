package gg.autoflip;

import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoFlipBuyCacheScopeTest
{
    @Test
    public void inventoryOnlyItemIsNotAuthorizedForBuyCache()
    {
        Set<Integer> authorizedBuyItems = new HashSet<>();

        assertFalse(AutoFlipBuyCacheScope.isAuthorized(2132, authorizedBuyItems));
    }

    @Test
    public void rankedToBuyOrSelectedItemIsAuthorizedAfterExplicitBuyRequest()
    {
        Set<Integer> authorizedBuyItems = new HashSet<>();
        authorizedBuyItems.add(562);

        assertTrue(AutoFlipBuyCacheScope.isAuthorized(562, authorizedBuyItems));
    }
}
