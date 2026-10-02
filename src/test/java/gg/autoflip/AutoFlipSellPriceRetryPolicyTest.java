package gg.autoflip;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AutoFlipSellPriceRetryPolicyTest
{
    @Test
    public void retriesOnlyEmptyTransportResponses()
    {
        assertTrue(AutoFlipSellPriceRetryPolicy.isTransportFailure(null));
        assertTrue(AutoFlipSellPriceRetryPolicy.isTransportFailure("  "));
        assertFalse(AutoFlipSellPriceRetryPolicy.isTransportFailure("{\"item\":{}}"));
    }

    @Test
    public void respectsRetryDeadline()
    {
        assertFalse(AutoFlipSellPriceRetryPolicy.isRetryDue(null, 1000L));
        assertFalse(AutoFlipSellPriceRetryPolicy.isRetryDue(1001L, 1000L));
        assertTrue(AutoFlipSellPriceRetryPolicy.isRetryDue(1000L, 1000L));
    }
}
