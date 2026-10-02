package gg.autoflip;

final class AutoFlipInventoryLifecyclePolicy
{
    private AutoFlipInventoryLifecyclePolicy()
    {
    }

    static boolean isSold(String status)
    {
        return status != null && "SOLD".equalsIgnoreCase(status.trim());
    }

    static int displayRank(String status)
    {
        return isSold(status) ? 1 : 0;
    }

    static boolean retainAfterRestart(String status)
    {
        return !isSold(status);
    }
}
