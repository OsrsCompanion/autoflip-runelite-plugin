package gg.autoflip;

final class AutoFlipSellPriceRetryPolicy
{
    static final long TRANSPORT_RETRY_DELAY_MS = 30_000L;

    private AutoFlipSellPriceRetryPolicy()
    {
    }

    static boolean isTransportFailure(String responseBody)
    {
        return responseBody == null || responseBody.trim().isEmpty();
    }

    static boolean isRetryDue(Long retryAtMs, long nowMs)
    {
        return retryAtMs != null && retryAtMs <= nowMs;
    }
}
