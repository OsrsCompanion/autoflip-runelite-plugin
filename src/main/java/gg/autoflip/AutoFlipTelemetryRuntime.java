package gg.autoflip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AutoFlipTelemetryRuntime
{
    private static final Logger log = LoggerFactory.getLogger(AutoFlipTelemetryRuntime.class);
    private static final String TELEMETRY_EVENTS_ENDPOINT = "/api/telemetry/plugin-events";
    private static final String TELEMETRY_CONTRACT_ENDPOINT = "/api/telemetry/contract";
    private static final String TELEMETRY_EVENT_SCHEMA = "autoflip.plugin_event.v1";
    private static final String TELEMETRY_BATCH_SCHEMA = "autoflip.plugin_event_batch.v1";
    private static final String TELEMETRY_SOURCE = "runelite_plugin";
    private static final String TELEMETRY_CLIENT = "runelite";
    private static final String TELEMETRY_GAME = "osrs";
    private static final String TELEMETRY_PLUGIN_VERSION = "0.1.0";
    private static final int MAX_SEND_BATCH = 250;

    private final Path runtimeDir;
    private final Path outboxPath;
    private final Path ackedEventIdsPath;
    private final Path rejectedEventsPath;
    private final Path telemetryArchiveDir;
    private final Path senderLogPath;
    private final String piBaseUrl;
    private final String telemetrySessionId;
    private final String localSalt;
    private final String accountKey;
    private final IntSupplier currentWorldSupplier;
    private final BooleanSupplier verboseLoggingSupplier;
    private final OkHttpClient okHttpClient;
    private final Set<String> ackedEventIds = new HashSet<>();
    private final ExecutorService senderExecutor = Executors.newSingleThreadExecutor();

    private long telemetryLocalSequence = 0L;
    private volatile boolean telemetryContractChecked = false;
    private volatile boolean telemetryGzipUploadSupported = false;
    private volatile boolean sendInProgress = false;

    AutoFlipTelemetryRuntime(
        Path runtimeDir,
        String piBaseUrl,
        String telemetrySessionId,
        String localSalt,
        String accountKey,
        IntSupplier currentWorldSupplier,
        BooleanSupplier verboseLoggingSupplier,
        OkHttpClient okHttpClient
    )
    {
        this.runtimeDir = runtimeDir;
        this.outboxPath = runtimeDir.resolve("outbox.jsonl");
        this.ackedEventIdsPath = runtimeDir.resolve("acked_event_ids.txt");
        this.rejectedEventsPath = runtimeDir.resolve("rejected_event_ids.jsonl");
        this.telemetryArchiveDir = runtimeDir.resolve("telemetry_upload_batches");
        this.senderLogPath = runtimeDir.resolve("sender_log.jsonl");
        this.piBaseUrl = cleanBaseUrl(piBaseUrl);
        this.telemetrySessionId = telemetrySessionId == null || telemetrySessionId.isEmpty()
            ? "session_" + java.util.UUID.randomUUID().toString().replace("-", "")
            : telemetrySessionId;
        this.localSalt = localSalt == null ? "" : localSalt;
        this.accountKey = accountKey == null ? "unknown_account" : accountKey;
        this.currentWorldSupplier = currentWorldSupplier == null ? () -> 0 : currentWorldSupplier;
        this.verboseLoggingSupplier = verboseLoggingSupplier == null ? () -> false : verboseLoggingSupplier;
        this.okHttpClient = okHttpClient == null ? new OkHttpClient() : okHttpClient;
    }

    void ensureRuntimeDir()
    {
        try
        {
            Files.createDirectories(runtimeDir);
        }
        catch (IOException e)
        {
            log.warn("Unable to create AutoFlip telemetry runtime directory", e);
        }
    }

    void loadAckedEventIds()
    {
        ackedEventIds.clear();
        loadIdsInto(ackedEventIdsPath, ackedEventIds);
    }

    void sendOutboxAsync(String reason)
    {
        if (sendInProgress)
        {
            return;
        }

        sendInProgress = true;
        senderExecutor.submit(() ->
        {
            try
            {
                sendOutboxSync(reason);
            }
            finally
            {
                sendInProgress = false;
            }
        });
    }

    void shutdown()
    {
        senderExecutor.shutdownNow();
    }

    void sendOutboxSync(String reason)
    {
        try
        {
            loadAckedEventIds();
            pruneAckedOutboxRows();

            List<OutboxRecord> batch = readUnackedOutboxBatch(MAX_SEND_BATCH);
            if (batch.isEmpty())
            {
                appendSenderLog("{\"event\":\"send_skipped_no_unacked\",\"reason\":\"" + safe(reason) + "\",\"ts\":\"" + now() + "\"}");
                return;
            }

            fetchTelemetryContractIfNeeded();

            String requestBody = buildBatchRequest(batch);
            String url = piBaseUrl + TELEMETRY_EVENTS_ENDPOINT;
            String response = postJson(url, requestBody);

            Set<String> ackable = new HashSet<>();
            ackable.addAll(parseStringArray(response, "accepted_event_ids"));
            ackable.addAll(parseStringArray(response, "duplicate_event_ids"));
            if (ackable.isEmpty() && readJsonBool(response, "ok", false))
            {
                for (OutboxRecord record : batch)
                {
                    ackable.add(record.eventId);
                }
            }

            int ackedNow = 0;
            for (String id : ackable)
            {
                if (id != null && !id.isEmpty() && !ackedEventIds.contains(id))
                {
                    ackedEventIds.add(id);
                    appendLine(ackedEventIdsPath, id);
                    ackedNow++;
                }
            }
            if (!ackable.isEmpty())
            {
                pruneAckedOutboxRows();
            }

            String rejectedBlock = extractRejectedBlock(response);
            if (!rejectedBlock.isEmpty())
            {
                appendLine(rejectedEventsPath, "{\"event\":\"pi_rejected_response\",\"reason\":\"" + safe(reason) + "\",\"ts\":\"" + now() + "\",\"response\":" + jsonStringLiteral(response) + "}");
            }

            appendSenderLog(
                "{\"event\":\"send_complete\","
                    + "\"reason\":\"" + safe(reason) + "\","
                    + "\"url\":\"" + safe(url) + "\","
                    + "\"compressed\":" + telemetryGzipUploadSupported + ","
                    + "\"batch_size\":" + batch.size() + ","
                    + "\"acked_now\":" + ackedNow + ","
                    + "\"accepted_count\":" + parseStringArray(response, "accepted_event_ids").size() + ","
                    + "\"duplicate_count\":" + parseStringArray(response, "duplicate_event_ids").size() + ","
                    + "\"ts\":\"" + now() + "\"}"
            );

        }
        catch (Exception e)
        {
            appendSenderLog(
                "{\"event\":\"send_failed\","
                    + "\"reason\":\"" + safe(reason) + "\","
                    + "\"error\":\"" + safe(e.getClass().getSimpleName()) + "\","
                    + "\"message\":\"" + safe(e.getMessage()) + "\","
                    + "\"ts\":\"" + now() + "\"}"
            );

        }
    }

    String getCurrentPackageJson()
    {
        try
        {
            List<OutboxRecord> batch = readUnackedOutboxBatch(MAX_SEND_BATCH);
            if (batch.isEmpty())
            {
                return "{\"schema\":\"" + TELEMETRY_BATCH_SCHEMA + "\",\"batch_id\":\"batch_empty\",\"player_hash\":\"\",\"session_id\":\"" + safe(telemetrySessionId) + "\",\"event_count\":0,\"events\":[]}";
            }
            return buildBatchRequest(batch);
        }
        catch (Throwable error)
        {
            return "{\"error\":\"" + safe(error.getClass().getSimpleName()) + "\",\"message\":\"" + safe(error.getMessage()) + "\"}";
        }
    }

    int getCurrentPackageEventCount()
    {
        try
        {
            return readUnackedOutboxBatch(MAX_SEND_BATCH).size();
        }
        catch (Throwable error)
        {
            return 0;
        }
    }

    long getNextSendCountdownMs()
    {
        return sendInProgress ? 0L : 500L;
    }

    boolean isSendInProgress()
    {
        return sendInProgress;
    }

    String getPendingSummaryJson()
    {
        try
        {
            List<OutboxRecord> batch = readUnackedOutboxBatch(MAX_SEND_BATCH);
            return buildPendingSummaryRequest(System.currentTimeMillis(), batch);
        }
        catch (Throwable error)
        {
            return "{\"error\":\"" + safe(error.getClass().getSimpleName()) + "\",\"message\":\"" + safe(error.getMessage()) + "\"}";
        }
    }

    int getPendingSummaryCount()
    {
        try
        {
            return readUnackedOutboxBatch(MAX_SEND_BATCH).size();
        }
        catch (Throwable error)
        {
            return 0;
        }
    }

    List<AutoFlipTelemetryArchiveEntry> getSentHistoryEntries(int maxEntries)
    {
        List<AutoFlipTelemetryArchiveEntry> out = new ArrayList<>();
        try
        {
            if (!Files.isDirectory(telemetryArchiveDir))
            {
                return out;
            }

            try (java.util.stream.Stream<Path> paths = Files.list(telemetryArchiveDir))
            {
                List<Path> ordered = paths
                    .filter(path -> path.getFileName().toString().startsWith("telemetry_batch_"))
                    .sorted((left, right) -> right.getFileName().toString().compareTo(left.getFileName().toString()))
                    .collect(Collectors.toList());

                for (Path path : ordered)
                {
                    if (out.size() >= maxEntries)
                    {
                        break;
                    }
                    try
                    {
                        out.add(readTelemetryArchiveEntry(path));
                    }
                    catch (Exception e)
                    {
                        log.warn("Unable to read telemetry archive entry {}", path, e);
                    }
                }
            }
        }
        catch (IOException e)
        {
            log.warn("Unable to enumerate telemetry archive directory", e);
        }
        return out;
    }

    private void fetchTelemetryContractIfNeeded()
    {
        if (telemetryContractChecked)
        {
            return;
        }

        telemetryContractChecked = true;
        String url = piBaseUrl + TELEMETRY_CONTRACT_ENDPOINT;
        try
        {
            String response = httpGetText(url, 5000);
            telemetryGzipUploadSupported = response.contains("\"gzip_upload_supported\":true")
                || response.contains("\"gzip_upload_supported\": true");
            appendSenderLog(
                "{\"event\":\"telemetry_contract_checked\","
                    + "\"url\":\"" + safe(url) + "\","
                    + "\"ok\":" + (!response.isEmpty() && response.contains("plugin_event_contract_v1")) + ","
                    + "\"gzip_upload_supported\":" + telemetryGzipUploadSupported + ","
                    + "\"bytes\":" + response.length() + ","
                    + "\"ts\":\"" + now() + "\"}"
            );
        }
        catch (Exception e)
        {
            appendSenderLog(
                "{\"event\":\"telemetry_contract_check_failed\","
                    + "\"url\":\"" + safe(url) + "\","
                    + "\"error\":\"" + safe(e.getClass().getSimpleName()) + "\","
                    + "\"message\":\"" + safe(e.getMessage()) + "\","
                    + "\"ts\":\"" + now() + "\"}"
            );
        }
    }

    private List<OutboxRecord> readUnackedOutboxBatch(int max)
    {
        List<OutboxRecord> records = new ArrayList<>();
        if (!Files.exists(outboxPath))
        {
            return records;
        }

        try
        {
            for (String line : Files.readAllLines(outboxPath, StandardCharsets.UTF_8))
            {
                if (records.size() >= max)
                {
                    break;
                }

                String eventId = jsonString(line, "event_id", "");
                if (eventId.isEmpty() || ackedEventIds.contains(eventId))
                {
                    continue;
                }

                String createdAt = jsonString(line, "created_at", now());
                String payload = extractPayloadObject(line);
                if (payload.isEmpty() || !isUploadWorthyTelemetryPayload(payload))
                {
                    continue;
                }

                records.add(new OutboxRecord(eventId, createdAt, payload));
            }
        }
        catch (IOException e)
        {
            log.warn("Unable to read telemetry outbox", e);
        }

        return records;
    }

    private void pruneAckedOutboxRows()
    {
        if (!Files.exists(outboxPath))
        {
            return;
        }

        try
        {
            List<String> retained = new ArrayList<>();
            int removedAcked = 0;
            int removedIneligible = 0;

            for (String line : Files.readAllLines(outboxPath, StandardCharsets.UTF_8))
            {
                String eventId = jsonString(line, "event_id", "");
                if (!eventId.isEmpty() && ackedEventIds.contains(eventId))
                {
                    removedAcked++;
                    continue;
                }

                String payload = extractPayloadObject(line);
                if (payload.isEmpty() || !isUploadWorthyTelemetryPayload(payload))
                {
                    removedIneligible++;
                    continue;
                }

                retained.add(line);
            }

            if (removedAcked <= 0 && removedIneligible <= 0)
            {
                return;
            }

            Path tempOutbox = outboxPath.resolveSibling(outboxPath.getFileName().toString() + ".tmp");
            Files.write(
                tempOutbox,
                retained,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
            Files.move(tempOutbox, outboxPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            appendSenderLog(
                "{\"event\":\"outbox_pruned\","
                    + "\"removed_acked\":" + removedAcked + ","
                    + "\"removed_ineligible\":" + removedIneligible + ","
                    + "\"retained\":" + retained.size() + ","
                    + "\"ts\":\"" + now() + "\"}"
            );
        }
        catch (IOException e)
        {
            log.warn("Unable to prune acked telemetry outbox rows", e);
            appendSenderLog("{\"event\":\"outbox_prune_failed\",\"error\":\"" + safe(e.getClass().getSimpleName()) + "\",\"message\":\"" + safe(e.getMessage()) + "\",\"ts\":\"" + now() + "\"}");
        }
    }

    private String buildBatchRequest(List<OutboxRecord> records)
    {
        StringBuilder sb = new StringBuilder();
        String playerHash = telemetryPlayerHash();
        String batchId = buildTelemetryBatchId(records);

        sb.append("{");
        sb.append("\"schema\":\"").append(TELEMETRY_BATCH_SCHEMA).append("\",");
        sb.append("\"batch_id\":\"").append(safe(batchId)).append("\",");
        sb.append("\"player_hash\":\"").append(safe(playerHash)).append("\",");
        sb.append("\"session_id\":\"").append(safe(telemetrySessionId)).append("\",");
        sb.append("\"event_count\":").append(records.size()).append(",");
        sb.append("\"events\":[");

        for (int i = 0; i < records.size(); i++)
        {
            if (i > 0)
            {
                sb.append(",");
            }
            sb.append(buildTelemetryEventJson(records.get(i), batchId, playerHash));
        }

        sb.append("]}");
        return sb.toString();
    }

    private String buildPendingSummaryRequest(long observedTsMs, List<OutboxRecord> records)
    {
        StringBuilder sb = new StringBuilder();
        String playerHash = telemetryPlayerHash();
        sb.append("{");
        sb.append("\"schema\":\"autoflip.plugin_pending_slot_summary.v1\",");
        sb.append("\"batch_id\":\"pending_").append(safe(telemetrySessionId)).append("\",");
        sb.append("\"player_hash\":\"").append(safe(playerHash)).append("\",");
        sb.append("\"session_id\":\"").append(safe(telemetrySessionId)).append("\",");
        sb.append("\"pending_count\":").append(records.size()).append(",");
        sb.append("\"observed_ts_ms\":").append(observedTsMs).append(",");
        sb.append("\"events\":[");
        for (int i = 0; i < records.size(); i++)
        {
            if (i > 0)
            {
                sb.append(",");
            }
            sb.append(buildTelemetryEventJson(records.get(i), "pending_" + telemetrySessionId, playerHash));
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildTelemetryBatchId(List<OutboxRecord> records)
    {
        if (records == null || records.isEmpty())
        {
            return "batch_" + telemetrySessionId;
        }

        String first = records.get(0).eventId;
        String last = records.get(records.size() - 1).eventId;
        return "batch_" + sha256(telemetrySessionId + "|" + first + "|" + last + "|" + records.size()).substring(0, 24);
    }

    private String buildTelemetryEventJson(OutboxRecord record, String batchId, String playerHash)
    {
        String payload = record.payloadJson == null || record.payloadJson.isEmpty() ? "{}" : record.payloadJson;
        String eventType = telemetryEventTypeFromPayload(payload);
        long eventTsMs = telemetryEventTsMs(record.createdAt, payload);
        int itemId = jsonInt(payload, "item_id", 0);
        int geSlot = jsonInt(payload, "slot", -1);
        String itemName = jsonString(payload, "item_name", "");

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"schema\":\"").append(TELEMETRY_EVENT_SCHEMA).append("\",");
        sb.append("\"event_id\":\"").append(safe(record.eventId)).append("\",");
        sb.append("\"event_ts_ms\":").append(eventTsMs).append(",");
        sb.append("\"event_type\":\"").append(safe(eventType)).append("\",");
        sb.append("\"game\":\"").append(TELEMETRY_GAME).append("\",");
        sb.append("\"source\":\"").append(TELEMETRY_SOURCE).append("\",");
        sb.append("\"plugin_version\":\"").append(TELEMETRY_PLUGIN_VERSION).append("\",");
        sb.append("\"client\":\"").append(TELEMETRY_CLIENT).append("\",");
        sb.append("\"player_hash\":\"").append(safe(playerHash)).append("\",");
        sb.append("\"session_id\":\"").append(safe(telemetrySessionId)).append("\",");
        sb.append("\"local_sequence\":").append(readJsonLong(payload, "local_sequence", 0L)).append(",");
        sb.append("\"batch_id\":\"").append(safe(batchId)).append("\"");

        int world = currentWorldSupplier.getAsInt();
        if (world > 0)
        {
            sb.append(",\"world\":").append(world);
        }
        if (geSlot >= 0)
        {
            sb.append(",\"ge_slot\":").append(geSlot);
        }
        if (itemId > 0)
        {
            sb.append(",\"item_id\":").append(itemId);
        }
        if (!itemName.isEmpty())
        {
            sb.append(",\"item_name\":\"").append(safe(itemName)).append("\"");
        }

        appendRecommendationEnvelopeFields(sb, payload);
        sb.append(",\"payload\":").append(payload);
        sb.append("}");
        return sb.toString();
    }

    private void appendRecommendationEnvelopeFields(StringBuilder sb, String payload)
    {
        appendJsonStringFieldIfPresent(sb, payload, "recommendation_id");
        appendJsonStringFieldIfPresent(sb, payload, "plan_id");
        appendJsonLongFieldIfPresent(sb, payload, "board_slot");
        appendJsonStringFieldIfPresent(sb, payload, "optimizer_version");
        appendJsonStringFieldIfPresent(sb, payload, "frontier_version");
        appendJsonStringFieldIfPresent(sb, payload, "proxy_model_version");
        appendJsonStringFieldIfPresent(sb, payload, "glmm_model_version");
        appendJsonStringFieldIfPresent(sb, payload, "active_model_id");
        appendJsonStringFieldIfPresent(sb, payload, "cache_build_id");
        appendJsonStringFieldIfPresent(sb, payload, "mode");
        appendJsonLongFieldIfPresent(sb, payload, "recommendation_generated_ts_ms");
        appendJsonLongFieldIfPresent(sb, payload, "recommendation_shown_ts_ms");
        appendJsonDoubleFieldIfPresent(sb, payload, "recommendation_age_seconds");
        appendJsonLongFieldIfPresent(sb, payload, "user_selected_budget_gp");
        appendJsonDoubleFieldIfPresent(sb, payload, "user_selected_hours_away");
        appendJsonLongFieldIfPresent(sb, payload, "suggested_buy_price");
        appendJsonLongFieldIfPresent(sb, payload, "suggested_sell_price");
        appendJsonLongFieldIfPresent(sb, payload, "suggested_quantity");
        appendJsonLongFieldIfPresent(sb, payload, "actual_quantity_entered");
        appendJsonLongFieldIfPresent(sb, payload, "suggested_total_budget_gp");
        appendJsonLongFieldIfPresent(sb, payload, "actual_total_budget_gp");
        appendJsonLongFieldIfPresent(sb, payload, "actual_buy_price_entered");
        appendJsonLongFieldIfPresent(sb, payload, "actual_sell_price_entered");
        appendJsonLongFieldIfPresent(sb, payload, "buy_price_user_delta_gp");
        appendJsonLongFieldIfPresent(sb, payload, "sell_price_user_delta_gp");
        appendJsonDoubleFieldIfPresent(sb, payload, "buy_price_user_delta_pct");
        appendJsonDoubleFieldIfPresent(sb, payload, "sell_price_user_delta_pct");
        appendJsonLongFieldIfPresent(sb, payload, "quantity_user_delta");
        appendJsonDoubleFieldIfPresent(sb, payload, "quantity_user_delta_pct");
        appendJsonLongFieldIfPresent(sb, payload, "hourly_volume_capacity_at_offer");
        appendJsonDoubleFieldIfPresent(sb, payload, "throughput_ratio_at_offer");
        appendJsonDoubleFieldIfPresent(sb, payload, "throughput_pressure_at_offer");
        appendJsonDoubleFieldIfPresent(sb, payload, "volume_fit_at_offer");
        appendJsonStringFieldIfPresent(sb, payload, "recommendation_slice_key");
        appendJsonStringFieldIfPresent(sb, payload, "recommendation_matrix_key");
        appendJsonStringFieldIfPresent(sb, payload, "execution_pricing_source");
    }

    private static void appendJsonStringFieldIfPresent(StringBuilder sb, String payload, String key)
    {
        String value = jsonString(payload, key, "");
        if (!value.isEmpty())
        {
            sb.append(",\"").append(key).append("\":\"").append(safe(value)).append("\"");
        }
    }

    private static void appendJsonLongFieldIfPresent(StringBuilder sb, String payload, String key)
    {
        if (!payload.contains("\"" + key + "\""))
        {
            return;
        }

        sb.append(",\"").append(key).append("\":").append(readJsonLong(payload, key, 0L));
    }

    private static void appendJsonDoubleFieldIfPresent(StringBuilder sb, String payload, String key)
    {
        if (!payload.contains("\"" + key + "\""))
        {
            return;
        }

        sb.append(",\"").append(key).append("\":").append(readJsonDouble(payload, key, 0.0D));
    }

    private String telemetryEventTypeFromPayload(String payload)
    {
        String explicit = jsonString(payload, "event_type", "");
        if (!explicit.isEmpty())
        {
            return explicit;
        }

        String event = jsonString(payload, "event", "");
        if ("ge_trade_history".equals(event))
        {
            return "ge_trade_history_entry";
        }
        if ("ge_slot_state_changed".equals(event))
        {
            return telemetryGeSlotEventType(payload);
        }

        return event.isEmpty() ? "plugin_health_summary" : event;
    }

    private boolean isUploadWorthyTelemetryPayload(String payload)
    {
        String explicit = jsonString(payload, "event_type", "");
        if (!explicit.isEmpty())
        {
            return isUploadWorthyTelemetryEventType(explicit);
        }

        String event = jsonString(payload, "event", "");
        if ("ge_trade_history".equals(event))
        {
            return true;
        }
        if (!"ge_slot_state_changed".equals(event))
        {
            return isUploadWorthyTelemetryEventType(event);
        }
        if (readJsonBool(payload, "capacity_event", false))
        {
            return false;
        }

        String state = jsonString(payload, "state", "unknown").toLowerCase(Locale.ROOT);
        if (!state.contains("cancel") && !state.contains("bought") && !state.contains("sold"))
        {
            return false;
        }

        return isUploadWorthyTelemetryEventType(telemetryGeSlotEventType(payload));
    }

    private boolean isUploadWorthyTelemetryEventType(String eventType)
    {
        if (eventType == null || eventType.isEmpty())
        {
            return false;
        }

        if ("buy_offer_filled".equals(eventType)
            || "sell_offer_filled".equals(eventType)
            || "offer_cancelled".equals(eventType)
            || "trade_completed".equals(eventType)
            || "trade_abandoned".equals(eventType)
            || "ge_trade_history_entry".equals(eventType)
            || "trade_history_entry".equals(eventType)
            || "active_offer_summary".equals(eventType))
        {
            return true;
        }

        return false;
    }

    private String telemetryGeSlotEventType(String payload)
    {
        if (readJsonBool(payload, "capacity_event", false))
        {
            return "ge_slot_observed";
        }

        String state = jsonString(payload, "state", "unknown").toUpperCase(Locale.ROOT);
        String side = jsonString(payload, "side", "").toUpperCase(Locale.ROOT);

        if (state.contains("CANCEL"))
        {
            return "offer_cancelled";
        }
        if (state.contains("BOUGHT"))
        {
            return "buy_offer_filled";
        }
        if (state.contains("SOLD"))
        {
            return "sell_offer_filled";
        }
        if (side.contains("SELL"))
        {
            return "sell_offer_filled";
        }
        return "buy_offer_filled";
    }

    private long telemetryEventTsMs(String createdAt, String payload)
    {
        long eventTs = readJsonLong(payload, "event_ts_ms", 0L);
        if (eventTs > 0L)
        {
            return eventTs;
        }

        try
        {
            if (createdAt != null && !createdAt.isEmpty())
            {
                return Instant.parse(createdAt).toEpochMilli();
            }
        }
        catch (Exception ignored)
        {
        }
        return System.currentTimeMillis();
    }

    private String telemetryPlayerHash()
    {
        return sha256((accountKey == null ? "" : accountKey) + "|" + (localSalt == null ? "" : localSalt));
    }

    private String postJson(String urlString, String body) throws IOException
    {
        boolean compressed = telemetryGzipUploadSupported;
        byte[] jsonBytes = body.getBytes(StandardCharsets.UTF_8);
        byte[] bodyBytes = compressed ? gzipBytes(jsonBytes) : jsonBytes;
        archiveTelemetryUploadBatch(bodyBytes, compressed);

        MediaType mediaType = MediaType.parse(compressed ? "application/gzip" : "application/json; charset=utf-8");
        Request request = new Request.Builder()
            .url(urlString)
            .post(RequestBody.create(mediaType, bodyBytes))
            .header("Accept", "application/json")
            .build();

        try (Response response = httpClient(10000).newCall(request).execute())
        {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful())
            {
                throw new IOException("HTTP " + response.code() + " " + responseBody);
            }
            return responseBody;
        }
    }

    private byte[] gzipBytes(byte[] input) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out))
        {
            gzip.write(input);
        }
        return out.toByteArray();
    }

    private void archiveTelemetryUploadBatch(byte[] bodyBytes, boolean compressed)
    {
        if (bodyBytes == null || bodyBytes.length == 0)
        {
            return;
        }

        try
        {
            Files.createDirectories(telemetryArchiveDir);
            String suffix = compressed ? ".json.gz" : ".json";
            String name = "telemetry_batch_" + System.currentTimeMillis() + suffix;
            Files.write(telemetryArchiveDir.resolve(name), bodyBytes, StandardOpenOption.CREATE_NEW);
        }
        catch (Exception e)
        {
            appendSenderLog("{\"event\":\"telemetry_upload_archive_failed\",\"error\":\"" + safe(e.getClass().getSimpleName()) + "\",\"message\":\"" + safe(e.getMessage()) + "\",\"ts\":\"" + now() + "\"}");
        }
    }

    private AutoFlipTelemetryArchiveEntry readTelemetryArchiveEntry(Path file) throws IOException
    {
        byte[] bytes = Files.readAllBytes(file);
        String payload;
        if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".gz"))
        {
            try (java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(bytes);
                 java.util.zip.GZIPInputStream gzip = new java.util.zip.GZIPInputStream(in))
            {
                payload = readAll(gzip);
            }
        }
        else
        {
            payload = new String(bytes, StandardCharsets.UTF_8);
        }
        String batchId = jsonString(payload, "batch_id", file.getFileName().toString());
        int eventCount = jsonInt(payload, "event_count", 0);
        long modifiedAtMs = Files.getLastModifiedTime(file).toMillis();
        return new AutoFlipTelemetryArchiveEntry(file.getFileName().toString(), modifiedAtMs, batchId, eventCount, payload);
    }

    private void appendSenderLog(String line)
    {
        appendLine(senderLogPath, line);
    }

    private boolean isVerboseLoggingEnabled()
    {
        try
        {
            return verboseLoggingSupplier.getAsBoolean();
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    private static void appendLine(Path path, String line)
    {
        try
        {
            Files.createDirectories(path.getParent());
            Files.write(
                path,
                (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        }
        catch (IOException e)
        {
            log.warn("Unable to write telemetry file: {}", path, e);
        }
    }

    private static void loadIdsInto(Path path, Set<String> target)
    {
        if (!Files.exists(path))
        {
            return;
        }

        try
        {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8))
            {
                String trimmed = line.trim();
                if (!trimmed.isEmpty())
                {
                    target.add(trimmed);
                }
            }
        }
        catch (IOException e)
        {
            log.warn("Unable to load telemetry ids from {}", path, e);
        }
    }

    private static String extractPayloadObject(String outboxLine)
    {
        if (outboxLine == null || outboxLine.isEmpty())
        {
            return "";
        }

        int keyIndex = outboxLine.indexOf("\"payload\":");
        if (keyIndex < 0)
        {
            return "";
        }

        int start = outboxLine.indexOf('{', keyIndex);
        if (start < 0)
        {
            return "";
        }

        int depth = 0;
        for (int i = start; i < outboxLine.length(); i++)
        {
            char c = outboxLine.charAt(i);
            if (c == '{')
            {
                depth++;
            }
            else if (c == '}')
            {
                depth--;
                if (depth == 0)
                {
                    return outboxLine.substring(start, i + 1);
                }
            }
        }

        return "";
    }

    private static String extractRejectedBlock(String response)
    {
        String rejected = jsonString(response, "rejected", "");
        return rejected == null ? "" : rejected;
    }

    private static String readAll(InputStream stream) throws IOException
    {
        if (stream == null)
        {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        byte[] buffer = new byte[4096];
        int read;
        try (InputStream in = stream)
        {
            while ((read = in.read(buffer)) >= 0)
            {
                sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }

    private String httpGetText(String urlString, int timeoutMs) throws IOException
    {
        Request request = new Request.Builder()
            .url(urlString)
            .header("Accept", "application/json")
            .build();

        try (Response response = httpClient(timeoutMs).newCall(request).execute())
        {
            String responseBody = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful())
            {
                throw new IOException("HTTP " + response.code() + " " + responseBody);
            }
            return responseBody;
        }
    }

    private OkHttpClient httpClient(int timeoutMs)
    {
        int timeout = Math.max(1000, timeoutMs);
        return okHttpClient.newBuilder()
            .connectTimeout(timeout, TimeUnit.MILLISECONDS)
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            .build();
    }

    private static List<String> parseStringArray(String json, String key)
    {
        List<String> values = new ArrayList<>();
        if (json == null || json.isEmpty())
        {
            return values;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(key) + "\\\"\\s*:\\s*\\[(.*?)\\]").matcher(json);
        if (!matcher.find())
        {
            return values;
        }

        String body = matcher.group(1);
        java.util.regex.Matcher itemMatcher = java.util.regex.Pattern.compile("\\\"([^\\\"]+)\\\"").matcher(body);
        while (itemMatcher.find())
        {
            values.add(itemMatcher.group(1));
        }
        return values;
    }

    private static boolean readJsonBool(String json, String key, boolean fallback)
    {
        String value = jsonString(json, key, "");
        if (value.isEmpty())
        {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(key) + "\\\"\\s*:\\s*(true|false)").matcher(json == null ? "" : json);
            if (!matcher.find())
            {
                return fallback;
            }
            return "true".equalsIgnoreCase(matcher.group(1));
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static int jsonInt(String json, String key, int fallback)
    {
        try
        {
            return (int) readJsonLong(json, key, fallback);
        }
        catch (Exception e)
        {
            return fallback;
        }
    }

    private static long readJsonLong(String json, String key, long fallback)
    {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+)").matcher(json == null ? "" : json);
        if (!matcher.find())
        {
            return fallback;
        }
        try
        {
            return Long.parseLong(matcher.group(1));
        }
        catch (Exception e)
        {
            return fallback;
        }
    }

    private static double readJsonDouble(String json, String key, double fallback)
    {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(json == null ? "" : json);
        if (!matcher.find())
        {
            return fallback;
        }
        try
        {
            return Double.parseDouble(matcher.group(1));
        }
        catch (Exception e)
        {
            return fallback;
        }
    }

    private static String jsonString(String json, String key, String fallback)
    {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"" + java.util.regex.Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json == null ? "" : json);
        if (!matcher.find())
        {
            return fallback;
        }
        return matcher.group(1);
    }

    private static String jsonStringLiteral(String value)
    {
        return "\"" + safe(value) + "\"";
    }

    private static String cleanBaseUrl(String value)
    {
        if (value == null)
        {
            return "https://autoflip.gg";
        }
        String cleaned = value.replace("\uFEFF", "").replace("\u00EF\u00BB\u00BF", "").replaceAll("[\\u0000-\\u001F\\u007F]", "").trim().replaceAll("/+$", "");
        return cleaned.isEmpty() ? "https://autoflip.gg" : cleaned;
    }

    private static String now()
    {
        return Instant.now().toString();
    }

    private static String sha256(String value)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes)
            {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            return Integer.toHexString(value.hashCode());
        }
    }

    private static String safe(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class OutboxRecord
    {
        private final String eventId;
        private final String createdAt;
        private final String payloadJson;

        private OutboxRecord(String eventId, String createdAt, String payloadJson)
        {
            this.eventId = eventId;
            this.createdAt = createdAt;
            this.payloadJson = payloadJson;
        }
    }
}
