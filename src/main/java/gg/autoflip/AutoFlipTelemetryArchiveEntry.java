package gg.autoflip;

public final class AutoFlipTelemetryArchiveEntry
{
    private final String fileName;
    private final long modifiedAtMs;
    private final String batchId;
    private final int eventCount;
    private final String payloadJson;

    public AutoFlipTelemetryArchiveEntry(String fileName, long modifiedAtMs, String batchId, int eventCount, String payloadJson)
    {
        this.fileName = fileName;
        this.modifiedAtMs = modifiedAtMs;
        this.batchId = batchId;
        this.eventCount = eventCount;
        this.payloadJson = payloadJson;
    }

    public String getFileName()
    {
        return fileName;
    }

    public long getModifiedAtMs()
    {
        return modifiedAtMs;
    }

    public String getBatchId()
    {
        return batchId;
    }

    public int getEventCount()
    {
        return eventCount;
    }

    public String getPayloadJson()
    {
        return payloadJson;
    }

    public String getDisplayLabel()
    {
        String label = batchId == null || batchId.isEmpty() ? fileName : batchId;
        return label + " (" + eventCount + " events)";
    }
}
