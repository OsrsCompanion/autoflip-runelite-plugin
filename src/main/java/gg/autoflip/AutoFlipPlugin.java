package gg.autoflip;


import net.runelite.client.game.ItemManager;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.MenuEntry;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.ClientToolbar;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.Player;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseWheelListener;
import net.runelite.client.input.MouseManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.LinkBrowser;
import net.runelite.http.api.item.ItemPrice;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
    name = "AutoFlip",
    description = "AutoFlip Grand Exchange assistant with AutoFlip.gg recommendations and telemetry",
    tags = {"autoflip", "ge", "flipping"},
    enabledByDefault = true
)
public class AutoFlipPlugin extends Plugin implements MouseListener, MouseWheelListener, KeyListener
{
    private static final Logger log = LoggerFactory.getLogger(AutoFlipPlugin.class);
    private static final String DEFAULT_PI_BASE_URL = "https://autoflip.gg";
    private static final String TELEMETRY_BATCH_SCHEMA = "autoflip.plugin_event_batch.v1";
    private static final int AUTOFLIP_GE_SEARCH_MODE = 14;
    private static final String TELEMETRY_SOURCE = "runelite_plugin";
    private static final int TELEMETRY_SEND_TICK_INTERVAL = 500;

    private static final Path RUNTIME_DIR = Paths.get(System.getProperty("user.home", "."), ".runelite", "plugin-data", "autoflip");
    private static final Path PI_BASE_URL_FILE = RUNTIME_DIR.resolve("pi_base_url.txt");
    private static final Path SALT_FILE = RUNTIME_DIR.resolve("local_account_salt.txt");
    private static final Path SEEN_EVENT_IDS = RUNTIME_DIR.resolve("seen_event_ids.txt");
    private static final Path OUTBOX = RUNTIME_DIR.resolve("outbox.jsonl");
    private static final Path GE_SLOT_EVENTS = RUNTIME_DIR.resolve("ge_slot_events.jsonl");
        private static final Path GE_SLOT_CAPACITY = RUNTIME_DIR.resolve("ge_slot_capacity.jsonl");
private static final Path GE_TRADE_HISTORY = RUNTIME_DIR.resolve("ge_trade_history_seen.jsonl");
    private static final Path GE_DEBUG = RUNTIME_DIR.resolve("ge_offers_seen.jsonl");
    private static final Path GE_WIDGET_BOUNDS = RUNTIME_DIR.resolve("ge_widget_bounds.jsonl");
    private static final Path SENDER_LOG = RUNTIME_DIR.resolve("sender_log.jsonl");
    private static final Path ORDINARY_SELL_DEBUG_LOG = RUNTIME_DIR.resolve("ordinary_sell_debug.log");
    private static final boolean AUTOFLIP_VERBOSE_RUNTIME_LOGGING = false;
    private static final Path SLOT_INSTANCE_SEQ_FILE = RUNTIME_DIR.resolve("slot_instance_seq.txt");
    private static final Path SMART_SELL_SETTINGS_PATH = RUNTIME_DIR.resolve("smart_sell_settings.properties");
    private static final Path AUTOFLIP_GE_ACCOUNTING_STATE_PATH = RUNTIME_DIR.resolve("autoflip_ge_accounting.jsonl");
    private static final Path AUTOFLIP_INVENTORY_STATE_PATH = RUNTIME_DIR.resolve("autoflip_inventory.jsonl");
    private static final Path AUTOFLIP_TO_BUY_STATE_PATH = RUNTIME_DIR.resolve("autoflip_to_buy_wishlist.jsonl");
    private static final long AUTOFLIP_TO_BUY_PRICE_REFRESH_TTL_MS = 45_000L;
    private static final long AUTOFLIP_SELL_PRICE_REFRESH_TTL_MS = 45_000L;
    private static final int AUTOFLIP_TO_BUY_SEARCH_LIMIT = 12;

    private final String[] lastCanonicalSnapshots = new String[8];
    private final int[] slotInstanceSeq = new int[8];
    private final long[] slotPlacedTsMs = new long[8];
    private final long[] slotLastSeenOpenTsMs = new long[8];
    private final int[] slotPlacedItemId = new int[8];
    private final int[] slotPlacedPrice = new int[8];
    private final int[] slotPlacedQuantity = new int[8];
    private final String[] slotPlacedSide = new String[8];
    private final AutoFlipRecommendationContext[] slotRecommendationContext = new AutoFlipRecommendationContext[8];
    private String lastActiveOfferSummaryFingerprint = "";
    private final int[] slotLastObservedFilledQuantity = new int[8];
    private final int[] slotLastObservedSpentGp = new int[8];
    private final String[] slotLastObservedState = new String[8];
    private final boolean[] capacitySlotObserved = new boolean[8];
    private final boolean[] currentSlotEmpty = new boolean[8];
    private final String[] currentSlotCapacityStates = new String[8];
    private String lastCapacityFingerprint = "";
private final Set<String> seenEventIds = new HashSet<>();

    private ExecutorService pricePrefetchExecutor = Executors.newSingleThreadExecutor();
    private volatile long autoFlipGeSearchInjectSeq = 0L;
    private volatile int autoFlipItemSearchSeededItemId = 0;
    private volatile String autoFlipItemSearchSeededText = "";
    private int tickCounter = 0;
    private final String telemetrySessionId = "session_" + UUID.randomUUID().toString().replace("-", "");
    private long telemetryLocalSequence = 0L;
    private AutoFlipTelemetryRuntime telemetryRuntime;

    private boolean pollingApiChecked = false;
    private boolean pollingApiAvailable = false;

    private String localSalt = "";
    private String accountKey = "unknown_account";
    private String piBaseUrl = DEFAULT_PI_BASE_URL;

    
    private static final long AUTOFLIP_GE_WIDGET_BOUNDS_REFRESH_INTERVAL_MS = 250L;
    private static final long AUTOFLIP_STATE_DETECTOR_LABEL_REFRESH_INTERVAL_MS = 250L;
    private volatile boolean autoFlipGeWidgetBoundsDirty = true;
    private volatile long autoFlipGeWidgetBoundsLastScanMs = 0L;
    private volatile boolean autoFlipStateDetectorLabelCacheDirty = true;
    private volatile long autoFlipStateDetectorLabelCacheLastRefreshMs = 0L;
    private String lastWidgetBoundsFingerprint = "";

    private volatile boolean geWindowOpenForOverlay = false;
    private volatile Boolean autoFlipCustomSetupUiOverrideForOverlay = null;
    private volatile Rectangle geHeaderBoundsForOverlay = null;
    private volatile String geHeaderTextForOverlay = "";
    private volatile Rectangle autoFlipButtonBounds = null;

    
    private final Rectangle[] geSlotBoundsForOverlay = new Rectangle[8];
    private volatile java.util.List<AutoFlipGeOfferSlotSnapshot> autoFlipGeOfferSlotSnapshots = createEmptyAutoFlipGeOfferSlotSnapshots();
private volatile boolean geHeaderFoundThisScan = false;

    private volatile boolean autoFlipOverlayActive = false;
    private static final int[] AUTOFLIP_HOUR_OPTIONS = new int[] {4, 8, 12, 16, 20, 24, 48};
    private static final long[] AUTOFLIP_BUDGET_OPTIONS = new long[] {500000L, 1000000L, 2000000L, 5000000L, 10000000L, 25000000L};
    private volatile int autoFlipMenuHoursAway = 6;
    private volatile long autoFlipMenuManualBudgetGp = 2000000L;
    private volatile boolean autoFlipMenuUseCashStack = true;
    private volatile String autoFlipMenuRiskMode = "optimize";
    private volatile boolean autoFlipRiskDropdownOpen = false;
    // Worker threads must use cached cash observed by the UI/client path, not live client reads.
    private volatile long autoFlipLastObservedCashStackGp = 0L;

    private static final String AUTOFLIP_ADD_INVENTORY_OPTION = "Add to AutoFlip Inventory";
    private final Object autoFlipInventoryLock = new Object();
    private volatile java.util.List<AutoFlipInventoryItem> autoFlipInventoryItems = java.util.Collections.emptyList();
    private final Object autoFlipToBuyLock = new Object();
    private volatile java.util.List<AutoFlipToBuyItem> autoFlipToBuyItems = java.util.Collections.emptyList();
    private volatile long autoFlipToBuyLastMarketRefreshAtMs = 0L;
    private volatile boolean autoFlipToBuyRefreshInFlight = false;
    private volatile boolean autoFlipStartupWarmAttempted = false;
    private volatile boolean autoFlipBoardWarmInFlight = false;
    private volatile boolean autoFlipOptimizeBoardInProgress = false;
    private volatile java.util.List<AutoFlipBoardCard> autoFlipWarmBoardCards = java.util.Collections.emptyList();
    private volatile String autoFlipWarmBoardCacheKey = "";
    private volatile long autoFlipWarmBoardBuiltAtMs = 0L;
    private final java.util.concurrent.ConcurrentHashMap<Integer, Long> autoFlipSellPriceCacheByItemId = new java.util.concurrent.ConcurrentHashMap<>();
    // Collector-owned /latest data is copied into this transient plugin cache; the plugin is never a Pi price writer.
    private final java.util.concurrent.ConcurrentHashMap<Integer, Long> autoFlipBuyPriceCacheByItemId = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Integer, Long> autoFlipLastBoughtPriceByItemId = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Integer, Long> autoFlipLastBoughtAtMsByItemId = new java.util.concurrent.ConcurrentHashMap<>();
    // Buy-cache membership is granted only by ranked, To-Buy, or explicitly selected buy requests.
    private final java.util.Set<Integer> autoFlipAuthorizedBuyPriceItemIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<Integer> autoFlipApiBuyPriceReadyItemIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<Integer> autoFlipApiBuyPriceRequestItemIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ConcurrentHashMap<Integer, Long> autoFlipApiBuyPriceLoadedAtByItemId = new java.util.concurrent.ConcurrentHashMap<>();
    // Transient coordination only; autoFlipSellPriceCacheByItemId remains the price-value authority.
    private final java.util.Set<Integer> autoFlipApiSellPriceReadyItemIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.concurrent.ConcurrentHashMap<Integer, Long> autoFlipApiSellPriceLoadedAtByItemId = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<Integer> autoFlipApiSellPriceRequestItemIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // Transient retry scheduling only; this map never stores or owns price values.
    private final java.util.concurrent.ConcurrentHashMap<Integer, Long> autoFlipApiSellPriceRetryAtByItemId = new java.util.concurrent.ConcurrentHashMap<>();
    // Transient display metadata captured on the client thread; never a price or item authority.
    private final java.util.concurrent.ConcurrentHashMap<Integer, String> autoFlipSellPriceDebugItemNameById = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Integer, Boolean> autoFlipMembersOnlyCacheByItemId = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long autoFlipLastInventoryPriceScanAtMs = 0L;
    private volatile java.util.List<Integer> autoFlipLastInventoryPriceCandidateIds = java.util.Collections.emptyList();
    private volatile int autoFlipOrdinarySellSetupItemId = 0;
    private volatile String autoFlipOrdinarySellSetupItemName = "";
    private volatile long autoFlipOrdinarySellSetupLastSeenMs = 0L;
    private volatile Widget autoFlipOrdinarySellPriceOptionWidget = null;
    private volatile String autoFlipOrdinarySellLastPromptText = "";
    private volatile boolean autoFlipOrdinarySellPriceOptionVisible = false;
    private volatile int autoFlipOrdinarySellPriceOptionGp = 0;
    private volatile int autoFlipOrdinaryBuySetupItemId = 0;
    private volatile String autoFlipOrdinaryBuySetupItemName = "";
    private volatile long autoFlipOrdinaryBuySetupLastSeenMs = 0L;
    private volatile int autoFlipOrdinaryBuyCurrentPriceGp = 0;
    private volatile int autoFlipOrdinaryBuySuggestedPriceGp = 0;
    private volatile int autoFlipOrdinaryBuyLastBoughtPriceGp = 0;
    private volatile long autoFlipOrdinaryBuyLastBoughtAtMs = 0L;
    private volatile int autoFlipOrdinaryBuyAutoFillItemId = 0;
    private volatile Rectangle autoFlipOrdinaryBuyRecommendedButtonBounds = null;
    private volatile Rectangle autoFlipOrdinaryBuyLastBoughtButtonBounds = null;
    private volatile Rectangle autoFlipOrdinaryBuyTargetPriceButtonBounds = null;
    private volatile String autoFlipOrdinaryBuyRecommendedButtonLabel = "";
    private volatile String autoFlipOrdinaryBuyLastBoughtButtonLabel = "";
    private volatile String autoFlipOrdinaryBuyTargetPriceButtonLabel = "";
    private volatile int autoFlipOrdinaryBuyTargetPriceGp = 0;
    private volatile int autoFlipOrdinaryBuyManualSelectedPriceGp = 0;
    private volatile boolean autoFlipOrdinaryBuyPricePromptManualChoiceMade = false;
    private final java.util.LinkedHashSet<Integer> autoFlipRecentlySkippedItemIds = new java.util.LinkedHashSet<>();
    private volatile boolean autoFlipShiftDown = false;
    private final int[] autoFlipHeldSaleFilledBySlot = new int[8];
    private final long[] autoFlipHeldSaleSpentBySlot = new long[8];
    private final int[] autoFlipHeldSaleInstanceSeqBySlot = new int[8];
    private final int[] autoFlipHeldBuyFilledBySlot = new int[8];
    private final long[] autoFlipHeldBuySpentBySlot = new long[8];
    private final int[] autoFlipHeldBuyInstanceSeqBySlot = new int[8];
    // Some menu clicks are more reliable when handled on mousePressed.
    // This flag prevents the following mouseClicked from toggling/selecting twice.
    private volatile boolean autoFlipSuppressNextMenuClick = false;

    private volatile boolean autoFlipHoursDropdownOpen = false;
    private volatile boolean autoFlipBudgetInputActive = false;
    private volatile String autoFlipBudgetInputBuffer = "";
    private volatile boolean autoFlipHoursInputActive = false;
    private volatile String autoFlipHoursInputBuffer = "";
    private volatile char autoFlipLastPressedInputChar = 0;
    private volatile long autoFlipLastPressedInputMs = 0L;
    private volatile int autoFlipLastMouseX = -1;
    private volatile int autoFlipLastMouseY = -1;
    private volatile boolean autoFlipOverlayPressConsumed = false;
    private volatile boolean autoFlipOverlaySuppressNextClick = false;
    private volatile int autoFlipOverlayPressX = -1;
    private volatile int autoFlipOverlayPressY = -1;

    private final java.awt.KeyEventDispatcher autoFlipTextInputKeyDispatcher = this::dispatchAutoFlipTextInputKeyEvent;




    private volatile String editDragHandle = null;
    private volatile int editDragStartMouseX = 0;
    private volatile int editDragStartMouseY = 0;
    private volatile int editDragStartX = 0;
    private volatile int editDragStartY = 0;
@Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    
    @Inject
    private OverlayManager overlayManager;

    
    @Inject
    private MouseManager mouseManager;

    @Inject
    private KeyManager keyManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ItemManager itemManager;
    @Inject
    private OkHttpClient okHttpClient;
@Inject
    private AutoFlipOverlay autoFlipOverlay;

    private AutoFlipSidePanel autoFlipSidePanel;
    private NavigationButton autoFlipNavigationButton;
@Override
    protected void startUp()
    {
        ensureRuntimeDir();
        ensurePricePrefetchExecutor();
        localSalt = loadOrCreateSalt();
        piBaseUrl = loadOrCreatePiBaseUrl();
        telemetryRuntime = new AutoFlipTelemetryRuntime(
            RUNTIME_DIR,
            piBaseUrl,
            telemetrySessionId,
            localSalt,
            accountKey,
            this::getCurrentWorld,
            this::isAutoFlipVerboseRuntimeLoggingEnabled,
            okHttpClient
        );
        loadSeenEventIds();
        telemetryRuntime.loadAckedEventIds();
        loadSlotInstanceSeq();
        bootstrapActiveOfferTelemetryContextFromExistingEvents();
        loadAutoFlipMenuState();
        loadAutoFlipInventory();
        loadAutoFlipToBuyWatchlist();
        requestAutoFlipApiBuyPrices(getAutoFlipToBuyItemIds());
        refreshAutoFlipToBuyMarketDataAsync();

        appendLine(GE_DEBUG, "{\"event\":\"plugin_started\",\"source\":\"autoflip_runelite\",\"detector\":\"pi_sender_v1\",\"ts\":\"" + now() + "\"}");
        enqueueTelemetryEvent(
            "plugin_session_started",
            "{"
                + "\"startup_reason\":\"plugin_startup\","
                + "\"pi_base_url\":\"" + safe(piBaseUrl) + "\","
                + "\"runtime_dir\":\"" + safe(RUNTIME_DIR.toString()) + "\""
                + "}"
        );

        logAutoFlipVerbose("AUTOFLIP_PLUGIN_STARTED");
        logAutoFlipVerbose("AUTOFLIP_PI_BASE_URL=" + piBaseUrl);
        logAutoFlipVerbose("AUTOFLIP_OUTBOX=" + OUTBOX);

        if (overlayManager != null && autoFlipOverlay != null)
        {
            overlayManager.add(autoFlipOverlay);
        }

        installAutoFlipSidePanel();

        if (mouseManager != null)
        {
            mouseManager.registerMouseListener(this);
            mouseManager.registerMouseWheelListener(this);
        }

        if (keyManager != null)
        {
            keyManager.registerKeyListener(this);
        }

        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(autoFlipTextInputKeyDispatcher);

        sendOutboxAsync("startup");
    }

    @Override
    protected void shutDown()
    {
        if (telemetryRuntime != null)
        {
            telemetryRuntime.shutdown();
        }
        hideAutoFlipOrdinarySellPriceOption();
        clearAutoFlipOrdinaryBuyState();
        clearAutoFlipGeSessionInventoryCache("plugin_shutdown");

        if (mouseManager != null)
        {
            mouseManager.unregisterMouseListener(this);
            mouseManager.unregisterMouseWheelListener(this);
        }

        if (keyManager != null)
        {
            keyManager.unregisterKeyListener(this);
        }

        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(autoFlipTextInputKeyDispatcher);

        uninstallAutoFlipSidePanel();

        if (overlayManager != null && autoFlipOverlay != null)
        {
            overlayManager.remove(autoFlipOverlay);
        }

        enqueueTelemetryEvent(
            "plugin_session_ended",
            "{"
                + "\"shutdown_reason\":\"plugin_shutdown\""
                + "}"
        );
        sendOutboxSync("shutdown");

        if (pricePrefetchExecutor != null)
        {
            pricePrefetchExecutor.shutdownNow();
        }
        logAutoFlipVerbose("AUTOFLIP_PLUGIN_STOPPED");
    }

    private void ensurePricePrefetchExecutor()
    {
        if (pricePrefetchExecutor == null || pricePrefetchExecutor.isShutdown() || pricePrefetchExecutor.isTerminated())
        {
            pricePrefetchExecutor = Executors.newSingleThreadExecutor();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        refreshAccountKey();
        maybeStartAutoFlipStartupWarm();
        pollGrandExchangeOffers();

        if (autoFlipGeWidgetBoundsDirty || geWindowOpenForOverlay)
        {
            inspectGeWidgetBounds();
        }

        if (geWindowOpenForOverlay && autoFlipGeSessionInventoryCapturePending)
        {
            captureAutoFlipGeSessionInventoryCacheIfPending();
        }

        if (shouldRunAutoFlipUiStateDetectorsOnTick())
        {
            refreshAutoFlipOrdinarySellSetupSelectionFromVisibleUi();
            maintainAutoFlipOrdinarySellSetupSelection();
            refreshAutoFlipOrdinaryBuySetupSelectionFromVisibleUi();
            maintainAutoFlipInventorySellPriceRetries();
            getAutoFlipStateDetectorActiveNativePromptText();
            refreshAutoFlipStateDetectorLabelCache();
            refreshAutoFlipOrdinarySellPriceOption();
            refreshAutoFlipOrdinaryBuyPriceOption();
            refreshAutoFlipTargetBuyPriceOption();
            retryAutoFlipPendingBuySearchSeedIfReady();
            inspectGeWidgetBounds();
        }

tickCounter++;
        if (tickCounter >= TELEMETRY_SEND_TICK_INTERVAL)
        {
            tickCounter = 0;
            enqueueActiveOfferSummaryIfNeeded("periodic");
            sendOutboxAsync("periodic");
        }
    }

    private boolean shouldRunAutoFlipUiStateDetectorsOnTick()
    {
        long nowMs = System.currentTimeMillis();

        if (autoFlipPendingGuidedSetupItemId > 0
            || autoFlipPendingBuySearchSeedDeadlineMs > nowMs
            || autoFlipOrdinarySellSetupItemId > 0
            || autoFlipOrdinaryBuySetupItemId > 0)
        {
            return true;
        }

        if (autoFlipGuidedSetupStage != null && !autoFlipGuidedSetupStage.trim().isEmpty())
        {
            return true;
        }

        if (autoFlipPriceChatboxButtonBounds != null
            || autoFlipQuantityChatboxButtonBounds != null
            || autoFlipOrdinarySellRecommendedButtonBounds != null
            || autoFlipOrdinarySellLastSoldButtonBounds != null
            || autoFlipOrdinaryBuyRecommendedButtonBounds != null
            || autoFlipOrdinaryBuyLastBoughtButtonBounds != null
            || autoFlipOrdinaryBuyTargetPriceButtonBounds != null)
        {
            return true;
        }

        if (autoFlipOrdinarySellForceWritePending
            || autoFlipOrdinaryBuyPricePromptManualChoiceMade
            || autoFlipQuantityPromptAutoFillLocked
            || autoFlipQuantityPromptManualChoiceMade)
        {
            return true;
        }

        return autoFlipLastNativeButtonItemId > 0
            && autoFlipLastNativeButtonRememberedAtMs > 0L
            && nowMs - autoFlipLastNativeButtonRememberedAtMs < 15000L;
    }

    public boolean isAutoFlipUiWorkflowActiveForOverlay()
    {
        return shouldRunAutoFlipUiStateDetectorsOnTick();
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (event == null
            || event.getContainerId() != InventoryID.INVENTORY.getId()
            || event.getItemContainer() == null)
        {
            return;
        }

        Item[] items = event.getItemContainer().getItems();
        if (items == null)
        {
            return;
        }

        boolean selectedInventoryItemStillPresent = false;
        int selectedSetupItemId = autoFlipOrdinarySellSetupItemId > 0
            ? canonicalizeAutoFlipInventoryItemId(autoFlipOrdinarySellSetupItemId)
            : 0;
        int selectedSetupItemQuantity = 0;

        java.util.List<Integer> candidateIds = new java.util.ArrayList<>();
        for (Item item : items)
        {
            if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
            {
                continue;
            }

            int itemId = canonicalizeAutoFlipInventoryItemId(item.getId());
            if (itemId <= 0 || isAutoFlipInventoryItem(itemId) || !isAutoFlipTradeableItem(itemId))
            {
                continue;
            }

            if (selectedSetupItemId > 0 && itemId == selectedSetupItemId)
            {
                selectedInventoryItemStillPresent = true;
                selectedSetupItemQuantity = Math.max(0, item.getQuantity());
            }

            if (!candidateIds.contains(itemId))
            {
                candidateIds.add(itemId);
            }
            String itemName = resolveAutoFlipItemName(itemId, "");
            if (!itemName.isEmpty())
            {
                autoFlipSellPriceDebugItemNameById.put(itemId, itemName);
            }
        }
        requestAutoFlipApiSellPrices(candidateIds);
        autoFlipLastInventoryPriceCandidateIds = java.util.Collections.unmodifiableList(candidateIds);
        autoFlipLastInventoryPriceScanAtMs = System.currentTimeMillis();
        if (geWindowOpenForOverlay || autoFlipGeSessionInventoryCacheActive)
        {
            markAutoFlipGeSessionInventoryCacheDirty("inventory_container_changed");
        }

        boolean selectedInventoryItemQuantityChanged = selectedSetupItemId > 0
            && isAutoFlipInventoryItem(selectedSetupItemId)
            && selectedInventoryItemStillPresent
            && autoFlipOrdinarySellSetupItemQuantity > 0
            && selectedSetupItemQuantity > 0
            && selectedSetupItemQuantity != autoFlipOrdinarySellSetupItemQuantity;

        appendLine(
            ORDINARY_SELL_DEBUG_LOG,
            now()
                + " inventory_change_seen"
                + " selected_item_id=" + selectedSetupItemId
                + " selected_item_name=" + safe(autoFlipOrdinarySellSetupItemName)
                + " selected_item_quantity=" + autoFlipOrdinarySellSetupItemQuantity
                + " visible_still_present=" + selectedInventoryItemStillPresent
                + " visible_quantity=" + selectedSetupItemQuantity
                + " quantity_changed=" + selectedInventoryItemQuantityChanged
                + " candidate_count=" + candidateIds.size()
                + " state_item_id=" + autoFlipOrdinarySellSetupItemId
                + " state_item_name=" + safe(autoFlipOrdinarySellSetupItemName)
        );

        if (selectedSetupItemId > 0
            && isAutoFlipInventoryItem(selectedSetupItemId)
            && (!selectedInventoryItemStillPresent || selectedInventoryItemQuantityChanged))
        {
            appendLine(
                ORDINARY_SELL_DEBUG_LOG,
                now()
                    + " inventory_item_invalidated_after_change"
                    + " item_id=" + selectedSetupItemId
                    + " prev_qty=" + autoFlipOrdinarySellSetupItemQuantity
                    + " current_qty=" + selectedSetupItemQuantity
                    + " clearing_ordinary_sell_state=true"
            );
            autoFlipOrdinarySellSuppressVisibleUiReseedUntilMs = System.currentTimeMillis() + 2000L;
            clearAutoFlipOrdinarySellSetupState();
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event != null && isAutoFlipGrandExchangeInterfaceGroup(event.getGroupId()))
        {
            ensureAutoFlipBoardWarmAsync("ge_widget_loaded");
            beginAutoFlipGeSessionInventoryCache();
            autoFlipGeWidgetBoundsDirty = true;
            autoFlipStateDetectorLabelCacheDirty = true;
        }
    }

    @Subscribe
    public void onWidgetClosed(WidgetClosed event)
    {
        if (event != null && isAutoFlipGrandExchangeInterfaceGroup(event.getGroupId()))
        {
            clearAutoFlipGeSessionInventoryCache("ge_closed");
            clearAutoFlipGuidedSetupState();
            autoFlipGeWidgetBoundsDirty = true;
            autoFlipStateDetectorLabelCacheDirty = true;
        }
    }

    @Subscribe
    public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged event)
    {
        refreshAccountKey();

        if (event == null || event.getOffer() == null)
        {
            return;
        }

        GrandExchangeOffer offer = event.getOffer();
        String offerState = stateName(offer.getState());
        markAutoFlipGeSessionInventoryCacheDirty("ge_offer_changed_" + safe(offerState));
        String offerSide = inferSide(offerState);
        if ("BUY".equals(offerSide))
        {
            autoFlipMaybeApplyHeldInventoryPurchase(event.getSlot(), offer.getItemId(), stateName(offer.getState()), offer.getTotalQuantity(), offer.getQuantitySold(), clampAutoFlipGpToInt(offer.getPrice()), offer.getSpent());
        }
        else
        {
            autoFlipMaybeApplyHeldInventorySale(event.getSlot(), offer.getItemId(), offerState, offer.getTotalQuantity(), offer.getQuantitySold(), clampAutoFlipGpToInt(offer.getPrice()), offer.getSpent());
            reconcileAutoFlipBoardForActiveSellOffer(event.getSlot(), offer.getItemId(), offerState);
        }

        int retireSlot = resolveAutoFlipBoardRetireSlot(event.getSlot(), offer.getItemId());
        logAutoFlipVerbose(
            "AUTOFLIP_SELL_SLOT_RESOLUTION"
                + " ge_slot=" + event.getSlot()
                + " resolved_board_slot=" + retireSlot
                + " item_id=" + offer.getItemId()
        );
        if (retireSlot != event.getSlot())
        {
            logAutoFlipVerbose(
                "AUTOFLIP_OUT_OF_ORDER_SELL_SLOT_RESOLVED"
                    + " ge_slot=" + event.getSlot()
                    + " board_slot=" + retireSlot
                    + " item_id=" + offer.getItemId()
            );
        }
        maybeRetireAutoFlipBoardSlot(
            retireSlot,
            offer.getItemId(),
            stateName(offer.getState()),
            offer.getQuantitySold(),
            clampAutoFlipGpToInt(offer.getSpent())
        );

        recordSlotState(
            "grand_exchange_offer_changed",
            event.getSlot(),
            offer.getItemId(),
            stateName(offer.getState()),
            offer.getTotalQuantity(),
            offer.getQuantitySold(),
            clampAutoFlipGpToInt(offer.getPrice()),
            clampAutoFlipGpToInt(offer.getSpent())
        );
    }

    private void reconcileAutoFlipBoardForActiveSellOffer(int offerSlot, int itemId, String state)
    {
        if (itemId <= 0 || offerSlot < 0 || offerSlot >= 8 || isTerminalOfferState(state))
        {
            return;
        }

        java.util.List<AutoFlipBoardCard> cards = getAutoFlipBoardCardsSnapshot();
        if (cards == null || cards.isEmpty())
        {
            return;
        }

        int plannedSlot = -1;
        AutoFlipBoardCard displacedCard = null;
        for (AutoFlipBoardCard card : cards)
        {
            if (card == null)
            {
                continue;
            }
            if (card.getItemId() == itemId)
            {
                plannedSlot = card.getSlotIndex();
            }
            if (card.getSlotIndex() == offerSlot)
            {
                displacedCard = card;
            }
        }

        if (plannedSlot < 0)
        {
            return;
        }

        boolean explicitNativeSlot = !autoFlipLastSellTargetWasInventoryClick
            && autoFlipLastNativeButtonItemId == itemId
            && autoFlipLastNativeButtonSlotIndex == offerSlot;
        int displacedDestination = resolveAutoFlipDisplacedRecommendationDestination(
            offerSlot,
            plannedSlot,
            explicitNativeSlot
        );

        java.util.List<AutoFlipBoardCard> updated = new java.util.ArrayList<>(cards);
        while (updated.size() < 8)
        {
            updated.add(null);
        }

        for (int i = 0; i < updated.size(); i++)
        {
            AutoFlipBoardCard card = updated.get(i);
            if (card != null && card.getItemId() == itemId)
            {
                updated.set(i, null);
            }
        }

        if (displacedDestination >= 0
            && displacedCard != null
            && displacedCard.getItemId() != itemId)
        {
            updated.set(offerSlot, null);
            updated.set(displacedDestination, copyAutoFlipBoardCardForSlot(displacedCard, displacedDestination));
        }

        autoFlipBoardCards = java.util.Collections.unmodifiableList(updated);
        notifyAutoFlipSidePanelRefresh();
        logAutoFlipVerbose(
            "AUTOFLIP_ACTIVE_SELL_BOARD_RECONCILED"
                + " offer_slot=" + offerSlot
                + " offered_item_id=" + itemId
                + " planned_slot=" + plannedSlot
                + " displaced_item_id=" + (displacedCard == null ? 0 : displacedCard.getItemId())
                + " displaced_destination=" + displacedDestination
                + " explicit_native_slot=" + explicitNativeSlot
                + " inventory_origin=" + autoFlipLastSellTargetWasInventoryClick
        );
    }

    static int resolveAutoFlipDisplacedRecommendationDestination(
        int offerSlot,
        int plannedSlot,
        boolean explicitNativeSlot)
    {
        if (explicitNativeSlot || offerSlot < 0 || plannedSlot < 0 || offerSlot == plannedSlot)
        {
            return -1;
        }
        return plannedSlot;
    }

    private AutoFlipBoardCard copyAutoFlipBoardCardForSlot(AutoFlipBoardCard card, int slotIndex)
    {
        return new AutoFlipBoardCard(
            slotIndex,
            card.getItemId(),
            card.getItemName(),
            card.getQuantity(),
            card.getMaxQuantity(),
            card.getBuyPriceGp(),
            card.getSellPriceGp(),
            card.getProfitEachGp(),
            card.getTotalProfitGp(),
            card.getMaxHoldTimeLabel(),
            card.getRiskLabel(),
            card.getReason(),
            card.getMarketUrl(),
            card.getConfidence(),
            card.getRecommendationContext()
        );
    }

    private int resolveAutoFlipBoardRetireSlot(int offerSlot, int itemId)
    {
        if (itemId <= 0)
        {
            return offerSlot;
        }

        AutoFlipBoardCard card = findAutoFlipBoardCardBySlotIndexOrItemId(-1, itemId);
        if (card != null && card.getSlotIndex() >= 0)
        {
            return card.getSlotIndex();
        }

        return offerSlot;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        refreshAccountKey();

        if (event == null || event.getGroup() == null)
        {
            return;
        }

        String group = event.getGroup();
        String key = event.getKey();
        String newValue = event.getNewValue();

        if ("geoffer".equals(group) && key != null)
        {
            int slot = parseIntSafe(key, -1);
            if (slot < 0 || slot >= lastCanonicalSnapshots.length)
            {
                return;
            }

                if (newValue == null || newValue.isEmpty())
                {
                    String previousSnapshot = lastCanonicalSnapshots[slot];
                    if (previousSnapshot != null && !previousSnapshot.isEmpty())
                    {
                        String[] parts = previousSnapshot.split("\\|", -1);
                        if (parts.length >= 7)
                        {
                            maybeRetireAutoFlipBoardSlot(
                                slot,
                                parseIntSafe(parts[1], 0),
                                parts[2],
                                parseIntSafe(parts[4], 0),
                                parseIntSafe(parts[6], 0)
                            );
                        }
                    }
                    recordSlotState("config_changed_geoffer", slot, 0, "EMPTY", 0, 0, 0, 0);
                    return;
                }

            recordSlotState(
                "config_changed_geoffer",
                slot,
                jsonInt(newValue, "itemId", 0),
                jsonString(newValue, "state", "UNKNOWN"),
                jsonInt(newValue, "totalQuantity", 0),
                jsonInt(newValue, "quantitySold", 0),
                jsonInt(newValue, "price", 0),
                jsonInt(newValue, "spent", 0)
            );

            return;
        }

        if ("grandexchange".equals(group) && key != null && key.contains("tradeHistory"))
        {
            recordTradeHistoryConfig(key, newValue == null ? "" : newValue);
        }
    }

    private void sendOutboxAsync(String reason)
    {
        if (telemetryRuntime != null)
        {
            telemetryRuntime.sendOutboxAsync(reason);
        }
    }

    private void sendOutboxSync(String reason)
    {
        if (telemetryRuntime != null)
        {
            telemetryRuntime.sendOutboxSync(reason);
        }
    }

    private String telemetryEventTypeFromPayload(String payload)
    {
        String explicit = readJsonString(payload, "event_type", "");
        if (!explicit.isEmpty())
        {
            return explicit;
        }

        String event = readJsonString(payload, "event", "");
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
        String explicit = readJsonString(payload, "event_type", "");
        if (!explicit.isEmpty())
        {
            return isUploadWorthyTelemetryEventType(explicit);
        }

        String event = readJsonString(payload, "event", "");
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

        String state = readJsonString(payload, "state", "unknown").toLowerCase(java.util.Locale.ROOT);
        if (!state.contains("cancel") && !state.contains("bought") && !state.contains("sold"))
        {
            return false;
        }

        String derivedType = telemetryGeSlotEventType(payload);
        return isUploadWorthyTelemetryEventType(derivedType);
    }

    private boolean isUploadWorthyTelemetryEventType(String eventType)
    {
        if (eventType == null || eventType.isEmpty())
        {
            return false;
        }

        switch (eventType)
        {
            case "buy_offer_filled":
            case "sell_offer_filled":
            case "offer_cancelled":
            case "trade_completed":
            case "trade_abandoned":
            case "ge_trade_history_entry":
            case "trade_history_entry":
            case "active_offer_summary":
                return true;
            default:
                return false;
        }
    }

    private String telemetryGeSlotEventType(String payload)
    {
        if (readJsonBool(payload, "capacity_event", false))
        {
            return "ge_slot_observed";
        }

        String side = readJsonString(payload, "side", "unknown").toLowerCase(java.util.Locale.ROOT);
        String state = readJsonString(payload, "state", "unknown").toLowerCase(java.util.Locale.ROOT);
        int total = readJsonInt(payload, "total_quantity", 0);
        int filled = readJsonInt(payload, "quantity_sold", 0);

        if (state.contains("cancel"))
        {
            return "offer_cancelled";
        }
        if (total > 0 && filled >= total)
        {
            return side.contains("sell") ? "sell_offer_filled" : "buy_offer_filled";
        }
        if (filled > 0)
        {
            return side.contains("sell") ? "sell_offer_partially_filled" : "buy_offer_partially_filled";
        }
        if (total > 0)
        {
            return side.contains("sell") ? "sell_offer_created" : "buy_offer_created";
        }

        return "ge_slot_observed";
    }

    private long telemetryEventTsMs(String createdAt, String payload)
    {
        long payloadTs = readJsonLong(payload, "event_ts_ms", 0L);
        if (payloadTs > 0L)
        {
            return payloadTs;
        }

        String ts = readJsonString(payload, "ts", "");
        if (ts.isEmpty())
        {
            ts = createdAt;
        }

        try
        {
            return Instant.parse(ts).toEpochMilli();
        }
        catch (Exception ignored)
        {
            return System.currentTimeMillis();
        }
    }

    private String telemetryPlayerHash()
    {
        if (accountKey == null || accountKey.isEmpty() || "unknown_account".equals(accountKey))
        {
            return "player_unknown";
        }
        return accountKey.startsWith("player_") ? accountKey : "player_" + accountKey;
    }

    private int getCurrentWorld()
    {
        try
        {
            return client == null ? 0 : client.getWorld();
        }
        catch (Exception ignored)
        {
            return 0;
        }
    }

    private void enqueueTelemetryEvent(String eventType, String payloadFieldsJson)
    {
        String normalizedType = eventType == null || eventType.isEmpty() ? "plugin_health_summary" : eventType;
        long sequence = ++telemetryLocalSequence;
        long nowMs = System.currentTimeMillis();
        String eventId = "evt_" + sha256(telemetrySessionId + "|" + sequence + "|" + normalizedType).substring(0, 32);
        String fields = payloadFieldsJson == null || payloadFieldsJson.trim().isEmpty() ? "{}" : payloadFieldsJson.trim();
        String extra = fields.startsWith("{") && fields.endsWith("}") ? fields.substring(1, fields.length() - 1).trim() : "";

        StringBuilder payload = new StringBuilder();
        payload.append("{");
        payload.append("\"event_type\":\"").append(safe(normalizedType)).append("\",");
        payload.append("\"event\":\"").append(safe(normalizedType)).append("\",");
        payload.append("\"event_ts_ms\":").append(nowMs).append(",");
        payload.append("\"source\":\"").append(TELEMETRY_SOURCE).append("\",");
        payload.append("\"account_key\":\"").append(safe(accountKey)).append("\",");
        payload.append("\"session_id\":\"").append(safe(telemetrySessionId)).append("\",");
        payload.append("\"local_sequence\":").append(sequence).append(",");
        payload.append("\"ts\":\"").append(now()).append("\"");
        if (!extra.isEmpty())
        {
            payload.append(",").append(extra);
        }
        payload.append("}");

        enqueueOnce(eventId, payload.toString(), GE_DEBUG);
    }

    public String getAutoFlipTelemetryCurrentPackageJson()
    {
        if (telemetryRuntime != null)
        {
            return telemetryRuntime.getCurrentPackageJson();
        }

        return "{\"schema\":\"" + TELEMETRY_BATCH_SCHEMA + "\",\"batch_id\":\"batch_empty\",\"player_hash\":\"\",\"session_id\":\"" + safe(telemetrySessionId) + "\",\"event_count\":0,\"events\":[]}";
    }

    public int getAutoFlipTelemetryCurrentPackageEventCount()
    {
        if (telemetryRuntime != null)
        {
            return telemetryRuntime.getCurrentPackageEventCount();
        }

        return 0;
    }

    public long getAutoFlipTelemetryNextSendCountdownMs()
    {
        if (telemetryRuntime != null)
        {
            return telemetryRuntime.getNextSendCountdownMs();
        }

        return 0L;
    }

    public boolean isAutoFlipTelemetrySendInProgress()
    {
        if (telemetryRuntime != null)
        {
            return telemetryRuntime.isSendInProgress();
        }

        return false;
    }

    public String getAutoFlipTelemetryPendingSummaryJson()
    {
        if (telemetryRuntime != null)
        {
            return telemetryRuntime.getPendingSummaryJson();
        }

        return "{\"schema\":\"autoflip.plugin_pending_slot_summary.v1\",\"pending_count\":0,\"slots\":[]}";
    }

    public int getAutoFlipTelemetryPendingSummaryCount()
    {
        if (telemetryRuntime != null)
        {
            return telemetryRuntime.getPendingSummaryCount();
        }

        try
        {
            int count = 0;
            for (int slot = 0; slot < slotPlacedTsMs.length; slot++)
            {
                if (slotPlacedTsMs[slot] > 0L && slotPlacedItemId[slot] > 0)
                {
                    count++;
                }
            }
            return count;
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("getAutoFlipTelemetryPendingSummaryCount", error);
            return 0;
        }
    }

    public java.util.List<AutoFlipTelemetryArchiveEntry> getAutoFlipTelemetrySentHistoryEntries(int maxEntries)
    {
        if (telemetryRuntime != null)
        {
            return telemetryRuntime.getSentHistoryEntries(maxEntries);
        }

        return java.util.Collections.emptyList();
    }

    private static Set<String> parseStringArray(String json, String key)
    {
        Set<String> values = new HashSet<>();

        Pattern arrayPattern = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
        Matcher arrayMatcher = arrayPattern.matcher(json);

        if (!arrayMatcher.find())
        {
            return values;
        }

        String body = arrayMatcher.group(1);
        Matcher itemMatcher = Pattern.compile("\\\"([^\\\"]+)\\\"").matcher(body);

        while (itemMatcher.find())
        {
            values.add(itemMatcher.group(1));
        }

        return values;
    }

    private static String extractRejectedBlock(String json)
    {
        Pattern p = Pattern.compile("\\\"rejected\\\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        if (!m.find())
        {
            return "";
        }

        String body = m.group(1).trim();
        return body;
    }

    private static String extractPayloadObject(String line)
    {
        String marker = "\"payload\":";
        int start = line.indexOf(marker);
        if (start < 0)
        {
            return "";
        }

        int objectStart = line.indexOf('{', start + marker.length());
        if (objectStart < 0)
        {
            return "";
        }

        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = objectStart; i < line.length(); i++)
        {
            char c = line.charAt(i);

            if (escape)
            {
                escape = false;
                continue;
            }

            if (c == '\\')
            {
                escape = true;
                continue;
            }

            if (c == '"')
            {
                inString = !inString;
                continue;
            }

            if (inString)
            {
                continue;
            }

            if (c == '{')
            {
                depth++;
            }
            else if (c == '}')
            {
                depth--;
                if (depth == 0)
                {
                    return line.substring(objectStart, i + 1);
                }
            }
        }

        return "";
    }
    private boolean shouldConsumeAutoFlipSetupCoverClick(int x, int y)
    {
        try
        {
            if (!autoFlipOverlayActive || !geWindowOpenForOverlay)
            {
                return false;
            }

            String title = geHeaderTextForOverlay;
            if (title == null || !title.startsWith("Grand Exchange: Set up offer"))
            {
                return false;
            }

            if (!readBoolConfig("setup.custom.ui.enabled", true))
            {
                return false;
            }

            Rectangle header = geHeaderBoundsForOverlay;
            if (header == null)
            {
                return false;
            }

            Rectangle cover = new Rectangle(
                header.x + readAutoFlipSetupConfigInt("setup.cover.x", -5),
                header.y + readAutoFlipSetupConfigInt("setup.cover.y", 27),
                readAutoFlipSetupConfigInt("setup.cover.w", 484),
                readAutoFlipSetupConfigInt("setup.cover.h", 270)
            );

            if (!cover.contains(x, y))
            {
                return false;
            }

            // Allow intentional native holes only.
            if (pointInsideAutoFlipSetupRect(x, y, header, "setup.item.icon", 56, 48, 42, 40)) { return false; }
            if (pointInsideAutoFlipSetupRect(x, y, header, "setup.item.description", 160, 32, 318, 92)) { return false; }
            if (pointInsideAutoFlipSetupRect(x, y, header, "setup.quantity.value", 33, 150, 152, 23)) { return false; }
            if (pointInsideAutoFlipSetupRect(x, y, header, "setup.price.value", 248, 150, 190, 23)) { return false; }
            if (pointInsideAutoFlipSetupRect(x, y, header, "setup.quantity.quick", 175, 177, 32, 22)) { return false; }
            if (pointInsideAutoFlipSetupRect(x, y, header, "setup.quick", 345, 177, 33, 23)) { return false; }
            if (pointInsideAutoFlipSetupRect(x, y, header, "setup.final.price", 34, 205, 409, 23)) { return false; }
            if (pointInsideAutoFlipSetupRect(x, y, header, "setup.confirm", 161, 244, 152, 39)) { return false; }
            if (pointInsideAutoFlipSetupRect(x, y, header, "setup.back.arrow", 17, 248, 43, 42)) { return false; }

            if (readBoolConfig("debug.mouse.logs.enabled", false))
            {
                logAutoFlipVerbose("AUTOFLIP_SETUP_COVER_CLICK_BLOCK x=" + x + " y=" + y);
            }

            return true;
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }
    private boolean pointInsideAutoFlipSetupRect(int x, int y, Rectangle header, String prefix, int defaultX, int defaultY, int defaultW, int defaultH)
    {
        if (header == null)
        {
            return false;
        }

        Rectangle rect = new Rectangle(
            header.x + readAutoFlipSetupConfigInt(prefix + ".x", defaultX),
            header.y + readAutoFlipSetupConfigInt(prefix + ".y", defaultY),
            readAutoFlipSetupConfigInt(prefix + ".w", defaultW),
            readAutoFlipSetupConfigInt(prefix + ".h", defaultH)
        );

        return rect.contains(x, y);
    }
    private int readAutoFlipSetupConfigInt(String key, int fallback)
    {
        return fallback;
    }
    @Override
    public MouseEvent mouseClicked(MouseEvent mouseEvent)
    {
        
        // AUTOFLIP_GUIDED_SETUP_FLOW_CLICK_HOOK_V17
        recordAutoFlipGuidedSetupClickIfNeeded(mouseEvent.getX(), mouseEvent.getY());try
        {
            if (mouseEvent != null)
            {
                autoFlipLastMouseX = mouseEvent.getX();
                autoFlipLastMouseY = mouseEvent.getY();
                rememberAutoFlipNativeButtonHoleSelection(mouseEvent.getX(), mouseEvent.getY());
                if (shouldConsumeAutoFlipSetupCoverClick(mouseEvent.getX(), mouseEvent.getY()))
                {
                    mouseEvent.consume();
                    return mouseEvent;
                }
                if (autoFlipSuppressNextMenuClick)
                {
                    autoFlipSuppressNextMenuClick = false;
                    if (readBoolConfig("debug.mouse.logs.enabled", false)) { logAutoFlipVerbose("AUTOFLIP_CLICK_ACTION_SUPPRESSED_AFTER_PRESS x=" + mouseEvent.getX() + " y=" + mouseEvent.getY()); }
                    mouseEvent.consume();
                    return mouseEvent;
                }

                if (readBoolConfig("debug.mouse.logs.enabled", false)) { logAutoFlipVerbose("AUTOFLIP_CLICK_ACTION x=" + mouseEvent.getX() + " y=" + mouseEvent.getY() + " geOpen=" + geWindowOpenForOverlay + " active=" + autoFlipOverlayActive); }

                if (handleAutoFlipQuantityChatboxInjectClick(mouseEvent.getX(), mouseEvent.getY()))
                {
                    logAutoFlipVerbose("AUTOFLIP_CLICK_ACTION_RESULT handled=true");

                    mouseEvent.consume();
                    return mouseEvent;
                }

                if (handleAutoFlipPriceChatboxConfiguredInjectClick(mouseEvent.getX(), mouseEvent.getY()))
                {
                    // AUTOFLIP_CHATBOX_CONFIG_CLICK_ROUTE_V1
                    logAutoFlipVerbose("AUTOFLIP_CLICK_ACTION_RESULT handled=true");
                    mouseEvent.consume();
                    return mouseEvent;
                }
                // Quantity/price helper clicks are allowed above this point.
                // Board/card ghost clicks are allowed only on the main GE board.
                if (!isAutoFlipMainGeBoardScreen())
                {
                    if (readBoolConfig("debug.mouse.logs.enabled", false)) { logAutoFlipVerbose("AUTOFLIP_CLICK_ACTION_RESULT handled=false reason=not_main_ge_board title=" + safe(geHeaderTextForOverlay)); }
                    return mouseEvent;
                }
                boolean handled = false;

                try
                {
                    // AUTOFLIP_SOURCE_TRUTH_CLICK_ROUTING_V1
                    handled = handleAutoFlipMenuClick(mouseEvent.getX(), mouseEvent.getY());
                }
                catch (Throwable menuError)
                {
                    logAutoFlipUiError("mouseClicked.menu", menuError);
                    logAutoFlipVerbose("AUTOFLIP_CLICK_ACTION_ERROR menu=" + menuError);
                }

                if (readBoolConfig("debug.mouse.logs.enabled", false)) { logAutoFlipVerbose("AUTOFLIP_CLICK_ACTION_RESULT handled=" + handled); }

                if (handled || shouldConsumeAutoFlipOverlayClick(mouseEvent.getX(), mouseEvent.getY()))
                {
                    mouseEvent.consume();
                }
            }

            return mouseEvent;
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("mouseClicked", error);
            logAutoFlipVerbose("AUTOFLIP_CLICK_ACTION_ERROR outer=" + error);
            return mouseEvent;
        }
    }

    @Override
    public MouseEvent mousePressed(MouseEvent mouseEvent)
    {
        try
        {
            if (mouseEvent != null)
            {
                autoFlipLastMouseX = mouseEvent.getX();
                autoFlipLastMouseY = mouseEvent.getY();
                rememberAutoFlipNativeButtonHoleSelection(mouseEvent.getX(), mouseEvent.getY());
                if (shouldConsumeAutoFlipSetupCoverClick(mouseEvent.getX(), mouseEvent.getY()))
                {
                    mouseEvent.consume();
                    return mouseEvent;
                }
                boolean menuHandledOnPress = false;
                try
                {
                    // Handle dropdown/menu clicks on press so the first click opens/selects reliably.
                    menuHandledOnPress = handleAutoFlipMenuClick(mouseEvent.getX(), mouseEvent.getY());
                }
                catch (Throwable menuPressError)
                {
                    logAutoFlipUiError("mousePressed.menu", menuPressError);
                }

                if (menuHandledOnPress)
                {
                    autoFlipSuppressNextMenuClick = true;
                    mouseEvent.consume();
                    if (readBoolConfig("debug.mouse.logs.enabled", false)) { logAutoFlipVerbose("AUTOFLIP_PRESS_MENU_HANDLED x=" + mouseEvent.getX() + " y=" + mouseEvent.getY()); }
                    return mouseEvent;
                }

                boolean consumed = shouldConsumeAutoFlipOverlayClick(mouseEvent.getX(), mouseEvent.getY());

                if (!consumed && isInsideConfiguredAutoFlipPriceChatboxInjectButton(mouseEvent.getX(), mouseEvent.getY()))
                {
                    // AUTOFLIP_CHATBOX_CONFIG_PRESS_BLOCK_V1
                    consumed = true;
                    if (readBoolConfig("debug.mouse.logs.enabled", false)) { logAutoFlipVerbose("AUTOFLIP_CHATBOX_CONFIG_PRESS_BLOCK x=" + mouseEvent.getX() + " y=" + mouseEvent.getY()); }
                }

                Rectangle directQuantityChatboxPressBounds = getAutoFlipQuantityChatboxButtonBounds();
                if (!consumed && directQuantityChatboxPressBounds != null && directQuantityChatboxPressBounds.contains(mouseEvent.getX(), mouseEvent.getY()))
                {
                    consumed = true;
                    if (readBoolConfig("debug.mouse.logs.enabled", false)) { logAutoFlipVerbose("AUTOFLIP_QUANTITY_CHATBOX_PRESS_BLOCK x=" + mouseEvent.getX() + " y=" + mouseEvent.getY()); }
                }

                Rectangle directChatboxPressBounds = getAutoFlipPriceChatboxButtonBounds();
                if (!consumed && directChatboxPressBounds != null && directChatboxPressBounds.contains(mouseEvent.getX(), mouseEvent.getY()))
                {
                    // AUTOFLIP_CHATBOX_INJECT_DIRECT_PRESS_BLOCK_V2
                    consumed = true;
                    if (readBoolConfig("debug.mouse.logs.enabled", false)) { logAutoFlipVerbose("AUTOFLIP_CHATBOX_INJECT_DIRECT_PRESS_BLOCK x=" + mouseEvent.getX() + " y=" + mouseEvent.getY()); }
                }

                if (readBoolConfig("debug.mouse.logs.enabled", false)) { logAutoFlipVerbose("AUTOFLIP_PRESS_BLOCK_CHECK x=" + mouseEvent.getX() + " y=" + mouseEvent.getY() + " consumed=" + consumed); }

                if (consumed)
                {
                    if (!isAutoFlipMainGeBoardScreen())
                    {
                        return mouseEvent;
                    }
                    // AUTOFLIP_FULL_CARD_PRESS_BLOCK_V1
                    // Consume press so native GE Buy/Sell buttons behind the custom card do not receive it.
                    mouseEvent.consume();

                }
            }

            return mouseEvent;
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("mousePressed", error);
            return mouseEvent;
        }
    }

    @Override
    public MouseEvent mouseReleased(MouseEvent mouseEvent)
    {
        try
        {
            if (mouseEvent != null)
            {
                autoFlipLastMouseX = mouseEvent.getX();
                autoFlipLastMouseY = mouseEvent.getY();
                if (shouldConsumeAutoFlipSetupCoverClick(mouseEvent.getX(), mouseEvent.getY()))
                {
                    mouseEvent.consume();
                    return mouseEvent;
                }
                Rectangle directChatboxReleaseBounds = getAutoFlipPriceChatboxButtonBounds();
                if ((directChatboxReleaseBounds != null && directChatboxReleaseBounds.contains(mouseEvent.getX(), mouseEvent.getY()))
                    || shouldConsumeAutoFlipOverlayClick(mouseEvent.getX(), mouseEvent.getY()))
                {
                    // AUTOFLIP_CHATBOX_INJECT_DIRECT_RELEASE_BLOCK_V2
                    mouseEvent.consume();
                }
            }

            return mouseEvent;
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("mouseReleased", error);
            return mouseEvent;
        }
    }

    @Override
    public MouseEvent mouseEntered(MouseEvent mouseEvent)
    {
        return mouseEvent;
    }

    @Override
    public MouseEvent mouseExited(MouseEvent mouseEvent)
    {
        return mouseEvent;
    }

    @Override
    public MouseEvent mouseDragged(MouseEvent mouseEvent)
    {
        try
        {
            if (mouseEvent != null)
            {
                autoFlipLastMouseX = mouseEvent.getX();
                autoFlipLastMouseY = mouseEvent.getY();

                if ((isInsideConfiguredAutoFlipPriceChatboxInjectButton(mouseEvent.getX(), mouseEvent.getY()))
                    || shouldConsumeAutoFlipOverlayClick(mouseEvent.getX(), mouseEvent.getY()))
                {
                    // AUTOFLIP_BLOCK_NATIVE_DRAG_THROUGH_V1
                    mouseEvent.consume();
                }
            }

            return mouseEvent;
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("mouseDragged", error);
            return mouseEvent;
        }
    }

    @Override
    public MouseEvent mouseMoved(MouseEvent mouseEvent)
    {
        try
        {
            if (mouseEvent != null)
            {
                autoFlipLastMouseX = mouseEvent.getX();
                autoFlipLastMouseY = mouseEvent.getY();

                if (isInsideConfiguredAutoFlipPriceChatboxInjectButton(mouseEvent.getX(), mouseEvent.getY()))                 {
                    // AUTOFLIP_BLOCK_NATIVE_HOVER_TOOLTIP_V2
                    // Consume mouse move and remove native GE Buy/Sell hover entries over blocked AutoFlip card areas.
                    suppressAutoFlipNativeGeHoverMenu(mouseEvent.getX(), mouseEvent.getY());
                    mouseEvent.consume();
                }
            }

            return mouseEvent;
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("mouseMoved", error);
            return mouseEvent;
        }
    }

    private boolean dispatchAutoFlipTextInputKeyEvent(KeyEvent keyEvent)
    {
        if (keyEvent == null || !isAutoFlipTextInputActive())
        {
            return false;
        }

        int eventId = keyEvent.getID();
        if (eventId == KeyEvent.KEY_PRESSED)
        {
            keyPressed(keyEvent);
        }
        else if (eventId == KeyEvent.KEY_TYPED)
        {
            keyTyped(keyEvent);
        }
        else if (eventId == KeyEvent.KEY_RELEASED)
        {
            keyReleased(keyEvent);
        }

        return keyEvent.isConsumed();
    }

    @Override
    public void keyTyped(KeyEvent keyEvent)
    {
        try
        {
            if (keyEvent == null || !isAutoFlipTextInputActive())
            {
                return;
            }

            char c = keyEvent.getKeyChar();

            if (c == '\n' || c == '\r')
            {
                commitAutoFlipTextInputs();
                keyEvent.consume();
                return;
            }

            if (c == '\b')
            {
                removeAutoFlipInputChar();
                keyEvent.consume();
                return;
            }

            if (isAcceptedAutoFlipInputChar(c))
            {
                if (isDuplicateAutoFlipPressedInputChar(c))
                {
                    keyEvent.consume();
                    return;
                }
                appendAutoFlipInputChar(c);
                keyEvent.consume();
                return;
            }

            keyEvent.consume();
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("keyTyped", error);
            if (keyEvent != null)
            {
                keyEvent.consume();
            }
        }
    }

    @Override
    public void keyPressed(KeyEvent keyEvent)
    {
        // AUTOFLIP_KEY_SHIFT_DOWN_PATCH
        if (keyEvent != null && keyEvent.isShiftDown())
        {
            autoFlipShiftDown = true;
        }
        try
        {
                        // AUTOFLIP_GUIDED_SETUP_EARLY_ENTER_HOOK_V23
            if (keyEvent != null && keyEvent.getKeyCode() == KeyEvent.VK_ENTER)
            {
                recordAutoFlipGuidedSetupEnterIfNeeded();
            }
if (keyEvent == null || !isAutoFlipTextInputActive())
            {
                return;
            }

            int code = keyEvent.getKeyCode();

            if (code == KeyEvent.VK_ENTER)
            {
                commitAutoFlipTextInputs();
                keyEvent.consume();
                return;
            }

            if (code == KeyEvent.VK_ESCAPE)
            {
                autoFlipBudgetInputActive = false;
                autoFlipHoursInputActive = false;
                autoFlipBudgetInputBuffer = "";
                autoFlipHoursInputBuffer = "";
                keyEvent.consume();
                return;
            }

            if (code == KeyEvent.VK_BACK_SPACE)
            {
                removeAutoFlipInputChar();
                keyEvent.consume();
                return;
            }

            char pressedChar = autoFlipInputCharForKeyPressed(keyEvent);
            if (pressedChar != 0 && isAcceptedAutoFlipInputChar(pressedChar))
            {
                if (isRecentAutoFlipPressedInputChar(pressedChar))
                {
                    keyEvent.consume();
                    return;
                }
                appendAutoFlipInputChar(pressedChar);
                rememberAutoFlipPressedInputChar(pressedChar);
                keyEvent.consume();
                return;
            }

            if (keyEvent.getKeyChar() != KeyEvent.CHAR_UNDEFINED)
            {
                return;
            }

            keyEvent.consume();
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("keyPressed", error);
            if (keyEvent != null)
            {
                keyEvent.consume();
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent keyEvent)
    {
        // AUTOFLIP_KEY_SHIFT_DOWN_PATCH
        if (keyEvent != null && keyEvent.getKeyCode() == KeyEvent.VK_SHIFT)
        {
            autoFlipShiftDown = false;
        }
        else if (keyEvent != null)
        {
            autoFlipShiftDown = keyEvent.isShiftDown();
        }
        try
        {
            if (keyEvent != null && isAutoFlipTextInputActive())
            {
                keyEvent.consume();
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("keyReleased", error);
            if (keyEvent != null)
            {
                keyEvent.consume();
            }
        }
    }
    @Override
    public MouseWheelEvent mouseWheelMoved(MouseWheelEvent mouseWheelEvent)
    {
        if (mouseWheelEvent == null)
        {
            return mouseWheelEvent;
        }

        if (!isEditModeUsable())
        {
            return mouseWheelEvent;
        }

        // AUTOFLIP_CHATBOX_CONFIG_WHEEL_ROUTE_V1
        Rectangle configChatboxWheelBounds = getConfiguredAutoFlipPriceChatboxInjectBounds();
        String priceChatboxWheelPrefix = "setup.price.chatbox.inject.";
        if (configChatboxWheelBounds != null && configChatboxWheelBounds.contains(mouseWheelEvent.getX(), mouseWheelEvent.getY()))
        {
            String key;
            int fallback;
            int min;
            int max;

            if (mouseWheelEvent.isControlDown())
            {
                key = priceChatboxWheelPrefix + "w";
                fallback = configChatboxWheelBounds.width;
                min = 40;
                max = 900;
            }
            else if (mouseWheelEvent.isAltDown())
            {
                key = priceChatboxWheelPrefix + "h";
                fallback = configChatboxWheelBounds.height;
                min = 16;
                max = 90;
            }
            else if (mouseWheelEvent.isShiftDown())
            {
                key = priceChatboxWheelPrefix + "x";
                fallback = configChatboxWheelBounds.x;
                min = 0;
                max = 900;
            }
            else
            {
                key = priceChatboxWheelPrefix + "y";
                fallback = configChatboxWheelBounds.y;
                min = 520;
                max = 980;
            }

            int current = readIntConfig(key, fallback);
            int step = mouseWheelEvent.getWheelRotation() < 0 ? -1 : 1;
            if (mouseWheelEvent.isControlDown() || mouseWheelEvent.isAltDown())
            {
                step = -step;
            }

            int next = Math.max(min, Math.min(max, current + step));
            writeIntConfig(key, next);
            if (readBoolConfig("debug.mouse.logs.enabled", false)) { logAutoFlipVerbose("AUTOFLIP_CHATBOX_CONFIG_WHEEL key=" + key + " value=" + next); }
            mouseWheelEvent.consume();
            return mouseWheelEvent;
        }
        EditHandleHit hit = findEditHandle(mouseWheelEvent.getX(), mouseWheelEvent.getY());
        if (hit == null)
        {
            return mouseWheelEvent;
        }

        if ("BUY_HOLE".equals(hit.name) || "SELL_HOLE".equals(hit.name) || "SETUP_ITEM_ICON".equals(hit.name) || "SETUP_QUICK".equals(hit.name) || "SETUP_CONFIRM".equals(hit.name) || "CHATBOX_INJECT".equals(hit.name))
        {
            // AUTOFLIP_TUNABLE_NATIVE_HOLE_WHEEL_EDIT_V1
            // Wheel = Y, Shift+Wheel = X, Ctrl+Wheel = Width, Alt+Wheel = Height.
            String prefix;
            if ("SETUP_ITEM_ICON".equals(hit.name))
            {
                // AUTOFLIP_SETUP_ITEM_TARGET_WHEEL_EDIT_V1
                prefix = "setup.item.icon.";
            }
            else if ("SETUP_QUICK".equals(hit.name))
            {
                // AUTOFLIP_SETUP_QUICK_SET_WHEEL_EDIT_V1
                prefix = "setup.quick.";
            }
            else if ("SETUP_CONFIRM".equals(hit.name))
            {
                // AUTOFLIP_SETUP_CONFIRM_WHEEL_EDIT_V1
                prefix = "setup.confirm.";
            }
            else if ("CHATBOX_INJECT".equals(hit.name))
            {
                // AUTOFLIP_CHATBOX_INJECT_WHEEL_EDIT_V1
            }
            else
            {
                prefix = "BUY_HOLE".equals(hit.name) ? "native.buy.hole." : "native.sell.hole.";
            }
            String key;
            int fallback;
            int min;
            int max;

            if (mouseWheelEvent.isControlDown())
            {
                key = priceChatboxWheelPrefix + "w";
                fallback = hit.bounds.width;
                min = 8;
                max = 120;
            }
            else if (mouseWheelEvent.isAltDown())
            {
                key = priceChatboxWheelPrefix + "h";
                fallback = hit.bounds.height;
                min = 8;
                max = 120;
            }
            else if (mouseWheelEvent.isShiftDown())
            {
                key = priceChatboxWheelPrefix + "x";
                fallback = hit.xKey == null ? 0 : readIntConfig(hit.xKey, 0);
                min = -80;
                max = 180;
            }
            else
            {
                key = priceChatboxWheelPrefix + "y";
                fallback = hit.yKey == null ? 0 : readIntConfig(hit.yKey, 0);
                min = -80;
                max = 180;
            }

            // AUTOFLIP_CHATBOX_INJECT_EDIT_RANGE_V1
            // Chatbox controls live near the bottom of the client, so they need full-screen ranges.
            if ("CHATBOX_INJECT".equals(hit.name))
            {
                if (key.endsWith(".x"))
                {
                    min = 0;
                    max = 900;
                }
                else if (key.endsWith(".y"))
                {
                    min = 520;
                    max = 980;
                }
                else if (key.endsWith(".w"))
                {
                    min = 40;
                    max = 900;
                }
                else if (key.endsWith(".h"))
                {
                    min = 16;
                    max = 90;
                }
            }
            // AUTOFLIP_SETUP_EDIT_RANGE_EXPAND_V1
            // Setup-screen controls need a wider coordinate range than GE slot/card-local controls.
            else if ("SETUP_ITEM_ICON".equals(hit.name) || "SETUP_QUICK".equals(hit.name) || "SETUP_CONFIRM".equals(hit.name))
            {
                if (key.endsWith(".x"))
                {
                    min = -200;
                    max = 520;
                }
                else if (key.endsWith(".y"))
                {
                    min = -120;
                    max = 360;
                }
                else if (key.endsWith(".w") || key.endsWith(".h"))
                {
                    min = 8;
                    max = 240;
                }
            }

            int current = readIntConfig(key, fallback);
            int step = mouseWheelEvent.getWheelRotation() < 0 ? -1 : 1;

            if (mouseWheelEvent.isControlDown() || mouseWheelEvent.isAltDown())
            {
                step = -step;
            }

            int next = Math.max(min, Math.min(max, current + step));
            writeIntConfig(key, next);
            mouseWheelEvent.consume();
            return mouseWheelEvent;
        }

        String fontKey = fontKeyForHandle(hit.name);
        if (fontKey == null)
        {
            return mouseWheelEvent;
        }

        double current = readDoubleConfig(fontKey, defaultFontForHandle(hit.name));
        double next = current + (mouseWheelEvent.getWheelRotation() < 0 ? 0.25 : -0.25);
        next = Math.max(5.00, Math.min(24.00, next));

        writeStringConfig(fontKey, String.format(java.util.Locale.US, "%.2f", next));
        mouseWheelEvent.consume();
        return mouseWheelEvent;
    }

    private boolean isEditModeUsable()
    {
        return false;
    }

    private EditHandleHit findEditHandle(int mouseX, int mouseY)
    {
        // AUTOFLIP_CHATBOX_INJECT_EARLY_EDIT_HITBOX_V1
        // Chatbox button lives outside the GE header/card coordinate flow.
        Rectangle earlyChatboxInjectBounds = getAutoFlipPriceChatboxButtonBounds();
        if (earlyChatboxInjectBounds != null && earlyChatboxInjectBounds.contains(mouseX, mouseY))
        {
            if (readBoolConfig("debug.mouse.logs.enabled", false)) { logAutoFlipVerbose("AUTOFLIP_CHATBOX_INJECT_WHEEL_HIT x=" + mouseX + " y=" + mouseY); }
            return new EditHandleHit(
                "CHATBOX_INJECT",
                "setup.price.chatbox.inject.x",
                "setup.price.chatbox.inject.y",
                earlyChatboxInjectBounds
            );
        }

        String geTitle = getGeHeaderTextForOverlay();
        Rectangle headerBounds = getGeHeaderBoundsForOverlay();

        if (geTitle != null && geTitle.startsWith("Grand Exchange:") && headerBounds != null)
        {
            Rectangle chatboxInjectBounds = getAutoFlipPriceChatboxButtonBounds();
            if (chatboxInjectBounds != null && chatboxInjectBounds.contains(mouseX, mouseY))
            {
                // AUTOFLIP_CHATBOX_INJECT_SCROLL_HITBOX_V1
                return new EditHandleHit(
                    "CHATBOX_INJECT",
                    "setup.price.chatbox.inject.x",
                    "setup.price.chatbox.inject.y",
                    chatboxInjectBounds
                );
            }

            Rectangle setupItemIconBounds = new Rectangle(
                headerBounds.x + readIntConfig("setup.item.icon.x", 56),
                headerBounds.y + readIntConfig("setup.item.icon.y", 48),
                readIntConfig("setup.item.icon.w", 44),
                readIntConfig("setup.item.icon.h", 44)
            );

            if (setupItemIconBounds.contains(mouseX, mouseY))
            {
                // AUTOFLIP_SETUP_ITEM_ICON_HEADER_HITBOX_V1
                return new EditHandleHit(
                    "SETUP_ITEM_ICON",
                    "setup.item.icon.x",
                    "setup.item.icon.y",
                    setupItemIconBounds
                );
            }

            Rectangle setupQuickBounds = new Rectangle(
                headerBounds.x + readIntConfig("setup.quick.x", 345),
                headerBounds.y + readIntConfig("setup.quick.y", 177),
                readIntConfig("setup.quick.w", 33),
                readIntConfig("setup.quick.h", 23)
            );

            if (setupQuickBounds.contains(mouseX, mouseY))
            {
                // AUTOFLIP_SETUP_QUICK_SET_HEADER_HITBOX_V1
                return new EditHandleHit(
                    "SETUP_QUICK",
                    "setup.quick.x",
                    "setup.quick.y",
                    setupQuickBounds
                );
            }

            Rectangle setupConfirmBounds = new Rectangle(
                headerBounds.x + readIntConfig("setup.confirm.x", 161),
                headerBounds.y + readIntConfig("setup.confirm.y", 244),
                readIntConfig("setup.confirm.w", 152),
                readIntConfig("setup.confirm.h", 39)
            );

            if (setupConfirmBounds.contains(mouseX, mouseY))
            {
                // AUTOFLIP_SETUP_CONFIRM_HEADER_HITBOX_V1
                return new EditHandleHit(
                    "SETUP_CONFIRM",
                    "setup.confirm.x",
                    "setup.confirm.y",
                    setupConfirmBounds
                );
            }
        }

        for (int slot = 0; slot < geSlotBoundsForOverlay.length; slot++)
        {
            Rectangle slotBounds = geSlotBoundsForOverlay[slot];
            if (slotBounds == null)
            {
                continue;
            }

            Rectangle base = new Rectangle(slotBounds);

            int cardOffsetX = readIntConfig("card.offset.x", 0);
            int cardOffsetY = readIntConfig("card.offset.y", 0);
            int cardWidth = readIntConfig("card.width", 112);
int x = base.x + cardOffsetX;
            int y = base.y + cardOffsetY;

            int iconX = x + readIntConfig("card.icon.x", 35);
            int iconY = y + readIntConfig("card.icon.y", 24);
            int iconSize = readIntConfig("card.icon.size", 30);

            EditHandleHit[] hits = new EditHandleHit[] {
                new EditHandleHit(
                    "ICON_BOX",
                    "card.icon.box.offset.x",
                    "card.icon.box.offset.y",
                    new Rectangle(
                        iconX + readIntConfig("card.icon.box.offset.x", 0),
                        iconY + readIntConfig("card.icon.box.offset.y", 0),
                        iconSize,
                        iconSize
                    )
                ),
                new EditHandleHit(
                    "ICON_GLOW",
                    "card.icon.glow.offset.x",
                    "card.icon.glow.offset.y",
                    new Rectangle(
                        iconX - 6 + readIntConfig("card.icon.glow.offset.x", 0),
                        iconY - 5 + readIntConfig("card.icon.glow.offset.y", 0),
                        iconSize + 12,
                        iconSize + 12
                    )
                ),
                new EditHandleHit(
                    "QUANTITY",
                    "card.quantity.x",
                    "card.quantity.y",
                    new Rectangle(
                        x + readIntConfig("card.quantity.x", 0) + ((cardWidth - readIntConfig("edit.quantity.handle.width", 70)) / 2),
                        y + readIntConfig("card.quantity.y", 81) - 11,
                        cardWidth,
                        14
                    )
                ),
                new EditHandleHit(
                    "EACH_LABEL",
                    "card.profit.label.x",
                    "card.profit.label.y",
                    new Rectangle(x + readIntConfig("card.profit.label.x", 8), y + readIntConfig("card.profit.label.y", 95) - readIntConfig("edit.text.box.baseline.pad.y", 11), readIntConfig("edit.each.label.box.width", 34), readIntConfig("edit.text.box.height", 15))
                ),
                new EditHandleHit(
                    "EACH_VALUE",
                    "card.profit.value.x",
                    "card.profit.value.y",
                    new Rectangle(x + readIntConfig("card.profit.value.x", 8), y + readIntConfig("card.profit.value.y", 107) - readIntConfig("edit.text.box.baseline.pad.y", 11), readIntConfig("edit.each.value.box.width", 78), readIntConfig("edit.text.box.height", 15))
                ),
                new EditHandleHit(
                    "TOTAL_LABEL",
                    "card.total.label.x",
                    "card.total.label.y",
                    new Rectangle(x + readIntConfig("card.total.label.x", 8), y + readIntConfig("card.total.label.y", 119) - readIntConfig("edit.text.box.baseline.pad.y", 11), readIntConfig("edit.total.label.box.width", 36), readIntConfig("edit.text.box.height", 15))
                ),
                new EditHandleHit(
                    "TOTAL_VALUE",
                    "card.total.value.x",
                    "card.total.value.y",
                    new Rectangle(x + readIntConfig("card.total.value.x", 8), y + readIntConfig("card.total.value.y", 131) - readIntConfig("edit.text.box.baseline.pad.y", 11), readIntConfig("edit.total.value.box.width", 92), readIntConfig("edit.text.box.height", 15))
                ),
                new EditHandleHit(
                    "BUY_HOLE",
                    "native.buy.hole.x",
                    "native.buy.hole.y",
                    new Rectangle(
                        x + readIntConfig("native.buy.hole.x", 2),
                        y + readIntConfig("native.buy.hole.y", 61),
                        readIntConfig("native.buy.hole.w", 44),
                        readIntConfig("native.buy.hole.h", 44)
                    )
                ),
                new EditHandleHit(
                    "SELL_HOLE",
                    "native.sell.hole.x",
                    "native.sell.hole.y",
                    new Rectangle(
                        x + readIntConfig("native.sell.hole.x", 68),
                        y + readIntConfig("native.sell.hole.y", 61),
                        readIntConfig("native.sell.hole.w", 44),
                        readIntConfig("native.sell.hole.h", 44)
                    )
                )
            };

            for (EditHandleHit hit : hits)
            {
                if (hit.bounds.contains(mouseX, mouseY))
                {
                    return hit;
                }
            }
        }

        return null;
    }

    private String fontKeyForHandle(String name)
    {
        if ("QUANTITY".equals(name))
        {
            return "card.font.quantity";
        }
        if ("EACH_LABEL".equals(name))
        {
            return "card.font.each.label";
        }
        if ("EACH_VALUE".equals(name))
        {
            return "card.font.each.value";
        }
        if ("TOTAL_LABEL".equals(name))
        {
            return "card.font.total.label";
        }
        if ("TOTAL_VALUE".equals(name))
        {
            return "card.font.total.value";
        }

        return null;
    }

    private double defaultFontForHandle(String name)
    {
        if ("QUANTITY".equals(name))
        {
            return 9.5;
        }
        if ("EACH_LABEL".equals(name) || "TOTAL_LABEL".equals(name))
        {
            return 8.5;
        }
        if ("EACH_VALUE".equals(name) || "TOTAL_VALUE".equals(name))
        {
            return 9.5;
        }

        return 9.5;
    }
    private EditHandleHit handleByName(String name)
    {
        if ("ICON_BOX".equals(name))
        {
            return new EditHandleHit(name, "card.icon.box.offset.x", "card.icon.box.offset.y", new Rectangle());
        }
        if ("ICON_GLOW".equals(name))
        {
            return new EditHandleHit(name, "card.icon.glow.offset.x", "card.icon.glow.offset.y", new Rectangle());
        }
        if ("QUANTITY".equals(name))
        {
            return new EditHandleHit(name, "card.quantity.x", "card.quantity.y", new Rectangle());
        }
        if ("EACH_LABEL".equals(name))
        {
            return new EditHandleHit(name, "card.profit.label.x", "card.profit.label.y", new Rectangle());
        }
        if ("EACH_VALUE".equals(name))
        {
            return new EditHandleHit(name, "card.profit.value.x", "card.profit.value.y", new Rectangle());
        }
        if ("TOTAL_LABEL".equals(name))
        {
            return new EditHandleHit(name, "card.total.label.x", "card.total.label.y", new Rectangle());
        }
        if ("TOTAL_VALUE".equals(name))
        {
            return new EditHandleHit(name, "card.total.value.x", "card.total.value.y", new Rectangle());
        }

        return null;
    }

    private int readIntConfig(String key, int fallback)
    {
        return fallback;
    }

    private double readDoubleConfig(String key, double fallback)
    {
        return fallback;
    }

    private boolean readBoolConfig(String key, boolean fallback)
    {
        return fallback;
    }

    private String readStringConfig(String key, String fallback)
    {
        return fallback;
    }


    private void updateOverlayConfig(String key, String value)
    {
        // Overlay tuning persistence is intentionally omitted from the Plugin Hub build.
    }
    private void writeIntConfig(String key, int value)
    {
        writeStringConfig(key, Integer.toString(value));
    }

    private void writeStringConfig(String key, String value)
    {
        // Overlay tuning persistence is intentionally omitted from the Plugin Hub build.
    }

    private static final class EditHandleHit
    {
        private final String name;
        private final String xKey;
        private final String yKey;
        private final Rectangle bounds;

        private EditHandleHit(String name, String xKey, String yKey, Rectangle bounds)
        {
            this.name = name;
            this.xKey = xKey;
            this.yKey = yKey;
            this.bounds = bounds;
        }
    }
    private void clearGeSlotBoundsForOverlay()
    {
        for (int i = 0; i < geSlotBoundsForOverlay.length; i++)
        {
            geSlotBoundsForOverlay[i] = null;
        }
    }

    private static java.util.List<AutoFlipGeOfferSlotSnapshot> createEmptyAutoFlipGeOfferSlotSnapshots()
    {
        java.util.List<AutoFlipGeOfferSlotSnapshot> snapshots = new java.util.ArrayList<>();
        for (int slot = 0; slot < 8; slot++)
        {
            snapshots.add(AutoFlipGeOfferSlotSnapshot.empty(slot));
        }
        return java.util.Collections.unmodifiableList(snapshots);
    }

    private AutoFlipGeOfferSlotSnapshot getAutoFlipGeOfferSlotSnapshot(int slot)
    {
        java.util.List<AutoFlipGeOfferSlotSnapshot> snapshots = autoFlipGeOfferSlotSnapshots;
        if (slot < 0 || snapshots == null || slot >= snapshots.size())
        {
            return AutoFlipGeOfferSlotSnapshot.empty(slot);
        }

        AutoFlipGeOfferSlotSnapshot snapshot = snapshots.get(slot);
        return snapshot == null ? AutoFlipGeOfferSlotSnapshot.empty(slot) : snapshot;
    }

    private void updateAutoFlipGeOfferSlotSnapshot(
        int slot,
        int itemId,
        String state,
        int totalQuantity,
        int quantitySold,
        int price,
        int spent)
    {
        if (slot < 0 || slot >= 8)
        {
            return;
        }

        java.util.List<AutoFlipGeOfferSlotSnapshot> current = autoFlipGeOfferSlotSnapshots;
        java.util.List<AutoFlipGeOfferSlotSnapshot> updated = new java.util.ArrayList<>(
            current == null || current.size() < 8 ? createEmptyAutoFlipGeOfferSlotSnapshots() : current
        );
        while (updated.size() < 8)
        {
            updated.add(AutoFlipGeOfferSlotSnapshot.empty(updated.size()));
        }

        String normalizedState = state == null ? "UNKNOWN" : state;
        boolean empty = itemId <= 0
            || "EMPTY".equalsIgnoreCase(normalizedState)
            || (totalQuantity <= 0 && quantitySold <= 0 && spent <= 0L);
        updated.set(slot, new AutoFlipGeOfferSlotSnapshot(
            slot,
            Math.max(0, itemId),
            normalizedState,
            Math.max(0, totalQuantity),
            Math.max(0, quantitySold),
            Math.max(0, price),
            Math.max(0, spent),
            empty,
            isTerminalOfferState(normalizedState),
            System.currentTimeMillis()
        ));
        autoFlipGeOfferSlotSnapshots = java.util.Collections.unmodifiableList(updated);
    }

    public Rectangle getGeSlotBoundsForOverlay(int slot)
    {
        if (slot < 0 || slot >= geSlotBoundsForOverlay.length)
        {
            return null;
        }

        Rectangle bounds = geSlotBoundsForOverlay[slot];
        return bounds == null ? null : new Rectangle(bounds);
    }

    public int getGeSlotItemIdForOverlay(int slot)
    {
        AutoFlipGeOfferSlotSnapshot snapshot = getAutoFlipGeOfferSlotSnapshot(slot);
        return snapshot.empty ? 0 : snapshot.itemId;
    }

    public int getGeSlotQuantityForOverlay(int slot)
    {
        AutoFlipGeOfferSlotSnapshot snapshot = getAutoFlipGeOfferSlotSnapshot(slot);
        return snapshot.empty ? 0 : snapshot.totalQuantity;
    }
    public boolean isAutoFlipCustomSetupUiEnabledForOverlay()
    {
        Boolean override = autoFlipCustomSetupUiOverrideForOverlay;
        if (override != null)
        {
            return override.booleanValue();
        }
        return readIntConfig("setup.custom.ui.enabled", 1) != 0;
    }
    private void toggleAutoFlipCustomSetupUiEnabledForOverlay()
    {
        boolean next = !isAutoFlipCustomSetupUiEnabledForOverlay();
        autoFlipCustomSetupUiOverrideForOverlay = Boolean.valueOf(next);
        logAutoFlipVerbose("AUTOFLIP_SETUP_CUSTOM_UI_TOGGLE enabled=" + next);
    }
    private Rectangle getAutoFlipSetupUiButtonBoundsForOverlay()
    {
        if (!geWindowOpenForOverlay || !autoFlipOverlayActive || geHeaderBoundsForOverlay == null)
        {
            return null;
        }
        int x = geHeaderBoundsForOverlay.x + readIntConfig("setup.ui.button.x", 82);
        int y = geHeaderBoundsForOverlay.y + readIntConfig("setup.ui.button.y", 5);
        int w = readIntConfig("setup.ui.button.w", 30);
        int h = readIntConfig("setup.ui.button.h", 20);
        if (w <= 0 || h <= 0)
        {
            return null;
        }
        return new Rectangle(x, y, w, h);
    }
    public boolean isGeWindowOpenForOverlay()
    {
        return geWindowOpenForOverlay;
    }

    public Rectangle getGeHeaderBoundsForOverlay()
    {
        return geHeaderBoundsForOverlay;
    }


    private boolean isAutoFlipMainGeBoardScreen()
    {
        String title = geHeaderTextForOverlay == null ? "" : cleanWidgetText(geHeaderTextForOverlay);
        return "Grand Exchange".equals(title) || title.startsWith("Grand Exchange (");
    }
    public String getGeHeaderTextForOverlay()
    {
        return geHeaderTextForOverlay == null ? "" : geHeaderTextForOverlay;
    }

    // AUTOFLIP_PRICE_CHATBOX_INJECT_STATE_V1
    private volatile Rectangle autoFlipPriceChatboxButtonBounds = null;
    private volatile String autoFlipPriceChatboxButtonLabel = "";
    private volatile Rectangle autoFlipOrdinarySellRecommendedButtonBounds = null;
    private volatile String autoFlipOrdinarySellRecommendedButtonLabel = "";
    private volatile Rectangle autoFlipOrdinarySellLastSoldButtonBounds = null;
    private volatile String autoFlipOrdinarySellLastSoldButtonLabel = "";
    private volatile int autoFlipPriceChatboxGp = 0;

    // AUTOFLIP_ORDINARY_SELL_PRICE_OPTION_STATE_V1
    private volatile int autoFlipOrdinarySellSuggestedPriceGp = 0;
    private volatile int autoFlipOrdinarySellLastSoldPriceGp = 0;
    private volatile long autoFlipOrdinarySellLastSoldPriceUpdatedAtMs = 0L;
    private volatile int autoFlipOrdinarySellSetupItemQuantity = 0;
    private volatile long autoFlipOrdinarySellSuppressVisibleUiReseedUntilMs = 0L;
    private volatile long autoFlipOrdinarySellSelectionLockUntilMs = 0L;
    private volatile boolean autoFlipOrdinarySellForceWritePending = false;
    private volatile int autoFlipOrdinarySellForceWriteGp = 0;
    private volatile int autoFlipOrdinarySellSuggestedAutoFillItemId = 0;
    private volatile int autoFlipOrdinarySellCurrentPriceGp = 0;
    private volatile String autoFlipOrdinarySellCurrentPriceText = "";
    private volatile String autoFlipOrdinarySellCurrentPriceSource = "";
    private final java.util.concurrent.ConcurrentHashMap<Integer, AutoFlipMarketSnapshot> autoFlipOrdinarySellSnapshotByItemId = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<Integer> autoFlipOrdinarySellSnapshotRequestItemIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile String autoFlipStateDetectorActiveNativePromptTextCache = "";
    private volatile String autoFlipStateDetectorLabelCache = "";
    private volatile int autoFlipLastInjectedPriceGp = 0;
    private volatile long autoFlipLastInjectedPriceAtMs = 0L;
    public void rememberAutoFlipButtonBounds(Rectangle bounds)
    {
        autoFlipButtonBounds = bounds;
    }

    public Rectangle getAutoFlipButtonBounds()
    {
        return autoFlipButtonBounds;
    }

    public boolean isAutoFlipOverlayActive()
    {
        return autoFlipOverlayActive;
    }


    public String getAutoFlipHoursLabel()
    {
        if (autoFlipHoursInputActive)
        {
            return autoFlipHoursInputBuffer.isEmpty() ? "|" : autoFlipHoursInputBuffer + "|";
        }

        return formatAutoFlipHoursLabel(autoFlipMenuHoursAway);
    }

    private String formatAutoFlipHoursLabel(int hours)
    {
        int safeHours = Math.max(1, hours);
        if (safeHours < 24)
        {
            return safeHours + " hours";
        }

        double days = (double) safeHours / 24.0D;
        return safeHours + " hours, (" + String.format(java.util.Locale.US, "%.1f", days) + " days)";
    }

    public String getAutoFlipBudgetLabel()
    {
        if (autoFlipBudgetInputActive)
        {
            return autoFlipBudgetInputBuffer.isEmpty() ? "|" : autoFlipBudgetInputBuffer + "|";
        }

        long cash = getCurrentCashStackGp();
        if (cash > 0L)
        {
            autoFlipLastObservedCashStackGp = cash;
        }

        long budget = autoFlipMenuUseCashStack ? (autoFlipLastObservedCashStackGp > 0L ? autoFlipLastObservedCashStackGp : cash) : autoFlipMenuManualBudgetGp;
        return formatGp(Math.max(0L, budget));
    }

    public String getAutoFlipCashBalanceLabel()
    {
        long cash = getCurrentCashStackGp();
        if (cash > 0L)
        {
            autoFlipLastObservedCashStackGp = cash;
        }

        return "Balance: " + formatGp(Math.max(0L, autoFlipLastObservedCashStackGp > 0L ? autoFlipLastObservedCashStackGp : cash)) + " gp";
    }

    public boolean isAutoFlipUseCurrentCashStack()
    {
        return autoFlipMenuUseCashStack;
    }


    public boolean isAutoFlipHoursDropdownOpen()
    {
        return autoFlipHoursDropdownOpen;
    }

    public String getAutoFlipRiskMode()
    {
        return normalizeAutoFlipRisk(autoFlipMenuRiskMode);
    }

    public boolean isAutoFlipRiskDropdownOpen()
    {
        return autoFlipRiskDropdownOpen;
    }


    public int getAutoFlipHoveredHoursIndex()
    {
        if (!autoFlipHoursDropdownOpen || !autoFlipOverlayActive || geHeaderBoundsForOverlay == null)
        {
            return -1;
        }

        int menuX = geHeaderBoundsForOverlay.x + geHeaderBoundsForOverlay.width + readIntConfig("menu.offset.x", 18);
        int menuY = geHeaderBoundsForOverlay.y + readIntConfig("menu.offset.y", -10);
        int menuWidth = readIntConfig("menu.width", 304);
        int fieldWidth = menuWidth - 40;

        int hoursY = menuY + 121;
        int dropdownX = menuX + 20;
        int dropdownY = hoursY + 33;
        int optionHeight = 31;
        int dropdownHeight = optionHeight * 8;

        if (autoFlipLastMouseX < dropdownX || autoFlipLastMouseX > dropdownX + fieldWidth)
        {
            return -1;
        }

        if (autoFlipLastMouseY < dropdownY || autoFlipLastMouseY > dropdownY + dropdownHeight)
        {
            return -1;
        }

        return Math.max(0, Math.min(7, (autoFlipLastMouseY - dropdownY) / optionHeight));
    }

    public int getAutoFlipHoveredRiskIndex()
    {
        if (!autoFlipRiskDropdownOpen || !autoFlipOverlayActive || geHeaderBoundsForOverlay == null)
        {
            return -1;
        }

        int menuX = geHeaderBoundsForOverlay.x + geHeaderBoundsForOverlay.width + readIntConfig("menu.offset.x", 18);
        int menuY = geHeaderBoundsForOverlay.y + readIntConfig("menu.offset.y", -10);
        int menuWidth = readIntConfig("menu.width", 304);
        int fieldWidth = menuWidth - 40;

        int hoursY = menuY + 112;
        int budgetY = hoursY + 62;
        int checkboxY = budgetY + 62;
        int riskLabelY = checkboxY + 50;
        int riskY = riskLabelY + 17;
        int dropdownX = menuX + 20;
        int dropdownY = riskY + 40;
        int optionHeight = 31;
        int dropdownHeight = optionHeight * 3;

        if (autoFlipLastMouseX < dropdownX || autoFlipLastMouseX > dropdownX + fieldWidth)
        {
            return -1;
        }

        if (autoFlipLastMouseY < dropdownY || autoFlipLastMouseY > dropdownY + dropdownHeight)
        {
            return -1;
        }

        return Math.max(0, Math.min(2, (autoFlipLastMouseY - dropdownY) / optionHeight));
    }
    public int getAutoFlipHoursAway()
    {
        return autoFlipMenuHoursAway;
    }


    private Rectangle getAutoFlipRefreshBoardBounds()
    {
        Rectangle buttonBounds = autoFlipButtonBounds;
        if (buttonBounds == null || !autoFlipOverlayActive)
        {
            return null;
        }

        int width = 96;
        int height = buttonBounds.height;
        int gap = 10;
        return new Rectangle(buttonBounds.x - width - gap, buttonBounds.y, width, height);
    }

    @Subscribe
    public void onPostMenuSort(PostMenuSort event)
    {
        try
        {
            if (client == null)
            {
                return;
            }

            boolean geInventoryMenuMode = isAutoFlipGeInventoryMenuMode();
            if (!autoFlipShiftDown && !geInventoryMenuMode)
            {
                return;
            }

            MenuEntry[] entries = client.getMenuEntries();
            if (entries == null || entries.length == 0)
            {
                return;
            }

            for (MenuEntry entry : entries)
            {
                if (entry != null && AUTOFLIP_ADD_INVENTORY_OPTION.equals(entry.getOption()))
                {
                    entry.setDeprioritized(true);
                    demoteAutoFlipInventoryMenuEntryBelowOffer();
                    return;
                }
            }

            MenuEntry base = findAutoFlipInventoryMenuBase(entries, geInventoryMenuMode && !autoFlipShiftDown);
            if (base == null)
            {
                return;
            }

            int itemId = Math.max(0, base.getIdentifier());
            if (itemId <= 0)
            {
                return;
            }

            String target = base.getTarget();
            if (target == null || target.trim().isEmpty())
            {
                target = "Item " + itemId;
            }

            client.createMenuEntry(-1)
                .setOption(AUTOFLIP_ADD_INVENTORY_OPTION)
                .setTarget(target)
                .setType(MenuAction.RUNELITE)
                .setIdentifier(itemId)
                .setParam0(base.getParam0())
                .setParam1(base.getParam1())
                .setDeprioritized(true);

            demoteAutoFlipInventoryMenuEntryBelowOffer();
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("onPostMenuSort", error);
        }
    }

    private void demoteAutoFlipInventoryMenuEntryBelowOffer()
    {
        try
        {
            if (!isAutoFlipGeInventoryMenuMode() || client == null)
            {
                return;
            }

            MenuEntry[] entries = client.getMenuEntries();
            if (entries == null || entries.length < 2)
            {
                return;
            }

            int addIndex = -1;
            int offerIndex = -1;
            for (int i = 0; i < entries.length; i++)
            {
                MenuEntry entry = entries[i];
                if (entry == null)
                {
                    continue;
                }

                String option = entry.getOption();
                if (AUTOFLIP_ADD_INVENTORY_OPTION.equals(option))
                {
                    entry.setDeprioritized(true);
                    addIndex = i;
                }
                else if ("Offer".equals(option) || "Sell offer".equals(option))
                {
                    offerIndex = i;
                }
            }

            if (addIndex <= offerIndex || offerIndex < 0)
            {
                return;
            }

            MenuEntry addEntry = entries[addIndex];
            System.arraycopy(entries, offerIndex, entries, offerIndex + 1, addIndex - offerIndex);
            entries[offerIndex] = addEntry;
            client.setMenuEntries(entries);
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("demoteAutoFlipInventoryMenuEntryBelowOffer", error);
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        try
        {
            if (event == null)
            {
                return;
            }

            String menuOption = event.getMenuOption();
            boolean addInventoryOption = AUTOFLIP_ADD_INVENTORY_OPTION.equals(menuOption);
            boolean offerOption = "Offer".equals(menuOption) || "Sell offer".equals(menuOption);
            if (!addInventoryOption && !offerOption)
            {
                String lowerOption = menuOption == null ? "" : menuOption.trim().toLowerCase(java.util.Locale.ROOT);
                if (lowerOption.contains("collect") || lowerOption.contains("cancel") || lowerOption.contains("abort"))
                {
                    markAutoFlipGeSessionInventoryCacheDirty("ge_menu_" + lowerOption.replace(' ', '_'));
                }
                return;
            }

            int itemId = Math.max(0, event.getId());
            String itemName = cleanAutoFlipMenuTarget(event.getMenuTarget());

            if (itemName == null || itemName.isEmpty())
            {
                itemName = "Item " + itemId;
            }

            int resolvedItemId = resolveAutoFlipMenuItemId(itemName, itemId, event.getParam0(), event.getParam1());
            if (resolvedItemId > 0)
            {
                itemId = resolvedItemId;
                itemName = resolveAutoFlipItemName(itemId, itemName);
            }

            appendLine(
                ORDINARY_SELL_DEBUG_LOG,
                now()
                    + " menu_option_clicked"
                    + " option=" + safe(menuOption)
                    + " item_id=" + itemId
                    + " item_name=" + safe(itemName)
                    + " ge_mode=" + isAutoFlipGeInventoryMenuMode()
                    + " add_inventory_option=" + addInventoryOption
                    + " offer_option=" + offerOption
                    + " param0=" + event.getParam0()
                    + " param1=" + event.getParam1()
            );

            if (itemId <= 0)
            {
                if (addInventoryOption)
                {
                    logAutoFlipMenuEvent("held_inventory_add_missing_item_id");
                }
                return;
            }

            if (offerOption)
            {
                if (isAutoFlipGeInventoryMenuMode())
                {
                    autoFlipOrdinarySellSuppressVisibleUiReseedUntilMs = 0L;
                    rememberAutoFlipSellInventoryOfferTarget(itemId, itemName);
                }
                return;
            }

            int quantity = resolveAutoFlipMenuItemQuantity(itemId, event.getParam0(), event.getParam1());
            addAutoFlipInventoryItem(itemId, itemName, quantity, isAutoFlipGeInventoryMenuMode() ? "ge_inventory_right_click" : "shift_right_click");
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("onMenuOptionClicked", error);
        }
    }

    private void rememberAutoFlipSellInventoryOfferTarget(int itemId, String itemName)
    {
        int canonicalItemId = canonicalizeAutoFlipInventoryItemId(itemId);
        if (canonicalItemId <= 0)
        {
            return;
        }

        boolean seededInventoryOfferTarget = false;
        java.util.List<AutoFlipSellGuidance> sellGuidanceSnapshot = getAutoFlipSellGuidanceListForOverlay();
        int sellGuidanceCount = sellGuidanceSnapshot == null ? 0 : sellGuidanceSnapshot.size();
        appendLine(
            ORDINARY_SELL_DEBUG_LOG,
            now()
                + " remember_sell_inventory_offer_target"
                + " item_id=" + canonicalItemId
                + " item_name=" + safe(itemName)
                + " guidance_count=" + sellGuidanceCount
        );

        for (AutoFlipSellGuidance guidance : sellGuidanceSnapshot)
        {
            if (guidance == null)
            {
                continue;
            }

            int guidanceItemId = canonicalizeAutoFlipInventoryItemId(guidance.getItemId());
            if (guidanceItemId != canonicalItemId || !guidance.isPresentInInventory())
            {
                continue;
            }

            String resolvedName = guidance.getItemName();
            if (resolvedName == null || resolvedName.trim().isEmpty())
            {
                resolvedName = itemName == null ? "" : itemName.trim();
            }

            logAutoFlipVerbose(
                "AUTOFLIP_SELL_TARGET_MATCH"
                    + " source=inventory_click"
                    + " item_id=" + canonicalItemId
                    + " guidance_slot=" + guidance.getSlotIndex()
                    + " guidance_item_id=" + guidanceItemId
                    + " guidance_name=" + safe(resolvedName)
                    + " guidance_qty=" + guidance.getInventoryQuantity()
                    + " guidance_present=" + guidance.isPresentInInventory()
            );

            autoFlipLastNativeButtonSlotIndex = -1;
            autoFlipLastNativeButtonItemId = guidanceItemId;
            autoFlipLastNativeButtonRememberedAtMs = System.currentTimeMillis();
            autoFlipLastSellTargetWasInventoryClick = true;
            autoFlipPendingGuidedSetupItemId = guidanceItemId;
            autoFlipPendingGuidedSetupItemName = resolvedName;
            autoFlipOrdinarySellSetupItemId = guidanceItemId;
            autoFlipOrdinarySellSetupItemName = resolvedName;
            autoFlipOrdinarySellSetupLastSeenMs = System.currentTimeMillis();
            autoFlipOrdinarySellSetupItemQuantity = Math.max(0, guidance.getInventoryQuantity());
            autoFlipOrdinarySellSelectionLockUntilMs = System.currentTimeMillis() + 2500L;
            seededInventoryOfferTarget = true;
            if (!autoFlipOrdinarySellSetupItemName.isEmpty())
            {
                autoFlipSellPriceDebugItemNameById.put(guidanceItemId, autoFlipOrdinarySellSetupItemName);
            }
            requestAutoFlipApiSellPrice(guidanceItemId);

            return;
        }

        if (!seededInventoryOfferTarget)
        {
            int inventoryQuantity = resolveAutoFlipInventoryQuantity(canonicalItemId);
            String resolvedName = itemName == null ? "" : itemName.trim();

            logAutoFlipVerbose(
                "AUTOFLIP_SELL_TARGET_FALLBACK"
                    + " source=inventory_click"
                    + " item_id=" + canonicalItemId
                    + " item_name=" + safe(resolvedName)
                    + " inventory_qty=" + inventoryQuantity
                    + " guidance_count=" + sellGuidanceCount
            );

            appendLine(
                ORDINARY_SELL_DEBUG_LOG,
                now()
                    + " remember_inventory_sell_target_fallback"
                    + " item_id=" + canonicalItemId
                    + " item_name=" + safe(resolvedName)
                    + " inventory_quantity=" + inventoryQuantity
            );

            autoFlipLastNativeButtonSlotIndex = -1;
            autoFlipLastNativeButtonItemId = canonicalItemId;
            autoFlipLastNativeButtonRememberedAtMs = System.currentTimeMillis();
            autoFlipLastSellTargetWasInventoryClick = true;
            autoFlipPendingGuidedSetupItemId = canonicalItemId;
            autoFlipPendingGuidedSetupItemName = resolvedName;
            autoFlipOrdinarySellSetupItemId = canonicalItemId;
            autoFlipOrdinarySellSetupItemName = resolvedName;
            autoFlipOrdinarySellSetupLastSeenMs = System.currentTimeMillis();
            autoFlipOrdinarySellSetupItemQuantity = Math.max(0, inventoryQuantity);
            autoFlipOrdinarySellSelectionLockUntilMs = System.currentTimeMillis() + 2500L;
            if (!autoFlipOrdinarySellSetupItemName.isEmpty())
            {
                autoFlipSellPriceDebugItemNameById.put(canonicalItemId, autoFlipOrdinarySellSetupItemName);
            }
            requestAutoFlipApiSellPrice(canonicalItemId);
        }

        if (!isAutoFlipInventoryItem(canonicalItemId))
        {
            appendLine(
                ORDINARY_SELL_DEBUG_LOG,
                now()
                    + " remember_non_inventory_sell_target"
                    + " item_id=" + canonicalItemId
                    + " item_name=" + safe(itemName)
            );
            autoFlipOrdinarySellSetupItemId = canonicalItemId;
            autoFlipOrdinarySellSetupItemName = itemName == null ? "" : itemName.trim();
            autoFlipOrdinarySellSetupItemQuantity = 0;
            autoFlipOrdinarySellSelectionLockUntilMs = System.currentTimeMillis() + 2500L;
            if (!autoFlipOrdinarySellSetupItemName.isEmpty())
            {
                autoFlipSellPriceDebugItemNameById.put(canonicalItemId, autoFlipOrdinarySellSetupItemName);
            }
            autoFlipOrdinarySellSetupLastSeenMs = System.currentTimeMillis();
            requestAutoFlipApiSellPrice(canonicalItemId);
            return;
        }

    }

    private MenuEntry findAutoFlipInventoryMenuBase(MenuEntry[] entries, boolean requireInventoryWidget)
    {
        for (int i = entries.length - 1; i >= 0; i--)
        {
            MenuEntry entry = entries[i];
            if (entry == null)
            {
                continue;
            }

            String option = entry.getOption();
            String target = entry.getTarget();
            int itemId = Math.max(0, entry.getIdentifier());

            if (itemId <= 0 || target == null || target.trim().isEmpty())
            {
                continue;
            }

            if (option == null)
            {
                continue;
            }

            String normalized = option.trim().toLowerCase(java.util.Locale.ROOT);
            if (normalized.isEmpty()
                || "cancel".equals(normalized)
                || "examine".equals(normalized)
                || AUTOFLIP_ADD_INVENTORY_OPTION.equals(option))
            {
                continue;
            }

            if (requireInventoryWidget && !isAutoFlipInventoryMenuEntry(entry))
            {
                continue;
            }

            return entry;
        }

        return null;
    }

    private boolean isAutoFlipGeInventoryMenuMode()
    {
        return geWindowOpenForOverlay || isGeWindowOpenForOverlay();
    }

    private boolean isAutoFlipInventoryMenuEntry(MenuEntry entry)
    {
        if (entry == null)
        {
            return false;
        }

        int param1 = entry.getParam1();
        if (param1 <= 0)
        {
            return false;
        }

        return param1 == net.runelite.api.widgets.ComponentID.INVENTORY_CONTAINER
            || param1 == net.runelite.api.widgets.ComponentID.GRAND_EXCHANGE_INVENTORY_INVENTORY_ITEM_CONTAINER
            || param1 == net.runelite.api.widgets.ComponentID.FIXED_VIEWPORT_INVENTORY_CONTAINER
            || param1 == net.runelite.api.widgets.ComponentID.RESIZABLE_VIEWPORT_INVENTORY_CONTAINER
            || param1 == net.runelite.api.widgets.ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_CONTAINER;
    }

    private String cleanAutoFlipMenuTarget(String target)
    {
        if (target == null)
        {
            return "";
        }

        return target
            .replaceAll("<[^>]*>", "")
            .replace("&nbsp;", " ")
            .replace(" ", " ")
            .trim();
    }



    private int resolveAutoFlipMenuItemId(String itemName, int eventItemId, int param0, int param1)
    {
        int widgetItemId = resolveAutoFlipWidgetItemId(param0, param1);
        if (widgetItemId > 0)
        {
            return widgetItemId;
        }

        int byName = resolveAutoFlipItemIdByName(itemName);
        if (byName > 0)
        {
            return byName;
        }

        if (eventItemId > 0 && autoFlipItemCompositionNameMatches(eventItemId, itemName))
        {
            return eventItemId;
        }

        logAutoFlipVerbose("AUTOFLIP_HELD_INVENTORY_ID_FALLBACK event_id=" + eventItemId + " item_name=" + itemName + " param0=" + param0 + " param1=" + param1);
        return Math.max(0, eventItemId);
    }

    private int resolveAutoFlipWidgetItemId(int param0, int param1)
    {
        try
        {
            if (client == null || param1 <= 0)
            {
                return 0;
            }

            Widget widget = client.getWidget(param1);
            if (widget == null)
            {
                return 0;
            }

            Widget child = param0 >= 0 ? widget.getChild(param0) : null;
            if (child != null && child.getItemId() > 0)
            {
                return child.getItemId();
            }

            if (widget.getItemId() > 0)
            {
                return widget.getItemId();
            }
        }
        catch (Throwable ignored)
        {
        }

        return 0;
    }

    private int resolveAutoFlipItemIdByName(String itemName)
    {
        String wanted = normalizeAutoFlipItemNameForCompare(itemName);
        if (wanted.isEmpty() || itemManager == null)
        {
            return 0;
        }

        try
        {
            for (ItemPrice row : itemManager.search(itemName))
            {
                if (row == null)
                {
                    continue;
                }

                String rowName = normalizeAutoFlipItemNameForCompare(row.getName());
                int rowId = row.getId();
                if (rowId > 0 && rowName.equals(wanted))
                {
                    return rowId;
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        return 0;
    }

    private boolean autoFlipItemCompositionNameMatches(int itemId, String itemName)
    {
        try
        {
            if (itemManager == null || itemId <= 0)
            {
                return false;
            }

            net.runelite.api.ItemComposition composition = itemManager.getItemComposition(itemId);
            if (composition == null)
            {
                return false;
            }

            return normalizeAutoFlipItemNameForCompare(composition.getName())
                .equals(normalizeAutoFlipItemNameForCompare(itemName));
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    private String resolveAutoFlipItemName(int itemId, String fallback)
    {
        try
        {
            if (itemManager != null && itemId > 0)
            {
                net.runelite.api.ItemComposition composition = itemManager.getItemComposition(itemId);
                if (composition != null && composition.getName() != null && !composition.getName().trim().isEmpty())
                {
                    return composition.getName().trim();
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        return fallback == null || fallback.trim().isEmpty() ? "Item " + itemId : fallback.trim();
    }

    private int canonicalizeAutoFlipInventoryItemId(int itemId)
    {
        if (itemId <= 0 || itemManager == null)
        {
            return itemId;
        }

        if (client != null && !client.isClientThread())
        {
            return itemId;
        }

        int bestItemId = itemId;

        try
        {
            int canonicalItemId = itemManager.canonicalize(itemId);
            if (canonicalItemId > 0)
            {
                bestItemId = canonicalItemId;
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("canonicalizeAutoFlipInventoryItemId", error);
        }

        int unnotedItemId = resolveAutoFlipUnnotedItemId(itemId);
        if (unnotedItemId > 0)
        {
            return unnotedItemId;
        }

        if (bestItemId != itemId)
        {
            unnotedItemId = resolveAutoFlipUnnotedItemId(bestItemId);
            if (unnotedItemId > 0)
            {
                return unnotedItemId;
            }
        }

        return bestItemId;
    }

    private int resolveAutoFlipUnnotedItemId(int itemId)
    {
        if (itemId <= 0 || itemManager == null)
        {
            return itemId;
        }

        if (client != null && !client.isClientThread())
        {
            return itemId;
        }

        try
        {
            net.runelite.api.ItemComposition original = itemManager.getItemComposition(itemId);
            if (original == null)
            {
                return itemId;
            }

            int linkedUnnotedId = resolveAutoFlipUnnotedCandidate(itemId, original, original.getLinkedNoteId());
            if (linkedUnnotedId > 0)
            {
                return linkedUnnotedId;
            }

            int noteUnnotedId = resolveAutoFlipUnnotedCandidate(itemId, original, original.getNote());
            if (noteUnnotedId > 0)
            {
                return noteUnnotedId;
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("resolveAutoFlipUnnotedItemId", error);
        }

        return itemId;
    }

    private int resolveAutoFlipUnnotedCandidate(int originalItemId, net.runelite.api.ItemComposition original, int candidateItemId)
    {
        if (originalItemId <= 0 || candidateItemId <= 0 || candidateItemId == originalItemId || itemManager == null || original == null)
        {
            return 0;
        }

        try
        {
            net.runelite.api.ItemComposition candidate = itemManager.getItemComposition(candidateItemId);
            if (candidate == null)
            {
                return 0;
            }

            String originalName = normalizeAutoFlipItemNameForCompare(original.getName());
            String candidateName = normalizeAutoFlipItemNameForCompare(candidate.getName());

            if (originalName.isEmpty() || !originalName.equals(candidateName))
            {
                return 0;
            }

            if (original.isStackable() && !candidate.isStackable())
            {
                return candidateItemId;
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("resolveAutoFlipUnnotedCandidate", error);
        }

        return 0;
    }

    private boolean isSameAutoFlipInventoryItemId(int leftItemId, int rightItemId)
    {
        if (leftItemId <= 0 || rightItemId <= 0)
        {
            return false;
        }

        if (leftItemId == rightItemId)
        {
            return true;
        }

        return canonicalizeAutoFlipInventoryItemId(leftItemId) == canonicalizeAutoFlipInventoryItemId(rightItemId);
    }

    private String normalizeAutoFlipItemNameForCompare(String value)
    {
        return value == null
            ? ""
            : value.replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replace('\u00a0', ' ')
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
    }

    private int resolveAutoFlipMenuItemQuantity(int itemId, int param0, int param1)
    {
        int widgetQuantity = resolveAutoFlipWidgetItemQuantity(itemId, param0, param1);
        if (widgetQuantity > 0)
        {
            return widgetQuantity;
        }

        int inventoryQuantity = resolveAutoFlipInventoryQuantity(itemId);
        if (inventoryQuantity > 0)
        {
            return inventoryQuantity;
        }

        int inventoryItemId = canonicalizeAutoFlipInventoryItemId(itemId);
        if (inventoryItemId > 0 && inventoryItemId != itemId)
        {
            inventoryQuantity = resolveAutoFlipInventoryQuantity(inventoryItemId);
            if (inventoryQuantity > 0)
            {
                return inventoryQuantity;
            }
        }

        return 1;
    }

    private int resolveAutoFlipWidgetItemQuantity(int itemId, int param0, int param1)
    {
        try
        {
            if (client == null || itemId <= 0 || param1 <= 0)
            {
                return 0;
            }

            Widget widget = client.getWidget(param1);
            if (widget == null)
            {
                return 0;
            }

            Widget child = param0 >= 0 ? widget.getChild(param0) : null;
            if (child != null && isSameAutoFlipInventoryItemId(child.getItemId(), itemId))
            {
                return Math.max(1, child.getItemQuantity());
            }

            if (isSameAutoFlipInventoryItemId(widget.getItemId(), itemId))
            {
                return Math.max(1, widget.getItemQuantity());
            }
        }
        catch (Throwable ignored)
        {
        }

        return 0;
    }

    // One immutable snapshot per GE session. The inventory is visible when group 465 opens;
    // switching side tabs must never replace this map with RuneLite's hidden/empty view.
    private volatile java.util.Map<Integer, Integer> autoFlipGeSessionInventoryQuantityByItemId = java.util.Collections.emptyMap();
    private volatile long autoFlipGeSessionInventoryCapturedAtMs = 0L;
    private volatile boolean autoFlipGeSessionInventoryCacheActive = false;
    private volatile boolean autoFlipGeSessionInventoryCapturePending = false;
    private volatile String autoFlipGeSessionInventoryCapturePendingReason = "";
    private volatile long autoFlipGeSessionInventorySessionId = 0L;
    private volatile java.util.Map<Integer, java.util.List<Rectangle>> autoFlipGeSessionInventoryBoundsByItemId = java.util.Collections.emptyMap();

    private void beginAutoFlipGeSessionInventoryCache()
    {
        autoFlipGeSessionInventoryQuantityByItemId = java.util.Collections.emptyMap();
        autoFlipGeSessionInventoryBoundsByItemId = java.util.Collections.emptyMap();
        autoFlipGeSessionInventoryCapturedAtMs = 0L;
        autoFlipGeSessionInventoryCacheActive = true;
        autoFlipGeSessionInventoryCapturePending = true;
        autoFlipGeSessionInventoryCapturePendingReason = "ge_opened";
        autoFlipGeSessionInventorySessionId++;
        captureAutoFlipGeSessionInventoryCacheIfPending();
    }

    private void captureAutoFlipGeSessionInventoryCacheIfPending()
    {
        if (!shouldCaptureAutoFlipGeSessionInventory(
            autoFlipGeSessionInventoryCacheActive,
            autoFlipGeSessionInventoryCapturePending,
            client != null))
        {
            return;
        }

        try
        {
            net.runelite.api.ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
            if (container == null || container.getItems() == null)
            {
                return;
            }

            captureAutoFlipGeSessionInventoryCache(container.getItems());
            autoFlipGeSessionInventoryCapturePending = false;
            autoFlipGeSessionInventoryCapturePendingReason = "";
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("captureAutoFlipGeSessionInventoryCacheIfPending", error);
        }
    }

    static boolean isAutoFlipGrandExchangeInterfaceGroup(int groupId)
    {
        return groupId == 465;
    }

    static boolean shouldCaptureAutoFlipGeSessionInventory(boolean cacheActive, boolean capturePending, boolean clientAvailable)
    {
        return cacheActive && capturePending && clientAvailable;
    }

    private void captureAutoFlipGeSessionInventoryCache(Item[] items)
    {
        java.util.Map<Integer, Integer> quantities = new java.util.HashMap<>();
        if (items != null)
        {
            for (Item item : items)
            {
                if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
                {
                    continue;
                }
                quantities.merge(item.getId(), Math.max(0, item.getQuantity()), Integer::sum);
            }
        }

        autoFlipGeSessionInventoryQuantityByItemId = java.util.Collections.unmodifiableMap(quantities);
        autoFlipGeSessionInventoryBoundsByItemId = captureAutoFlipGeSessionInventoryBoundsByItemId();
        autoFlipGeSessionInventoryCapturedAtMs = System.currentTimeMillis();
        logAutoFlipVerbose(
            "AUTOFLIP_GE_SESSION_INVENTORY_CACHE"
                + " action=captured"
                + " distinct_items=" + quantities.size()
                + " bounds_items=" + autoFlipGeSessionInventoryBoundsByItemId.size()
                + " captured_at_ms=" + autoFlipGeSessionInventoryCapturedAtMs
                + " session_id=" + autoFlipGeSessionInventorySessionId
        );
    }

    private java.util.Map<Integer, java.util.List<Rectangle>> captureAutoFlipGeSessionInventoryBoundsByItemId()
    {
        if (client == null)
        {
            return java.util.Collections.emptyMap();
        }

        java.util.Map<Integer, java.util.List<Rectangle>> mutable = new java.util.HashMap<>();
        java.util.Set<String> seen = new java.util.HashSet<>();

        addAutoFlipInventoryBoundsFromComponentId(mutable, seen, net.runelite.api.widgets.ComponentID.GRAND_EXCHANGE_INVENTORY_INVENTORY_ITEM_CONTAINER);
        addAutoFlipInventoryBoundsFromComponentId(mutable, seen, net.runelite.api.widgets.ComponentID.INVENTORY_CONTAINER);
        addAutoFlipInventoryBoundsFromComponentId(mutable, seen, net.runelite.api.widgets.ComponentID.FIXED_VIEWPORT_INVENTORY_CONTAINER);
        addAutoFlipInventoryBoundsFromComponentId(mutable, seen, net.runelite.api.widgets.ComponentID.RESIZABLE_VIEWPORT_INVENTORY_CONTAINER);
        addAutoFlipInventoryBoundsFromComponentId(mutable, seen, net.runelite.api.widgets.ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_INVENTORY_CONTAINER);
        addAutoFlipInventoryBoundsFromComponentId(mutable, seen, net.runelite.api.widgets.ComponentID.BANK_INVENTORY_ITEM_CONTAINER);

        if (mutable.isEmpty())
        {
            return java.util.Collections.emptyMap();
        }

        java.util.Map<Integer, java.util.List<Rectangle>> frozen = new java.util.HashMap<>();
        for (java.util.Map.Entry<Integer, java.util.List<Rectangle>> entry : mutable.entrySet())
        {
            if (entry == null || entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty())
            {
                continue;
            }
            frozen.put(entry.getKey(), java.util.Collections.unmodifiableList(new java.util.ArrayList<>(entry.getValue())));
        }

        return frozen.isEmpty()
            ? java.util.Collections.emptyMap()
            : java.util.Collections.unmodifiableMap(frozen);
    }

    private void addAutoFlipInventoryBoundsFromComponentId(
        java.util.Map<Integer, java.util.List<Rectangle>> out,
        java.util.Set<String> seen,
        int componentId)
    {
        try
        {
            if (client == null || out == null || seen == null || componentId <= 0)
            {
                return;
            }

            Widget widget = client.getWidget(componentId);
            if (widget == null || widget.isHidden())
            {
                return;
            }

            addAutoFlipWidgetTreeInventoryBounds(out, seen, widget, 0);
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("addAutoFlipInventoryBoundsFromComponentId", error);
        }
    }

    private void addAutoFlipWidgetTreeInventoryBounds(
        java.util.Map<Integer, java.util.List<Rectangle>> out,
        java.util.Set<String> seen,
        Widget widget,
        int depth)
    {
        if (out == null || seen == null || widget == null || depth > 5)
        {
            return;
        }

        try
        {
            if (!widget.isHidden())
            {
                int itemId = canonicalizeAutoFlipInventoryItemId(widget.getItemId());
                Rectangle bounds = widget.getBounds();
                if (itemId > 0 && bounds != null && bounds.width > 0 && bounds.height > 0)
                {
                    String key = itemId + ":" + bounds.x + ":" + bounds.y + ":" + bounds.width + ":" + bounds.height;
                    if (seen.add(key))
                    {
                        out.computeIfAbsent(itemId, ignored -> new java.util.ArrayList<>()).add(new Rectangle(bounds));
                    }
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        addAutoFlipWidgetArrayInventoryBounds(out, seen, widget.getChildren(), depth + 1);
        addAutoFlipWidgetArrayInventoryBounds(out, seen, widget.getStaticChildren(), depth + 1);
        addAutoFlipWidgetArrayInventoryBounds(out, seen, widget.getDynamicChildren(), depth + 1);
        addAutoFlipWidgetArrayInventoryBounds(out, seen, widget.getNestedChildren(), depth + 1);
    }

    private void addAutoFlipWidgetArrayInventoryBounds(
        java.util.Map<Integer, java.util.List<Rectangle>> out,
        java.util.Set<String> seen,
        Widget[] widgets,
        int depth)
    {
        if (widgets == null || widgets.length == 0)
        {
            return;
        }

        for (Widget child : widgets)
        {
            addAutoFlipWidgetTreeInventoryBounds(out, seen, child, depth);
        }
    }

    private void markAutoFlipGeSessionInventoryCacheDirty(String reason)
    {
        if (!autoFlipGeSessionInventoryCacheActive && !geWindowOpenForOverlay)
        {
            return;
        }

        autoFlipGeSessionInventoryCacheActive = true;
        autoFlipGeSessionInventoryCapturePending = true;
        autoFlipGeSessionInventoryCapturePendingReason = reason == null || reason.trim().isEmpty()
            ? "state_changed"
            : reason.trim();
        logAutoFlipVerbose(
            "AUTOFLIP_GE_SESSION_INVENTORY_CACHE"
                + " action=dirty"
                + " reason=" + safe(autoFlipGeSessionInventoryCapturePendingReason)
                + " session_id=" + autoFlipGeSessionInventorySessionId
        );
    }

    private void clearAutoFlipGeSessionInventoryCache(String reason)
    {
        int previousSize = autoFlipGeSessionInventoryQuantityByItemId.size();
        autoFlipGeSessionInventoryQuantityByItemId = java.util.Collections.emptyMap();
        autoFlipGeSessionInventoryBoundsByItemId = java.util.Collections.emptyMap();
        autoFlipGeSessionInventoryCapturedAtMs = 0L;
        autoFlipGeSessionInventoryCacheActive = false;
        autoFlipGeSessionInventoryCapturePending = false;
        autoFlipGeSessionInventoryCapturePendingReason = "";
        if (previousSize > 0)
        {
            logAutoFlipVerbose(
                "AUTOFLIP_GE_SESSION_INVENTORY_CACHE"
                    + " action=cleared"
                    + " reason=" + safe(reason)
                    + " previous_distinct_items=" + previousSize
            );
        }
    }

    private int resolveAutoFlipInventoryQuantity(int itemId)
    {
        if (itemId <= 0)
        {
            return 0;
        }

        int total = 0;
        java.util.Map<Integer, Integer> snapshot = autoFlipGeSessionInventoryQuantityByItemId;
        for (java.util.Map.Entry<Integer, Integer> entry : snapshot.entrySet())
        {
            if (entry != null && isSameAutoFlipInventoryItemId(entry.getKey(), itemId))
            {
                total += Math.max(0, entry.getValue());
            }
        }
        return total;
    }

    public String getAutoFlipGeSessionInventoryCachePseudoJson()
    {
        java.util.Set<Integer> uniqueItemIds = new java.util.TreeSet<>();
        uniqueItemIds.addAll(autoFlipGeSessionInventoryQuantityByItemId.keySet());

        StringBuilder out = new StringBuilder(Math.max(256, uniqueItemIds.size() * 32));
        out.append("{\n");
        out.append("  schema = autoflip.ge_session_inventory_cache.v1\n");
        out.append("  unique_item_count = ").append(uniqueItemIds.size()).append('\n');
        out.append("  item_ids = [\n");
        if (uniqueItemIds.isEmpty())
        {
            out.append("    (empty)\n");
        }
        else
        {
            int index = 0;
            for (Integer itemId : uniqueItemIds)
            {
                out.append("    item_id = ").append(itemId == null ? 0 : itemId);
                if (++index < uniqueItemIds.size())
                {
                    out.append(',');
                }
                out.append('\n');
            }
        }
        out.append("  ]\n");
        appendAutoFlipTimeToSellInventoryCacheChecks(out, "  ");
        out.append("}");
        return out.toString();
    }

    public String getAutoFlipMembersCurrentListPseudoJson()
    {
        fetchAutoFlipPayloadIfNeeded(false);
        return buildAutoFlipCurrentListPseudoJson(true);
    }

    public String getAutoFlipF2pCurrentListPseudoJson()
    {
        fetchAutoFlipPayloadIfNeeded(false);
        return buildAutoFlipCurrentListPseudoJson(false);
    }

    private String buildAutoFlipCurrentListPseudoJson(boolean membersOnly)
    {
        java.util.List<AutoFlipPayloadRow> rankedRows = localPayloadCurrentRankedUniverseRows(membersOnly);
        java.util.List<AutoFlipPayloadRow> filteredRows = new java.util.ArrayList<>();
        java.util.Map<Integer, Integer> displayedSlotByItemId = new java.util.TreeMap<>();
        java.util.Set<Integer> recentSkipItemIds = new java.util.TreeSet<>();
        java.util.Set<Integer> displayedItemIds = new java.util.TreeSet<>();

        java.util.List<AutoFlipBoardCard> visibleCards = getAutoFlipVisibleBoardCardsSnapshot();
        for (AutoFlipBoardCard card : visibleCards)
        {
            if (card == null || card.getItemId() <= 0)
            {
                continue;
            }

            int canonicalItemId = canonicalizeAutoFlipInventoryItemId(card.getItemId());
            if (canonicalItemId <= 0 || isAutoFlipMembersOnlyItemCachedForDebug(canonicalItemId) != membersOnly)
            {
                continue;
            }

            displayedItemIds.add(canonicalItemId);
            displayedSlotByItemId.putIfAbsent(canonicalItemId, Math.max(0, card.getSlotIndex()));
        }

        for (Integer itemId : getAutoFlipSkippedItemIdsSnapshot())
        {
            if (itemId == null || itemId <= 0)
            {
                continue;
            }

            int canonicalItemId = canonicalizeAutoFlipInventoryItemId(itemId);
            if (canonicalItemId > 0 && isAutoFlipMembersOnlyItemCachedForDebug(canonicalItemId) == membersOnly)
            {
                recentSkipItemIds.add(canonicalItemId);
            }
        }

        for (AutoFlipPayloadRow row : rankedRows)
        {
            if (row == null || row.itemId <= 0)
            {
                continue;
            }

            int canonicalItemId = canonicalizeAutoFlipInventoryItemId(row.itemId);
            if (canonicalItemId <= 0 || isAutoFlipMembersOnlyItemCachedForDebug(canonicalItemId) != membersOnly)
            {
                continue;
            }

            filteredRows.add(row);
        }

        String listType = membersOnly ? "members" : "f2p";
        String schema = membersOnly
            ? "autoflip.current_members_list.v1"
            : "autoflip.current_f2p_list.v1";

        StringBuilder out = new StringBuilder(Math.max(512, filteredRows.size() * 80));
        out.append("schema = ").append(schema).append('\n');
        out.append("generated_at = ").append(java.time.Instant.now().toString()).append('\n');
        out.append("list_type = ").append(listType).append('\n');
        out.append("item_count = ").append(filteredRows.size()).append('\n');
        out.append("board_displayed_item_ids = ").append(displayedItemIds).append('\n');
        out.append("recent_skip_item_ids = ").append(recentSkipItemIds).append('\n');
        out.append("items:\n");

        if (filteredRows.isEmpty())
        {
            out.append("(no ").append(listType).append(" ranked items)\n");
        }
        else
        {
            for (AutoFlipPayloadRow row : filteredRows)
            {
                int canonicalItemId = canonicalizeAutoFlipInventoryItemId(row.itemId);
                boolean skipped = recentSkipItemIds.contains(canonicalItemId);
                boolean displayed = displayedItemIds.contains(canonicalItemId);
                String markers = (skipped ? "*" : "") + (displayed ? "^" : "");
                String decoratedName = (markers.isEmpty() ? "" : markers) + safe(row.itemName);
                out.append("  ").append(decoratedName).append('\n');
            }
        }
        return out.toString();
    }

    private String formatAutoFlipConfidencePercent(double confidence)
    {
        if (!Double.isFinite(confidence))
        {
            return "0.0%";
        }

        double percent = confidence <= 1.0 ? confidence * 100.0 : confidence;
        return String.format(java.util.Locale.ROOT, "%.1f%%", Math.max(0.0, percent));
    }

    private void appendAutoFlipTimeToSellInventoryCacheChecks(StringBuilder out, String indent)
    {
        String prefix = indent == null ? "" : indent;
        boolean emitted = false;
        java.util.Set<Integer> emittedIds = new java.util.HashSet<>();
        for (AutoFlipBoardCard card : getAutoFlipBoardCardsSnapshot())
        {
            if (!isAutoFlipSellPlanBoardCard(card) || card.getItemId() <= 0)
            {
                continue;
            }

            int canonicalItemId = canonicalizeAutoFlipInventoryItemId(card.getItemId());
            if (!emittedIds.add(canonicalItemId))
            {
                continue;
            }

            boolean exists = resolveAutoFlipInventoryQuantity(canonicalItemId) > 0;
            out.append(prefix)
                .append("time_to_sell_item_id = ").append(card.getItemId())
                .append(" canonical_item_id = ").append(canonicalItemId)
                .append(" exists_in_cache = ").append(exists)
                .append('\n');
            emitted = true;
        }

        if (!emitted)
        {
            out.append(prefix).append("(no time-to-sell items)\n");
        }
    }
    private java.nio.file.Path getAutoFlipInventoryPath()
    {
        return AUTOFLIP_INVENTORY_STATE_PATH;
    }

    private java.nio.file.Path getLegacyAutoFlipInventoryPath()
    {
        try
        {
            if (OUTBOX != null && OUTBOX.getParent() != null)
            {
                return OUTBOX.getParent().resolve("autoflip_inventory.jsonl");
            }
        }
        catch (Throwable ignored)
        {
        }

        return java.nio.file.Paths.get(System.getProperty("user.home"), ".runelite", "autoflip_inventory.jsonl");
    }

    private void migrateAutoFlipInventoryStateIfNeeded()
    {
        synchronized (autoFlipInventoryLock)
        {
            try
            {
                java.nio.file.Path stablePath = getAutoFlipInventoryPath();
                java.nio.file.Path legacyPath = getLegacyAutoFlipInventoryPath();

                if (stablePath == null || java.nio.file.Files.exists(stablePath))
                {
                    return;
                }

                if (legacyPath == null || !java.nio.file.Files.exists(legacyPath))
                {
                    return;
                }

                java.nio.file.Path parent = stablePath.getParent();
                if (parent != null)
                {
                    java.nio.file.Files.createDirectories(parent);
                }

                java.nio.file.Files.copy(
                    legacyPath,
                    stablePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES
                );

                logAutoFlipVerbose("AUTOFLIP_HELD_INVENTORY_MIGRATED legacy=" + legacyPath + " stable=" + stablePath);
                logAutoFlipMenuEvent("held_inventory_migrated");
                notifyAutoFlipSidePanelRefresh();
            }
            catch (Throwable error)
            {
                logAutoFlipUiError("migrateAutoFlipInventoryStateIfNeeded", error);
                logAutoFlipVerbose("AUTOFLIP_HELD_INVENTORY_MIGRATION_ERROR " + error);
            }
        }
    }

    private void loadAutoFlipInventory()
    {
        synchronized (autoFlipInventoryLock)
        {
            migrateAutoFlipInventoryStateIfNeeded();
            java.util.List<AutoFlipInventoryItem> loaded = new java.util.ArrayList<>();
            java.nio.file.Path path = getAutoFlipInventoryPath();

            try
            {
                if (!java.nio.file.Files.exists(path))
                {
                    autoFlipInventoryItems = java.util.Collections.emptyList();
                    return;
                }

                java.util.List<String> lines = java.nio.file.Files.readAllLines(path, StandardCharsets.UTF_8);
                for (String line : lines)
                {
                    if (line == null || line.trim().isEmpty())
                    {
                        continue;
                    }

                    int itemId = jsonInt(line, "item_id", 0);
                    String itemName = jsonString(line, "item_name", "");
                    int quantity = Math.max(1, jsonInt(line, "quantity", 1));
                    String status = jsonString(line, "status", "HOLD");
                    String source = jsonString(line, "source", "local");
                    String addedAt = jsonString(line, "added_at", "");
                    long assessedUnitValueGp = readJsonLong(line, "assessed_unit_value_gp", readJsonLong(line, "unit_value_gp", 0L));

                    // Completed cards are visible for the session in which they sold, then retired on restart.
                    if (!AutoFlipInventoryLifecyclePolicy.retainAfterRestart(status))
                    {
                        continue;
                    }

                    if (itemId <= 0)
                    {
                        continue;
                    }

                    int inventoryItemId = canonicalizeAutoFlipInventoryItemId(itemId);
                    if (inventoryItemId <= 0)
                    {
                        inventoryItemId = itemId;
                    }

                    if (itemName == null || itemName.isEmpty() || inventoryItemId != itemId)
                    {
                        itemName = resolveAutoFlipItemName(inventoryItemId, itemName);
                    }

                    if (assessedUnitValueGp <= 0L)
                    {
                        assessedUnitValueGp = assessAutoFlipItemValueGp(inventoryItemId);
                    }
                    else
                    {
                        primeAutoFlipSellPriceCache(inventoryItemId, assessedUnitValueGp);
                    }

                    boolean merged = false;
                    for (int i = 0; i < loaded.size(); i++)
                    {
                        AutoFlipInventoryItem existingItem = loaded.get(i);
                        if (existingItem != null && isSameAutoFlipInventoryItemId(existingItem.getItemId(), inventoryItemId))
                        {
                            loaded.set(i, new AutoFlipInventoryItem(
                                canonicalizeAutoFlipInventoryItemId(existingItem.getItemId()),
                                resolveAutoFlipItemName(inventoryItemId, existingItem.getItemName()),
                                Math.max(1, existingItem.getQuantity()) + quantity,
                                existingItem.getStatus(),
                                existingItem.getSource(),
                                existingItem.getAddedAt(),
                                existingItem.getAssessedUnitValueGp() > 0L ? existingItem.getAssessedUnitValueGp() : assessedUnitValueGp,
                                existingItem.getPatienceBaselineGp(),
                                existingItem.getPatienceResultGp()
                            ));
                            merged = true;
                            break;
                        }
                    }

                    if (!merged)
                    {
                        loaded.add(new AutoFlipInventoryItem(inventoryItemId, itemName, quantity, status, source, addedAt, assessedUnitValueGp));
                    }
                }

                autoFlipInventoryItems = java.util.Collections.unmodifiableList(loaded);
                persistAutoFlipInventoryStateLocked();
            }
            catch (Throwable error)
            {
                logAutoFlipUiError("loadAutoFlipInventory", error);
                autoFlipInventoryItems = java.util.Collections.unmodifiableList(loaded);
            }
        }
    }

    private void loadAutoFlipToBuyWatchlist()
    {
        synchronized (autoFlipToBuyLock)
        {
            java.util.List<AutoFlipToBuyItem> loaded = new java.util.ArrayList<>();

            try
            {
                if (!java.nio.file.Files.exists(AUTOFLIP_TO_BUY_STATE_PATH))
                {
                    autoFlipToBuyItems = java.util.Collections.emptyList();
                    return;
                }

                java.util.List<String> lines = java.nio.file.Files.readAllLines(AUTOFLIP_TO_BUY_STATE_PATH, StandardCharsets.UTF_8);
                for (String line : lines)
                {
                    if (line == null || line.trim().isEmpty())
                    {
                        continue;
                    }

                    int itemId = jsonInt(line, "item_id", 0);
                    if (itemId <= 0)
                    {
                        continue;
                    }

                    String itemName = jsonString(line, "item_name", "");
                    int quantity = Math.max(1, jsonInt(line, "quantity", 1));
                    String status = jsonString(line, "status", "WATCH");
                    String source = jsonString(line, "source", "local");
                    String addedAt = jsonString(line, "added_at", "");
                    long targetBuyPriceGp = readJsonLong(line, "target_buy_price_gp", readJsonLong(line, "desired_buy_price_gp", 0L));
                    int buyWindowHours = Math.max(0, jsonInt(line, "buy_window_hours", 0));
                    long autoGoodBuyPriceGp = readJsonLong(line, "auto_good_buy_price_gp", 0L);
                    long currentBuyPriceGp = readJsonLong(line, "current_buy_price_gp", 0L);
                    long currentSellPriceGp = readJsonLong(line, "current_sell_price_gp", 0L);
                    long executionBuyPriceGp = readJsonLong(line, "execution_buy_price_gp", 0L);
                    long executionSellPriceGp = readJsonLong(line, "execution_sell_price_gp", 0L);
                    long marketBuyPriceGp = readJsonLong(line, "market_buy_price_gp", 0L);
                    long marketSellPriceGp = readJsonLong(line, "market_sell_price_gp", 0L);
                    String priceSource = jsonString(line, "price_source", "");
                    String priceReason = jsonString(line, "price_reason", "");
                    long priceUpdatedAtMs = readJsonLong(line, "price_updated_at_ms", 0L);

                    int canonicalItemId = canonicalizeAutoFlipInventoryItemId(itemId);
                    if (canonicalItemId > 0)
                    {
                        itemId = canonicalItemId;
                    }

                    if (itemName == null || itemName.trim().isEmpty() || canonicalItemId != itemId)
                    {
                        itemName = resolveAutoFlipItemName(itemId, itemName);
                    }

                    AutoFlipToBuyItem next = new AutoFlipToBuyItem(
                        itemId,
                        itemName,
                        quantity,
                        status,
                        source,
                        addedAt,
                        targetBuyPriceGp,
                        buyWindowHours,
                        autoGoodBuyPriceGp,
                        currentBuyPriceGp,
                        currentSellPriceGp,
                        executionBuyPriceGp,
                        executionSellPriceGp,
                        marketBuyPriceGp,
                        marketSellPriceGp,
                        priceSource,
                        priceReason,
                        priceUpdatedAtMs
                    );

                    boolean replaced = false;
                    for (int i = 0; i < loaded.size(); i++)
                    {
                        AutoFlipToBuyItem existing = loaded.get(i);
                        if (existing != null && isSameAutoFlipInventoryItemId(existing.getItemId(), next.getItemId()))
                        {
                            loaded.set(i, next);
                            replaced = true;
                            break;
                        }
                    }

                    if (!replaced)
                    {
                        loaded.add(next);
                    }
                }

                autoFlipToBuyItems = java.util.Collections.unmodifiableList(loaded);
                persistAutoFlipToBuyWatchlistLocked();
            }
            catch (Throwable error)
            {
                logAutoFlipUiError("loadAutoFlipToBuyWatchlist", error);
                autoFlipToBuyItems = java.util.Collections.unmodifiableList(loaded);
            }
        }
    }

    private void refreshAutoFlipToBuyMarketDataAsync()
    {
        if (autoFlipToBuyRefreshInFlight)
        {
            return;
        }

        long nowMs = System.currentTimeMillis();
        if (nowMs - autoFlipToBuyLastMarketRefreshAtMs < AUTOFLIP_TO_BUY_PRICE_REFRESH_TTL_MS && autoFlipToBuyItems != null && !autoFlipToBuyItems.isEmpty())
        {
            return;
        }

        autoFlipToBuyRefreshInFlight = true;
        ensurePricePrefetchExecutor();
        pricePrefetchExecutor.submit(() ->
        {
            try
            {
                refreshAutoFlipToBuyMarketData();
            }
            catch (Throwable error)
            {
                logAutoFlipUiError("refreshAutoFlipToBuyMarketDataAsync", error);
            }
            finally
            {
                autoFlipToBuyRefreshInFlight = false;
            }
        });
    }

    private void refreshAutoFlipToBuyMarketData()
    {
        synchronized (autoFlipToBuyLock)
        {
            java.util.List<AutoFlipToBuyItem> existing = autoFlipToBuyItems == null
                ? java.util.Collections.emptyList()
                : autoFlipToBuyItems;

            if (existing.isEmpty())
            {
                autoFlipToBuyLastMarketRefreshAtMs = System.currentTimeMillis();
                return;
            }

            java.util.List<AutoFlipToBuyItem> updated = new java.util.ArrayList<>(existing.size());
            boolean changed = false;
            java.util.Map<Integer, AutoFlipMarketSnapshot> snapshots = fetchAutoFlipMarketSnapshots(existing);

            for (AutoFlipToBuyItem item : existing)
            {
                if (item == null)
                {
                    continue;
                }

                AutoFlipToBuyItem refreshed = item;
                AutoFlipMarketSnapshot snapshot = snapshots.get(item.getItemId());
                if (snapshot != null)
                {
                    refreshed = item.withMarketSnapshot(snapshot);
                    if (!refreshed.isSameMarketSnapshot(item))
                    {
                        changed = true;
                    }
                }

                updated.add(refreshed);
            }

            autoFlipToBuyItems = java.util.Collections.unmodifiableList(updated);
            autoFlipToBuyLastMarketRefreshAtMs = System.currentTimeMillis();

            if (changed || !updated.isEmpty())
            {
                persistAutoFlipToBuyWatchlistLocked();
            }
        }

        notifyAutoFlipSidePanelRefresh();
    }

    public java.util.List<AutoFlipToBuyItem> getAutoFlipToBuySnapshot()
    {
        refreshAutoFlipToBuyMarketDataAsync();

        java.util.List<AutoFlipToBuyItem> items = autoFlipToBuyItems;
        if (items == null)
        {
            return java.util.Collections.emptyList();
        }

        return new java.util.ArrayList<>(items);
    }

    private java.util.List<Integer> getAutoFlipToBuyItemIds()
    {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (AutoFlipToBuyItem item : autoFlipToBuyItems)
        {
            if (item != null && item.getItemId() > 0 && !ids.contains(item.getItemId()))
            {
                ids.add(item.getItemId());
            }
        }
        return ids;
    }

    public java.util.List<AutoFlipToBuyItem> getAutoFlipToBuyReadySnapshot()
    {
        refreshAutoFlipToBuyMarketDataAsync();

        java.util.List<AutoFlipToBuyItem> items = autoFlipToBuyItems;
        if (items == null || items.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        java.util.List<AutoFlipToBuyItem> ready = new java.util.ArrayList<>();
        for (AutoFlipToBuyItem item : items)
        {
            if (item != null && item.isBuyReady())
            {
                ready.add(item);
            }
        }

        ready.sort((left, right) ->
        {
            long leftGap = Math.max(0L, left.getEffectiveBuyThresholdGp() - left.getCurrentBuyPriceGp());
            long rightGap = Math.max(0L, right.getEffectiveBuyThresholdGp() - right.getCurrentBuyPriceGp());
            int compareGap = Long.compare(rightGap, leftGap);
            if (compareGap != 0)
            {
                return compareGap;
            }

            return Integer.compare(Math.max(0, left.getItemId()), Math.max(0, right.getItemId()));
        });

        return ready;
    }

    private AutoFlipToBuyItem findAutoFlipToBuyItem(int itemId)
    {
        if (itemId <= 0)
        {
            return null;
        }

        java.util.List<AutoFlipToBuyItem> items = autoFlipToBuyItems;
        if (items == null)
        {
            return null;
        }

        for (AutoFlipToBuyItem item : items)
        {
            if (item != null && isSameAutoFlipInventoryItemId(item.getItemId(), itemId))
            {
                return item;
            }
        }
        return null;
    }

    static long resolveAutoFlipPreferredBuyAutofillGp(long recommendedGp, long targetGp)
    {
        return targetGp > 0L ? targetGp : Math.max(0L, recommendedGp);
    }

    static int resolveAutoFlipBoardPriority(String riskLabel)
    {
        if ("SELL".equalsIgnoreCase(riskLabel)) return 0;
        if ("TO_BUY".equalsIgnoreCase(riskLabel)) return 1;
        return 2;
    }

    public java.util.List<AutoFlipMarketSearchResult> searchAutoFlipMarketItems(String query, int limit)
    {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        int safeLimit = Math.max(1, Math.min(50, limit));
        String url = cleanBaseUrl(piBaseUrl) + "/market/explorer/search?q=" + queryParam(trimmed) + "&limit=" + safeLimit;
        String body = httpGetText(url, 5000);
        if (body == null || body.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        java.util.List<String> objects = extractJsonArrayObjects(body, "items");
        if (objects.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        java.util.List<AutoFlipMarketSearchResult> results = new java.util.ArrayList<>();
        for (String obj : objects)
        {
            if (obj == null || obj.isEmpty())
            {
                continue;
            }

            int itemId = readJsonInt(obj, "item_id", readJsonInt(obj, "id", 0));
            String itemName = readJsonString(obj, "item_name", readJsonString(obj, "name", "Item " + itemId));
            String slug = readJsonString(obj, "slug", "");
            long buyPriceGp = readJsonLong(obj, "buy_price_gp", readJsonLong(obj, "buy_price", 0L));
            long sellPriceGp = readJsonLong(obj, "sell_price_gp", readJsonLong(obj, "sell_price", 0L));
            long executionBuyPriceGp = readJsonLong(obj, "execution_buy_price_gp", readJsonLong(obj, "execution_buy_price", 0L));
            long executionSellPriceGp = readJsonLong(obj, "execution_sell_price_gp", readJsonLong(obj, "execution_sell_price", 0L));
            String priceSource = readJsonString(obj, "execution_pricing_source", readJsonString(obj, "current_price_source", ""));
            String priceReason = readJsonString(obj, "execution_price_reason", "");

            if (itemId <= 0)
            {
                continue;
            }

            results.add(new AutoFlipMarketSearchResult(
                itemId,
                itemName,
                slug,
                buyPriceGp,
                sellPriceGp,
                executionBuyPriceGp,
                executionSellPriceGp,
                priceSource,
                priceReason,
                getAutoFlipMarketItemUrl(itemId, itemName)
            ));
        }

        results.sort((left, right) ->
        {
            int compareBuy = Long.compare(Math.max(0L, left.getBuyPriceGp()), Math.max(0L, right.getBuyPriceGp()));
            if (compareBuy != 0)
            {
                return compareBuy;
            }

            return left.getItemName().compareToIgnoreCase(right.getItemName());
        });

        if (results.size() > safeLimit)
        {
            return new java.util.ArrayList<>(results.subList(0, safeLimit));
        }

        return results;
    }

    public java.util.List<AutoFlipMarketSearchResult> searchAutoFlipMarketItemsLocally(String query, int limit)
    {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty() || itemManager == null)
        {
            return java.util.Collections.emptyList();
        }

        int safeLimit = Math.max(1, Math.min(50, limit));
        java.util.List<AutoFlipMarketSearchResult> results = new java.util.ArrayList<>();

        try
        {
            for (ItemPrice row : itemManager.search(trimmed))
            {
                if (row == null)
                {
                    continue;
                }

                int itemId = row.getId();
                String itemName = row.getName() == null ? "" : row.getName();

                if (itemId <= 0 || itemName.trim().isEmpty())
                {
                    continue;
                }

                results.add(new AutoFlipMarketSearchResult(
                    itemId,
                    itemName,
                    "",
                    0L,
                    0L,
                    0L,
                    0L,
                    "local_item_search",
                    "",
                    getAutoFlipMarketItemUrl(itemId, itemName)
                ));
            }
        }
        catch (Throwable ignored)
        {
        }

        results.sort((left, right) ->
        {
            String leftName = left == null ? "" : left.getItemName();
            String rightName = right == null ? "" : right.getItemName();
            return leftName.compareToIgnoreCase(rightName);
        });

        if (results.size() > safeLimit)
        {
            return new java.util.ArrayList<>(results.subList(0, safeLimit));
        }

        return results;
    }

    public void addAutoFlipToBuyItem(int itemId, String itemName)
    {
        if (itemId <= 0)
        {
            return;
        }

        int canonicalItemId = canonicalizeAutoFlipInventoryItemId(itemId);
        if (canonicalItemId > 0)
        {
            itemId = canonicalItemId;
        }

        String safeName = resolveAutoFlipItemName(itemId, itemName);
        long nowMs = System.currentTimeMillis();
        // Keep the add-to-buy action instant: insert the item immediately, then
        // let the existing async market refresh backfill prices in the background.
        AutoFlipToBuyItem next = AutoFlipToBuyItem.create(itemId, safeName, now(), null, nowMs);

        synchronized (autoFlipToBuyLock)
        {
            java.util.List<AutoFlipToBuyItem> existing = autoFlipToBuyItems == null
                ? java.util.Collections.emptyList()
                : autoFlipToBuyItems;

            java.util.List<AutoFlipToBuyItem> updated = new java.util.ArrayList<>();
            boolean replaced = false;

            for (AutoFlipToBuyItem item : existing)
            {
                if (item != null && isSameAutoFlipInventoryItemId(item.getItemId(), itemId))
                {
                    updated.add(next.withCopiedSettingsFrom(item));
                    replaced = true;
                }
                else if (item != null)
                {
                    updated.add(item);
                }
            }

            if (!replaced)
            {
                updated.add(next);
            }

            autoFlipToBuyItems = java.util.Collections.unmodifiableList(updated);
            autoFlipToBuyLastMarketRefreshAtMs = nowMs;
            persistAutoFlipToBuyWatchlistLocked();
        }

        requestAutoFlipApiBuyPrices(java.util.Collections.singletonList(itemId));
        refreshAutoFlipToBuyMarketDataAsync();
        notifyAutoFlipSidePanelRefresh();
        logAutoFlipVerbose("AUTOFLIP_TO_BUY_ADDED item_id=" + itemId + " item=" + safeName);
    }

    public void removeAutoFlipToBuyItem(int itemId)
    {
        if (itemId <= 0)
        {
            return;
        }

        int canonicalItemId = canonicalizeAutoFlipInventoryItemId(itemId);
        if (canonicalItemId > 0)
        {
            itemId = canonicalItemId;
        }

        synchronized (autoFlipToBuyLock)
        {
            java.util.List<AutoFlipToBuyItem> existing = autoFlipToBuyItems == null
                ? java.util.Collections.emptyList()
                : autoFlipToBuyItems;

            java.util.List<AutoFlipToBuyItem> updated = new java.util.ArrayList<>();
            boolean changed = false;

            for (AutoFlipToBuyItem item : existing)
            {
                if (item != null && isSameAutoFlipInventoryItemId(item.getItemId(), itemId))
                {
                    changed = true;
                    continue;
                }

                if (item != null)
                {
                    updated.add(item);
                }
            }

            if (changed)
            {
                autoFlipToBuyItems = java.util.Collections.unmodifiableList(updated);
                persistAutoFlipToBuyWatchlistLocked();
            }
        }

        notifyAutoFlipSidePanelRefresh();
    }

    public void setAutoFlipToBuyTargetPrice(int itemId, long targetPriceGp)
    {
        updateAutoFlipToBuySettings(itemId, Math.max(0L, targetPriceGp), -1, -1);
    }

    public void setAutoFlipToBuyWindowHours(int itemId, int windowHours)
    {
        updateAutoFlipToBuySettings(itemId, -1L, Math.max(0, windowHours), -1);
    }

    public void setAutoFlipToBuyQuantity(int itemId, int quantity)
    {
        updateAutoFlipToBuySettings(itemId, -1L, -1, Math.max(1, quantity));
    }

    public void clearAutoFlipToBuyCustomSettings(int itemId)
    {
        updateAutoFlipToBuySettings(itemId, 0L, 0, -1);
    }

    private void updateAutoFlipToBuySettings(int itemId, long targetPriceGp, int windowHours)
    {
        updateAutoFlipToBuySettings(itemId, targetPriceGp, windowHours, -1);
    }

    private void updateAutoFlipToBuySettings(int itemId, long targetPriceGp, int windowHours, int quantity)
    {
        if (itemId <= 0)
        {
            return;
        }

        int canonicalItemId = canonicalizeAutoFlipInventoryItemId(itemId);
        if (canonicalItemId > 0)
        {
            itemId = canonicalItemId;
        }

        synchronized (autoFlipToBuyLock)
        {
            java.util.List<AutoFlipToBuyItem> existing = autoFlipToBuyItems == null
                ? java.util.Collections.emptyList()
                : autoFlipToBuyItems;

            java.util.List<AutoFlipToBuyItem> updated = new java.util.ArrayList<>();
            boolean changed = false;

            for (AutoFlipToBuyItem item : existing)
            {
                if (item == null)
                {
                    continue;
                }

                if (isSameAutoFlipInventoryItemId(item.getItemId(), itemId))
                {
                    long resolvedTargetPriceGp = targetPriceGp < 0L ? item.getTargetBuyPriceGp() : Math.max(0L, targetPriceGp);
                    int resolvedWindowHours = windowHours < 0 ? item.getBuyWindowHours() : Math.max(0, windowHours);
                    int resolvedQuantity = quantity < 0 ? item.getQuantity() : Math.max(1, quantity);
                    updated.add(item.withCustomSettings(resolvedTargetPriceGp, resolvedWindowHours, resolvedQuantity));
                    changed = true;
                }
                else
                {
                    updated.add(item);
                }
            }

            if (changed)
            {
                autoFlipToBuyItems = java.util.Collections.unmodifiableList(updated);
                persistAutoFlipToBuyWatchlistLocked();
            }
        }

        notifyAutoFlipSidePanelRefresh();
    }

    private void addAutoFlipInventoryItem(int itemId, String itemName, int quantity, String source)
    {
        if (itemId <= 0)
        {
            return;
        }

        int inventoryItemId = canonicalizeAutoFlipInventoryItemId(itemId);
        String safeName = resolveAutoFlipItemName(inventoryItemId, itemName);
        int safeQuantity = Math.max(1, quantity);
        String safeSource = source == null || source.trim().isEmpty() ? "manual" : source.trim();
        String addedAt = now();
        long assessedUnitValueGp = assessAutoFlipItemValueGp(inventoryItemId);
        primeAutoFlipSellPriceCache(inventoryItemId, assessedUnitValueGp);

        synchronized (autoFlipInventoryLock)
        {
            java.util.List<AutoFlipInventoryItem> existing = autoFlipInventoryItems == null
                ? java.util.Collections.emptyList()
                : autoFlipInventoryItems;

            java.util.List<AutoFlipInventoryItem> updated = new java.util.ArrayList<>();
            boolean merged = false;

            for (AutoFlipInventoryItem item : existing)
            {
                if (item != null && isSameAutoFlipInventoryItemId(item.getItemId(), inventoryItemId))
                {
                    int mergedQuantity = Math.max(1, item.getQuantity()) + safeQuantity;
                    long unitValue = assessedUnitValueGp > 0L ? assessedUnitValueGp : item.getAssessedUnitValueGp();
                    updated.add(new AutoFlipInventoryItem(inventoryItemId, safeName, mergedQuantity, "HOLD", safeSource, item.getAddedAt(), unitValue));
                    merged = true;
                }
                else if (item != null)
                {
                    updated.add(item);
                }
            }

            if (!merged)
            {
                updated.add(new AutoFlipInventoryItem(inventoryItemId, safeName, safeQuantity, "HOLD", safeSource, addedAt, assessedUnitValueGp));
            }

            autoFlipInventoryItems = java.util.Collections.unmodifiableList(updated);
            persistAutoFlipInventoryStateLocked();

            logAutoFlipMenuEvent("held_inventory_added_" + inventoryItemId);
            logAutoFlipVerbose("AUTOFLIP_HELD_INVENTORY_ADDED item_id=" + inventoryItemId + " original_item_id=" + itemId + " qty=" + safeQuantity + " item=" + safeName);
            notifyAutoFlipSidePanelRefresh();
        }
    }

    public void markAutoFlipInventorySmartSellBaseline(int itemId)
    {
        if (itemId <= 0)
        {
            return;
        }

        int inventoryItemId = canonicalizeAutoFlipInventoryItemId(itemId);

        synchronized (autoFlipInventoryLock)
        {
            java.util.List<AutoFlipInventoryItem> existing = autoFlipInventoryItems == null
                ? java.util.Collections.emptyList()
                : autoFlipInventoryItems;

            java.util.List<AutoFlipInventoryItem> updated = new java.util.ArrayList<>();
            boolean changed = false;

            for (AutoFlipInventoryItem item : existing)
            {
                if (item == null)
                {
                    continue;
                }

                if (isSameAutoFlipInventoryItemId(item.getItemId(), inventoryItemId))
                {
                    long currentBaselineGp = Math.max(0L, item.getAssessedUnitValueGp()) * Math.max(0, item.getQuantity());

                    updated.add(new AutoFlipInventoryItem(
                        item.getItemId(),
                        item.getItemName(),
                        item.getQuantity(),
                        item.getStatus(),
                        item.getSource(),
                        item.getAddedAt(),
                        item.getAssessedUnitValueGp(),
                        currentBaselineGp,
                        item.getPatienceResultGp()
                    ));
                    changed = true;
                }
                else
                {
                    updated.add(item);
                }
            }

            if (changed)
            {
                autoFlipInventoryItems = java.util.Collections.unmodifiableList(updated);
                persistAutoFlipInventoryStateLocked();
                notifyAutoFlipSidePanelRefresh();
            }
        }
    }
    public java.util.List<AutoFlipInventoryItem> getAutoFlipReadyToSellInventorySnapshot()
    {
        refreshAutoFlipInventoryAssessedValues();

        java.util.Properties props = loadAutoFlipSmartSellSettingsForOverlay();
        if (props.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        java.util.List<AutoFlipInventoryItem> items = autoFlipInventoryItems == null
            ? java.util.Collections.emptyList()
            : autoFlipInventoryItems;

        if (items.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        java.util.List<AutoFlipInventoryItem> ready = new java.util.ArrayList<>();

        for (AutoFlipInventoryItem item : items)
        {
            if (item == null || item.getItemId() <= 0)
            {
                continue;
            }

            if ("SOLD".equals(item.getStatus()) || item.getPatienceResultGp() > 0L)
            {
                continue;
            }

            String instruction = props.getProperty(String.valueOf(item.getItemId()), "");
            long targetEachGp = parseAutoFlipSmartSellTargetGp(instruction);
            long currentEachGp = Math.max(0L, item.getAssessedUnitValueGp());

            if (targetEachGp > 0L && currentEachGp >= targetEachGp)
            {
                ready.add(item);
            }
        }

        return ready;
    }

    public java.util.List<AutoFlipInventoryItem> getAutoFlipHoldInventorySnapshot()
    {
        refreshAutoFlipInventoryAssessedValues();

        java.util.List<AutoFlipInventoryItem> items = autoFlipInventoryItems == null
            ? java.util.Collections.emptyList()
            : autoFlipInventoryItems;

        if (items.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        java.util.List<AutoFlipInventoryItem> hold = new java.util.ArrayList<>();
        for (AutoFlipInventoryItem item : items)
        {
            if (item == null || item.getItemId() <= 0 || item.getQuantity() <= 0)
            {
                continue;
            }

            String status = item.getStatus() == null
                ? ""
                : item.getStatus().trim().toUpperCase(java.util.Locale.ROOT);

            if ("SOLD".equals(status))
            {
                continue;
            }

            if (isAutoFlipActiveOfferPresent(item.getItemId()))
            {
                logAutoFlipVerbose(
                    "AUTOFLIP_HOLD_INVENTORY_SKIP_ACTIVE_OFFER"
                        + " item_id=" + item.getItemId()
                        + " item_name=" + safe(item.getItemName())
                        + " qty=" + item.getQuantity()
                );
                continue;
            }

            if (status.isEmpty() || "HOLD".equals(status) || "HELD".equals(status) || "ACTIVE".equals(status))
            {
                hold.add(item);
            }
        }

        hold.sort((left, right) ->
        {
            int compareCapital = Long.compare(Math.max(0L, right.getHeldCapitalGp()), Math.max(0L, left.getHeldCapitalGp()));
            if (compareCapital != 0)
            {
                return compareCapital;
            }

            int compareQuantity = Integer.compare(Math.max(0, right.getQuantity()), Math.max(0, left.getQuantity()));
            if (compareQuantity != 0)
            {
                return compareQuantity;
            }

            return Integer.compare(Math.max(0, left.getItemId()), Math.max(0, right.getItemId()));
        });

        return hold;
    }

    private java.util.List<AutoFlipInventoryItem> getAutoFlipHoldInventorySnapshotInDisplayOrder()
    {
        refreshAutoFlipInventoryAssessedValues();

        java.util.List<AutoFlipInventoryItem> items = autoFlipInventoryItems == null
            ? java.util.Collections.emptyList()
            : autoFlipInventoryItems;

        if (items.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        java.util.List<AutoFlipInventoryItem> hold = new java.util.ArrayList<>();
        for (AutoFlipInventoryItem item : items)
        {
            if (item == null || item.getItemId() <= 0 || item.getQuantity() <= 0)
            {
                continue;
            }

            String status = item.getStatus() == null
                ? ""
                : item.getStatus().trim().toUpperCase(java.util.Locale.ROOT);

            if ("SOLD".equals(status))
            {
                continue;
            }

            if (isAutoFlipActiveOfferPresent(item.getItemId()))
            {
                logAutoFlipVerbose(
                    "AUTOFLIP_HOLD_INVENTORY_DISPLAY_SKIP_ACTIVE_OFFER"
                        + " item_id=" + item.getItemId()
                        + " item_name=" + safe(item.getItemName())
                        + " qty=" + item.getQuantity()
                );
                continue;
            }

            if (status.isEmpty() || "HOLD".equals(status) || "HELD".equals(status) || "ACTIVE".equals(status))
            {
                hold.add(item);
            }
        }

        return hold;
    }

    public AutoFlipSellGuidance getAutoFlipSellGuidanceForOverlay()
    {
        java.util.List<AutoFlipSellGuidance> guidance = getAutoFlipSellGuidanceListForOverlay();
        return guidance.isEmpty() ? null : guidance.get(0);
    }

    public java.util.List<AutoFlipSellGuidance> getAutoFlipSellGuidanceListForOverlay()
    {
        java.util.List<AutoFlipBoardCard> cards = getAutoFlipBoardCardsSnapshot();
        if (cards == null || cards.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        java.util.List<AutoFlipSellGuidance> out = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (AutoFlipBoardCard card : cards)
        {
            if (card == null || !isAutoFlipSellPlanBoardCard(card))
            {
                continue;
            }

            int slotIndex = card.getSlotIndex();
            if (slotIndex >= 0 && slotIndex < autoFlipRetiredBoardSlots.length && autoFlipRetiredBoardSlots[slotIndex])
            {
                continue;
            }

            int itemId = canonicalizeAutoFlipInventoryItemId(card.getItemId());
            if (itemId <= 0)
            {
                continue;
            }

            String key = slotIndex + ":" + itemId;
            if (!seen.add(key))
            {
                continue;
            }

            int inventoryQuantity = resolveAutoFlipInventoryQuantity(itemId);
            boolean activeOfferPresent = isAutoFlipActiveOfferPresent(itemId);
            if (activeOfferPresent)
            {
                logAutoFlipVerbose(
                    "AUTOFLIP_SELL_GUIDANCE_ACTIVE_OFFER_SUPPRESSED"
                        + " slot=" + slotIndex
                        + " item_id=" + itemId
                        + " item_name=" + safe(card.getItemName())
                );
                continue;
            }
            boolean presentInInventory = inventoryQuantity > 0;

            logAutoFlipVerbose(
                "AUTOFLIP_SELL_GUIDANCE_DECISION"
                    + " slot=" + slotIndex
                    + " item_id=" + itemId
                    + " item_name=" + safe(card.getItemName())
                    + " inventory_qty=" + inventoryQuantity
                    + " active_offer_present=" + activeOfferPresent
                    + " present_in_inventory=" + presentInInventory
                    + " card_qty=" + Math.max(0, card.getQuantity())
                    + " sell_price_gp=" + Math.max(0L, card.getSellPriceGp())
            );

            out.add(new AutoFlipSellGuidance(
                slotIndex,
                itemId,
                card.getItemName(),
                Math.max(0, card.getQuantity()),
                Math.max(0L, card.getSellPriceGp()),
                presentInInventory,
                inventoryQuantity
            ));
        }

        return out.isEmpty()
            ? java.util.Collections.emptyList()
            : java.util.Collections.unmodifiableList(out);
    }

    private boolean isAutoFlipActiveOfferPresent(int itemId)
    {
        int canonicalItemId = canonicalizeAutoFlipInventoryItemId(itemId);
        if (canonicalItemId <= 0)
        {
            return false;
        }

        java.util.Set<Integer> liveOfferIds = getAutoFlipLiveActiveOfferItemIdsSnapshot();
        if (!liveOfferIds.isEmpty() && liveOfferIds.contains(canonicalItemId))
        {
            return true;
        }

        for (int slot = 0; slot < slotPlacedItemId.length; slot++)
        {
            if (slotPlacedItemId[slot] != canonicalItemId)
            {
                continue;
            }

            if (slotPlacedTsMs[slot] <= 0L)
            {
                continue;
            }

            String state = slotLastObservedState[slot];
            if (isTerminalOfferState(state))
            {
                continue;
            }

            if (slotPlacedQuantity[slot] <= 0)
            {
                continue;
            }

            return true;
        }

        return false;
    }

    private java.util.Set<Integer> getAutoFlipLiveActiveOfferItemIdsSnapshot()
    {
        java.util.Set<Integer> itemIds = new java.util.LinkedHashSet<>();
        java.util.List<AutoFlipGeOfferSlotSnapshot> snapshots = autoFlipGeOfferSlotSnapshots;
        if (snapshots == null || snapshots.isEmpty())
        {
            return itemIds;
        }

        for (AutoFlipGeOfferSlotSnapshot snapshot : snapshots)
        {
            if (snapshot == null || snapshot.empty || snapshot.terminal)
            {
                continue;
            }

            int itemId = canonicalizeAutoFlipInventoryItemId(snapshot.itemId);
            if (itemId > 0)
            {
                itemIds.add(itemId);
            }
        }

        return itemIds;
    }

    private boolean isAutoFlipSellPlanBoardCard(AutoFlipBoardCard card)
    {
        if (card == null)
        {
            return false;
        }

        String risk = card.getRiskLabel() == null ? "" : card.getRiskLabel().trim();
        if ("SELL".equalsIgnoreCase(risk))
        {
            return true;
        }

        String text = card.getReason() == null
            ? ""
            : card.getReason().trim().toLowerCase(java.util.Locale.ROOT);
        return text.startsWith("sell ")
            || text.startsWith("sell:")
            || text.startsWith("action: sell")
            || text.startsWith("ready to sell")
            || text.startsWith("liquidate")
            || text.startsWith("collect")
            || text.startsWith("exit");
    }

    private long getAutoFlipBoardCardExpectedSetupPrice(AutoFlipBoardCard card)
    {
        if (card == null)
        {
            return 0L;
        }

        if (isAutoFlipSellPlanBoardCard(card) && card.getSellPriceGp() > 0L)
        {
            return card.getSellPriceGp();
        }

        return card.getBuyPriceGp() > 0L ? card.getBuyPriceGp() : card.getSellPriceGp();
    }

    public java.util.List<Rectangle> getAutoFlipInventoryItemBoundsForOverlay(int itemId)
    {
        if (itemId <= 0)
        {
            return java.util.Collections.emptyList();
        }

        int canonicalItemId = canonicalizeAutoFlipInventoryItemId(itemId);
        if (canonicalItemId <= 0)
        {
            return java.util.Collections.emptyList();
        }

        java.util.List<Rectangle> bounds = autoFlipGeSessionInventoryBoundsByItemId.get(canonicalItemId);
        return bounds == null || bounds.isEmpty()
            ? java.util.Collections.emptyList()
            : bounds;
    }

    public String getAutoFlipLiveSellPriceCachePseudoJson()
    {
        java.util.List<java.util.Map.Entry<Integer, Long>> entries = new java.util.ArrayList<>(autoFlipSellPriceCacheByItemId.entrySet());
        entries.sort(java.util.Map.Entry.comparingByKey());

        StringBuilder out = new StringBuilder(Math.max(256, entries.size() * 96));
        out.append("{\n");
        out.append("  schema = autoflip.live_sell_price_cache.v1\n");
        out.append("  generated_at = ").append(java.time.Instant.now().toString()).append('\n');
        out.append("  item_count = ").append(entries.size()).append('\n');
        out.append("  live = true\n");
        out.append("  source = sell_price_cache\n");
        out.append("  entries = [\n");

        if (entries.isEmpty())
        {
            out.append("    (no cached sell prices yet)\n");
        }
        else
        {
            for (int i = 0; i < entries.size(); i++)
            {
                java.util.Map.Entry<Integer, Long> entry = entries.get(i);
                int itemId = entry.getKey() == null ? 0 : entry.getKey();
                long sellPriceGp = Math.max(0L, entry.getValue() == null ? 0L : entry.getValue());
                String itemName = resolveAutoFlipSellPriceDebugItemName(itemId);

                out.append("    {\n");
                out.append("      item_id = ").append(itemId).append('\n');
                out.append("      item_name = ").append(itemName).append('\n');
                out.append("      sell_price_gp = ").append(sellPriceGp).append('\n');
                out.append("      cache_state = live\n");
                out.append("    }");
                if (i + 1 < entries.size())
                {
                    out.append(',');
                }
                out.append('\n');
            }
        }

        out.append("  ]\n");
        out.append("}");
        return out.toString();
    }

    public String getAutoFlipLiveBuyPriceCachePseudoJson()
    {
        java.util.List<java.util.Map.Entry<Integer, Long>> entries = new java.util.ArrayList<>(autoFlipBuyPriceCacheByItemId.entrySet());
        entries.sort(java.util.Map.Entry.comparingByKey());
        StringBuilder out = new StringBuilder(Math.max(256, entries.size() * 128));
        out.append("{\n  schema = autoflip.live_buy_price_cache.v1\n");
        out.append("  generated_at = ").append(java.time.Instant.now()).append('\n');
        out.append("  item_count = ").append(entries.size()).append("\n  live = true\n  source = buy_price_cache\n  entries = [\n");
        if (entries.isEmpty()) out.append("    (no cached buy prices yet)\n");
        for (int i = 0; i < entries.size(); i++)
        {
            int itemId = entries.get(i).getKey();
            out.append("    {\n      item_id = ").append(itemId).append('\n');
            out.append("      item_name = ").append(resolveAutoFlipSellPriceDebugItemName(itemId)).append('\n');
            out.append("      recommended_buy_gp = ").append(entries.get(i).getValue()).append('\n');
            out.append("      last_bought_gp = ").append(autoFlipLastBoughtPriceByItemId.getOrDefault(itemId, 0L)).append('\n');
            out.append("      last_bought_at_ms = ").append(autoFlipLastBoughtAtMsByItemId.getOrDefault(itemId, 0L)).append('\n');
            out.append("      cache_state = live\n    }").append(i + 1 < entries.size() ? ",\n" : "\n");
        }
        out.append("  ]\n}");
        return out.toString();
    }

    public String getAutoFlipCurrentOffersPseudoJson()
    {
        if (client == null)
        {
            return "{\n  schema = autoflip.current_offers.v1\n  active_offer_count = 0\n  entries = [\n    (client unavailable)\n  ]\n}";
        }

        final net.runelite.api.GrandExchangeOffer[][] offersHolder = new net.runelite.api.GrandExchangeOffer[1][];
        final Throwable[] failure = new Throwable[1];

        java.lang.Runnable task = () ->
        {
            try
            {
                offersHolder[0] = client.getGrandExchangeOffers();
            }
            catch (Throwable error)
            {
                failure[0] = error;
            }
        };

        if (!client.isClientThread() && clientThread != null)
        {
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            clientThread.invoke(() ->
            {
                try
                {
                    task.run();
                }
                finally
                {
                    latch.countDown();
                }
            });

            try
            {
                latch.await(250L, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException interruptedException)
            {
                Thread.currentThread().interrupt();
            }
        }
        else
        {
            task.run();
        }

        if (failure[0] != null)
        {
            logAutoFlipUiError("getAutoFlipCurrentOffersPseudoJson", failure[0]);
        }

        net.runelite.api.GrandExchangeOffer[] offers = offersHolder[0];
        if (offers == null)
        {
            return "{\n  schema = autoflip.current_offers.v1\n  active_offer_count = 0\n  entries = [\n    (no active offers yet)\n  ]\n}";
        }

        StringBuilder out = new StringBuilder(1024);
        out.append("{\n");
        out.append("  schema = autoflip.current_offers.v1\n");
        out.append("  generated_at = ").append(java.time.Instant.now().toString()).append('\n');
        out.append("  active_offer_count = ");
        int activeCount = 0;
        for (net.runelite.api.GrandExchangeOffer offer : offers)
        {
            if (offer == null)
            {
                continue;
            }

            int itemId = Math.max(0, offer.getItemId());
            String state = stateName(offer.getState());
            int totalQty = Math.max(0, offer.getTotalQuantity());
            int soldQty = Math.max(0, offer.getQuantitySold());
            long spent = Math.max(0L, offer.getSpent());
            boolean empty = itemId <= 0
                || "EMPTY".equalsIgnoreCase(state)
                || (totalQty <= 0 && soldQty <= 0 && spent <= 0L);
            if (!empty)
            {
                activeCount++;
            }
        }
        out.append(activeCount).append('\n');
        out.append("  entries = [\n");

        if (activeCount <= 0)
        {
            out.append("    (no active offers yet)\n");
        }
        else
        {
            int emitted = 0;
            for (int slot = 0; slot < offers.length; slot++)
            {
                net.runelite.api.GrandExchangeOffer offer = offers[slot];
                if (offer == null)
                {
                    continue;
                }

                int itemId = Math.max(0, offer.getItemId());
                String state = stateName(offer.getState());
                int totalQty = Math.max(0, offer.getTotalQuantity());
                int soldQty = Math.max(0, offer.getQuantitySold());
                long spent = Math.max(0L, offer.getSpent());
                boolean empty = itemId <= 0
                    || "EMPTY".equalsIgnoreCase(state)
                    || (totalQty <= 0 && soldQty <= 0 && spent <= 0L);
                if (empty)
                {
                    continue;
                }

                int offeredPrice = clampAutoFlipGpToInt(offer.getPrice());
                int remainingQty = Math.max(0, totalQty - soldQty);
                long ageSeconds = 0L;
                if (slot >= 0 && slot < slotPlacedTsMs.length && slotPlacedTsMs[slot] > 0L)
                {
                    ageSeconds = Math.max(0L, (System.currentTimeMillis() - slotPlacedTsMs[slot]) / 1000L);
                }

                if (emitted > 0)
                {
                    out.append(",\n");
                }

                out.append("    {\n");
                out.append("      slot = ").append(slot).append('\n');
                out.append("      item_id = ").append(itemId).append('\n');
                out.append("      item_name = ").append(resolveAutoFlipItemName(itemId, "Item " + itemId)).append('\n');
                out.append("      side = ").append(inferSide(state)).append('\n');
                out.append("      state = ").append(state).append('\n');
                out.append("      offered_price_gp = ").append(offeredPrice).append('\n');
                out.append("      offered_quantity = ").append(totalQty).append('\n');
                out.append("      sold_quantity = ").append(soldQty).append('\n');
                out.append("      remaining_quantity = ").append(remainingQty).append('\n');
                out.append("      spent_or_received_gp = ").append(spent).append('\n');
                out.append("      age_seconds = ").append(ageSeconds).append('\n');
                out.append("    }");
                emitted++;
            }
        }

        out.append("  ]\n");
        out.append("}");
        return out.toString();
    }

    public java.util.List<AutoFlipCurrentOfferSnapshot> getAutoFlipCurrentOffersSnapshot()
    {
        java.util.List<AutoFlipCurrentOfferSnapshot> snapshots = new java.util.ArrayList<>();
        if (client == null)
        {
            return snapshots;
        }

        java.lang.Runnable task = () ->
        {
            try
            {
                GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
                if (offers == null || offers.length == 0)
                {
                    return;
                }

                long observedTsMs = System.currentTimeMillis();
                for (int slot = 0; slot < offers.length; slot++)
                {
                    GrandExchangeOffer offer = offers[slot];
                    if (offer == null)
                    {
                        continue;
                    }

                    int itemId = Math.max(0, offer.getItemId());
                    String state = stateName(offer.getState());
                    int totalQty = Math.max(0, offer.getTotalQuantity());
                    int soldQty = Math.max(0, offer.getQuantitySold());
                    long spent = Math.max(0L, offer.getSpent());
                    boolean empty = itemId <= 0
                        || "EMPTY".equalsIgnoreCase(state)
                        || (totalQty <= 0 && soldQty <= 0 && spent <= 0L);
                    if (empty)
                    {
                        continue;
                    }

                    int remainingQty = Math.max(0, totalQty - soldQty);
                    long ageSeconds = 0L;
                    if (slot >= 0 && slot < slotPlacedTsMs.length && slotPlacedTsMs[slot] > 0L)
                    {
                        ageSeconds = Math.max(0L, (observedTsMs - slotPlacedTsMs[slot]) / 1000L);
                    }

                    snapshots.add(new AutoFlipCurrentOfferSnapshot(
                        slot,
                        itemId,
                        resolveAutoFlipItemName(itemId, "Item " + itemId),
                        inferSide(state),
                        state,
                        clampAutoFlipGpToInt(offer.getPrice()),
                        totalQty,
                        soldQty,
                        remainingQty,
                        spent,
                        ageSeconds
                    ));
                }
            }
            catch (Throwable error)
            {
                logAutoFlipUiError("getAutoFlipCurrentOffersSnapshot", error);
            }
        };

        if (!client.isClientThread() && clientThread != null)
        {
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            clientThread.invoke(() ->
            {
                try
                {
                    task.run();
                }
                finally
                {
                    latch.countDown();
                }
            });

            try
            {
                latch.await(250L, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            catch (InterruptedException interruptedException)
            {
                Thread.currentThread().interrupt();
            }

            return snapshots;
        }

        task.run();
        return snapshots;
    }

    private java.util.Properties loadAutoFlipSmartSellSettingsForOverlay()
    {
        java.util.Properties props = new java.util.Properties();

        try
        {
            if (!java.nio.file.Files.exists(SMART_SELL_SETTINGS_PATH))
            {
                return props;
            }

            try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(SMART_SELL_SETTINGS_PATH, java.nio.charset.StandardCharsets.UTF_8))
            {
                props.load(reader);
            }
        }
        catch (Throwable ignored)
        {
        }

        return props;
    }

    private long parseAutoFlipSmartSellTargetGp(String instruction)
    {
        if (instruction == null || !instruction.startsWith("Target Sell:"))
        {
            return 0L;
        }

        String value = instruction
            .replace("Target Sell:", "")
            .replace("gp", "")
            .replace(",", "")
            .trim()
            .toLowerCase(java.util.Locale.ROOT);

        if (value.isEmpty() || value.contains("invalid"))
        {
            return 0L;
        }

        try
        {
            long multiplier = 1L;

            if (value.endsWith("k"))
            {
                multiplier = 1_000L;
                value = value.substring(0, value.length() - 1).trim();
            }
            else if (value.endsWith("m"))
            {
                multiplier = 1_000_000L;
                value = value.substring(0, value.length() - 1).trim();
            }
            else if (value.endsWith("b"))
            {
                multiplier = 1_000_000_000L;
                value = value.substring(0, value.length() - 1).trim();
            }

            double parsed = Double.parseDouble(value);
            if (parsed <= 0)
            {
                return 0L;
            }

            return Math.max(0L, Math.round(parsed * multiplier));
        }
        catch (Throwable ignored)
        {
            return 0L;
        }
    }

    public String getAutoFlipMarketItemUrl(int itemId, String itemName)
    {
        return AutoFlipGeMarketLink.buildUrl(piBaseUrl, itemId, itemName);
    }
    public void openAutoFlipMarketForItem(int itemId, String itemName)
    {
        try
        {
            if (itemId <= 0)
            {
                return;
            }

            String url = getAutoFlipMarketItemUrl(itemId, itemName);
            LinkBrowser.browse(url);
            logAutoFlipMenuEvent("market_opened_" + itemId);
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("openAutoFlipMarketForItem", error);
        }
    }
    public java.util.List<AutoFlipInventoryItem> getAutoFlipInventorySnapshot()
    {
        refreshAutoFlipInventoryAssessedValues();

        java.util.List<AutoFlipInventoryItem> items = autoFlipInventoryItems;
        if (items == null)
        {
            return java.util.Collections.emptyList();
        }

        return items;
    }

    private void persistAutoFlipToBuyWatchlistLocked()
    {
        try
        {
            java.nio.file.Path path = AUTOFLIP_TO_BUY_STATE_PATH;
            java.nio.file.Path parent = path.getParent();
            if (parent != null)
            {
                java.nio.file.Files.createDirectories(parent);
            }

            java.util.List<AutoFlipToBuyItem> items = autoFlipToBuyItems == null
                ? java.util.Collections.emptyList()
                : autoFlipToBuyItems;

            java.util.List<String> lines = new java.util.ArrayList<>();
            for (AutoFlipToBuyItem item : items)
            {
                if (item == null || item.getItemId() <= 0)
                {
                    continue;
                }

                lines.add(item.toJsonLine());
            }

            java.nio.file.Files.write(
                path,
                lines,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            );
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("persistAutoFlipToBuyWatchlistLocked", error);
        }
    }

    private AutoFlipMarketSnapshot fetchAutoFlipMarketSnapshot(int itemId, String fallbackName)
    {
        if (itemId <= 0)
        {
            return null;
        }

        String url = cleanBaseUrl(piBaseUrl) + "/market/explorer/item/" + itemId;
        String body = httpGetText(url, 5000);
        if (body == null || body.trim().isEmpty())
        {
            return new AutoFlipMarketSnapshot(
                itemId,
                resolveAutoFlipItemName(itemId, fallbackName),
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                "",
                "",
                now()
            );
        }

        java.util.List<String> objects = extractJsonArrayObjects(body, "items");
        if (objects.isEmpty())
        {
            objects = extractJsonArrayObjects(body, "cards");
        }

        if (objects.isEmpty())
        {
            return new AutoFlipMarketSnapshot(
                itemId,
                resolveAutoFlipItemName(itemId, fallbackName),
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                "",
                "",
                now()
            );
        }

        String obj = objects.get(0);
        String itemName = readJsonString(obj, "item_name", readJsonString(obj, "name", resolveAutoFlipItemName(itemId, fallbackName)));
        long marketBuyPriceGp = readJsonLong(obj, "market_buy_price", readJsonLong(obj, "buy_price", readJsonLong(obj, "low", 0L)));
        long marketSellPriceGp = readJsonLong(obj, "market_sell_price", readJsonLong(obj, "sell_price", readJsonLong(obj, "high", 0L)));
        long executionBuyPriceGp = readJsonLong(obj, "execution_buy_price_gp", readJsonLong(obj, "execution_buy_price", 0L));
        long executionSellPriceGp = readJsonLong(obj, "execution_sell_price_gp", readJsonLong(obj, "execution_sell_price", 0L));
        long dayLowGp = readJsonLong(obj, "day_low", readJsonLong(obj, "low", 0L));
        long weekLowGp = readJsonLong(obj, "week_low", 0L);
        long monthLowGp = readJsonLong(obj, "month_low", 0L);
        long dayHighGp = readJsonLong(obj, "day_high", readJsonLong(obj, "high", 0L));
        long weekHighGp = readJsonLong(obj, "week_high", 0L);
        long monthHighGp = readJsonLong(obj, "month_high", 0L);
        long currentBuyPriceGp = readJsonLong(obj, "buy_price_gp", readJsonLong(obj, "buy_price", marketBuyPriceGp));
        long currentSellPriceGp = readJsonLong(obj, "sell_price_gp", readJsonLong(obj, "sell_price", marketSellPriceGp));
        String priceSource = readJsonString(obj, "execution_pricing_source", readJsonString(obj, "current_price_source", ""));
        String priceReason = readJsonString(obj, "execution_price_reason", "");
        String priceUpdatedAt = readJsonString(obj, "updated_at", readJsonString(obj, "snapshot_bucket", now()));

        return new AutoFlipMarketSnapshot(
            itemId,
            itemName,
            currentBuyPriceGp,
            currentSellPriceGp,
            marketBuyPriceGp,
            marketSellPriceGp,
            executionBuyPriceGp,
            executionSellPriceGp,
            dayLowGp,
            weekLowGp,
            monthLowGp,
            dayHighGp,
            weekHighGp,
            monthHighGp,
            priceSource,
            priceReason,
            priceUpdatedAt
        );
    }

    private java.util.Map<Integer, AutoFlipMarketSnapshot> fetchAutoFlipMarketSnapshots(
        java.util.List<AutoFlipToBuyItem> items
    )
    {
        java.util.Map<Integer, AutoFlipMarketSnapshot> snapshots = new java.util.LinkedHashMap<>();
        if (items == null || items.isEmpty())
        {
            return snapshots;
        }

        StringBuilder ids = new StringBuilder();
        java.util.Map<Integer, String> fallbackNames = new java.util.LinkedHashMap<>();
        for (AutoFlipToBuyItem item : items)
        {
            if (item == null || item.getItemId() <= 0 || fallbackNames.containsKey(item.getItemId()))
            {
                continue;
            }
            if (ids.length() > 0)
            {
                ids.append(',');
            }
            ids.append(item.getItemId());
            fallbackNames.put(item.getItemId(), item.getItemName());
        }

        if (fallbackNames.isEmpty())
        {
            return snapshots;
        }

        String url = cleanBaseUrl(piBaseUrl) + "/market/explorer/prices?ids=" + ids + "&limit=" + fallbackNames.size();
        String body = httpGetText(url, 5000);
        if (body == null || body.trim().isEmpty())
        {
            return snapshots;
        }

        for (String obj : extractJsonArrayObjects(body, "items"))
        {
            int itemId = readJsonInt(obj, "item_id", readJsonInt(obj, "id", 0));
            if (itemId <= 0 || !fallbackNames.containsKey(itemId))
            {
                continue;
            }

            String itemName = readJsonString(obj, "item_name", readJsonString(obj, "name", resolveAutoFlipItemName(itemId, fallbackNames.get(itemId))));
            long marketBuyPriceGp = readJsonLong(obj, "raw_buy_price", readJsonLong(obj, "market_buy_price", readJsonLong(obj, "buy_price", 0L)));
            long marketSellPriceGp = readJsonLong(obj, "raw_sell_price", readJsonLong(obj, "market_sell_price", readJsonLong(obj, "sell_price", 0L)));
            long executionBuyPriceGp = readJsonLong(obj, "execution_buy_price", 0L);
            long executionSellPriceGp = readJsonLong(obj, "execution_sell_price", 0L);
            long currentBuyPriceGp = readJsonLong(obj, "buy_price", marketBuyPriceGp);
            long currentSellPriceGp = readJsonLong(obj, "sell_price", marketSellPriceGp);
            String priceSource = readJsonString(obj, "price_source", readJsonString(obj, "market_price_source", ""));
            String priceReason = readJsonString(obj, "execution_price_reason", "");
            String priceUpdatedAt = readJsonString(obj, "market_price_collected_at", readJsonString(obj, "price_timestamp", now()));

            primeAutoFlipBuyPriceCache(itemId, new AutoFlipExplorerPriceParser.PricePoint(
                executionBuyPriceGp > 0L ? executionBuyPriceGp : currentBuyPriceGp,
                executionSellPriceGp > 0L ? executionSellPriceGp : currentSellPriceGp,
                marketBuyPriceGp,
                marketSellPriceGp,
                readJsonLong(obj, "buy_time", 0L),
                readJsonLong(obj, "sell_time", 0L)
            ));

            snapshots.put(itemId, new AutoFlipMarketSnapshot(
                itemId,
                itemName,
                currentBuyPriceGp,
                currentSellPriceGp,
                marketBuyPriceGp,
                marketSellPriceGp,
                executionBuyPriceGp,
                executionSellPriceGp,
                readJsonLong(obj, "day_low", currentBuyPriceGp),
                readJsonLong(obj, "week_low", 0L),
                readJsonLong(obj, "month_low", 0L),
                readJsonLong(obj, "day_high", currentSellPriceGp),
                readJsonLong(obj, "week_high", 0L),
                readJsonLong(obj, "month_high", 0L),
                priceSource,
                priceReason,
                priceUpdatedAt
            ));
        }

        return snapshots;
    }
    private void refreshAutoFlipInventoryAssessedValues()
    {
        synchronized (autoFlipInventoryLock)
        {
            java.util.List<AutoFlipInventoryItem> existing = autoFlipInventoryItems == null
                ? java.util.Collections.emptyList()
                : autoFlipInventoryItems;

            if (existing.isEmpty())
            {
                return;
            }

            java.util.List<AutoFlipInventoryItem> updated = new java.util.ArrayList<>();
            boolean changed = false;

            for (AutoFlipInventoryItem item : existing)
            {
                if (item == null)
                {
                    continue;
                }

                long currentUnitValueGp = assessAutoFlipItemValueGp(item.getItemId());
                if (currentUnitValueGp > 0L && currentUnitValueGp != item.getAssessedUnitValueGp())
                {
                    updated.add(new AutoFlipInventoryItem(
                        item.getItemId(),
                        item.getItemName(),
                        item.getQuantity(),
                        item.getStatus(),
                        item.getSource(),
                        item.getAddedAt(),
                        currentUnitValueGp
                    ));
                    changed = true;
                }
                else
                {
                    updated.add(item);
                }
            }

            if (changed)
            {
                autoFlipInventoryItems = java.util.Collections.unmodifiableList(updated);
                persistAutoFlipInventoryStateLocked();
            }
        }
    }

    public void removeAutoFlipInventoryItem(int itemId)
    {
        if (itemId <= 0)
        {
            return;
        }

        int inventoryItemId = canonicalizeAutoFlipInventoryItemId(itemId);

        synchronized (autoFlipInventoryLock)
        {
            java.util.List<AutoFlipInventoryItem> existing = autoFlipInventoryItems == null
                ? java.util.Collections.emptyList()
                : autoFlipInventoryItems;

            java.util.List<AutoFlipInventoryItem> updated = new java.util.ArrayList<>();
            boolean removed = false;

            for (AutoFlipInventoryItem item : existing)
            {
                if (item != null && isSameAutoFlipInventoryItemId(item.getItemId(), inventoryItemId))
                {
                    removed = true;
                    continue;
                }

                if (item != null)
                {
                    updated.add(item);
                }
            }

            if (removed)
            {
                autoFlipInventoryItems = java.util.Collections.unmodifiableList(updated);
                persistAutoFlipInventoryStateLocked();
                logAutoFlipMenuEvent("held_inventory_removed_" + inventoryItemId);
                logAutoFlipVerbose("AUTOFLIP_HELD_INVENTORY_REMOVED item_id=" + inventoryItemId + " original_item_id=" + itemId);
                notifyAutoFlipSidePanelRefresh();
            }
        }
    }

    private void decrementAutoFlipInventoryItem(int itemId, int soldQuantity)
    {
        if (itemId <= 0 || soldQuantity <= 0)
        {
            return;
        }

        int inventoryItemId = canonicalizeAutoFlipInventoryItemId(itemId);

        synchronized (autoFlipInventoryLock)
        {
            java.util.List<AutoFlipInventoryItem> existing = autoFlipInventoryItems == null
                ? java.util.Collections.emptyList()
                : autoFlipInventoryItems;

            java.util.List<AutoFlipInventoryItem> updated = new java.util.ArrayList<>();
            boolean changed = false;

            for (AutoFlipInventoryItem item : existing)
            {
                if (item == null)
                {
                    continue;
                }

                if (isSameAutoFlipInventoryItemId(item.getItemId(), inventoryItemId))
                {
                    int remaining = Math.max(0, item.getQuantity() - soldQuantity);
                    changed = true;

                    if (remaining > 0)
                    {
                        updated.add(new AutoFlipInventoryItem(
                            item.getItemId(),
                            item.getItemName(),
                            remaining,
                            item.getStatus(),
                            item.getSource(),
                            item.getAddedAt(),
                            item.getAssessedUnitValueGp(),
                            item.getPatienceBaselineGp(),
                            item.getPatienceResultGp()
                        ));
                    }
                }
                else
                {
                    updated.add(item);
                }
            }

            if (changed)
            {
                autoFlipInventoryItems = java.util.Collections.unmodifiableList(updated);
                persistAutoFlipInventoryStateLocked();
                logAutoFlipMenuEvent("held_inventory_decremented_" + inventoryItemId + "_" + soldQuantity);
                logAutoFlipVerbose("AUTOFLIP_HELD_INVENTORY_DECREMENTED item_id=" + inventoryItemId + " original_item_id=" + itemId + " sold_qty=" + soldQuantity);
                notifyAutoFlipSidePanelRefresh();
            }
        }
    }

    private void resetAutoFlipHeldSaleMemoryForSlot(int slot)
    {
        if (slot < 0 || slot >= autoFlipHeldSaleFilledBySlot.length)
        {
            return;
        }

        autoFlipHeldSaleFilledBySlot[slot] = 0;
        autoFlipHeldSaleSpentBySlot[slot] = 0L;

        if (slot < autoFlipHeldSaleInstanceSeqBySlot.length)
        {
            autoFlipHeldSaleInstanceSeqBySlot[slot] = slotInstanceSeq[slot];
        }
    }

    private void resetAutoFlipHeldBuyMemoryForSlot(int slot)
    {
        if (slot < 0 || slot >= autoFlipHeldBuyFilledBySlot.length)
        {
            return;
        }

        autoFlipHeldBuyFilledBySlot[slot] = 0;
        autoFlipHeldBuySpentBySlot[slot] = 0L;

        if (slot < autoFlipHeldBuyInstanceSeqBySlot.length)
        {
            autoFlipHeldBuyInstanceSeqBySlot[slot] = slotInstanceSeq[slot];
        }
    }

    private int autoFlipAccountingSeqForObservedOffer(int slot, int itemId, String state, int totalQuantity, int filledQuantity, int price, long spent)
    {
        if (slot < 0 || slot >= slotInstanceSeq.length)
        {
            return 0;
        }

        String snapshot = slot
            + "|" + itemId
            + "|" + (state == null ? "UNKNOWN" : state)
            + "|" + totalQuantity
            + "|" + filledQuantity
            + "|" + price
            + "|" + spent;

        boolean currentIsEmpty = isCapacityEmptySlot(itemId, state, totalQuantity, filledQuantity, price, (int) Math.max(0L, Math.min(Integer.MAX_VALUE, spent)));
        boolean previousWasEmpty = lastCanonicalSnapshots[slot] == null || lastCanonicalSnapshots[slot].contains("|EMPTY|");

        if (!currentIsEmpty && previousWasEmpty && !snapshot.equals(lastCanonicalSnapshots[slot]))
        {
            return slotInstanceSeq[slot] + 1;
        }

        return slotInstanceSeq[slot];
    }

    private String autoFlipGeAccountingKey(int slot, int itemId, String side, int totalQuantity, int offerPriceGp, int accountingSeq)
    {
        String normalizedSide = side == null ? "UNKNOWN" : side;
        return sha256(
            accountKey
                + "|ge_inventory_accounting"
                + "|slot=" + slot
                + "|item=" + itemId
                + "|side=" + normalizedSide
                + "|total=" + Math.max(0, totalQuantity)
                + "|price=" + Math.max(0, offerPriceGp)
        );
    }

    private long[] loadAutoFlipGeAccountedTotals(String accountingKey)
    {
        long[] totals = new long[] {0L, 0L};

        if (accountingKey == null || accountingKey.isEmpty() || !Files.exists(AUTOFLIP_GE_ACCOUNTING_STATE_PATH))
        {
            return totals;
        }

        try
        {
            for (String line : Files.readAllLines(AUTOFLIP_GE_ACCOUNTING_STATE_PATH, StandardCharsets.UTF_8))
            {
                if (!accountingKey.equals(jsonString(line, "accounting_key", "")))
                {
                    continue;
                }

                totals[0] = Math.max(totals[0], readJsonLong(line, "accounted_quantity", 0L));
                totals[1] = Math.max(totals[1], readJsonLong(line, "accounted_spent_gp", 0L));
            }
        }
        catch (IOException e)
        {
            log.warn("Unable to load AutoFlip GE accounting state", e);
        }

        return totals;
    }

    private long[] loadAutoFlipGeAccountedTotals(String accountingKey, int slot, int itemId, String side, int totalQuantity, int offerPriceGp)
    {
        long[] totals = new long[] {0L, 0L};

        if (!Files.exists(AUTOFLIP_GE_ACCOUNTING_STATE_PATH))
        {
            return totals;
        }

        String normalizedSide = side == null ? "UNKNOWN" : side;

        try
        {
            for (String line : Files.readAllLines(AUTOFLIP_GE_ACCOUNTING_STATE_PATH, StandardCharsets.UTF_8))
            {
                boolean sameKey = accountingKey != null && !accountingKey.isEmpty() && accountingKey.equals(jsonString(line, "accounting_key", ""));
                boolean sameOffer =
                    accountKey.equals(jsonString(line, "account_key", ""))
                        && jsonInt(line, "slot", -1) == slot
                        && jsonInt(line, "item_id", -1) == itemId
                        && normalizedSide.equals(jsonString(line, "side", "UNKNOWN"))
                        && jsonInt(line, "total_quantity", -1) == Math.max(0, totalQuantity)
                        && jsonInt(line, "offer_price_gp", -1) == Math.max(0, offerPriceGp);

                if (!sameKey && !sameOffer)
                {
                    continue;
                }

                totals[0] = Math.max(totals[0], readJsonLong(line, "accounted_quantity", 0L));
                totals[1] = Math.max(totals[1], readJsonLong(line, "accounted_spent_gp", 0L));
            }
        }
        catch (IOException e)
        {
            log.warn("Unable to load AutoFlip GE accounting state", e);
        }

        return totals;
    }

    private void persistAutoFlipGeAccountedTotals(
        String accountingKey,
        int slot,
        int itemId,
        String side,
        int totalQuantity,
        int offerPriceGp,
        int accountingSeq,
        long accountedQuantity,
        long accountedSpentGp
    )
    {
        if (accountingKey == null || accountingKey.isEmpty())
        {
            return;
        }

        try
        {
            java.nio.file.Path parent = AUTOFLIP_GE_ACCOUNTING_STATE_PATH.getParent();
            if (parent != null)
            {
                Files.createDirectories(parent);
            }

            java.util.List<String> lines = Files.exists(AUTOFLIP_GE_ACCOUNTING_STATE_PATH)
                ? new java.util.ArrayList<>(Files.readAllLines(AUTOFLIP_GE_ACCOUNTING_STATE_PATH, StandardCharsets.UTF_8))
                : new java.util.ArrayList<>();

            String replacement = "{"
                + "\"event\":\"autoflip_ge_inventory_accounted\","
                + "\"accounting_key\":\"" + safe(accountingKey) + "\","
                + "\"account_key\":\"" + safe(accountKey) + "\","
                + "\"slot\":" + slot + ","
                + "\"slot_instance_seq\":" + accountingSeq + ","
                + "\"item_id\":" + itemId + ","
                + "\"side\":\"" + safe(side == null ? "UNKNOWN" : side) + "\","
                + "\"total_quantity\":" + Math.max(0, totalQuantity) + ","
                + "\"offer_price_gp\":" + Math.max(0, offerPriceGp) + ","
                + "\"accounted_quantity\":" + Math.max(0L, accountedQuantity) + ","
                + "\"accounted_spent_gp\":" + Math.max(0L, accountedSpentGp) + ","
                + "\"updated_at\":\"" + now() + "\""
                + "}";

            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++)
            {
                if (accountingKey.equals(jsonString(lines.get(i), "accounting_key", "")))
                {
                    lines.set(i, replacement);
                    replaced = true;
                    break;
                }
            }

            if (!replaced)
            {
                lines.add(replacement);
            }

            Files.write(AUTOFLIP_GE_ACCOUNTING_STATE_PATH, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("persistAutoFlipGeAccountedTotals", error);
        }
    }

    private void purgeAutoFlipGeAccountedTotalsForSlot(int slot)
    {
        if (slot < 0 || !Files.exists(AUTOFLIP_GE_ACCOUNTING_STATE_PATH))
        {
            return;
        }

        try
        {
            java.util.List<String> lines = Files.readAllLines(AUTOFLIP_GE_ACCOUNTING_STATE_PATH, StandardCharsets.UTF_8);
            java.util.List<String> kept = new java.util.ArrayList<>();
            boolean changed = false;

            for (String line : lines)
            {
                if (line == null || line.trim().isEmpty())
                {
                    continue;
                }

                boolean sameAccount = accountKey.equals(jsonString(line, "account_key", ""));
                boolean sameSlot = jsonInt(line, "slot", -1) == slot;
                if (sameAccount && sameSlot)
                {
                    changed = true;
                    continue;
                }

                kept.add(line);
            }

            if (changed)
            {
                Files.write(AUTOFLIP_GE_ACCOUNTING_STATE_PATH, kept, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                logAutoFlipVerbose("AUTOFLIP_GE_ACCOUNTING_PURGED slot=" + slot);
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("purgeAutoFlipGeAccountedTotalsForSlot", error);
        }
    }

    private void autoFlipMaybeApplyHeldInventoryPurchase(int slot, int itemId, String state, int totalQuantity, int cumulativeBoughtQuantity, int offerPriceGp, long cumulativeSpentGp)
    {
        if (slot < 0 || slot >= autoFlipHeldBuyFilledBySlot.length || itemId <= 0 || cumulativeBoughtQuantity <= 0)
        {
            return;
        }

        if (slot < autoFlipHeldBuyInstanceSeqBySlot.length && autoFlipHeldBuyInstanceSeqBySlot[slot] != slotInstanceSeq[slot])
        {
            resetAutoFlipHeldBuyMemoryForSlot(slot);
        }

        int accountingSeq = autoFlipAccountingSeqForObservedOffer(slot, itemId, state, totalQuantity, cumulativeBoughtQuantity, offerPriceGp, cumulativeSpentGp);
        String accountingKey = autoFlipGeAccountingKey(slot, itemId, "BUY", totalQuantity, offerPriceGp, accountingSeq);
        long[] persistedTotals = loadAutoFlipGeAccountedTotals(accountingKey, slot, itemId, "BUY", totalQuantity, offerPriceGp);
        int previousBoughtQuantity = Math.max(Math.max(0, autoFlipHeldBuyFilledBySlot[slot]), (int) Math.min(Integer.MAX_VALUE, persistedTotals[0]));
        int newlyBoughtQuantity = cumulativeBoughtQuantity - previousBoughtQuantity;

        if (newlyBoughtQuantity <= 0)
        {
            autoFlipHeldBuyFilledBySlot[slot] = Math.max(Math.max(0, autoFlipHeldBuyFilledBySlot[slot]), cumulativeBoughtQuantity);
            autoFlipHeldBuySpentBySlot[slot] = Math.max(Math.max(0L, autoFlipHeldBuySpentBySlot[slot]), cumulativeSpentGp);
            return;
        }

        long previousSpentGp = Math.max(Math.max(0L, autoFlipHeldBuySpentBySlot[slot]), persistedTotals[1]);
        long newlySpentGp = cumulativeSpentGp > previousSpentGp
            ? cumulativeSpentGp - previousSpentGp
            : Math.max(0L, assessAutoFlipItemValueGp(itemId)) * Math.max(1, newlyBoughtQuantity);

        autoFlipHeldBuyFilledBySlot[slot] = Math.max(previousBoughtQuantity, cumulativeBoughtQuantity);
        autoFlipHeldBuySpentBySlot[slot] = Math.max(previousSpentGp, cumulativeSpentGp);
        persistAutoFlipGeAccountedTotals(accountingKey, slot, itemId, "BUY", totalQuantity, offerPriceGp, accountingSeq, autoFlipHeldBuyFilledBySlot[slot], autoFlipHeldBuySpentBySlot[slot]);

        String itemName = resolveAutoFlipItemName(itemId, "Item " + itemId);
        addAutoFlipInventoryItem(itemId, itemName, newlyBoughtQuantity, "grand_exchange_buy_fill");

        logAutoFlipVerbose(
            "AUTOFLIP_HELD_INVENTORY_PURCHASE_RECORDED"
                + " slot=" + slot
                + " item_id=" + itemId
                + " state=" + safe(state)
                + " bought_qty=" + newlyBoughtQuantity
                + " cumulative_bought=" + cumulativeBoughtQuantity
                + " spent_gp=" + newlySpentGp
        );
    }

    private void autoFlipMaybeApplyHeldInventorySale(int slot, int itemId, String state, int totalQuantity, int cumulativeSoldQuantity, int offerPriceGp, long cumulativeSpentGp)
    {
        if (slot < 0 || slot >= autoFlipHeldSaleFilledBySlot.length || itemId <= 0 || cumulativeSoldQuantity <= 0)
        {
            return;
        }

        if (slot < autoFlipHeldSaleInstanceSeqBySlot.length && autoFlipHeldSaleInstanceSeqBySlot[slot] != slotInstanceSeq[slot])
        {
            resetAutoFlipHeldSaleMemoryForSlot(slot);
        }

        int accountingSeq = autoFlipAccountingSeqForObservedOffer(slot, itemId, state, totalQuantity, cumulativeSoldQuantity, offerPriceGp, cumulativeSpentGp);
        String accountingKey = autoFlipGeAccountingKey(slot, itemId, "SELL", totalQuantity, offerPriceGp, accountingSeq);
        long[] persistedTotals = loadAutoFlipGeAccountedTotals(accountingKey, slot, itemId, "SELL", totalQuantity, offerPriceGp);
        int previousSoldQuantity = Math.max(Math.max(0, autoFlipHeldSaleFilledBySlot[slot]), (int) Math.min(Integer.MAX_VALUE, persistedTotals[0]));
        int newlySoldQuantity = cumulativeSoldQuantity - previousSoldQuantity;

        if (newlySoldQuantity <= 0)
        {
            autoFlipHeldSaleFilledBySlot[slot] = Math.max(Math.max(0, autoFlipHeldSaleFilledBySlot[slot]), cumulativeSoldQuantity);
            autoFlipHeldSaleSpentBySlot[slot] = Math.max(Math.max(0L, autoFlipHeldSaleSpentBySlot[slot]), cumulativeSpentGp);
            return;
        }

        long previousSpentGp = Math.max(Math.max(0L, autoFlipHeldSaleSpentBySlot[slot]), persistedTotals[1]);
        long newlySpentGp = cumulativeSpentGp > previousSpentGp
            ? cumulativeSpentGp - previousSpentGp
            : Math.max(0L, assessAutoFlipItemValueGp(itemId)) * Math.max(1, newlySoldQuantity);

        autoFlipHeldSaleFilledBySlot[slot] = Math.max(previousSoldQuantity, cumulativeSoldQuantity);
        autoFlipHeldSaleSpentBySlot[slot] = Math.max(previousSpentGp, cumulativeSpentGp);
        persistAutoFlipGeAccountedTotals(accountingKey, slot, itemId, "SELL", totalQuantity, offerPriceGp, accountingSeq, autoFlipHeldSaleFilledBySlot[slot], autoFlipHeldSaleSpentBySlot[slot]);

        // Sale result must be a single inventory mutation.
        // applyAutoFlipPatienceSaleResult() decrements quantity and records patience profit.
        applyAutoFlipPatienceSaleResult(itemId, newlySoldQuantity, newlySpentGp);
    }

    private void applyAutoFlipPatienceSaleResult(int itemId, int soldQuantity, long grossSoldGp)
    {
        if (itemId <= 0 || soldQuantity <= 0)
        {
            return;
        }

        int inventoryItemId = canonicalizeAutoFlipInventoryItemId(itemId);
        if (inventoryItemId <= 0)
        {
            inventoryItemId = itemId;
        }

        synchronized (autoFlipInventoryLock)
        {
            java.util.List<AutoFlipInventoryItem> existing = autoFlipInventoryItems == null
                ? java.util.Collections.emptyList()
                : autoFlipInventoryItems;

            if (existing.isEmpty())
            {
                return;
            }

            java.util.List<AutoFlipInventoryItem> updated = new java.util.ArrayList<>();
            boolean changed = false;

            for (AutoFlipInventoryItem item : existing)
            {
                if (item == null)
                {
                    continue;
                }

                if (isSameAutoFlipInventoryItemId(item.getItemId(), inventoryItemId))
                {
                    int safeSoldQuantity = Math.min(Math.max(1, soldQuantity), Math.max(1, item.getQuantity()));
                    int remaining = Math.max(0, item.getQuantity() - safeSoldQuantity);
                    long baselineForSoldGp = calculateAutoFlipBaselineForSoldQuantity(item, safeSoldQuantity);
                    long takeHomeGp = Math.max(0L, grossSoldGp - estimateAutoFlipGeTaxGp(grossSoldGp));
                    long patienceDeltaGp = takeHomeGp - baselineForSoldGp;
                    long totalPatienceResultGp = Math.max(0L, item.getPatienceResultGp() + patienceDeltaGp);

                    String priorStatus = item.getStatus();
                    String nextStatus = remaining > 0
                        ? (priorStatus == null || priorStatus.trim().isEmpty() || "SOLD".equalsIgnoreCase(priorStatus.trim())
                            ? "HOLD"
                            : priorStatus)
                        : "SOLD";

                    updated.add(new AutoFlipInventoryItem(
                        item.getItemId(),
                        item.getItemName(),
                        remaining,
                        nextStatus,
                        item.getSource(),
                        item.getAddedAt(),
                        item.getAssessedUnitValueGp(),
                        item.getPatienceBaselineGp(),
                        totalPatienceResultGp
                    ));

                    changed = true;
                    logAutoFlipVerbose(
                        "AUTOFLIP_HELD_INVENTORY_DECREMENTED"
                            + " item_id=" + inventoryItemId
                            + " original_item_id=" + itemId
                            + " stored_item_id=" + item.getItemId()
                            + " sold_qty=" + safeSoldQuantity
                            + " remaining_qty=" + remaining
                    );
                }
                else
                {
                    updated.add(item);
                }
            }

            if (changed)
            {
                autoFlipInventoryItems = java.util.Collections.unmodifiableList(updated);
                persistAutoFlipInventoryStateLocked();
                logAutoFlipMenuEvent("patience_profit_recorded_" + inventoryItemId);
                logAutoFlipVerbose("AUTOFLIP_PATIENCE_PROFIT_RECORDED item_id=" + inventoryItemId + " original_item_id=" + itemId + " sold_qty=" + soldQuantity + " gross_gp=" + grossSoldGp);
                notifyAutoFlipSidePanelRefresh();
            }
            else
            {
                logAutoFlipVerbose("AUTOFLIP_PATIENCE_SALE_NO_INVENTORY_MATCH item_id=" + inventoryItemId + " original_item_id=" + itemId + " sold_qty=" + soldQuantity + " gross_gp=" + grossSoldGp);
            }
        }
    }

    private long calculateAutoFlipBaselineForSoldQuantity(AutoFlipInventoryItem item, int soldQuantity)
    {
        if (item == null || soldQuantity <= 0)
        {
            return 0L;
        }

        long storedBaselineGp = Math.max(0L, item.getPatienceBaselineGp());
        int originalQuantity = Math.max(1, item.getQuantity());

        if (storedBaselineGp > 0L)
        {
            long baselineEachGp = Math.max(0L, storedBaselineGp / originalQuantity);
            return baselineEachGp * Math.max(1, soldQuantity);
        }

        return Math.max(0L, item.getAssessedUnitValueGp()) * Math.max(1, soldQuantity);
    }
    private long estimateAutoFlipGeTaxGp(long grossSoldGp)
    {
        if (grossSoldGp <= 0L)
        {
            return 0L;
        }

        long tax = grossSoldGp / 50L;
        return Math.min(5_000_000L, Math.max(0L, tax));
    }
    private void persistAutoFlipInventoryStateLocked()
    {
        try
        {
            java.nio.file.Path path = getAutoFlipInventoryPath();
            java.nio.file.Path parent = path.getParent();
            if (parent != null)
            {
                java.nio.file.Files.createDirectories(parent);
            }

            java.util.List<String> lines = new java.util.ArrayList<>();
            java.util.List<AutoFlipInventoryItem> items = autoFlipInventoryItems == null
                ? java.util.Collections.emptyList()
                : autoFlipInventoryItems;

            for (AutoFlipInventoryItem item : items)
            {                if (item == null || item.getItemId() <= 0)
                {
                    continue;
                }

                boolean soldComplete = "SOLD".equals(item.getStatus());

                if (item.getQuantity() <= 0 && item.getPatienceResultGp() <= 0L && !soldComplete)
                {
                    continue;
                }

                lines.add("{"
                    + "\"event\":\"autoflip_inventory_hold\","
                    + "\"item_id\":" + item.getItemId() + ","
                    + "\"item_name\":\"" + safe(item.getItemName()) + "\","
                    + "\"quantity\":" + item.getQuantity() + ","
                    + "\"status\":\"" + safe(item.getStatus()) + "\","
                    + "\"source\":\"" + safe(item.getSource()) + "\","
                    + "\"added_at\":\"" + safe(item.getAddedAt()) + "\","
                    + "\"assessed_unit_value_gp\":" + item.getAssessedUnitValueGp() + ","
                    + "\"held_capital_gp\":" + item.getHeldCapitalGp() + ","
                    + "\"patience_baseline_gp\":" + item.getPatienceBaselineGp() + ","
                    + "\"patience_result_gp\":" + item.getPatienceResultGp()
                    + "}");
            }

            java.nio.file.Files.write(path, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("persistAutoFlipInventoryStateLocked", error);
        }
    }

    private void suppressAutoFlipNativeGeHoverMenu(int mouseX, int mouseY)
    {
        try
        {
            if (!geWindowOpenForOverlay || !autoFlipOverlayActive)
            {
                return;
            }

            if (!isInsideAutoFlipCardBlockBounds(mouseX, mouseY))
            {
                return;
            }

            if (client == null)
            {
                return;
            }

            net.runelite.api.MenuEntry[] entries = client.getMenuEntries();
            if (entries == null || entries.length == 0)
            {
                return;
            }

            java.util.List<net.runelite.api.MenuEntry> kept = new java.util.ArrayList<>();

            for (net.runelite.api.MenuEntry entry : entries)
            {
                if (entry == null)
                {
                    continue;
                }

                String option = entry.getOption() == null ? "" : entry.getOption().toLowerCase(java.util.Locale.ROOT);
                String target = entry.getTarget() == null ? "" : entry.getTarget().toLowerCase(java.util.Locale.ROOT);
                String combined = option + " " + target;

                boolean nativeGeBuySell =
                    combined.contains("buy")
                    || combined.contains("sell")
                    || combined.contains("buy item")
                    || combined.contains("sell item")
                    || combined.contains("buy items")
                    || combined.contains("sell items");

                if (!nativeGeBuySell)
                {
                    kept.add(entry);
                }
            }

            if (kept.size() != entries.length)
            {
                // AUTOFLIP_SUPPRESS_NATIVE_GE_HOVER_MENU_V1
                client.setMenuEntries(kept.toArray(new net.runelite.api.MenuEntry[0]));
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("suppressAutoFlipNativeGeHoverMenu", error);
        }
    }
    private boolean isAutoFlipInventoryItem(int itemId)
    {
        java.util.List<AutoFlipInventoryItem> items = autoFlipInventoryItems;
        if (itemId <= 0 || items == null || items.isEmpty())
        {
            return false;
        }

        for (AutoFlipInventoryItem item : items)
        {
            if (item != null && isSameAutoFlipInventoryItemId(item.getItemId(), itemId))
            {
                return true;
            }
        }

        return false;
    }

    private boolean isAutoFlipTradeableItem(int itemId)
    {
        try
        {
            return itemManager != null
                && itemId > 0
                && itemManager.getItemComposition(itemId).isTradeable();
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("isAutoFlipTradeableItem", error);
            return false;
        }
    }

    private void requestAutoFlipApiSellPrice(int itemId)
    {
        requestAutoFlipApiSellPrices(java.util.Collections.singletonList(itemId));
    }

    private void requestAutoFlipApiSellPrices(java.util.Collection<Integer> itemIds)
    {
        if (itemIds == null || itemIds.isEmpty())
        {
            return;
        }

        java.util.List<Integer> requestedItemIds = new java.util.ArrayList<>();
        long nowMs = System.currentTimeMillis();
        for (Integer itemIdValue : itemIds)
        {
            int itemId = itemIdValue == null ? 0 : canonicalizeAutoFlipInventoryItemId(itemIdValue);
            if (itemId <= 0
                || isAutoFlipApiSellPriceFresh(itemId, nowMs)
                || !autoFlipApiSellPriceRequestItemIds.add(itemId))
            {
                continue;
            }
            requestedItemIds.add(itemId);
        }

        if (requestedItemIds.isEmpty())
        {
            return;
        }

        ensurePricePrefetchExecutor();
        pricePrefetchExecutor.submit(() ->
        {
            StringBuilder ids = new StringBuilder();
            for (Integer itemId : requestedItemIds)
            {
                if (ids.length() > 0)
                {
                    ids.append(',');
                }
                ids.append(itemId);
            }
            String url = cleanBaseUrl(piBaseUrl) + "/market/explorer/prices?ids=" + ids + "&limit=" + requestedItemIds.size();
            try
            {
                String body = httpGetText(url, 5000);
                java.util.Map<Integer, AutoFlipExplorerPriceParser.PricePoint> points = AutoFlipExplorerPriceParser.parsePricePoints(body);
                boolean transportFailure = AutoFlipSellPriceRetryPolicy.isTransportFailure(body);
                for (Integer itemId : requestedItemIds)
                {
                    AutoFlipExplorerPriceParser.PricePoint point = points.get(itemId);
                    captureAutoFlipPricePointItemName(itemId, point);
                    long sellPriceGp = point == null ? 0L : Math.max(0L, point.suggestedSellPrice);
                    if (sellPriceGp > 0L)
                    {
                        primeAutoFlipSellPriceCache(itemId, sellPriceGp);
                        autoFlipApiSellPriceReadyItemIds.add(itemId);
                        autoFlipApiSellPriceLoadedAtByItemId.put(itemId, System.currentTimeMillis());
                        autoFlipApiSellPriceRetryAtByItemId.remove(itemId);
                        logAutoFlipVerbose(
                            "AUTOFLIP_INVENTORY_PRICE_PREFETCH loaded=true"
                                + " item_id=" + itemId
                                + " sell_price_gp=" + sellPriceGp
                                + " mode=bulk"
                        );
                        continue;
                    }

                    if (transportFailure)
                    {
                        autoFlipApiSellPriceRetryAtByItemId.put(
                            itemId,
                            System.currentTimeMillis() + AutoFlipSellPriceRetryPolicy.TRANSPORT_RETRY_DELAY_MS
                        );
                    }
                    else
                    {
                        autoFlipApiSellPriceRetryAtByItemId.remove(itemId);
                        if (autoFlipApiSellPriceReadyItemIds.contains(itemId))
                        {
                            autoFlipApiSellPriceLoadedAtByItemId.put(itemId, System.currentTimeMillis());
                        }
                    }
                    logAutoFlipVerbose(
                        "AUTOFLIP_INVENTORY_PRICE_PREFETCH loaded=false"
                            + " item_id=" + itemId
                            + " response_bytes=" + (body == null ? 0 : body.length())
                            + " mode=bulk"
                    );
                }
            }
            catch (Throwable error)
            {
                for (Integer itemId : requestedItemIds)
                {
                    autoFlipApiSellPriceRetryAtByItemId.put(
                        itemId,
                        System.currentTimeMillis() + AutoFlipSellPriceRetryPolicy.TRANSPORT_RETRY_DELAY_MS
                    );
                }
                logAutoFlipUiError("requestAutoFlipApiSellPrices", error);
            }
            finally
            {
                autoFlipApiSellPriceRequestItemIds.removeAll(requestedItemIds);
            }
        });

        evictUnauthorizedAutoFlipBuyPrices(requestedItemIds);
    }

    private void requestAutoFlipApiBuyPrices(java.util.Collection<Integer> itemIds)
    {
        if (itemIds == null || itemIds.isEmpty()) return;
        java.util.List<Integer> requested = new java.util.ArrayList<>();
        long nowMs = System.currentTimeMillis();
        for (Integer value : itemIds)
        {
            int itemId = value == null ? 0 : canonicalizeAutoFlipInventoryItemId(value);
            if (itemId <= 0) continue;
            autoFlipAuthorizedBuyPriceItemIds.add(itemId);
            if (isAutoFlipApiBuyPriceFresh(itemId, nowMs) || !autoFlipApiBuyPriceRequestItemIds.add(itemId)) continue;
            requested.add(itemId);
        }
        if (requested.isEmpty()) return;

        ensurePricePrefetchExecutor();
        pricePrefetchExecutor.submit(() ->
        {
            StringBuilder ids = new StringBuilder();
            for (Integer itemId : requested)
            {
                if (ids.length() > 0) ids.append(',');
                ids.append(itemId);
            }
            String url = cleanBaseUrl(piBaseUrl) + "/market/explorer/prices?ids=" + ids + "&limit=" + requested.size();
            try
            {
                String body = httpGetText(url, 5000);
                java.util.Map<Integer, AutoFlipExplorerPriceParser.PricePoint> points = AutoFlipExplorerPriceParser.parsePricePoints(body);
                for (Integer itemId : requested)
                {
                    AutoFlipExplorerPriceParser.PricePoint point = points.get(itemId);
                    captureAutoFlipPricePointItemName(itemId, point);
                    if (point != null && point.suggestedBuyPrice > 0L)
                    {
                        primeAutoFlipBuyPriceCache(itemId, point);
                        if (point.suggestedSellPrice > 0L) primeAutoFlipSellPriceCache(itemId, point.suggestedSellPrice);
                    }
                }
            }
            catch (Throwable error)
            {
                logAutoFlipUiError("requestAutoFlipApiBuyPrices", error);
            }
            finally
            {
                autoFlipApiBuyPriceRequestItemIds.removeAll(requested);
            }
        });
    }

    private void evictUnauthorizedAutoFlipBuyPrices(java.util.Collection<Integer> itemIds)
    {
        if (itemIds == null || itemIds.isEmpty()) return;
        for (Integer value : itemIds)
        {
            int itemId = value == null ? 0 : canonicalizeAutoFlipInventoryItemId(value);
            if (AutoFlipBuyCacheScope.isAuthorized(itemId, autoFlipAuthorizedBuyPriceItemIds)) continue;
            autoFlipBuyPriceCacheByItemId.remove(itemId);
            autoFlipLastBoughtPriceByItemId.remove(itemId);
            autoFlipLastBoughtAtMsByItemId.remove(itemId);
            autoFlipApiBuyPriceReadyItemIds.remove(itemId);
            autoFlipApiBuyPriceLoadedAtByItemId.remove(itemId);
        }
    }

    private void primeAutoFlipBuyPriceCache(int itemId, AutoFlipExplorerPriceParser.PricePoint point)
    {
        if (itemId <= 0 || point == null || point.suggestedBuyPrice <= 0L) return;
        captureAutoFlipPricePointItemName(itemId, point);
        long completedAtMs = System.currentTimeMillis();
        autoFlipBuyPriceCacheByItemId.put(itemId, point.suggestedBuyPrice);
        if (point.latestBuyPrice > 0L) autoFlipLastBoughtPriceByItemId.put(itemId, point.latestBuyPrice);
        if (point.buyTimeSeconds > 0L) autoFlipLastBoughtAtMsByItemId.put(itemId, point.buyTimeSeconds * 1000L);
        autoFlipApiBuyPriceReadyItemIds.add(itemId);
        autoFlipApiBuyPriceLoadedAtByItemId.put(itemId, completedAtMs);
    }

    private void captureAutoFlipPricePointItemName(int itemId, AutoFlipExplorerPriceParser.PricePoint point)
    {
        if (itemId <= 0 || point == null || point.itemName == null || point.itemName.trim().isEmpty()) return;
        autoFlipSellPriceDebugItemNameById.put(itemId, point.itemName.trim());
    }

    private boolean isAutoFlipApiBuyPriceFresh(int itemId, long nowMs)
    {
        long loadedAt = autoFlipApiBuyPriceLoadedAtByItemId.getOrDefault(itemId, 0L);
        return autoFlipApiBuyPriceReadyItemIds.contains(itemId) && loadedAt > 0L && nowMs - loadedAt < AUTOFLIP_SELL_PRICE_REFRESH_TTL_MS;
    }

    private long getAutoFlipCachedBuyPriceGp(int itemId)
    {
        return itemId <= 0 ? 0L : Math.max(0L, autoFlipBuyPriceCacheByItemId.getOrDefault(canonicalizeAutoFlipInventoryItemId(itemId), 0L));
    }

    private void maintainAutoFlipInventorySellPriceRetries()
    {
        long nowMs = System.currentTimeMillis();
        java.util.List<Integer> dueItemIds = new java.util.ArrayList<>();
        for (Integer itemIdValue : autoFlipLastInventoryPriceCandidateIds)
        {
            int itemId = itemIdValue == null ? 0 : itemIdValue;
            if (itemId <= 0 || isAutoFlipApiSellPriceFresh(itemId, nowMs))
            {
                continue;
            }

            Long retryAtMs = autoFlipApiSellPriceRetryAtByItemId.get(itemId);
            if ((autoFlipApiSellPriceReadyItemIds.contains(itemId) && retryAtMs == null)
                || AutoFlipSellPriceRetryPolicy.isRetryDue(retryAtMs, nowMs))
            {
                dueItemIds.add(itemId);
            }
        }
        requestAutoFlipApiSellPrices(dueItemIds);
    }

    private boolean isAutoFlipApiSellPriceFresh(int itemId, long nowMs)
    {
        if (!autoFlipApiSellPriceReadyItemIds.contains(itemId))
        {
            return false;
        }
        long loadedAtMs = autoFlipApiSellPriceLoadedAtByItemId.getOrDefault(itemId, 0L);
        return loadedAtMs > 0L && nowMs - loadedAtMs < AUTOFLIP_SELL_PRICE_REFRESH_TTL_MS;
    }

    private long getAutoFlipCachedSellPriceGp(int itemId)
    {
        if (itemId <= 0)
        {
            return 0L;
        }

        int canonicalItemId = canonicalizeAutoFlipInventoryItemId(itemId);
        return Math.max(0L, autoFlipSellPriceCacheByItemId.getOrDefault(canonicalItemId, 0L));
    }

    private long assessAutoFlipItemValueGp(int itemId)
    {
        try
        {
            long cached = autoFlipSellPriceCacheByItemId.getOrDefault(itemId, 0L);
            if (cached > 0L)
            {
                return cached;
            }

            if (itemManager != null && itemId > 0)
            {
                long priced = Math.max(0, itemManager.getItemPrice(itemId));
                if (priced > 0L)
                {
                    primeAutoFlipSellPriceCache(itemId, priced);
                }
                return priced;
            }
        }
        catch (Throwable ignored)
        {
        }

        return 0L;
    }

    private void primeAutoFlipSellPriceCache(int itemId, long sellPriceGp)
    {
        if (itemId <= 0 || sellPriceGp <= 0L)
        {
            return;
        }

        autoFlipSellPriceCacheByItemId.put(itemId, sellPriceGp);
    }

    private boolean shouldConsumeAutoFlipOverlayClick(int mouseX, int mouseY)
    {
        // AUTOFLIP_GUIDED_SETUP_FLOW_CONSUME_V15
        Rectangle guidedSetupBlockingBounds = getAutoFlipGuidedSetupBlockingBounds();
        if (guidedSetupBlockingBounds != null && guidedSetupBlockingBounds.contains(mouseX, mouseY))
        {
            return true;
        }
        if (!geWindowOpenForOverlay)
        {
            return false;
        }

        Rectangle buttonBounds = autoFlipButtonBounds;
        if (buttonBounds != null && buttonBounds.contains(mouseX, mouseY))
        {
            return true;
        }

        Rectangle directChatboxConsumeBounds = getAutoFlipPriceChatboxButtonBounds();
        if (directChatboxConsumeBounds != null && directChatboxConsumeBounds.contains(mouseX, mouseY))
        {
            // AUTOFLIP_CHATBOX_INJECT_SHOULD_CONSUME_V2
            return true;
        }

        Rectangle refreshBounds = getAutoFlipRefreshBoardBounds();
        if (refreshBounds != null && refreshBounds.contains(mouseX, mouseY))
        {
            return true;
        }

        Rectangle chatboxInjectBounds = getAutoFlipPriceChatboxButtonBounds();
        if (chatboxInjectBounds != null && chatboxInjectBounds.contains(mouseX, mouseY))
        {
            // AUTOFLIP_CHATBOX_INJECT_PRESS_BLOCK_V1
            logAutoFlipVerbose("AUTOFLIP_CHATBOX_INJECT_PRESS_BLOCK x=" + mouseX + " y=" + mouseY);
            return true;
        }

        if (findAutoFlipCardActionBounds(mouseX, mouseY) != null)
        {
            return true;
        }

        if (isInsideAutoFlipCardBlockBounds(mouseX, mouseY))
        {
            // AUTOFLIP_FULL_CARD_CLICK_BLOCK_V1
            // The custom card is a real mouse-blocking surface, not a click-through painting.
            return true;
        }

        if (!autoFlipOverlayActive || geHeaderBoundsForOverlay == null)
        {
            return false;
        }

        int menuX = geHeaderBoundsForOverlay.x + geHeaderBoundsForOverlay.width + readIntConfig("menu.offset.x", 18);
        int menuY = geHeaderBoundsForOverlay.y + readIntConfig("menu.offset.y", -10);
        int menuWidth = readIntConfig("menu.width", 304);
        int menuHeight = readIntConfig("menu.height", 596);

        return new Rectangle(menuX, menuY, menuWidth, menuHeight).contains(mouseX, mouseY);
    }

    private boolean handleAutoFlipMenuClick(int mouseX, int mouseY)
    {
        try
        {
            if (handleAutoFlipPriceChatboxInjectClick(mouseX, mouseY))
            {
                // AUTOFLIP_CHATBOX_INJECT_PRIORITY_CLICK_ROUTE_V1
                logAutoFlipVerbose("AUTOFLIP_CHATBOX_INJECT_PRIORITY_CLICK handled=true x=" + mouseX + " y=" + mouseY);
                return true;
            }

            Rectangle logoBounds = autoFlipButtonBounds;
            Rectangle setupUiButtonBounds = getAutoFlipSetupUiButtonBoundsForOverlay();
            if (setupUiButtonBounds != null && setupUiButtonBounds.contains(mouseX, mouseY))
            {
                toggleAutoFlipCustomSetupUiEnabledForOverlay();
                return true;
            }

            if (geWindowOpenForOverlay && logoBounds != null && logoBounds.contains(mouseX, mouseY))
            {
                boolean nextActive = !autoFlipOverlayActive;
                setAutoFlipOverlayActive(nextActive);

                autoFlipHoursDropdownOpen = false;
                autoFlipBudgetInputActive = false;
                autoFlipHoursInputActive = false;
                autoFlipRiskDropdownOpen = false;

                logAutoFlipMenuEvent(nextActive ? "logo_activated" : "logo_deactivated");
                return true;
            }

            if (handleAutoFlipGeMarketLinkClick(mouseX, mouseY))
            {
                return true;
            }

            if (!geWindowOpenForOverlay || !autoFlipOverlayActive || geHeaderBoundsForOverlay == null)
            {
                return false;
            }

            int menuX = geHeaderBoundsForOverlay.x + geHeaderBoundsForOverlay.width + readIntConfig("menu.offset.x", 18);
            int menuY = geHeaderBoundsForOverlay.y + readIntConfig("menu.offset.y", -10);
            int menuWidth = readIntConfig("menu.width", 304);
            int menuHeight = readIntConfig("menu.height", 430);
            int fieldWidth = menuWidth - 40;

            Rectangle refreshRect = getAutoFlipRefreshBoardBounds();
            if (refreshRect != null && refreshRect.contains(mouseX, mouseY))
            {
                logAutoFlipMenuEvent("refresh_board_clicked");
                triggerAutoFlipRefreshBoard();
                return true;
            }

            if (handleAutoFlipPriceChatboxInjectClick(mouseX, mouseY))
            {
                // AUTOFLIP_PRICE_CHATBOX_INJECT_CLICK_ROUTE_V1
                return true;
            }

            if (handleAutoFlipCardActionClick(mouseX, mouseY))
            {
                return true;
            }

            Rectangle menuBounds = new Rectangle(menuX, menuY, menuWidth, menuHeight);
            if (!menuBounds.contains(mouseX, mouseY))
            {
                commitAutoFlipTextInputs();
                autoFlipHoursDropdownOpen = false;
                autoFlipRiskDropdownOpen = false;
                return false;
            }

            int hoursY = menuY + 112;
            int budgetY = hoursY + 62;
            int checkboxY = budgetY + 62;
            int riskLabelY = checkboxY + 50;
            int riskY = riskLabelY + 17;
            int optimizeY = riskY + 54;

            Rectangle hoursRect = new Rectangle(menuX + 20, hoursY, fieldWidth, 30);
            Rectangle hoursDropdownRect = new Rectangle(menuX + 20, hoursY + 33, fieldWidth, 248);
            Rectangle budgetRect = new Rectangle(menuX + 20, budgetY, fieldWidth, 30);
            Rectangle checkboxRect = new Rectangle(menuX + 20, checkboxY - 7, fieldWidth, 28);
            Rectangle riskHeaderRect = new Rectangle(menuX + 20, riskY, fieldWidth, 40);
            Rectangle riskDropdownRect = new Rectangle(menuX + 20, riskY + 40, fieldWidth, 93);
            Rectangle optimizeRect = new Rectangle(menuX + 20, optimizeY, fieldWidth, 46);

            if (autoFlipHoursDropdownOpen && hoursDropdownRect.contains(mouseX, mouseY))
            {
                int optionHeight = 31;
                int relativeY = mouseY - (hoursY + 33);
                int optionIndex = Math.max(0, Math.min(7, relativeY / optionHeight));

                if (optionIndex >= 0 && optionIndex < AUTOFLIP_HOUR_OPTIONS.length)
                {
                    autoFlipMenuHoursAway = AUTOFLIP_HOUR_OPTIONS[optionIndex];
                    autoFlipHoursInputActive = false;
                    autoFlipHoursInputBuffer = "";
                    autoFlipHoursDropdownOpen = false;
                    persistAutoFlipMenuState();
                    logAutoFlipMenuEvent("hours_selected");
                    return true;
                }

                autoFlipHoursInputActive = true;
                autoFlipHoursInputBuffer = "";
                autoFlipHoursDropdownOpen = false;
                logAutoFlipMenuEvent("hours_custom_input_started");
                return true;
            }

            if (autoFlipRiskDropdownOpen && riskDropdownRect.contains(mouseX, mouseY))
            {
                int optionHeight = 31;
                int optionIndex = Math.max(0, Math.min(2, (mouseY - (riskY + 40)) / optionHeight));
                if (optionIndex == 0)
                {
                    autoFlipMenuRiskMode = "optimize";
                    logAutoFlipMenuEvent("strategy_optimize");
                }
                else if (optionIndex == 1)
                {
                    autoFlipMenuRiskMode = "adaptive";
                    logAutoFlipMenuEvent("strategy_adaptive");
                }
                else
                {
                    autoFlipMenuRiskMode = "exploratory";
                    logAutoFlipMenuEvent("strategy_exploratory");
                }

                autoFlipRiskDropdownOpen = false;
                persistAutoFlipMenuState();
                return true;
            }

            if (hoursRect.contains(mouseX, mouseY))
            {
                commitAutoFlipTextInputs();
                autoFlipBudgetInputActive = false;
                autoFlipRiskDropdownOpen = false;
                autoFlipHoursDropdownOpen = true;
                autoFlipHoursInputActive = true;
                autoFlipHoursInputBuffer = "";
                logAutoFlipMenuEvent("hours_input_started");
                return true;
            }

            if (budgetRect.contains(mouseX, mouseY))
            {
                autoFlipHoursDropdownOpen = false;
                autoFlipRiskDropdownOpen = false;
                autoFlipHoursInputActive = false;
                autoFlipMenuUseCashStack = false;
                autoFlipBudgetInputActive = true;
                autoFlipBudgetInputBuffer = "";
                logAutoFlipMenuEvent("budget_input_started");
                return true;
            }

            if (autoFlipBudgetInputActive)
            {
                commitAutoFlipBudgetInput();
            }

            if (autoFlipHoursInputActive)
            {
                commitAutoFlipHoursInput();
            }

            if (!hoursDropdownRect.contains(mouseX, mouseY))
            {
                autoFlipHoursDropdownOpen = false;
            }

            if (riskHeaderRect.contains(mouseX, mouseY))
            {
                autoFlipHoursDropdownOpen = false;
                autoFlipBudgetInputActive = false;
                autoFlipRiskDropdownOpen = !autoFlipRiskDropdownOpen;
                logAutoFlipMenuEvent(autoFlipRiskDropdownOpen ? "strategy_dropdown_opened" : "strategy_dropdown_closed");
                return true;
            }

            if (autoFlipRiskDropdownOpen && !riskDropdownRect.contains(mouseX, mouseY))
            {
                autoFlipRiskDropdownOpen = false;
            }

            if (checkboxRect.contains(mouseX, mouseY))
            {
                autoFlipBudgetInputActive = false;
                autoFlipHoursInputActive = false;
                autoFlipMenuUseCashStack = !autoFlipMenuUseCashStack;

                persistAutoFlipMenuState();
                logAutoFlipMenuEvent("use_cash_toggled");
                return true;
            }

            if (optimizeRect.contains(mouseX, mouseY))
            {
                commitAutoFlipTextInputs();
                persistAutoFlipMenuState();
                logAutoFlipMenuEvent("optimize_board_clicked");
                triggerAutoFlipOptimizeBoard();
                return true;
            }

            return true;
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("handleAutoFlipMenuClick", error);
            return true;
        }
    }


    private boolean isAutoFlipTextInputActive()
    {
        return autoFlipBudgetInputActive || autoFlipHoursInputActive;
    }

    private boolean isAcceptedAutoFlipInputChar(char c)
    {
        return Character.isDigit(c)
            || c == '.'
            || c == ','
            || c == '_'
            || c == 'k'
            || c == 'K'
            || c == 'm'
            || c == 'M'
            || c == 'b'
            || c == 'B';
    }

    private char autoFlipInputCharForKeyPressed(KeyEvent keyEvent)
    {
        if (keyEvent == null)
        {
            return 0;
        }

        char keyChar = keyEvent.getKeyChar();
        if (keyChar != KeyEvent.CHAR_UNDEFINED && isAcceptedAutoFlipInputChar(keyChar))
        {
            return keyChar;
        }

        int code = keyEvent.getKeyCode();
        if (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9)
        {
            return (char) ('0' + (code - KeyEvent.VK_0));
        }
        if (code >= KeyEvent.VK_NUMPAD0 && code <= KeyEvent.VK_NUMPAD9)
        {
            return (char) ('0' + (code - KeyEvent.VK_NUMPAD0));
        }

        switch (code)
        {
            case KeyEvent.VK_PERIOD:
            case KeyEvent.VK_DECIMAL:
                return '.';
            case KeyEvent.VK_COMMA:
                return ',';
            case KeyEvent.VK_K:
                return keyEvent.isShiftDown() ? 'K' : 'k';
            case KeyEvent.VK_M:
                return keyEvent.isShiftDown() ? 'M' : 'm';
            case KeyEvent.VK_B:
                return keyEvent.isShiftDown() ? 'B' : 'b';
            default:
                return 0;
        }
    }

    private void rememberAutoFlipPressedInputChar(char c)
    {
        autoFlipLastPressedInputChar = c;
        autoFlipLastPressedInputMs = System.currentTimeMillis();
    }

    private boolean isDuplicateAutoFlipPressedInputChar(char c)
    {
        long ageMs = System.currentTimeMillis() - autoFlipLastPressedInputMs;
        if (autoFlipLastPressedInputChar == c && ageMs >= 0L && ageMs < 120L)
        {
            autoFlipLastPressedInputChar = 0;
            autoFlipLastPressedInputMs = 0L;
            return true;
        }

        return false;
    }

    private boolean isRecentAutoFlipPressedInputChar(char c)
    {
        long ageMs = System.currentTimeMillis() - autoFlipLastPressedInputMs;
        return autoFlipLastPressedInputChar == c && ageMs >= 0L && ageMs < 120L;
    }

    private void appendAutoFlipInputChar(char c)
    {
        if (autoFlipBudgetInputActive)
        {
            if (autoFlipBudgetInputBuffer.length() < 18)
            {
                autoFlipBudgetInputBuffer += c;
            }
            return;
        }

        if (autoFlipHoursInputActive)
        {
            if (Character.isDigit(c) && autoFlipHoursInputBuffer.length() < 4)
            {
                autoFlipHoursDropdownOpen = false;
                autoFlipHoursInputBuffer += c;
            }
        }
    }

    private void removeAutoFlipInputChar()
    {
        if (autoFlipBudgetInputActive && !autoFlipBudgetInputBuffer.isEmpty())
        {
            autoFlipBudgetInputBuffer = autoFlipBudgetInputBuffer.substring(0, autoFlipBudgetInputBuffer.length() - 1);
            return;
        }

        if (autoFlipHoursInputActive && !autoFlipHoursInputBuffer.isEmpty())
        {
            autoFlipHoursInputBuffer = autoFlipHoursInputBuffer.substring(0, autoFlipHoursInputBuffer.length() - 1);
        }
    }

    private void commitAutoFlipTextInputs()
    {
        if (autoFlipBudgetInputActive)
        {
            commitAutoFlipBudgetInput();
        }

        if (autoFlipHoursInputActive)
        {
            commitAutoFlipHoursInput();
        }
    }

    private void commitAutoFlipBudgetInput()
    {
        String raw = autoFlipBudgetInputBuffer == null ? "" : autoFlipBudgetInputBuffer.trim();

        if (!raw.isEmpty())
        {
            long parsed = parseBudgetGp(raw, autoFlipMenuManualBudgetGp);
            autoFlipMenuManualBudgetGp = Math.max(0L, parsed);
            autoFlipMenuUseCashStack = false;
            persistAutoFlipMenuState();
            logAutoFlipMenuEvent("budget_input_committed");
        }

        autoFlipBudgetInputActive = false;
        autoFlipBudgetInputBuffer = "";
    }

    private void commitAutoFlipHoursInput()
    {
        String raw = autoFlipHoursInputBuffer == null ? "" : autoFlipHoursInputBuffer.trim();

        if (!raw.isEmpty())
        {
            try
            {
                int parsed = Integer.parseInt(raw);
                autoFlipMenuHoursAway = roundAutoFlipHoursUpToTier(parsed);
                persistAutoFlipMenuState();
                logAutoFlipMenuEvent("hours_input_committed");
            }
            catch (Exception ignored)
            {
            }
        }

        autoFlipHoursInputActive = false;
        autoFlipHoursInputBuffer = "";
    }

    private int roundAutoFlipHoursUpToTier(int rawHours)
    {
        int safeHours = Math.max(1, Math.min(336, rawHours));
        int rounded = ((safeHours + 3) / 4) * 4;
        return Math.max(4, Math.min(336, rounded));
    }

    private long parseBudgetGp(String raw, long fallback)
    {
        if (raw == null)
        {
            return fallback;
        }

        String value = raw.trim().replace(",", "").replace("_", "").toLowerCase(java.util.Locale.ROOT);

        if (value.isEmpty())
        {
            return fallback;
        }

        double multiplier = 1.0d;

        if (value.endsWith("k"))
        {
            multiplier = 1000.0d;
            value = value.substring(0, value.length() - 1);
        }
        else if (value.endsWith("m"))
        {
            multiplier = 1000000.0d;
            value = value.substring(0, value.length() - 1);
        }
        else if (value.endsWith("b"))
        {
            multiplier = 1000000000.0d;
            value = value.substring(0, value.length() - 1);
        }

        try
        {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed) || parsed < 0)
            {
                return fallback;
            }

            return Math.round(parsed * multiplier);
        }
        catch (Exception ignored)
        {
            return fallback;
        }
    }
    private void cycleAutoFlipHours()
    {
        int current = autoFlipMenuHoursAway;
        int next = AUTOFLIP_HOUR_OPTIONS[0];

        for (int i = 0; i < AUTOFLIP_HOUR_OPTIONS.length; i++)
        {
            if (AUTOFLIP_HOUR_OPTIONS[i] == current)
            {
                next = AUTOFLIP_HOUR_OPTIONS[(i + 1) % AUTOFLIP_HOUR_OPTIONS.length];
                break;
            }
        }

        autoFlipMenuHoursAway = next;
    }

    private void cycleAutoFlipBudget()
    {
        long current = autoFlipMenuManualBudgetGp;
        long next = AUTOFLIP_BUDGET_OPTIONS[0];

        for (int i = 0; i < AUTOFLIP_BUDGET_OPTIONS.length; i++)
        {
            if (AUTOFLIP_BUDGET_OPTIONS[i] == current)
            {
                next = AUTOFLIP_BUDGET_OPTIONS[(i + 1) % AUTOFLIP_BUDGET_OPTIONS.length];
                break;
            }
        }

        autoFlipMenuManualBudgetGp = next;
    }


    private void installAutoFlipSidePanel()
    {
        try
        {
            if (clientToolbar == null)
            {
                logAutoFlipVerbose("AUTOFLIP_SIDE_PANEL_NO_TOOLBAR");
                return;
            }

            autoFlipSidePanel = new AutoFlipSidePanel(this);
            BufferedImage icon = loadAutoFlipSidePanelIcon();

            autoFlipNavigationButton = NavigationButton.builder()
                .tooltip("AutoFlip.gg")
                .icon(icon)
                .priority(5)
                .panel(autoFlipSidePanel)
                .build();

            clientToolbar.addNavigation(autoFlipNavigationButton);
            logAutoFlipVerbose("AUTOFLIP_SIDE_PANEL_INSTALLED");
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("installAutoFlipSidePanel", error);
            logAutoFlipVerbose("AUTOFLIP_SIDE_PANEL_INSTALL_ERROR " + error);
        }
    }

    private void uninstallAutoFlipSidePanel()
    {
        try
        {
            if (clientToolbar != null && autoFlipNavigationButton != null)
            {
                clientToolbar.removeNavigation(autoFlipNavigationButton);
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("uninstallAutoFlipSidePanel", error);
        }

        autoFlipNavigationButton = null;
        autoFlipSidePanel = null;
    }

    private BufferedImage loadAutoFlipSidePanelIcon()
    {
        try
        {
            URL url = AutoFlipPlugin.class.getResource("/gg/autoflip/AppIcon_GeButton24.png");
            if (url != null)
            {
                BufferedImage image = ImageIO.read(url);
                if (image != null)
                {
                    return image;
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        BufferedImage fallback = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = fallback.createGraphics();
        try
        {
            graphics.setColor(new java.awt.Color(135, 255, 78));
            graphics.setStroke(new java.awt.BasicStroke(3.0f));
            graphics.drawLine(4, 20, 10, 5);
            graphics.drawLine(10, 5, 15, 17);
            graphics.drawLine(15, 17, 21, 4);
        }
        finally
        {
            graphics.dispose();
        }
        return fallback;
    }

    private void notifyAutoFlipSidePanelRefresh()
    {
        AutoFlipSidePanel panel = autoFlipSidePanel;
        if (panel != null)
        {
            javax.swing.SwingUtilities.invokeLater(panel::refreshFromPlugin);
        }
    }

    private long getCurrentCashStackGp()
    {
        long detected = detectInventoryCoinsGp();
        return Math.max(0L, detected);
    }

    private long detectInventoryCoinsGp()
    {
        if (client == null)
        {
            return 0L;
        }

        try
        {
            ItemContainer container = client.getItemContainer(InventoryID.INVENTORY);
            if (container == null)
            {
                return 0L;
            }

            Item[] items = container.getItems();
            if (items == null)
            {
                return 0L;
            }

            long coins = 0L;

            for (Item item : items)
            {
                if (item == null)
                {
                    continue;
                }

                if (item.getId() == 995)
                {
                    coins += Math.max(0, item.getQuantity());
                }
            }

            return coins;
        }
        catch (Exception ignored)
        {
            return 0L;
        }
    }

    private void loadAutoFlipMenuState()
    {
        autoFlipMenuHoursAway = clampMenuHours(readIntConfig("menu.hours.away", autoFlipMenuHoursAway));
        autoFlipMenuUseCashStack = readBoolConfig("menu.use.current.cash", autoFlipMenuUseCashStack);
        autoFlipMenuRiskMode = normalizeAutoFlipRisk(readStringConfig("menu.risk.mode", autoFlipMenuRiskMode));

    }

    private void persistAutoFlipMenuState()
    {
        updateOverlayConfig("menu.hours.away", String.valueOf(autoFlipMenuHoursAway));
        updateOverlayConfig("menu.manual.budget.gp", String.valueOf(autoFlipMenuManualBudgetGp));
        updateOverlayConfig("menu.use.current.cash", String.valueOf(autoFlipMenuUseCashStack));
        updateOverlayConfig("menu.risk.mode", normalizeAutoFlipRisk(autoFlipMenuRiskMode));
    }

    private int clampMenuHours(int value)
    {
        return Math.max(1, Math.min(336, value));
    }

    private String normalizeAutoFlipRisk(String raw)
    {
        if (raw == null)
        {
            return "optimize";
        }

        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);

        if ("optimize".equals(value) || "adaptive".equals(value) || "exploratory".equals(value))
        {
            return value;
        }

        if ("safe".equals(value))
        {
            return "optimize";
        }

        if ("balanced".equals(value))
        {
            return "adaptive";
        }

        if ("risk".equals(value))
        {
            return "exploratory";
        }

        return "optimize";
    }

    private String formatGp(long value)
    {
        return String.format(java.util.Locale.US, "%,d", Math.max(0L, value));
    }


    private void logAutoFlipUiError(String where, Throwable error)
    {
        try
        {
            String message = error == null ? "" : String.valueOf(error.getMessage());
            appendLine(
                GE_DEBUG,
                "{"
                    + "\"event\":\"autoflip_ui_error\","
                    + "\"source\":\"autoflip_runelite\","
                    + "\"where\":\"" + safe(where) + "\","
                    + "\"message\":\"" + safe(message) + "\","
                    + "\"ts\":\"" + now() + "\""
                    + "}"
            );
        }
        catch (Throwable ignored)
        {
        }
    }
    private void logAutoFlipMenuEvent(String event)
    {
        appendLine(
            GE_DEBUG,
            "{"
                + "\"event\":\"autoflip_menu_" + safe(event) + "\","
                + "\"source\":\"autoflip_runelite\","
                + "\"hours\":" + autoFlipMenuHoursAway + ","
                + "\"use_cash\":" + autoFlipMenuUseCashStack + ","
                + "\"cash_gp\":" + Math.max(0L, autoFlipLastObservedCashStackGp) + ","
                + "\"manual_budget_gp\":" + autoFlipMenuManualBudgetGp + ","
                + "\"ts\":\"" + now() + "\""
                + "}"
        );
    }
    private void setAutoFlipOverlayActive(boolean active)
    {
        autoFlipOverlayActive = active;
        appendLine(
            GE_DEBUG,
            "{\"event\":\"overlay_toggle\",\"source\":\"autoflip_runelite\",\"active\":" + active + ",\"ts\":\"" + now() + "\"}"
        );
    }
    private void inspectGeWidgetBounds()
    {
        long nowMs = System.currentTimeMillis();
        if (!autoFlipGeWidgetBoundsDirty
            && nowMs - autoFlipGeWidgetBoundsLastScanMs < AUTOFLIP_GE_WIDGET_BOUNDS_REFRESH_INTERVAL_MS)
        {
            return;
        }
        autoFlipGeWidgetBoundsLastScanMs = nowMs;
        autoFlipGeWidgetBoundsDirty = false;
        boolean wasGeWindowOpen = geWindowOpenForOverlay;

        // autoflip_marker: ge_overlay_scan_reset_v2
        geHeaderFoundThisScan = false;
        geWindowOpenForOverlay = false;
        geHeaderBoundsForOverlay = null;
        autoFlipButtonBounds = null;
        clearGeSlotBoundsForOverlay();
        boolean verboseBoundsLogging = isAutoFlipVerboseRuntimeLoggingEnabled();

        if (client == null)
        {
            if (verboseBoundsLogging)
            {
                appendLine(
                    GE_WIDGET_BOUNDS,
                    "{\"event\":\"ge_widget_bounds_heartbeat\","
                        + "\"source\":\"autoflip_runelite\","
                        + "\"ts\":\"" + now() + "\","
                        + "\"client_null\":true}"
                );
            }
            return;
        }

        try
        {
            Widget[] roots = client.getWidgetRoots();
            int rootCount = roots == null ? 0 : roots.length;

            StringBuilder matches = new StringBuilder();
            int[] count = new int[] {0};

            if (roots != null)
            {
                for (Widget root : roots)
                {
                    collectGeWidgetMatches(root, matches, count, 0);
                    if (count[0] >= 120)
                    {
                        break;
                    }
                }
            }

            if (!geHeaderFoundThisScan)
            {
                // autoflip_marker: ge_overlay_scan_finalize_v2
                geWindowOpenForOverlay = false;
                geHeaderBoundsForOverlay = null;
                autoFlipButtonBounds = null;
        clearGeSlotBoundsForOverlay();
                // AUTOFLIP_TOGGLE_IS_SOURCE_OF_TRUTH_V1
                // Do not turn AutoFlip off just because the GE widget scan temporarily loses the main-grid header.
                // The user toggle is the only source of truth for plugin on/off state.
            }

            String fingerprint = rootCount + "|" + count[0] + "|" + matches.toString();
            boolean changed = !fingerprint.equals(lastWidgetBoundsFingerprint);

            if (changed)
            {
                lastWidgetBoundsFingerprint = fingerprint;
            }

            // Only print widget-bounds summary when the widget fingerprint changes.
            if (changed && verboseBoundsLogging)
            {
                logAutoFlipVerbose("AUTOFLIP_GE_WIDGET_BOUNDS root_count=" + rootCount + " matches=" + count[0]);
            }

            if (geHeaderFoundThisScan && !wasGeWindowOpen)
            {
                markAutoFlipGeSessionInventoryCacheDirty("ge_open_detected");
            }
        }
        catch (Exception e)
        {
            appendLine(
                GE_WIDGET_BOUNDS,
                "{\"event\":\"ge_widget_bounds_error\","
                    + "\"source\":\"autoflip_runelite\","
                    + "\"ts\":\"" + now() + "\","
                    + "\"error\":\"" + safe(e.getClass().getSimpleName()) + "\","
                    + "\"message\":\"" + safe(e.getMessage()) + "\"}"
            );
        }
    }
    private void refreshAutoFlipStateDetectorLabelCache()
    {
        long nowMs = System.currentTimeMillis();
        if (!autoFlipStateDetectorLabelCacheDirty
            && nowMs - autoFlipStateDetectorLabelCacheLastRefreshMs < AUTOFLIP_STATE_DETECTOR_LABEL_REFRESH_INTERVAL_MS)
        {
            return;
        }

        try
        {
            autoFlipStateDetectorLabelCache = getAutoFlipStateDetectorLabelForOverlay();
            autoFlipStateDetectorLabelCacheLastRefreshMs = nowMs;
            autoFlipStateDetectorLabelCacheDirty = false;
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("refreshAutoFlipStateDetectorLabelCache", error);
        }
    }
    private void collectGeWidgetMatches(Widget widget, StringBuilder matches, int[] count, int depth)
    {
        if (widget == null || count[0] >= 250 || depth > 12)
        {
            return;
        }

        if (!widget.isHidden() && (isGeRelevantWidget(widget) || widget.getItemId() > 0))
        {
            Rectangle bounds = widget.getBounds();
            String text = widget.getText();
            String name = widget.getName();

            if (isGrandExchangeHeaderText(text) && bounds != null && bounds.width > 100 && bounds.height > 10)
            {
                // autoflip_marker: ge_overlay_header_found_v2
                geHeaderFoundThisScan = true;
                geWindowOpenForOverlay = true;
                geHeaderBoundsForOverlay = new Rectangle(bounds);
                geHeaderTextForOverlay = cleanWidgetText(text);
            }
            int id = widget.getId();
            int group = id >>> 16;
            int child = id & 0xFFFF;

            if (group == 465 && child >= 7 && child <= 14 && bounds != null)
            {
                // autoflip_marker: ge_slot_bounds_for_overlay
                // Slot roots retain the same child IDs after an offer is placed. Keep their
                // bounds for both empty and occupied slots so universal item links can render.
                // Nested item widgets can inherit the same packed ID, so retain the largest
                // rectangle instead of allowing a small descendant to move the link downward.
                int slotIndex = child - 7;
                Rectangle current = geSlotBoundsForOverlay[slotIndex];
                long currentArea = current == null ? 0L : (long) current.width * current.height;
                long candidateArea = (long) bounds.width * bounds.height;
                if (candidateArea > currentArea)
                {
                    geSlotBoundsForOverlay[slotIndex] = new Rectangle(bounds);
                }
            }

            if (matches.length() > 0)
            {
                matches.append(" || ");
            }

            matches
                .append("id=").append(id)
                .append(",group=").append(group)
                .append(",child=").append(child)
                .append(",type=").append(widget.getType())
                .append(",bounds=");

            if (bounds == null)
            {
                matches.append("null");
            }
            else
            {
                matches
                    .append(bounds.x).append(":")
                    .append(bounds.y).append(":")
                    .append(bounds.width).append(":")
                    .append(bounds.height);
            }

            matches
                .append(",text=").append(cleanWidgetText(text))
                .append(",itemId=").append(widget.getItemId())
                .append(",itemQty=").append(widget.getItemQuantity())
                .append(",name=").append(cleanWidgetText(name));

            count[0]++;
        }

        collectWidgetArray(widget.getChildren(), matches, count, depth + 1);
        collectWidgetArray(widget.getStaticChildren(), matches, count, depth + 1);
        collectWidgetArray(widget.getDynamicChildren(), matches, count, depth + 1);
        collectWidgetArray(widget.getNestedChildren(), matches, count, depth + 1);
    }

    private void collectWidgetArray(Widget[] widgets, StringBuilder matches, int[] count, int depth)
    {
        if (widgets == null || count[0] >= 250)
        {
            return;
        }

        for (Widget child : widgets)
        {
            collectGeWidgetMatches(child, matches, count, depth);
            if (count[0] >= 250)
            {
                return;
            }
        }
    }

    private boolean isGeRelevantWidget(Widget widget)
    {
        String text = cleanWidgetText(widget.getText()).toLowerCase();
        String name = cleanWidgetText(widget.getName()).toLowerCase();

        return text.contains("grand exchange")
            || text.contains("select an offer slot")
            || text.equals("empty")
            || text.equals("history")
            || text.contains("offer")
            || text.contains("buy")
            || text.contains("sell")
            || name.contains("grand exchange")
            || name.contains("empty")
            || name.contains("offer")
            || name.contains("buy")
            || name.contains("sell");
    }

    private boolean isGrandExchangeHeaderText(String text)
    {
        String cleaned = cleanWidgetText(text);

        // AUTOFLIP_GE_SETUP_HEADER_DETECTION_V1
        // Main GE title: "Grand Exchange"
        // Setup title: "Grand Exchange: Set up offer"
        // Keep setup windows inside the same AutoFlip GE session so the toggle remains visible.
        return "Grand Exchange".equals(cleaned)
            || cleaned.matches("Grand Exchange \\([0-9][0-9,.]*[kKmMbB]?\\)")
            || cleaned.startsWith("Grand Exchange:");
    }
    private String cleanWidgetText(String value)
    {
        if (value == null)
        {
            return "";
        }

        return value
            .replace("<br>", " ")
            .replace("<col=ff981f>", "")
            .replace("<col=ffffff>", "")
            .replace("</col>", "")
            .replace("\\", "\\\\")
            .replace("\"", "'")
            .trim();
    }
    private void pollGrandExchangeOffers()
    {
        if (client == null)
        {
            return;
        }

        if (!pollingApiChecked)
        {
            pollingApiChecked = true;
            pollingApiAvailable = true;
        }

        if (!pollingApiAvailable)
        {
            return;
        }

        try
        {
            GrandExchangeOffer[] offers = client.getGrandExchangeOffers();

            for (int slot = 0; slot < offers.length && slot < lastCanonicalSnapshots.length; slot++)
            {
                GrandExchangeOffer offer = offers[slot];
                if (offer == null)
                {
                    continue;
                }

                recordSlotState(
                    "game_tick_polling",
                    slot,
                    offer.getItemId(),
                    stateName(offer.getState()),
                    offer.getTotalQuantity(),
                    offer.getQuantitySold(),
                    clampAutoFlipGpToInt(offer.getPrice()),
                    clampAutoFlipGpToInt(offer.getSpent())
                );
            }
        }
        catch (Exception e)
        {
            appendLine(GE_DEBUG, "{\"event\":\"ge_polling_error\",\"source\":\"autoflip_runelite\",\"message\":\"" + safe(e.getClass().getSimpleName()) + "\",\"ts\":\"" + now() + "\"}");
        }
    }

    private void updateSlotCapacity(
        int slot,
        int itemId,
        String state,
        int totalQuantity,
        int quantitySold,
        int price,
        int spent,
        String detector
    )
    {
        if (slot < 0 || slot >= currentSlotEmpty.length)
        {
            return;
        }

        String normalizedState = state == null ? "UNKNOWN" : state;
        boolean empty = isCapacityEmptySlot(itemId, normalizedState, totalQuantity, quantitySold, price, spent);

        capacitySlotObserved[slot] = true;
        currentSlotEmpty[slot] = empty;
        currentSlotCapacityStates[slot] =
            slot
                + ":" + (empty ? "EMPTY" : "USED")
                + ":" + normalizedState
                + ":" + itemId
                + ":" + totalQuantity
                + ":" + quantitySold;

        if (!allCapacitySlotsObserved())
        {
            return;
        }

        int slotsTotal = currentSlotEmpty.length;
        int slotsAvailable = 0;

        for (boolean isEmptySlot : currentSlotEmpty)
        {
            if (isEmptySlot)
            {
                slotsAvailable++;
            }
        }

        int slotsUsed = slotsTotal - slotsAvailable;
        String summary = buildSlotCapacitySummary();
        String fingerprint =
            "total=" + slotsTotal
                + "|used=" + slotsUsed
                + "|available=" + slotsAvailable
                + "|" + summary;

        if (fingerprint.equals(lastCapacityFingerprint))
        {
            return;
        }

        lastCapacityFingerprint = fingerprint;

        String eventId = sha256(accountKey + "|ge_slot_capacity_changed|" + fingerprint);

        String payload = "{"
            + "\"event\":\"ge_slot_state_changed\","
            + "\"event_id\":\"" + eventId + "\","
            + "\"source\":\"autoflip_runelite\","
            + "\"account_key\":\"" + safe(accountKey) + "\","
            + "\"detector\":\"slot_capacity_changed\","
            + "\"capacity_event\":true,"
            + "\"ts\":\"" + now() + "\","
            + "\"slot\":-1,"
            + "\"slot_instance_seq\":0,"
            + "\"item_id\":0,"
            + "\"side\":\"UNKNOWN\","
            + "\"state\":\"SLOT_CAPACITY\","
            + "\"total_quantity\":0,"
            + "\"quantity_sold\":0,"
            + "\"price\":0,"
            + "\"spent\":0,"
            + "\"slots_total\":" + slotsTotal + ","
            + "\"slots_used\":" + slotsUsed + ","
            + "\"slots_available\":" + slotsAvailable + ","
            + "\"slots_label\":\"" + slotsAvailable + "/" + slotsTotal + "\","
            + "\"slot_capacity_summary\":\"" + safe(summary) + "\","
            + "\"source_detector\":\"" + safe(detector) + "\","
            + "\"dedupe_key_source\":\"account_ge_slot_capacity_snapshot\""
            + "}";

        appendLine(GE_SLOT_CAPACITY, payload);

        appendLine(
            SENDER_LOG,
            "{\"event\":\"slot_capacity_changed\","
                + "\"slots_total\":" + slotsTotal + ","
                + "\"slots_used\":" + slotsUsed + ","
                + "\"slots_available\":" + slotsAvailable + ","
                + "\"slots_label\":\"" + slotsAvailable + "/" + slotsTotal + "\","
                + "\"ts\":\"" + now() + "\"}"
        );

        logAutoFlipVerbose(
            "AUTOFLIP_SLOT_CAPACITY"
                + " available=" + slotsAvailable + "/" + slotsTotal
                + " used=" + slotsUsed
        );
    }

    private boolean allCapacitySlotsObserved()
    {
        for (boolean observed : capacitySlotObserved)
        {
            if (!observed)
            {
                return false;
            }
        }

        return true;
    }

    private boolean isCapacityEmptySlot(
        int itemId,
        String state,
        int totalQuantity,
        int quantitySold,
        int price,
        int spent
    )
    {
        String normalizedState = state == null ? "UNKNOWN" : state;

        if ("EMPTY".equals(normalizedState))
        {
            return true;
        }

        return itemId == 0
            && totalQuantity == 0
            && quantitySold == 0
            && price == 0
            && spent == 0;
    }

    private boolean isOpenOfferState(
        int itemId,
        String state,
        int totalQuantity,
        int quantitySold,
        int price,
        int spent
    )
    {
        if (itemId <= 0 || totalQuantity <= 0)
        {
            return false;
        }

        String normalizedState = state == null ? "UNKNOWN" : state;
        if (isTerminalOfferState(normalizedState) || isCapacityEmptySlot(itemId, normalizedState, totalQuantity, quantitySold, price, spent))
        {
            return false;
        }

        return quantitySold < totalQuantity;
    }

    private boolean isTerminalOfferState(String state)
    {
        String normalizedState = state == null ? "UNKNOWN" : state.toUpperCase(java.util.Locale.ROOT);
        return normalizedState.contains("CANCEL")
            || normalizedState.contains("MISSING")
            || normalizedState.contains("ABANDON")
            || "BOUGHT".equals(normalizedState)
            || "SOLD".equals(normalizedState);
    }

    private void rememberSlotPlacementContext(int slot, int itemId, String state, int totalQuantity, int price, long observedTsMs)
    {
        if (slot < 0 || slot >= slotPlacedTsMs.length)
        {
            return;
        }

        if (slotPlacedTsMs[slot] <= 0L
            || slotPlacedItemId[slot] != itemId
            || slotPlacedPrice[slot] != price
            || slotPlacedQuantity[slot] != totalQuantity)
        {
            slotPlacedTsMs[slot] = observedTsMs;
            slotPlacedItemId[slot] = itemId;
            slotPlacedPrice[slot] = price;
            slotPlacedQuantity[slot] = totalQuantity;
            slotPlacedSide[slot] = inferSide(state);
            slotRecommendationContext[slot] = findAutoFlipRecommendationContextForOffer(
                itemId,
                slotPlacedSide[slot],
                totalQuantity,
                price
            );
        }

        slotLastSeenOpenTsMs[slot] = Math.max(slotLastSeenOpenTsMs[slot], observedTsMs);
    }

    private void clearSlotPlacementContext(int slot)
    {
        if (slot < 0 || slot >= slotPlacedTsMs.length)
        {
            return;
        }

        slotPlacedTsMs[slot] = 0L;
        slotLastSeenOpenTsMs[slot] = 0L;
        slotPlacedItemId[slot] = 0;
        slotPlacedPrice[slot] = 0;
        slotPlacedQuantity[slot] = 0;
        slotPlacedSide[slot] = null;
        slotRecommendationContext[slot] = null;
        slotLastObservedFilledQuantity[slot] = 0;
        slotLastObservedSpentGp[slot] = 0;
        slotLastObservedState[slot] = null;
    }

    private void enqueueActiveOfferSummaryIfNeeded(String reason)
    {
        long observedTsMs = System.currentTimeMillis();
        StringBuilder offers = new StringBuilder();
        StringBuilder fingerprint = new StringBuilder();
        int count = 0;

        for (int slot = 0; slot < slotPlacedTsMs.length; slot++)
        {
            if (slotPlacedTsMs[slot] <= 0L || slotPlacedItemId[slot] <= 0)
            {
                continue;
            }

            long ageSeconds = Math.max(0L, (observedTsMs - slotPlacedTsMs[slot]) / 1000L);
            long ageBucketMinutes = Math.max(0L, ageSeconds / 60L);
            String state = slotLastObservedState[slot] == null ? "OPEN" : slotLastObservedState[slot];
            String side = slotPlacedSide[slot] == null ? "UNKNOWN" : slotPlacedSide[slot];

            fingerprint
                .append("|").append(slot)
                .append(":").append(slotInstanceSeq[slot])
                .append(":").append(slotPlacedItemId[slot])
                .append(":").append(side)
                .append(":").append(slotPlacedPrice[slot])
                .append(":").append(slotPlacedQuantity[slot])
                .append(":").append(slotLastObservedFilledQuantity[slot])
                .append(":").append(slotLastObservedSpentGp[slot])
                .append(":").append(ageBucketMinutes);

            if (count > 0)
            {
                offers.append(",");
            }

            StringBuilder offer = new StringBuilder();
            offer.append("{");
            offer.append("\"slot\":").append(slot);
            offer.append(",\"slot_instance_seq\":").append(slotInstanceSeq[slot]);
            offer.append(",\"item_id\":").append(slotPlacedItemId[slot]);
            offer.append(",\"side\":\"").append(safe(side)).append("\"");
            offer.append(",\"state\":\"").append(safe(state)).append("\"");
            offer.append(",\"offered_price\":").append(Math.max(0, slotPlacedPrice[slot]));
            offer.append(",\"offered_quantity\":").append(Math.max(0, slotPlacedQuantity[slot]));
            offer.append(",\"filled_quantity\":").append(Math.max(0, slotLastObservedFilledQuantity[slot]));
            offer.append(",\"spent_or_received_gp\":").append(Math.max(0, slotLastObservedSpentGp[slot]));
            offer.append(",\"placed_ts_ms\":").append(slotPlacedTsMs[slot]);
            offer.append(",\"last_seen_open_ts_ms\":").append(Math.max(slotLastSeenOpenTsMs[slot], slotPlacedTsMs[slot]));
            offer.append(",\"age_seconds\":").append(ageSeconds);
            offer.append(",\"snapshot_anchor_role\":\"active_offer_state_snapshot\"");

            AutoFlipRecommendationContext context = slotRecommendationContext[slot];
            if (context != null)
            {
                appendJsonStringField(offer, "recommendation_id", context.recommendationId);
                appendJsonStringField(offer, "plan_id", context.planId);
                appendJsonStringField(offer, "cache_build_id", context.cacheBuildId);
                appendJsonStringField(offer, "payload_hash", context.payloadHash);
                appendJsonLongField(offer, "recommendation_generated_ts_ms", context.recommendationGeneratedTsMs);
                appendJsonLongField(offer, "recommendation_shown_ts_ms", context.recommendationShownTsMs);
                if (context.boardSlot >= 0)
                {
                    offer.append(",\"board_slot\":").append(context.boardSlot);
                }
                appendJsonLongField(offer, "suggested_buy_price", context.suggestedBuyPriceGp);
                appendJsonLongField(offer, "suggested_sell_price", context.suggestedSellPriceGp);
                appendJsonLongField(offer, "suggested_quantity", context.suggestedQuantity);
                appendJsonStringField(offer, "execution_pricing_source", context.executionPricingSource);
            }

            offer.append("}");
            offers.append(offer);
            count++;
        }

        if (count <= 0)
        {
            lastActiveOfferSummaryFingerprint = "";
            return;
        }

        String currentFingerprint = fingerprint.toString();
        if (currentFingerprint.equals(lastActiveOfferSummaryFingerprint))
        {
            return;
        }
        lastActiveOfferSummaryFingerprint = currentFingerprint;

        String cacheBuildId = !autoFlipPayloadHash.isEmpty()
            ? autoFlipPayloadHash
            : sha256(autoFlipPayloadGeneratedAt + "|" + autoFlipPayloadSourceCacheBuilder).substring(0, 32);
        long snapshotTsMs = autoFlipPayloadGeneratedAtMs > 0L
            ? autoFlipPayloadGeneratedAtMs
            : parseIsoTsMs(autoFlipPayloadGeneratedAt);

        enqueueTelemetryEvent(
            "active_offer_summary",
            "{"
                + "\"reason\":\"" + safe(reason) + "\","
                + "\"stream\":\"active_intent\","
                + "\"lifecycle_stream\":\"active_intent\","
                + "\"snapshot_anchor_role\":\"active_offer_summary_snapshot\","
                + "\"snapshot_observed_ts_ms\":" + observedTsMs + ","
                + "\"active_offer_count\":" + count + ","
                + "\"snapshot_cache_build_id\":\"" + safe(cacheBuildId) + "\","
                + "\"snapshot_payload_hash\":\"" + safe(autoFlipPayloadHash) + "\","
                + "\"snapshot_generated_ts_ms\":" + Math.max(0L, snapshotTsMs) + ","
                + "\"offers\":[" + offers + "]"
                + "}"
        );
    }

    private AutoFlipRecommendationContext findAutoFlipRecommendationContextForOffer(
        int itemId,
        String side,
        int quantity,
        int price
    )
    {
        if (itemId <= 0)
        {
            return null;
        }

        java.util.List<AutoFlipBoardCard> cards = getAutoFlipVisibleBoardCardsSnapshot();
        if (cards == null || cards.isEmpty())
        {
            return null;
        }

        AutoFlipBoardCard best = null;
        long bestScore = Long.MAX_VALUE;
        boolean sellSide = side != null && side.toUpperCase(java.util.Locale.ROOT).contains("SELL");

        for (AutoFlipBoardCard card : cards)
        {
            if (card == null || card.getItemId() != itemId)
            {
                continue;
            }

            long suggestedPrice = sellSide ? card.getSellPriceGp() : card.getBuyPriceGp();
            long priceDelta = suggestedPrice > 0L && price > 0 ? Math.abs(suggestedPrice - (long) price) : 1000000L;
            long quantityDelta = quantity > 0 ? Math.abs((long) card.getQuantity() - (long) quantity) : 0L;
            long score = (priceDelta * 100000L) + quantityDelta;

            if (best == null || score < bestScore)
            {
                best = card;
                bestScore = score;
            }
        }

        return best == null ? null : best.getRecommendationContext();
    }

    private String buildSlotRecommendationPayloadFields(int slot, int actualPrice, int actualQuantity, String state, long observedTsMs)
    {
        if (slot < 0 || slot >= slotRecommendationContext.length)
        {
            return "";
        }

        AutoFlipRecommendationContext context = slotRecommendationContext[slot];
        if (context == null)
        {
            return "";
        }

        String side = slotPlacedSide[slot] == null ? "UNKNOWN" : slotPlacedSide[slot].toUpperCase(java.util.Locale.ROOT);
        boolean sellSide = side.contains("SELL");
        boolean terminal = isTerminalOfferState(state);
        String lifecycleStream = terminal ? "final_outcome" : "active_intent";
        String anchorRole = sellSide
            ? (terminal ? "sell_completion_snapshot" : "sell_placement_snapshot")
            : (terminal ? "buy_completion_snapshot" : "buy_placement_snapshot");
        long currentSnapshotTsMs = autoFlipPayloadGeneratedAtMs > 0L
            ? autoFlipPayloadGeneratedAtMs
            : parseIsoTsMs(autoFlipPayloadGeneratedAt);
        String currentSnapshotCacheBuildId = !autoFlipPayloadHash.isEmpty()
            ? autoFlipPayloadHash
            : context.cacheBuildId;
        String currentSnapshotPayloadHash = !autoFlipPayloadHash.isEmpty()
            ? autoFlipPayloadHash
            : context.payloadHash;
        long actualBuy = sellSide ? 0L : Math.max(0, actualPrice);
        long actualSell = sellSide ? Math.max(0, actualPrice) : 0L;
        long buyDelta = actualBuy > 0L && context.suggestedBuyPriceGp > 0L ? actualBuy - context.suggestedBuyPriceGp : 0L;
        long sellDelta = actualSell > 0L && context.suggestedSellPriceGp > 0L ? actualSell - context.suggestedSellPriceGp : 0L;
        long quantityDelta = actualQuantity > 0 && context.suggestedQuantity > 0 ? (long) actualQuantity - (long) context.suggestedQuantity : 0L;

        StringBuilder sb = new StringBuilder();
        appendJsonStringField(sb, "recommendation_id", context.recommendationId);
        appendJsonStringField(sb, "plan_id", context.planId);
        if (context.boardSlot >= 0)
        {
            sb.append(",\"board_slot\":").append(context.boardSlot);
        }
        appendJsonStringField(sb, "cache_build_id", context.cacheBuildId);
        appendJsonStringField(sb, "payload_hash", context.payloadHash);
        appendJsonStringField(sb, "lifecycle_stream", lifecycleStream);
        appendJsonStringField(sb, "snapshot_anchor_role", anchorRole);
        appendJsonStringField(sb, "recommendation_snapshot_cache_build_id", context.cacheBuildId);
        appendJsonStringField(sb, "recommendation_snapshot_payload_hash", context.payloadHash);
        appendJsonLongField(sb, "recommendation_snapshot_ts_ms", context.recommendationGeneratedTsMs);
        appendJsonStringField(sb, "offer_snapshot_cache_build_id", context.cacheBuildId);
        appendJsonStringField(sb, "offer_snapshot_payload_hash", context.payloadHash);
        appendJsonLongField(sb, "offer_snapshot_ts_ms", context.recommendationGeneratedTsMs);
        appendJsonStringField(sb, "current_snapshot_cache_build_id", currentSnapshotCacheBuildId);
        appendJsonStringField(sb, "current_snapshot_payload_hash", currentSnapshotPayloadHash);
        appendJsonLongField(sb, "current_snapshot_ts_ms", currentSnapshotTsMs);
        if (terminal)
        {
            appendJsonStringField(sb, "completion_snapshot_cache_build_id", currentSnapshotCacheBuildId);
            appendJsonStringField(sb, "completion_snapshot_payload_hash", currentSnapshotPayloadHash);
            appendJsonLongField(sb, "completion_snapshot_ts_ms", currentSnapshotTsMs);
        }
        if (sellSide)
        {
            appendJsonStringField(sb, "sell_placement_snapshot_cache_build_id", context.cacheBuildId);
            appendJsonStringField(sb, "sell_placement_snapshot_payload_hash", context.payloadHash);
            appendJsonLongField(sb, "sell_placement_snapshot_ts_ms", context.recommendationGeneratedTsMs);
            if (terminal)
            {
                appendJsonStringField(sb, "sell_completion_snapshot_cache_build_id", currentSnapshotCacheBuildId);
                appendJsonStringField(sb, "sell_completion_snapshot_payload_hash", currentSnapshotPayloadHash);
                appendJsonLongField(sb, "sell_completion_snapshot_ts_ms", currentSnapshotTsMs);
            }
        }
        else
        {
            appendJsonStringField(sb, "buy_placement_snapshot_cache_build_id", context.cacheBuildId);
            appendJsonStringField(sb, "buy_placement_snapshot_payload_hash", context.payloadHash);
            appendJsonLongField(sb, "buy_placement_snapshot_ts_ms", context.recommendationGeneratedTsMs);
            if (terminal)
            {
                appendJsonStringField(sb, "buy_completion_snapshot_cache_build_id", currentSnapshotCacheBuildId);
                appendJsonStringField(sb, "buy_completion_snapshot_payload_hash", currentSnapshotPayloadHash);
                appendJsonLongField(sb, "buy_completion_snapshot_ts_ms", currentSnapshotTsMs);
            }
        }
        appendJsonLongField(sb, "snapshot_observed_ts_ms", observedTsMs);
        appendJsonStringField(sb, "optimizer_version", context.optimizerVersion);
        appendJsonStringField(sb, "mode", context.mode);
        appendJsonLongField(sb, "recommendation_generated_ts_ms", context.recommendationGeneratedTsMs);
        appendJsonLongField(sb, "recommendation_shown_ts_ms", context.recommendationShownTsMs);
        if (context.recommendationGeneratedTsMs > 0L && context.recommendationShownTsMs > 0L)
        {
            appendJsonDoubleField(sb, "recommendation_age_seconds", Math.max(0.0D, (context.recommendationShownTsMs - context.recommendationGeneratedTsMs) / 1000.0D));
        }
        appendJsonLongField(sb, "user_selected_budget_gp", context.userSelectedBudgetGp);
        appendJsonLongField(sb, "user_selected_hours_away", context.userSelectedHoursAway);
        appendJsonLongField(sb, "suggested_buy_price", context.suggestedBuyPriceGp);
        appendJsonLongField(sb, "suggested_sell_price", context.suggestedSellPriceGp);
        appendJsonLongField(sb, "suggested_quantity", context.suggestedQuantity);
        appendJsonLongField(sb, "actual_quantity_entered", Math.max(0, actualQuantity));
        appendJsonLongField(sb, "suggested_total_budget_gp", context.suggestedTotalBudgetGp);
        appendJsonLongField(sb, "actual_total_budget_gp", Math.max(0L, (long) Math.max(0, actualPrice) * (long) Math.max(0, actualQuantity)));
        if (actualBuy > 0L)
        {
            appendJsonLongField(sb, "actual_buy_price_entered", actualBuy);
            appendJsonLongField(sb, "buy_price_user_delta_gp", buyDelta);
            appendJsonDoubleField(sb, "buy_price_user_delta_pct", context.suggestedBuyPriceGp > 0L ? buyDelta / (double) context.suggestedBuyPriceGp : 0.0D);
        }
        if (actualSell > 0L)
        {
            appendJsonLongField(sb, "actual_sell_price_entered", actualSell);
            appendJsonLongField(sb, "sell_price_user_delta_gp", sellDelta);
            appendJsonDoubleField(sb, "sell_price_user_delta_pct", context.suggestedSellPriceGp > 0L ? sellDelta / (double) context.suggestedSellPriceGp : 0.0D);
        }
        appendJsonLongField(sb, "quantity_user_delta", quantityDelta);
        appendJsonDoubleField(sb, "quantity_user_delta_pct", context.suggestedQuantity > 0 ? quantityDelta / (double) context.suggestedQuantity : 0.0D);
        appendJsonLongField(sb, "hourly_volume_capacity_at_offer", context.hourlyVolumeCapacity);
        appendJsonDoubleField(sb, "throughput_ratio_at_offer", context.throughputRatio);
        appendJsonDoubleField(sb, "throughput_pressure_at_offer", context.throughputPressure);
        appendJsonDoubleField(sb, "volume_fit_at_offer", context.volumeFit);
        appendJsonStringField(sb, "recommendation_slice_key", context.sliceKey);
        appendJsonStringField(sb, "recommendation_matrix_key", context.matrixKey);
        appendJsonStringField(sb, "execution_pricing_source", context.executionPricingSource);

        return sb.toString();
    }

    private static void appendJsonStringField(StringBuilder sb, String key, String value)
    {
        if (value != null && !value.isEmpty())
        {
            sb.append(",\"").append(key).append("\":\"").append(safe(value)).append("\"");
        }
    }

    private static void appendJsonLongField(StringBuilder sb, String key, long value)
    {
        if (value != 0L)
        {
            sb.append(",\"").append(key).append("\":").append(value);
        }
    }

    private static void appendJsonDoubleField(StringBuilder sb, String key, double value)
    {
        if (!Double.isNaN(value) && !Double.isInfinite(value) && value != 0.0D)
        {
            sb.append(",\"").append(key).append("\":").append(String.format(java.util.Locale.US, "%.6f", value));
        }
    }

    private static long parseIsoTsMs(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return 0L;
        }

        try
        {
            return Instant.parse(value.trim()).toEpochMilli();
        }
        catch (Exception ignored)
        {
            return 0L;
        }
    }

    private String buildSlotCapacitySummary()
    {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < currentSlotCapacityStates.length; i++)
        {
            if (i > 0)
            {
                sb.append("|");
            }

            String state = currentSlotCapacityStates[i];
            sb.append(state == null ? (i + ":UNKNOWN") : state);
        }

        return sb.toString();
    }

    private String buildSlotTimingPayloadFields(
        int slot,
        int itemId,
        String state,
        int totalQuantity,
        int price,
        long observedTsMs
    )
    {
        if (slot < 0 || slot >= slotPlacedTsMs.length || slotPlacedTsMs[slot] <= 0L)
        {
            return "";
        }

        long placedTsMs = slotPlacedTsMs[slot];
        long lastSeenOpenTsMs = Math.max(slotLastSeenOpenTsMs[slot], placedTsMs);
        boolean terminal = isTerminalOfferState(state);
        boolean missingWindow = itemId <= 0 || "EMPTY".equals(state);
        long lowerBoundSeconds = Math.max(0L, (lastSeenOpenTsMs - placedTsMs) / 1000L);
        long upperBoundSeconds = Math.max(lowerBoundSeconds, (observedTsMs - placedTsMs) / 1000L);
        String observationType = terminal ? "exact_observed_final_state" : (missingWindow ? "bounded_missing_offer_window" : "open_observation");

        return ",\"placed_ts_ms\":" + placedTsMs
            + ",\"placed_ts\":\"" + Instant.ofEpochMilli(placedTsMs).toString() + "\""
            + ",\"last_seen_open_ts_ms\":" + lastSeenOpenTsMs
            + ",\"last_seen_open_ts\":\"" + Instant.ofEpochMilli(lastSeenOpenTsMs).toString() + "\""
            + ",\"final_observed_ts_ms\":" + observedTsMs
            + ",\"final_observed_ts\":\"" + Instant.ofEpochMilli(observedTsMs).toString() + "\""
            + ",\"offered_price\":" + Math.max(0, slotPlacedPrice[slot])
            + ",\"offered_quantity\":" + Math.max(0, slotPlacedQuantity[slot])
            + ",\"offered_item_id\":" + Math.max(0, slotPlacedItemId[slot])
            + ",\"offered_side\":\"" + safe(slotPlacedSide[slot] == null ? inferSide(state) : slotPlacedSide[slot]) + "\""
            + buildSlotRecommendationPayloadFields(slot, price, totalQuantity, state, observedTsMs)
            + ",\"time_in_trade_lower_bound_seconds\":" + lowerBoundSeconds
            + ",\"time_in_trade_upper_bound_seconds\":" + upperBoundSeconds
            + ",\"fill_time_lower_bound_seconds\":" + lowerBoundSeconds
            + ",\"fill_time_upper_bound_seconds\":" + upperBoundSeconds
            + ",\"fill_time_observation_type\":\"" + observationType + "\"";
    }

    private void enqueueMissingOfferWindowEvent(int slot, String detector, long observedTsMs)
    {
        if (slot < 0 || slot >= slotPlacedTsMs.length || slotPlacedTsMs[slot] <= 0L || slotPlacedItemId[slot] <= 0)
        {
            return;
        }

        String eventId = sha256(
            accountKey
                + "|missing_offer_window"
                + "|slot=" + slot
                + "|instance=" + slotInstanceSeq[slot]
                + "|item=" + slotPlacedItemId[slot]
                + "|placed=" + slotPlacedTsMs[slot]
                + "|observed=" + observedTsMs
        );

        String payload = "{"
            + "\"event\":\"ge_slot_state_changed\","
            + "\"event_type\":\"trade_abandoned\","
            + "\"event_id\":\"" + eventId + "\","
            + "\"source\":\"autoflip_runelite\","
            + "\"account_key\":\"" + safe(accountKey) + "\","
            + "\"detector\":\"missing_offer_reconciliation\","
            + "\"source_detector\":\"" + safe(detector) + "\","
            + "\"ts\":\"" + Instant.ofEpochMilli(observedTsMs).toString() + "\","
            + "\"slot\":" + slot + ","
            + "\"slot_instance_seq\":" + slotInstanceSeq[slot] + ","
            + "\"item_id\":" + slotPlacedItemId[slot] + ","
            + "\"side\":\"" + safe(slotPlacedSide[slot] == null ? "UNKNOWN" : slotPlacedSide[slot]) + "\","
            + "\"state\":\"MISSING_FROM_GE\","
            + "\"outcome\":\"unknown_pending_history_reconciliation\","
            + "\"total_quantity\":" + Math.max(0, slotPlacedQuantity[slot]) + ","
            + "\"quantity_sold\":0,"
            + "\"price\":" + Math.max(0, slotPlacedPrice[slot]) + ","
            + "\"spent\":0"
            + buildSlotTimingPayloadFields(slot, slotPlacedItemId[slot], "MISSING_FROM_GE", Math.max(0, slotPlacedQuantity[slot]), Math.max(0, slotPlacedPrice[slot]), observedTsMs) + ","
            + "\"dedupe_key_source\":\"account_slot_instance_missing_window\""
            + "}";

        appendLine(GE_SLOT_EVENTS, payload);
        enqueueUploadOnce(eventId, payload);
    }
    private void recordSlotState(
        String detector,
        int slot,
        int itemId,
        String state,
        int totalQuantity,
        int quantitySold,
        int price,
        int spent
    )
    {
        if (slot < 0 || slot >= lastCanonicalSnapshots.length)
        {
            return;
        }

        String normalizedState = state == null ? "UNKNOWN" : state;
        long observedTsMs = System.currentTimeMillis();
        String observedTsIso = Instant.ofEpochMilli(observedTsMs).toString();
        updateAutoFlipGeOfferSlotSnapshot(slot, itemId, normalizedState, totalQuantity, quantitySold, price, spent);

        String snapshot = slot
            + "|" + itemId
            + "|" + normalizedState
            + "|" + totalQuantity
            + "|" + quantitySold
            + "|" + price
            + "|" + spent;

        boolean isEmpty =
            itemId == 0
                && "EMPTY".equals(normalizedState)
                && totalQuantity == 0
                && quantitySold == 0
                && price == 0
                && spent == 0;

        
        updateSlotCapacity(slot, itemId, normalizedState, totalQuantity, quantitySold, price, spent, detector);
boolean isEmptyBaseline = isEmpty && lastCanonicalSnapshots[slot] == null;

        if (isEmptyBaseline)
        {
            resetAutoFlipHeldSaleMemoryForSlot(slot);
            lastCanonicalSnapshots[slot] = snapshot;
            return;
        }

        if (snapshot.equals(lastCanonicalSnapshots[slot]))
        {
            return;
        }

        boolean previousWasEmpty = lastCanonicalSnapshots[slot] == null || lastCanonicalSnapshots[slot].contains("|EMPTY|");
        if (!isEmpty && previousWasEmpty)
        {
            slotInstanceSeq[slot]++;
            resetAutoFlipHeldSaleMemoryForSlot(slot);
            rememberSlotPlacementContext(slot, itemId, normalizedState, totalQuantity, price, observedTsMs);
            saveSlotInstanceSeq();
        }
        else if (isOpenOfferState(itemId, normalizedState, totalQuantity, quantitySold, price, spent))
        {
            rememberSlotPlacementContext(slot, itemId, normalizedState, totalQuantity, price, observedTsMs);
        }

        if (isEmpty && !previousWasEmpty)
        {
            resetAutoFlipHeldSaleMemoryForSlot(slot);
            purgeAutoFlipGeAccountedTotalsForSlot(slot);
        }

        lastCanonicalSnapshots[slot] = snapshot;
        if (!isEmpty)
        {
            slotLastObservedFilledQuantity[slot] = Math.max(0, quantitySold);
            slotLastObservedSpentGp[slot] = Math.max(0, spent);
            slotLastObservedState[slot] = normalizedState;
        }

        String side = inferSide(normalizedState);
        String eventCore = accountKey
            + "|ge_slot_state_changed"
            + "|slot=" + slot
            + "|instance=" + slotInstanceSeq[slot]
            + "|item=" + itemId
            + "|side=" + side
            + "|state=" + normalizedState
            + "|total=" + totalQuantity
            + "|filled=" + quantitySold
            + "|price=" + price
            + "|spent=" + spent;

        String eventId = sha256(eventCore);

        String payload = "{"
            + "\"event\":\"ge_slot_state_changed\","
            + "\"event_id\":\"" + eventId + "\","
            + "\"source\":\"autoflip_runelite\","
            + "\"account_key\":\"" + safe(accountKey) + "\","
            + "\"detector\":\"" + safe(detector) + "\","
            + "\"ts\":\"" + observedTsIso + "\","
            + "\"slot\":" + slot + ","
            + "\"slot_instance_seq\":" + slotInstanceSeq[slot] + ","
            + "\"item_id\":" + itemId + ","
            + "\"side\":\"" + side + "\","
            + "\"state\":\"" + safe(normalizedState) + "\","
            + "\"total_quantity\":" + totalQuantity + ","
            + "\"quantity_sold\":" + quantitySold + ","
            + "\"price\":" + price + ","
            + "\"spent\":" + spent + buildSlotTimingPayloadFields(slot, itemId, normalizedState, totalQuantity, price, observedTsMs) + ","
            + "\"dedupe_key_source\":\"account_slot_instance_state\""
            + "}";

        appendLine(GE_SLOT_EVENTS, payload);
        if (isUploadWorthyTelemetryPayload(payload))
        {
            enqueueUploadOnce(eventId, payload);
        }
        else if (isEmpty && !previousWasEmpty && slotPlacedTsMs[slot] > 0L)
        {
            enqueueMissingOfferWindowEvent(slot, detector, observedTsMs);
        }

        if (isTerminalOfferState(normalizedState) || isEmpty)
        {
            clearSlotPlacementContext(slot);
        }

        logAutoFlipVerbose(
            "AUTOFLIP_GE_SLOT_CHANGED"
                + " event_id=" + eventId.substring(0, 12)
                + " slot=" + slot
                + " item_id=" + itemId
                + " side=" + side
                + " state=" + normalizedState
                + " qty=" + quantitySold + "/" + totalQuantity
                + " price=" + price
                + " spent=" + spent
        );
    }

    private void recordTradeHistoryConfig(String key, String value)
    {
        if (value == null || value.isEmpty())
        {
            return;
        }

        Pattern tradePattern = Pattern.compile("\\{\\\"b\\\":(true|false),\\\"i\\\":(-?\\d+),\\\"q\\\":(-?\\d+),\\\"p\\\":(-?\\d+),\\\"t\\\":(-?\\d+)\\}");
        Matcher matcher = tradePattern.matcher(value);

        while (matcher.find())
        {
            boolean buy = Boolean.parseBoolean(matcher.group(1));
            int itemId = parseIntSafe(matcher.group(2), 0);
            int quantity = parseIntSafe(matcher.group(3), 0);
            int price = parseIntSafe(matcher.group(4), 0);
            String tradeTimeMs = matcher.group(5);
            String side = buy ? "BUY" : "SELL";

            String eventCore = accountKey
                + "|ge_trade_history"
                + "|side=" + side
                + "|item=" + itemId
                + "|quantity=" + quantity
                + "|price=" + price
                + "|trade_time_ms=" + tradeTimeMs;

            String eventId = sha256(eventCore);

            String payload = "{"
                + "\"event\":\"ge_trade_history\","
                + "\"event_id\":\"" + eventId + "\","
                + "\"source\":\"runelite_grandexchange_config\","
                + "\"account_key\":\"" + safe(accountKey) + "\","
                + "\"config_key\":\"" + safe(key) + "\","
                + "\"ts\":\"" + now() + "\","
                + "\"side\":\"" + side + "\","
                + "\"buy\":" + buy + ","
                + "\"item_id\":" + itemId + ","
                + "\"quantity\":" + quantity + ","
                + "\"price\":" + price + ","
                + "\"trade_time_ms\":" + tradeTimeMs + ","
                + "\"dedupe_key_source\":\"account_trade_history_tuple\""
                + "}";

            enqueueOnce(eventId, payload, GE_TRADE_HISTORY);
        }

        appendLine(SENDER_LOG, "{\"event\":\"trade_history_recorded_local\",\"ts\":\"" + now() + "\"}");
    }

    private void enqueueOnce(String eventId, String payload, Path eventLog)
    {
        if (eventId == null || eventId.isEmpty())
        {
            return;
        }

        if (seenEventIds.contains(eventId))
        {
            return;
        }

        seenEventIds.add(eventId);
        appendLine(SEEN_EVENT_IDS, eventId);
        appendLine(eventLog, payload);

        String outboxLine = "{"
            + "\"event_id\":\"" + eventId + "\","
            + "\"created_at\":\"" + now() + "\","
            + "\"sent_at\":null,"
            + "\"acked_at\":null,"
            + "\"payload\":" + payload
            + "}";

        appendLine(OUTBOX, outboxLine);
    }

    private void enqueueUploadOnce(String eventId, String payload)
    {
        if (eventId == null || eventId.isEmpty())
        {
            return;
        }

        if (seenEventIds.contains(eventId))
        {
            return;
        }

        seenEventIds.add(eventId);
        appendLine(SEEN_EVENT_IDS, eventId);

        String outboxLine = "{"
            + "\"event_id\":\"" + eventId + "\","
            + "\"created_at\":\"" + now() + "\","
            + "\"sent_at\":null,"
            + "\"acked_at\":null,"
            + "\"payload\":" + payload
            + "}";

        appendLine(OUTBOX, outboxLine);
    }

    private void refreshAccountKey()
    {
        if (client == null)
        {
            return;
        }

        String observed = null;

        try
        {
            Player player = client.getLocalPlayer();
            if (player != null && player.getName() != null && !player.getName().isEmpty())
            {
                observed = player.getName();
            }
        }
        catch (Exception ignored)
        {
        }

        if (observed == null || observed.isEmpty())
        {
            try
            {
                observed = client.getUsername();
            }
            catch (Exception ignored)
            {
            }
        }

        if (observed == null || observed.isEmpty())
        {
            return;
        }

        accountKey = sha256(localSalt + "|" + observed.trim().toLowerCase());
    }

    private void loadSlotInstanceSeq()
    {
        boolean loadedFromFile = false;

        if (Files.exists(SLOT_INSTANCE_SEQ_FILE))
        {
            try
            {
                for (String line : Files.readAllLines(SLOT_INSTANCE_SEQ_FILE, StandardCharsets.UTF_8))
                {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || !trimmed.contains("="))
                    {
                        continue;
                    }

                    String[] parts = trimmed.split("=", 2);
                    int slot = parseIntSafe(parts[0], -1);
                    int seq = parseIntSafe(parts[1], 0);

                    if (slot >= 0 && slot < slotInstanceSeq.length)
                    {
                        slotInstanceSeq[slot] = Math.max(slotInstanceSeq[slot], seq);
                        loadedFromFile = true;
                    }
                }
            }
            catch (IOException e)
            {
                log.warn("Unable to load slot instance sequence file", e);
            }
        }

        if (!loadedFromFile)
        {
            bootstrapSlotInstanceSeqFromExistingEvents();
            saveSlotInstanceSeq();
        }

        appendLine(SENDER_LOG, "{\"event\":\"slot_instance_seq_loaded\",\"slot0\":" + slotInstanceSeq[0] + ",\"slot1\":" + slotInstanceSeq[1] + ",\"ts\":\"" + now() + "\"}");
    }

    private void bootstrapSlotInstanceSeqFromExistingEvents()
    {
        if (!Files.exists(GE_SLOT_EVENTS))
        {
            return;
        }

        try
        {
            for (String line : Files.readAllLines(GE_SLOT_EVENTS, StandardCharsets.UTF_8))
            {
                int slot = jsonInt(line, "slot", -1);
                int seq = jsonInt(line, "slot_instance_seq", 0);

                if (slot >= 0 && slot < slotInstanceSeq.length)
                {
                    slotInstanceSeq[slot] = Math.max(slotInstanceSeq[slot], seq);
                }
            }
        }
        catch (IOException e)
        {
            log.warn("Unable to bootstrap slot sequence from existing GE slot events", e);
        }
    }

    private void bootstrapActiveOfferTelemetryContextFromExistingEvents()
    {
        if (!Files.exists(GE_SLOT_EVENTS))
        {
            return;
        }

        try
        {
            for (String line : Files.readAllLines(GE_SLOT_EVENTS, StandardCharsets.UTF_8))
            {
                int slot = jsonInt(line, "slot", -1);
                if (slot < 0 || slot >= lastCanonicalSnapshots.length)
                {
                    continue;
                }

                int itemId = jsonInt(line, "item_id", 0);
                String state = jsonString(line, "state", "UNKNOWN");
                int totalQuantity = jsonInt(line, "total_quantity", 0);
                int quantitySold = jsonInt(line, "quantity_sold", 0);
                int price = jsonInt(line, "price", 0);
                int spent = jsonInt(line, "spent", 0);
                long observedTsMs = parseIsoTsMs(jsonString(line, "ts", ""));
                if (observedTsMs <= 0L)
                {
                    observedTsMs = System.currentTimeMillis();
                }

                String snapshot = slot
                    + "|" + itemId
                    + "|" + state
                    + "|" + totalQuantity
                    + "|" + quantitySold
                    + "|" + price
                    + "|" + spent;
                lastCanonicalSnapshots[slot] = snapshot;

                if (isOpenOfferState(itemId, state, totalQuantity, quantitySold, price, spent))
                {
                    rememberSlotPlacementContext(slot, itemId, state, totalQuantity, price, observedTsMs);
                    slotLastSeenOpenTsMs[slot] = observedTsMs;
                }
                else if (isTerminalOfferState(state) || isCapacityEmptySlot(itemId, state, totalQuantity, quantitySold, price, spent))
                {
                    clearSlotPlacementContext(slot);
                }
            }
        }
        catch (IOException e)
        {
            log.warn("Unable to bootstrap active offer telemetry context from existing GE slot events", e);
        }
    }

    private void saveSlotInstanceSeq()
    {
        ensureRuntimeDir();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < slotInstanceSeq.length; i++)
        {
            sb.append(i).append("=").append(slotInstanceSeq[i]).append(System.lineSeparator());
        }

        try
        {
            Files.write(
                SLOT_INSTANCE_SEQ_FILE,
                sb.toString().getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
        }
        catch (IOException e)
        {
            log.warn("Unable to save slot instance sequence file", e);
        }
    }
    private void loadSeenEventIds()
    {
        seenEventIds.clear();
        loadIdsInto(SEEN_EVENT_IDS, seenEventIds);
    }

    private void loadAckedEventIds()
    {
        if (telemetryRuntime != null)
        {
            telemetryRuntime.loadAckedEventIds();
        }
    }

    private void loadIdsInto(Path path, Set<String> target)
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
            log.warn("Unable to load ids from {}", path, e);
        }
    }

    private String loadOrCreateSalt()
    {
        ensureRuntimeDir();

        try
        {
            if (Files.exists(SALT_FILE))
            {
                String existing = new String(Files.readAllBytes(SALT_FILE), StandardCharsets.UTF_8).trim();
                if (!existing.isEmpty())
                {
                    return existing;
                }
            }

            String created = UUID.randomUUID().toString();
            Files.write(SALT_FILE, created.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return created;
        }
        catch (IOException e)
        {
            log.warn("Unable to load/create local account salt", e);
            return "fallback-local-salt";
        }
    }

    private String loadOrCreatePiBaseUrl()
    {
        ensureRuntimeDir();

        try
        {
            if (Files.exists(PI_BASE_URL_FILE))
            {
                String existing = new String(Files.readAllBytes(PI_BASE_URL_FILE), StandardCharsets.UTF_8).trim();
                if (!existing.isEmpty())
                {
                    String cleanedExisting = cleanBaseUrl(existing);
                    if (!isLegacyOrReleaseBaseUrl(cleanedExisting))
                    {
                        return cleanedExisting;
                    }
                }
            }

            Files.write(PI_BASE_URL_FILE, DEFAULT_PI_BASE_URL.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        catch (IOException e)
        {
            log.warn("Unable to load/create Pi base URL file", e);
        }

        return cleanBaseUrl(DEFAULT_PI_BASE_URL);
    }

    private static boolean isLegacyOrReleaseBaseUrl(String value)
    {
        if (value == null)
        {
            return true;
        }

        String cleaned = value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("/+$", "");
        return cleaned.isEmpty()
            || "https://autoflip.gg".equals(cleaned)
            || "http://autoflip.gg".equals(cleaned)
            || "http://127.0.0.1:8002".equals(cleaned)
            || "http://localhost:8002".equals(cleaned);
    }

    private static String cleanBaseUrl(String value)
    {
        if (value == null)
        {
            return DEFAULT_PI_BASE_URL;
        }

        String cleaned = value
            .replace("\uFEFF", "")
            .replace("\u00EF\u00BB\u00BF", "")
            .replaceAll("[\\u0000-\\u001F\\u007F]", "")
            .trim()
            .replaceAll("/+$", "");

        if (cleaned.isEmpty())
        {
            return DEFAULT_PI_BASE_URL;
        }

        return cleaned;
    }

    private static String inferSide(String state)
    {
        if (state == null)
        {
            return "UNKNOWN";
        }

        if (state.contains("BUY"))
        {
            return "BUY";
        }

        if (state.contains("SELL"))
        {
            return "SELL";
        }

        if ("BOUGHT".equals(state))
        {
            return "BUY";
        }

        if ("SOLD".equals(state))
        {
            return "SELL";
        }

        return "UNKNOWN";
    }

    private static String stateName(GrandExchangeOfferState state)
    {
        return state == null ? "UNKNOWN" : state.name();
    }

    private static int parseIntSafe(String value, int fallback)
    {
        try
        {
            return Integer.parseInt(value);
        }
        catch (Exception e)
        {
            return fallback;
        }
    }

    private static int jsonInt(String json, String key, int fallback)
    {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(-?\\d+)").matcher(json);
        if (!matcher.find())
        {
            return fallback;
        }

        return parseIntSafe(matcher.group(1), fallback);
    }

    private static String jsonString(String json, String key, String fallback)
    {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(json);
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

    private void ensureRuntimeDir()
    {
        try
        {
            Files.createDirectories(RUNTIME_DIR);
        }
        catch (IOException e)
        {
            log.warn("Unable to create AutoFlip runtime directory", e);
        }
    }

    private void appendLine(Path path, String line)
    {
        ensureRuntimeDir();

        try
        {
            Files.write(
                path,
                (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        }
        catch (IOException e)
        {
            log.warn("Unable to write AutoFlip file: {} ({})", path, e.toString());
        }
    }

    private static String now()
    {
        return Instant.now().toString();
    }

    public boolean isAutoFlipVerboseRuntimeLoggingEnabled()
    {
        return AUTOFLIP_VERBOSE_RUNTIME_LOGGING || readBoolConfig("debug.verbose.logs.enabled", false);
    }

    private void logAutoFlipVerbose(String message)
    {
        if (isAutoFlipVerboseRuntimeLoggingEnabled())
        {
            logAutoFlipVerbose(message);
        }
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

    private volatile java.util.List<AutoFlipBoardCard> autoFlipBoardCards = java.util.Collections.emptyList();
    private volatile java.util.List<AutoFlipBoardCard> autoFlipCanonicalBoardCards = java.util.Collections.emptyList();
    private volatile AutoFlipBudgetWarmBuffer autoFlipBudgetWarmBuffer = null;
    private final java.util.concurrent.locks.ReentrantLock autoFlipBoardMutationLock = new java.util.concurrent.locks.ReentrantLock(true);
    private final boolean[] autoFlipRetiredBoardSlots = new boolean[8];

    // AUTOFLIP_LOCAL_PAYLOAD_CACHE_FIELDS_V1
    private volatile String autoFlipPayloadJson = "";
    private volatile String autoFlipPayloadGeneratedAt = "";
    private volatile String autoFlipPayloadHash = "";
    private volatile long autoFlipPayloadGeneratedAtMs = 0L;
    private volatile String autoFlipPayloadAlgorithmVersion = "";
    private volatile String autoFlipPayloadExecutionPricingSource = "";
    private volatile String autoFlipPayloadSourceCacheBuilder = "";
    private volatile long autoFlipPayloadFetchedAtMs = 0L;
    private volatile long autoFlipPayloadTtlMs = 300000L;
    private volatile boolean autoFlipPayloadFetchInFlight = false;
    private static final long AUTOFLIP_DEFAULT_PAYLOAD_TTL_MS = 300000L;
    private static final String AUTOFLIP_RELEASE_PAYLOAD_GZ_ENDPOINT = DEFAULT_PI_BASE_URL + "/api/plugin/ranked-pool-payload-gz";
    private static final String AUTOFLIP_RELEASE_PAYLOAD_JSON_ENDPOINT = DEFAULT_PI_BASE_URL + "/api/plugin/ranked-pool-payload";

    // Kept only as a parity/debug endpoint during migration. Normal overlay render must not call this.
    private static final String AUTOFLIP_RELEASE_RANKED_POOL_ENDPOINT = DEFAULT_PI_BASE_URL + "/api/plugin/ranked-pool";

    public java.util.List<AutoFlipBoardCard> getAutoFlipBoardCardsSnapshot()
    {
        java.util.List<AutoFlipBoardCard> cards = autoFlipBoardCards;
        if (cards == null || cards.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        return new java.util.ArrayList<>(cards);
    }

    public java.util.List<AutoFlipBoardCard> getAutoFlipCanonicalBoardCardsSnapshot()
    {
        java.util.List<AutoFlipBoardCard> cards = autoFlipCanonicalBoardCards;
        if (cards == null || cards.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        return compactAutoFlipBoardCards(cards);
    }

    private java.util.List<AutoFlipBoardCard> getAutoFlipBoardStateSnapshot()
    {
        java.util.List<AutoFlipBoardCard> cards = getAutoFlipBoardCardsSnapshot();
        if (!cards.isEmpty())
        {
            return cards;
        }

        return getAutoFlipCanonicalBoardCardsSnapshot();
    }

    private java.util.List<AutoFlipBoardCard> getAutoFlipVisibleBoardCardsSnapshot()
    {
        java.util.List<AutoFlipBoardCard> cards = getAutoFlipBoardCardsSnapshot();
        if (cards.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        int usableSlots = getAutoFlipUsableGeSlotCountForCurrentAccount();
        java.util.List<AutoFlipBoardCard> visible = new java.util.ArrayList<>();
        for (AutoFlipBoardCard card : cards)
        {
            if (card == null)
            {
                continue;
            }

            int slot = card.getSlotIndex();
            if (slot >= 0 && slot < usableSlots)
            {
                visible.add(card);
            }
        }

        return compactAutoFlipBoardCards(visible);
    }

    private int countAutoFlipVisibleBoardCards(java.util.List<AutoFlipBoardCard> cards)
    {
        if (cards == null || cards.isEmpty())
        {
            return 0;
        }

        int usableSlots = getAutoFlipUsableGeSlotCountForCurrentAccount();
        int visibleCount = 0;
        for (AutoFlipBoardCard card : cards)
        {
            if (card == null)
            {
                continue;
            }

            int slot = card.getSlotIndex();
            if (slot >= 0 && slot < usableSlots)
            {
                visibleCount++;
            }
        }

        return visibleCount;
    }

    private java.util.List<AutoFlipBoardCard> snapshotAutoFlipBoardCards(java.util.List<AutoFlipBoardCard> cards)
    {
        if (cards == null || cards.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        java.util.List<AutoFlipBoardCard> normalized = new java.util.ArrayList<>(java.util.Collections.nCopies(8, null));
        for (AutoFlipBoardCard card : cards)
        {
            if (card == null)
            {
                continue;
            }

            int slot = card.getSlotIndex();
            if (slot >= 0 && slot < normalized.size())
            {
                normalized.set(slot, card);
            }
        }

        return java.util.Collections.unmodifiableList(normalized);
    }

    private java.util.List<AutoFlipBoardCard> compactAutoFlipBoardCards(java.util.List<AutoFlipBoardCard> cards)
    {
        if (cards == null || cards.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        java.util.List<AutoFlipBoardCard> compacted = new java.util.ArrayList<>();
        for (AutoFlipBoardCard card : cards)
        {
            if (card != null)
            {
                compacted.add(card);
            }
        }

        if (compacted.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        compacted.sort(
            java.util.Comparator
                .comparingInt(AutoFlipBoardCard::getSlotIndex)
                .thenComparingInt(AutoFlipBoardCard::getItemId)
        );

        return java.util.Collections.unmodifiableList(compacted);
    }

    private synchronized void captureAutoFlipCanonicalBoardCards(java.util.List<AutoFlipBoardCard> cards, String reason)
    {
        java.util.List<AutoFlipBoardCard> snapshot = compactAutoFlipBoardCards(cards);
        autoFlipCanonicalBoardCards = snapshot;
        logAutoFlipVerbose(
            "AUTOFLIP_BOARD_CANONICAL_CAPTURE"
                + " reason=" + safe(reason)
                + " cards=" + snapshot.size()
        );
    }

    public void maybeRefreshAutoFlipBoardCache()
    {
        // AUTOFLIP_STARTUP_AND_GE_ONLY_WARM_CACHE_V1
        // Startup may warm once during normal login loading. After that, refreshes happen only
        // while the GE interface is open, so the plugin does not keep doing background market
        // work during unrelated gameplay.
        if (!autoFlipStartupWarmAttempted)
        {
            maybeStartAutoFlipStartupWarm();
            return;
        }

        if (!geWindowOpenForOverlay)
        {
            return;
        }

        ensureAutoFlipBoardWarmAsync("ge_open_render");
    }

    private boolean isAutoFlipPayloadFresh()
    {
        String payload = autoFlipPayloadJson;
        long fetchedAt = autoFlipPayloadFetchedAtMs;
        long ttl = autoFlipPayloadTtlMs > 0L ? autoFlipPayloadTtlMs : AUTOFLIP_DEFAULT_PAYLOAD_TTL_MS;

        return payload != null
            && !payload.isEmpty()
            && payload.contains("\"schema\":\"autoflip.plugin_ranked_pool_payload.v1\"")
            && fetchedAt > 0L
            && System.currentTimeMillis() - fetchedAt < ttl;
    }

    private void maybeStartAutoFlipStartupWarm()
    {
        if (autoFlipStartupWarmAttempted || client == null)
        {
            return;
        }

        try
        {
            if (client.getGameState() != GameState.LOGGED_IN)
            {
                return;
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("startup_warm_game_state", error);
            return;
        }

        autoFlipStartupWarmAttempted = true;
        ensureAutoFlipBoardWarmAsync("startup_login");
    }

    private boolean isAutoFlipWarmBoardCacheFresh()
    {
        java.util.List<AutoFlipBoardCard> cards = autoFlipWarmBoardCards;
        String key = autoFlipWarmBoardCacheKey;
        return isAutoFlipPayloadFresh()
            && cards != null
            && !compactAutoFlipBoardCards(cards).isEmpty()
            && key != null
            && !key.isEmpty()
            && key.equals(buildAutoFlipWarmBoardCacheKey());
    }

    private String buildAutoFlipWarmBoardCacheKey()
    {
        boolean freeToPlay = isAutoFlipFreeToPlayAccount();
        int hours = getAutoFlipPayloadHourBucket(autoFlipMenuHoursAway);
        long budget = getAutoFlipEffectiveBudgetGp();
        String budgetBand = getAutoFlipPayloadBudgetBand(budget);
        String payloadKey = autoFlipPayloadHash == null || autoFlipPayloadHash.isEmpty()
            ? autoFlipPayloadGeneratedAt
            : autoFlipPayloadHash;

        return "payload=" + safe(payloadKey)
            + "|f2p=" + freeToPlay
            + "|hours=" + hours
            + "|budget_band=" + safe(budgetBand);
    }

    private void ensureAutoFlipBoardWarmAsync(String reason)
    {
        if (autoFlipBoardWarmInFlight || isAutoFlipWarmBoardCacheFresh())
        {
            return;
        }

        autoFlipBoardWarmInFlight = true;

        Thread worker = new Thread(() ->
        {
            long startedAtMs = System.currentTimeMillis();
            try
            {
                logAutoFlipVerbose("AUTOFLIP_BOARD_WARM started reason=" + safe(reason));

                if (!fetchAutoFlipPayloadIfNeeded(false))
                {
                    logAutoFlipVerbose("AUTOFLIP_BOARD_WARM loaded=false reason=" + safe(reason) + " cause=payload_unavailable");
                    return;
                }

                String key = buildAutoFlipWarmBoardCacheKey();
                java.util.List<AutoFlipBoardCard> cards = buildAutoFlipBoardFromLocalPayload();
                if (cards == null || compactAutoFlipBoardCards(cards).isEmpty())
                {
                    logAutoFlipVerbose("AUTOFLIP_BOARD_WARM loaded=false reason=" + safe(reason) + " cause=cards_empty");
                    return;
                }

                autoFlipWarmBoardCards = snapshotAutoFlipBoardCards(cards);
                autoFlipWarmBoardCacheKey = key;
                autoFlipWarmBoardBuiltAtMs = System.currentTimeMillis();

                logAutoFlipVerbose(
                    "AUTOFLIP_BOARD_WARM"
                        + " loaded=true"
                        + " reason=" + safe(reason)
                        + " cards=" + compactAutoFlipBoardCards(cards).size()
                        + " elapsed_ms=" + Math.max(0L, autoFlipWarmBoardBuiltAtMs - startedAtMs)
                        + " payload_hash=" + safe(autoFlipPayloadHash)
                );
            }
            catch (Throwable error)
            {
                logAutoFlipUiError("board_warm_" + safe(reason), error);
            }
            finally
            {
                autoFlipBoardWarmInFlight = false;
            }
        }, "autoflip-board-warm-cache");

        worker.setDaemon(true);
        worker.start();
    }

    private void ensureAutoFlipPayloadFreshAsync()
    {
        if (autoFlipPayloadFetchInFlight || isAutoFlipPayloadFresh())
        {
            return;
        }

        autoFlipPayloadFetchInFlight = true;

        Thread worker = new Thread(() ->
        {
            try
            {
                fetchAutoFlipPayloadIfNeeded(false);
            }
            catch (Throwable error)
            {
                logAutoFlipUiError("payload_fetch_async", error);
            }
            finally
            {
                autoFlipPayloadFetchInFlight = false;
            }
        }, "autoflip-payload-cache-fetch");

        worker.setDaemon(true);
        worker.start();
    }
    private boolean fetchAutoFlipPayloadIfNeeded(boolean force)
    {
        if (!force && isAutoFlipPayloadFresh())
        {
            logAutoFlipVerbose(
                "AUTOFLIP_PAYLOAD_CACHE"
                    + " reused=true"
                    + " generated_at=" + safe(autoFlipPayloadGeneratedAt)
                    + " payload_hash=" + safe(autoFlipPayloadHash)
                    + " age_ms=" + Math.max(0L, System.currentTimeMillis() - autoFlipPayloadFetchedAtMs)
            );
            return true;
        }

        String body = httpGetPayloadText(AUTOFLIP_RELEASE_PAYLOAD_GZ_ENDPOINT, true, 12000);
        if (body == null || body.isEmpty() || !body.contains("\"schema\":\"autoflip.plugin_ranked_pool_payload.v1\""))
        {
            body = httpGetPayloadText(AUTOFLIP_RELEASE_PAYLOAD_JSON_ENDPOINT, false, 12000);
        }

        if (body == null || body.isEmpty() || !body.contains("\"schema\":\"autoflip.plugin_ranked_pool_payload.v1\""))
        {
            logAutoFlipVerbose("AUTOFLIP_PAYLOAD_CACHE loaded=false reason=invalid_or_empty");
            return false;
        }

        autoFlipPayloadJson = body;
        autoFlipPayloadFetchedAtMs = System.currentTimeMillis();
        autoFlipPayloadGeneratedAt = readJsonString(body, "generated_at", "");
        autoFlipPayloadHash = readJsonString(body, "payload_hash", "");
        autoFlipPayloadGeneratedAtMs = readJsonLong(body, "generated_at_ms", parseIsoTsMs(autoFlipPayloadGeneratedAt));
        autoFlipPayloadAlgorithmVersion = readJsonString(body, "algorithm_version", "");
        autoFlipPayloadExecutionPricingSource = readJsonString(body, "execution_pricing_source", "");
        autoFlipPayloadSourceCacheBuilder = readJsonString(body, "source_cache_builder", "");

        // Ranked payload order is the cache-priority order. Hydrate those first, then the user's To-Buy list.
        requestAutoFlipApiBuyPrices(extractAutoFlipRankedBuyPriorityIds(body, 160));
        requestAutoFlipApiBuyPrices(getAutoFlipToBuyItemIds());

        long ttlSeconds = readJsonLong(body, "ttl_seconds", 300L);
        autoFlipPayloadTtlMs = Math.max(60000L, ttlSeconds * 1000L);

        logAutoFlipVerbose(
            "AUTOFLIP_PAYLOAD_CACHE"
                + " loaded=true"
                + " schema=" + readJsonString(body, "schema", "unknown")
                + " generated_at=" + safe(autoFlipPayloadGeneratedAt)
                + " payload_hash=" + safe(autoFlipPayloadHash)
                + " ttl_ms=" + autoFlipPayloadTtlMs
                + " bytes=" + body.length()
        );

        return true;
    }

    private java.util.List<Integer> extractAutoFlipRankedBuyPriorityIds(String payload, int limit)
    {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        if (payload == null || payload.isEmpty() || limit <= 0) return ids;
        int perBundle = Math.max(1, limit / 2);
        appendAutoFlipRankedBuyPriorityIds(localPayloadExtractObjectField(payload, "f2p_bundle"), ids, perBundle);
        // The root payload is the members bundle; scan its root slices so nested F2P rows are not counted twice.
        appendAutoFlipRankedBuyPriorityIds(localPayloadExtractObjectField(payload, "slices"), ids, perBundle);
        return ids;
    }

    private void appendAutoFlipRankedBuyPriorityIds(String json, java.util.List<Integer> ids, int addLimit)
    {
        if (json == null || json.isEmpty() || ids == null || addLimit <= 0) return;
        int initialSize = ids.size();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\\"item_id\\\"\\s*:\\s*(\\d+)").matcher(json);
        while (matcher.find() && ids.size() - initialSize < addLimit)
        {
            try
            {
                int itemId = canonicalizeAutoFlipInventoryItemId(Integer.parseInt(matcher.group(1)));
                if (itemId > 0 && !ids.contains(itemId)) ids.add(itemId);
            }
            catch (NumberFormatException ignored)
            {
            }
        }
    }

    private String httpGetPayloadText(String url, boolean gzip, int timeoutMs)
    {
        try
        {
            Request request = new Request.Builder()
                .url(url)
                .header("Accept", gzip ? "application/gzip, application/json" : "application/json")
                .header("User-Agent", "AutoFlip-RuneLite-Plugin/1.0")
                .build();

            try (Response response = autoFlipHttpClient(timeoutMs).newCall(request).execute())
            {
                int status = response.code();
                if (response.body() == null)
                {
                    logAutoFlipVerbose("AUTOFLIP_PAYLOAD_HTTP url=" + url + " status=" + status + " bytes=0");
                    return "";
                }

                byte[] responseBytes = response.body().bytes();
                java.io.InputStream stream = new ByteArrayInputStream(responseBytes);
                if (gzip)
                {
                    try
                    {
                        stream = new java.util.zip.GZIPInputStream(new ByteArrayInputStream(responseBytes));
                    }
                    catch (Throwable gzipError)
                    {
                        logAutoFlipVerbose("AUTOFLIP_PAYLOAD_HTTP gzip_decode_failed=" + safe(gzipError.getMessage()) + " fallback_plain=true");
                        stream = new ByteArrayInputStream(responseBytes);
                    }
                }

                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)))
                {
                    StringBuilder builder = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        builder.append(line);
                    }

                    String body = builder.toString();
                    logAutoFlipVerbose("AUTOFLIP_PAYLOAD_HTTP url=" + url + " status=" + status + " bytes=" + body.length());
                    return response.isSuccessful() ? body : "";
                }
            }
        }
        catch (Exception error)
        {
            logAutoFlipVerbose("AUTOFLIP_PAYLOAD_HTTP url=" + url + " error=" + error.getClass().getSimpleName() + " message=" + safe(error.getMessage()));
            return "";
        }
    }
    private void refreshAutoFlipBoardCacheNow()
    {
        String url = buildAutoFlipRankedPoolUrl(100);
        String body = httpGetText(url, 6500);
        java.util.List<AutoFlipBoardCard> parsed = parseAutoFlipBoardCards(body);

        if (!parsed.isEmpty())
        {
            autoFlipBoardCards = snapshotAutoFlipBoardCards(parsed);
            notifyAutoFlipSidePanelRefresh();

            logAutoFlipVerbose(
                "AUTOFLIP_RANKED_POOL_LOAD"
                    + " loaded=true"
                    + " cards=" + parsed.size()
                    + " endpoint=" + AUTOFLIP_RELEASE_RANKED_POOL_ENDPOINT
                    + " schema=" + readJsonString(body, "schema", "unknown")
                    + " generated_at=" + readJsonString(body, "generated_at", "unknown")
                    + " matrix_key=" + readJsonString(body, "matrix_key", "unknown")
            );
        }
        else
        {
            logAutoFlipVerbose(
                "AUTOFLIP_RANKED_POOL_LOAD"
                    + " loaded=false"
                    + " cards=0"
                    + " endpoint=" + AUTOFLIP_RELEASE_RANKED_POOL_ENDPOINT
                    + " response_bytes=" + (body == null ? 0 : body.length())
                    + " fallback_used=true"
            );
        }
    }

    private String buildAutoFlipRankedPoolUrl(int limit)
    {
        long cash = client != null && client.isClientThread() ? getCurrentCashStackGp() : Math.max(0L, autoFlipLastObservedCashStackGp);
        long budget = autoFlipMenuUseCashStack ? cash : autoFlipMenuManualBudgetGp;

        if (budget <= 0L)
        {
            budget = autoFlipMenuManualBudgetGp > 0L ? autoFlipMenuManualBudgetGp : 2000000L;
        }

        int slots = getAutoFlipRankedPoolRequestedSlots();

        return AUTOFLIP_RELEASE_RANKED_POOL_ENDPOINT
            + "?hours_away=" + queryParam(String.valueOf(Math.max(1, autoFlipMenuHoursAway)))
            + "&budget_gp=" + queryParam(String.valueOf(Math.max(1L, budget)))
            + "&slots=" + queryParam(String.valueOf(slots))
            + "&preference_profile=" + queryParam("balanced")
            + "&limit=" + queryParam(String.valueOf(Math.max(8, Math.min(500, limit))));
    }

    private int getAutoFlipRankedPoolRequestedSlots()
    {
        int usableSlots = getAutoFlipUsableGeSlotCountForCurrentAccount();
        return Math.max(1, Math.min(8, usableSlots));
    }

    private String queryParam(String value)
    {
        try
        {
            return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8.name());
        }
        catch (Exception ignored)
        {
            return value == null ? "" : value;
        }
    }

    private String httpGetText(String url, int timeoutMs)
    {
        try
        {
            Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "AutoFlip-RuneLite-Plugin/1.0")
                .build();

            try (Response response = autoFlipHttpClient(timeoutMs).newCall(request).execute())
            {
                int status = response.code();
                if (response.body() == null)
                {
                    logAutoFlipVerbose("AUTOFLIP_RANKED_POOL_HTTP status=" + status + " bytes=0");
                    return "";
                }

                String body = response.body().string();
                logAutoFlipVerbose("AUTOFLIP_RANKED_POOL_HTTP status=" + status + " bytes=" + body.length());
                return response.isSuccessful() ? body : "";
            }
        }
        catch (Exception error)
        {
            logAutoFlipVerbose("AUTOFLIP_RANKED_POOL_HTTP error=" + error.getClass().getSimpleName() + " message=" + safe(error.getMessage()));
            return "";
        }
    }

    private OkHttpClient autoFlipHttpClient(int timeoutMs)
    {
        int timeout = Math.max(1000, timeoutMs);
        return okHttpClient.newBuilder()
            .connectTimeout(timeout, TimeUnit.MILLISECONDS)
            .readTimeout(timeout, TimeUnit.MILLISECONDS)
            .build();
    }

    private java.util.List<AutoFlipBoardCard> parseAutoFlipBoardCards(String json)
    {
        java.util.List<AutoFlipBoardCard> cards = new java.util.ArrayList<>();
        if (json == null || json.isEmpty())
        {
            return cards;
        }

        String schema = readJsonString(json, "schema", "");
        java.util.List<String> objects;

        if (json.contains("\"items\""))
        {
            objects = extractJsonArrayObjects(json, "items");
        }
        else if (json.contains("\"cards\""))
        {
            objects = extractJsonArrayObjects(json, "cards");
        }
        else
        {
            return cards;
        }

        int slotIndex = 0;
        for (String obj : objects)
        {
            String itemName = readJsonString(obj, "item_name", readJsonString(obj, "name", "Unknown item"));
            int itemId = readJsonInt(obj, "item_id", readJsonInt(obj, "id", 0));
            int quantity = readJsonInt(obj, "quantity", readJsonInt(obj, "suggested_quantity", 0));
            int maxQuantity = readJsonInt(obj, "max_quantity", quantity);
            int realisticQuantityCap = readJsonInt(
                obj,
                "realistic_quantity_cap",
                readJsonInt(obj, "strict_liquidity_quantity_cap", 0)
            );
            if (realisticQuantityCap > 0)
            {
                maxQuantity = Math.min(Math.max(1, maxQuantity), realisticQuantityCap);
            }
            if (maxQuantity > 0)
            {
                quantity = Math.min(Math.max(1, quantity), maxQuantity);
            }
            else
            {
                quantity = Math.max(1, quantity);
                maxQuantity = quantity;
            }
            long buyPriceGp = readJsonLong(obj, "buy_price_gp", readJsonLong(obj, "buy_price", 0L));
            long sellPriceGp = readJsonLong(obj, "sell_price_gp", readJsonLong(obj, "sell_price", 0L));

            long profitEach = readJsonLong(
                obj,
                "profit_each_gp",
                readJsonLong(obj, "profit_after_tax_per_item", 0L)
            );

            long totalProfit = readJsonLong(
                obj,
                "expected_profit_gp",
                readJsonLong(obj, "total_profit_gp", Math.max(0L, profitEach) * Math.max(0, quantity))
            );
            if (quantity > maxQuantity)
            {
                quantity = maxQuantity;
                totalProfit = Math.max(0L, profitEach) * Math.max(0, quantity);
            }

            String holdLabel = readJsonString(
                obj,
                "max_hold_time_label",
                readJsonString(obj, "time_label", readJsonString(obj, "expected_time_label", ""))
            );

            String riskLabel = readJsonString(
                obj,
                "risk_label",
                readJsonString(obj, "confidence", readJsonString(obj, "confidence_band", ""))
            );

            String reason = readJsonString(obj, "reason", readJsonString(obj, "short_reason", ""));
            String marketUrl = readJsonString(obj, "market_url", "");
            double confidence = readJsonDouble(obj, "confidence", readJsonDouble(obj, "model_score", 0.0D));

            AutoFlipBoardCard card = new AutoFlipBoardCard(
                readJsonInt(obj, "slot_index", slotIndex),
                itemId,
                itemName,
                quantity,
                maxQuantity,
                buyPriceGp,
                sellPriceGp,
                profitEach,
                totalProfit,
                holdLabel,
                riskLabel,
                reason,
                marketUrl,
                confidence,
                null
            );

            cards.add(card);
            slotIndex++;

            if (cards.size() >= 8)
            {
                break;
            }
        }

        if (!cards.isEmpty())
        {
            logAutoFlipVerbose(
                "AUTOFLIP_RANKED_POOL_PARSE"
                    + " schema=" + (schema.isEmpty() ? "unknown" : schema)
                    + " items_seen=" + objects.size()
                    + " cards_mapped=" + cards.size()
                    + " fallback_used=false"
            );
        }

        return cards;
    }

    private java.util.List<String> extractJsonArrayObjects(String json, String key)
    {
        java.util.List<String> objects = new java.util.ArrayList<>();
        int arrayStart = findJsonArrayStart(json, key);
        if (arrayStart < 0)
        {
            return objects;
        }

        boolean inString = false;
        boolean escaping = false;
        int objectDepth = 0;
        int objectStart = -1;

        for (int i = arrayStart + 1; i < json.length(); i++)
        {
            char ch = json.charAt(i);

            if (escaping)
            {
                escaping = false;
                continue;
            }

            if (ch == '\\' && inString)
            {
                escaping = true;
                continue;
            }

            if (ch == '"')
            {
                inString = !inString;
                continue;
            }

            if (inString)
            {
                continue;
            }

            if (ch == '{')
            {
                if (objectDepth == 0)
                {
                    objectStart = i;
                }
                objectDepth++;
            }
            else if (ch == '}')
            {
                objectDepth--;
                if (objectDepth == 0 && objectStart >= 0)
                {
                    objects.add(json.substring(objectStart, i + 1));
                    objectStart = -1;
                }
            }
            else if (ch == ']' && objectDepth == 0)
            {
                break;
            }
        }

        return objects;
    }

    private int findJsonArrayStart(String json, String key)
    {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\\[")
            .matcher(json);

        if (!matcher.find())
        {
            return -1;
        }

        return json.indexOf('[', matcher.start());
    }

    private String readJsonString(String obj, String key, String fallback)
    {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
            .matcher(obj);

        if (!matcher.find())
        {
            return fallback;
        }

        return jsonUnescape(matcher.group(1));
    }

    private String jsonUnescape(String raw)
    {
        if (raw == null || raw.isEmpty())
        {
            return raw == null ? "" : raw;
        }

        StringBuilder out = new StringBuilder(raw.length());
        boolean escaping = false;

        for (int i = 0; i < raw.length(); i++)
        {
            char ch = raw.charAt(i);

            if (!escaping)
            {
                if (ch == '\\')
                {
                    escaping = true;
                }
                else
                {
                    out.append(ch);
                }
                continue;
            }

            escaping = false;
            switch (ch)
            {
                case '"':
                    out.append('"');
                    break;
                case '\\':
                    out.append('\\');
                    break;
                case '/':
                    out.append('/');
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'u':
                    if (i + 4 < raw.length())
                    {
                        try
                        {
                            String hex = raw.substring(i + 1, i + 5);
                            out.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                        catch (Exception ignored)
                        {
                            out.append("\\u");
                        }
                    }
                    else
                    {
                        out.append("\\u");
                    }
                    break;
                default:
                    out.append(ch);
                    break;
            }
        }

        if (escaping)
        {
            out.append('\\');
        }

        return out.toString();
    }

    private int readJsonInt(String obj, String key, int fallback)
    {
        long value = readJsonLong(obj, key, fallback);
        if (value > Integer.MAX_VALUE)
        {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE)
        {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private int clampAutoFlipGpToInt(long value)
    {
        if (value <= 0L)
        {
            return 0;
        }
        if (value > Integer.MAX_VALUE)
        {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    private long readJsonLong(String obj, String key, long fallback)
    {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)")
            .matcher(obj);

        if (!matcher.find())
        {
            return fallback;
        }

        try
        {
            return Long.parseLong(matcher.group(1));
        }
        catch (NumberFormatException ignored)
        {
            return fallback;
        }
    }

    public String getAutoFlipItemNameForDebug(int itemId)
    {
        if (itemId <= 0 || itemManager == null)
        {
            return "";
        }

        try
        {
            net.runelite.api.ItemComposition composition = itemManager.getItemComposition(itemId);
            if (composition == null)
            {
                return "";
            }

            String name = composition.getName();
            return name == null ? "" : name.trim();
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("getAutoFlipItemNameForDebug", error);
            return "";
        }
    }

    private boolean readJsonBool(String obj, String key, boolean fallback)
    {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(true|false)", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(obj);

        if (!matcher.find())
        {
            return fallback;
        }

        return Boolean.parseBoolean(matcher.group(1));
    }

    private double readJsonDouble(String obj, String key, double fallback)
    {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
            .matcher(obj);

        if (!matcher.find())
        {
            return fallback;
        }

        try
        {
            return Double.parseDouble(matcher.group(1));
        }
        catch (NumberFormatException ignored)
        {
            return fallback;
        }
    }

    public java.util.List<Integer> getAutoFlipAvailableGeSlotIndicesForOverlay()
    {
        boolean freeToPlay = isAutoFlipFreeToPlayAccount();
        int slotCount = getAutoFlipUsableGeSlotCountForCurrentAccount(freeToPlay);
        java.util.List<Integer> available = new java.util.ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++)
        {
            if (isAutoFlipGeSlotAvailableForRecommendation(slot))
            {
                available.add(slot);
            }
        }

        logAutoFlipVerbose(String.format(
            "AUTOFLIP_AVAILABLE_SLOTS mode=%s count=%d slots=%s",
            freeToPlay ? "F2P" : "MEMBERS",
            available.size(),
            available));

        return java.util.Collections.unmodifiableList(available);
    }

    public int getAutoFlipUsableGeSlotCountForCurrentAccount()
    {
        return getAutoFlipUsableGeSlotCountForCurrentAccount(isAutoFlipFreeToPlayAccount());
    }

    private int getAutoFlipUsableGeSlotCountForCurrentAccount(boolean freeToPlay)
    {
        return freeToPlay ? 3 : 8;
    }

    public boolean isAutoFlipGeSlotUsableForCurrentAccount(int slot)
    {
        return slot >= 0 && slot < getAutoFlipUsableGeSlotCountForCurrentAccount();
    }

    public boolean isAutoFlipGeSlotAvailableForRecommendation(int slot)
    {
        try
        {
            if (!isAutoFlipGeSlotUsableForCurrentAccount(slot))
            {
                logAutoFlipVerbose(
                    "AUTOFLIP_SLOT_UNAVAILABLE"
                        + " slot=" + slot
                        + " reason=membership_restricted"
                        + " usable_slots=" + getAutoFlipUsableGeSlotCountForCurrentAccount()
                );
                return false;
            }

            AutoFlipGeOfferSlotSnapshot snapshot = getAutoFlipGeOfferSlotSnapshot(slot);
            if (!snapshot.empty)
            {
                logAutoFlipVerbose("AUTOFLIP_SLOT_UNAVAILABLE slot=" + slot + " item_id=" + snapshot.itemId + " state=" + snapshot.state);
            }

            return snapshot.empty;
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("isAutoFlipGeSlotAvailableForRecommendation", error);
            return true;
        }
    }

    public long getAutoFlipOverlayBudgetGp()
    {
        try
        {
            // Summary bar calls this every frame. Do not call getAutoFlipEffectiveBudgetGp()
            // because that method logs AUTOFLIP_EFFECTIVE_BUDGET diagnostics.
            long cachedCash = autoFlipLastObservedCashStackGp;
            long liveCash = 0L;

            if (autoFlipMenuUseCashStack && cachedCash <= 0L)
            {
                liveCash = client != null && client.isClientThread() ? getCurrentCashStackGp() : 0L;
                if (liveCash > 0L)
                {
                    autoFlipLastObservedCashStackGp = liveCash;
                    cachedCash = liveCash;
                }
            }

            long budget = autoFlipMenuUseCashStack
                ? (cachedCash > 0L ? cachedCash : liveCash)
                : (autoFlipMenuManualBudgetGp > 0L ? autoFlipMenuManualBudgetGp : 2000000L);

            return Math.max(1L, budget);
        }
        catch (Throwable ignored)
        {
            return Math.max(1L, autoFlipMenuManualBudgetGp);
        }
    }

    private void triggerAutoFlipOptimizeBoard()
    {
        synchronized (this)
        {
            if (autoFlipOptimizeBoardInProgress)
            {
                logAutoFlipVerbose("AUTOFLIP_LOCAL_OPTIMIZE skipped=busy");
                return;
            }
            autoFlipOptimizeBoardInProgress = true;
        }

        Thread worker = new Thread(() ->
        {
            autoFlipBoardMutationLock.lock();
            try
            {
                pollGrandExchangeOffers();

                if (!fetchAutoFlipPayloadIfNeeded(false))
                {
                    logAutoFlipVerbose("AUTOFLIP_LOCAL_OPTIMIZE loaded=false reason=payload_unavailable");
                    return;
                }

                clearAutoFlipSkippedItemIds("optimize_board");
                clearAutoFlipRetiredBoardSlots("optimize_board");

                java.util.List<AutoFlipBoardCard> cards = buildAutoFlipBoardFromLocalPayload();
                if (cards == null || cards.isEmpty())
                {
                    logAutoFlipVerbose("AUTOFLIP_LOCAL_OPTIMIZE loaded=false cards=0");
                    return;
                }

                clearAutoFlipCardActionBounds();
                clearAutoFlipCardBlockBounds();
                cards = filterAutoFlipBoardCardsForAvailableSlots(cards, "optimize_board");
                cards = localPayloadRescaleBoardCards(cards, getAutoFlipEffectiveBudgetGp());
                captureAutoFlipCanonicalBoardCards(cards, "optimize_board");
                autoFlipBoardCards = snapshotAutoFlipBoardCards(cards);
                synchronized (this)
                {
                    for (AutoFlipBoardCard card : cards)
                    {
                        if (card != null && card.getItemId() > 0)
                        {
                            rememberAutoFlipSkippedItem(card.getItemId());
                        }
                    }
                }
                notifyAutoFlipSidePanelRefresh();

                logAutoFlipVerbose(
                    "AUTOFLIP_LOCAL_OPTIMIZE"
                        + " loaded=true"
                        + " cards=" + cards.size()
                        + " budget_planned_gp=" + getAutoFlipBoardBudgetPlannedGp()
                        + " expected_profit_gp=" + getAutoFlipBoardTotalExpectedProfitGp()
                );
            }
            catch (Throwable error)
            {
                logAutoFlipUiError("local_optimize_board", error);
            }
            finally
            {
                autoFlipOptimizeBoardInProgress = false;
                autoFlipBoardMutationLock.unlock();
            }
        }, "autoflip-local-optimize-board");

        worker.setDaemon(true);
        worker.start();

        logAutoFlipMenuEvent("optimize_board_local_payload");
    }

    private void triggerAutoFlipRefreshBoard()
    {
        Thread worker = new Thread(() ->
        {
            try
            {
                java.util.List<AutoFlipBoardCard> visibleCards = getAutoFlipVisibleBoardCardsSnapshot();
                int skipped = 0;
                for (AutoFlipBoardCard card : visibleCards)
                {
                    if (card != null && card.getItemId() > 0)
                    {
                        performAutoFlipSkipItemRefreshNow(card.getSlotIndex(), card.getItemId(), "refresh_board");
                        skipped++;
                    }
                }

                notifyAutoFlipSidePanelRefresh();

                logAutoFlipVerbose(
                    "AUTOFLIP_LOCAL_REFRESH_BOARD"
                        + " loaded=true"
                        + " simulated_skip_count=" + skipped
                        + " cards=" + getAutoFlipVisibleBoardCardsSnapshot().size()
                        + " budget_planned_gp=" + getAutoFlipBoardBudgetPlannedGp()
                        + " expected_profit_gp=" + getAutoFlipBoardTotalExpectedProfitGp()
                );
            }
            catch (Throwable error)
            {
                logAutoFlipUiError("local_refresh_board", error);
            }
        }, "autoflip-local-refresh-board");

        worker.setDaemon(true);
        worker.start();

        logAutoFlipMenuEvent("refresh_board_local_payload");
    }

    private void performAutoFlipSkipItemRefreshNow(int slotIndex, int itemId, String source)
    {
        autoFlipBoardMutationLock.lock();
        try
        {
            if (!fetchAutoFlipPayloadIfNeeded(false))
            {
                logAutoFlipVerbose(
                    "AUTOFLIP_LOCAL_REFRESH_ITEM loaded=false reason=payload_unavailable"
                        + " slot=" + slotIndex
                        + " item_id=" + itemId
                        + " source=" + safe(source)
                );
                return;
            }

            rememberAutoFlipSkippedItem(itemId);

            java.util.List<AutoFlipBoardCard> cards = localPayloadReplaceBoardCard(slotIndex, itemId);
            if (cards == null || cards.isEmpty())
            {
                logAutoFlipVerbose(
                    "AUTOFLIP_LOCAL_REFRESH_ITEM loaded=false cards=0"
                        + " slot=" + slotIndex
                        + " item_id=" + itemId
                        + " source=" + safe(source)
                );
                return;
            }

            clearAutoFlipCardActionBounds();
            clearAutoFlipCardBlockBounds();
            cards = filterAutoFlipBoardCardsForAvailableSlots(cards, "refresh_item");
            cards = localPayloadRescaleBoardCards(cards, getAutoFlipEffectiveBudgetGp());
            autoFlipBoardCards = snapshotAutoFlipBoardCards(cards);
            captureAutoFlipCanonicalBoardCards(cards, "refresh_item");

            notifyAutoFlipSidePanelRefresh();

            logAutoFlipVerbose(
                "AUTOFLIP_LOCAL_REFRESH_ITEM"
                    + " loaded=true"
                    + " cards=" + cards.size()
                    + " slot=" + slotIndex
                    + " item_id=" + itemId
                    + " source=" + safe(source)
                    + " budget_planned_gp=" + getAutoFlipBoardBudgetPlannedGp()
                    + " expected_profit_gp=" + getAutoFlipBoardTotalExpectedProfitGp()
            );
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("local_refresh_item_sync", error);
        }
        finally
        {
            autoFlipBoardMutationLock.unlock();
        }
    }
    private void triggerAutoFlipRefreshItem(int slotIndex, int itemId)
    {
        // AUTOFLIP_LOCAL_REFRESH_ITEM_FROM_PAYLOAD_V1
        // Refresh Item is local replacement from cached ranked_universe.
        // It does not call /api/plugin/ranked-pool.
        Thread worker = new Thread(() ->
        {
            autoFlipBoardMutationLock.lock();
            try
            {
                if (!fetchAutoFlipPayloadIfNeeded(false))
                {
                    logAutoFlipVerbose("AUTOFLIP_LOCAL_REFRESH_ITEM loaded=false reason=payload_unavailable slot=" + slotIndex + " item_id=" + itemId);
                    return;
                }

                rememberAutoFlipSkippedItem(itemId);

                java.util.List<AutoFlipBoardCard> cards = buildAutoFlipBoardFromLocalPayload();
                if (cards == null || cards.isEmpty())
                {
                    logAutoFlipVerbose("AUTOFLIP_LOCAL_REFRESH_ITEM loaded=false cards=0 slot=" + slotIndex + " item_id=" + itemId);
                    return;
                }

                clearAutoFlipCardActionBounds();
                clearAutoFlipCardBlockBounds();
                cards = filterAutoFlipBoardCardsForAvailableSlots(cards, "refresh_item");
                autoFlipBoardCards = snapshotAutoFlipBoardCards(cards);
                captureAutoFlipCanonicalBoardCards(cards, "refresh_item");
                notifyAutoFlipSidePanelRefresh();

                logAutoFlipVerbose(
                    "AUTOFLIP_LOCAL_REFRESH_ITEM"
                        + " loaded=true"
                        + " cards=" + cards.size()
                        + " slot=" + slotIndex
                        + " item_id=" + itemId
                        + " budget_planned_gp=" + getAutoFlipBoardBudgetPlannedGp()
                        + " expected_profit_gp=" + getAutoFlipBoardTotalExpectedProfitGp()
                );
            }
            catch (Throwable error)
            {
                logAutoFlipUiError("local_refresh_item", error);
            }
            finally
            {
                autoFlipBoardMutationLock.unlock();
            }
        }, "autoflip-local-refresh-item");

        worker.setDaemon(true);
        worker.start();

        logAutoFlipMenuEvent("refresh_item_local_payload_slot_" + slotIndex + "_item_" + itemId);
    }

    private void triggerAutoFlipSkipItem(int slotIndex, int itemId)
    {
        // AUTOFLIP_LOCAL_SKIP_ITEM_FROM_PAYLOAD_V1
        // Skip means rotate this card out and replace it from the cached ranked universe.
        // Reuse the same local replacement path as refresh so the UI stays instant.
        rememberAutoFlipSkippedItem(itemId);
        logAutoFlipVerbose(
            "AUTOFLIP_LOCAL_SKIP_ITEM"
                + " slot=" + slotIndex
                + " item_id=" + itemId
        );
        performAutoFlipSkipItemRefreshNow(slotIndex, itemId, "skip_item");
        logAutoFlipMenuEvent("skip_item_local_payload_slot_" + slotIndex + "_item_" + itemId);
    }

    private java.util.List<AutoFlipBoardCard> buildAutoFlipBoardFromLocalPayload()
    {
        String payload = autoFlipPayloadJson;
        if (payload == null || payload.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        String risk = "balanced";
        int hours = getAutoFlipPayloadHourBucket(autoFlipMenuHoursAway);
        long budget = getAutoFlipEffectiveBudgetGp();
        String budgetBand = getAutoFlipPayloadBudgetBand(budget);
        java.util.List<Integer> availableSlots = new java.util.ArrayList<>(getAutoFlipAvailableGeSlotIndicesForOverlay());
        int slots = Math.max(1, Math.min(8, availableSlots.size()));
        String profile = "balanced";
        java.util.Set<Integer> skippedItemIds = getAutoFlipSkippedItemIdsSnapshot();

        logAutoFlipVerbose("AUTOFLIP_AVAILABLE_SLOTS count=" + availableSlots.size() + " slots=" + availableSlots);

        java.util.List<Integer> filteredAvailableSlots = new java.util.ArrayList<>();
        for (Integer slot : availableSlots)
        {
            if (slot == null)
            {
                continue;
            }

            if (isAutoFlipBoardSlotRetired(slot))
            {
                logAutoFlipVerbose("AUTOFLIP_BOARD_SLOT_RETIRED_SKIP slot=" + slot + " reason=retired_until_refresh");
                continue;
            }

            filteredAvailableSlots.add(slot);
        }

        if (filteredAvailableSlots.isEmpty())
        {
            logAutoFlipVerbose("AUTOFLIP_LOCAL_SELECT retired_slots_only=true");
            return new java.util.ArrayList<>(java.util.Collections.nCopies(8, null));
        }

        availableSlots = filteredAvailableSlots;
        slots = Math.max(1, Math.min(8, availableSlots.size()));

        java.util.List<AutoFlipBoardCard> sellPriorityCards = new java.util.ArrayList<>();
        java.util.List<AutoFlipBoardCard> toBuyPriorityCards = new java.util.ArrayList<>();
        java.util.Set<Integer> priorityItemIds = new java.util.HashSet<>();
        java.util.Set<Integer> reservedSlots = new java.util.HashSet<>();
        long toBuyReservedCapitalGp = 0L;
        java.util.List<AutoFlipPayloadRow> universeRows = localPayloadCurrentRankedUniverseRows();
        java.util.List<AutoFlipInventoryItem> holdItems = getAutoFlipHoldInventorySnapshotInDisplayOrder();
        for (AutoFlipInventoryItem holdItem : holdItems)
        {
            if (holdItem == null || holdItem.getItemId() <= 0 || holdItem.getQuantity() <= 0)
            {
                continue;
            }

            Integer chosenSlot = findNextAvailableGeSlotIndex(availableSlots, reservedSlots, null);

            if (chosenSlot == null)
            {
                break;
            }

            reservedSlots.add(chosenSlot);
            priorityItemIds.add(canonicalizeAutoFlipInventoryItemId(holdItem.getItemId()));

            String itemName = holdItem.getItemName();
            if (itemName == null || itemName.trim().isEmpty())
            {
                itemName = resolveAutoFlipItemName(holdItem.getItemId(), "Item " + holdItem.getItemId());
            }

            sellPriorityCards.add(new AutoFlipBoardCard(
                chosenSlot,
                holdItem.getItemId(),
                itemName,
                holdItem.getQuantity(),
                holdItem.getQuantity(),
                0L,
                Math.max(0L, assessAutoFlipItemValueGp(holdItem.getItemId())),
                0L,
                0L,
                "sell now",
                "SELL",
                "sell: default hold inventory prioritized for sale",
                getAutoFlipMarketItemUrl(holdItem.getItemId(), itemName),
                1.0D,
                null
            ));
        }

        // Priority contract: sell held items first, then user To-Buy watches, then
        // fill the remaining slots from the ranked AutoFlip universe.
        for (AutoFlipToBuyItem toBuyItem : getAutoFlipToBuySnapshot())
        {
            if (toBuyItem == null || toBuyItem.getItemId() <= 0)
            {
                continue;
            }

            String status = toBuyItem.getStatus() == null
                ? ""
                : toBuyItem.getStatus().trim().toUpperCase(java.util.Locale.ROOT);
            if ("SOLD".equals(status) || "REMOVED".equals(status))
            {
                continue;
            }

            int canonicalItemId = canonicalizeAutoFlipInventoryItemId(toBuyItem.getItemId());
            AutoFlipPayloadRow payloadRow = findAutoFlipPayloadRowByCanonicalItemId(universeRows, canonicalItemId);
            boolean accountEligible = isAutoFlipItemAllowedForCurrentAccount(toBuyItem.getItemId());
            boolean activeOfferPresent = isAutoFlipActiveOfferPresent(toBuyItem.getItemId());
            boolean duplicatePriority = priorityItemIds.contains(canonicalItemId);
            // A To-Buy watch is an explicit user priority. Keep it on the board even
            // when the ordinary ranked-item membership filter would reject it; the watchlist
            // owns this priority decision.
            if (!shouldReserveAutoFlipPriorityItem(
                isAutoFlipPriorityCardAllowedForCurrentAccount("TO_BUY", accountEligible),
                activeOfferPresent,
                duplicatePriority))
            {
                logAutoFlipVerbose(
                    "AUTOFLIP_TO_BUY_PRIORITY_SKIP"
                        + " item_id=" + toBuyItem.getItemId()
                        + " item_name=" + safe(toBuyItem.getItemName())
                        + " account_eligible=" + accountEligible
                        + " active_offer_present=" + activeOfferPresent
                        + " duplicate_priority=" + duplicatePriority
                );
                continue;
            }

            Integer chosenSlot = findNextAvailableGeSlotIndex(availableSlots, reservedSlots, null);
            if (chosenSlot == null)
            {
                break;
            }

            long targetOrRecommendedGp = resolveAutoFlipPreferredBuyAutofillGp(
                toBuyItem.getEffectiveBuyThresholdGp(),
                toBuyItem.getTargetBuyPriceGp()
            );
            if (targetOrRecommendedGp <= 0L && payloadRow != null)
            {
                targetOrRecommendedGp = Math.max(0L, payloadRow.buyPriceGp);
            }
            long toBuyTotalCostGp = Math.max(0L, targetOrRecommendedGp) * Math.max(1, toBuyItem.getQuantity());
            if (targetOrRecommendedGp <= 0L || toBuyTotalCostGp <= 0L)
            {
                continue;
            }
            if (toBuyTotalCostGp > Math.max(0L, budget - toBuyReservedCapitalGp))
            {
                continue;
            }

            reservedSlots.add(chosenSlot);
            priorityItemIds.add(canonicalItemId);
            toBuyReservedCapitalGp += toBuyTotalCostGp;
            String itemName = toBuyItem.getItemName();
            if ((itemName == null || itemName.trim().isEmpty()) && payloadRow != null)
            {
                itemName = payloadRow.itemName;
            }
            if (itemName == null || itemName.trim().isEmpty())
            {
                itemName = resolveAutoFlipItemName(toBuyItem.getItemId(), "Item " + toBuyItem.getItemId());
            }
            long sellPriceGp = Math.max(0L, toBuyItem.getCurrentSellPriceGp());
            if (sellPriceGp <= 0L && payloadRow != null)
            {
                sellPriceGp = Math.max(0L, payloadRow.sellPriceGp);
            }
            toBuyPriorityCards.add(new AutoFlipBoardCard(
                chosenSlot,
                toBuyItem.getItemId(),
                itemName,
                Math.max(1, toBuyItem.getQuantity()),
                Math.max(1, toBuyItem.getQuantity()),
                targetOrRecommendedGp,
                sellPriceGp,
                0L,
                0L,
                "buy now",
                "TO_BUY",
                "to-buy: user watch prioritized",
                getAutoFlipMarketItemUrl(toBuyItem.getItemId(), itemName),
                1.0D,
                null
            ));
            logAutoFlipVerbose(
                "AUTOFLIP_TO_BUY_PRIORITY_CARD"
                    + " slot=" + chosenSlot
                    + " item_id=" + toBuyItem.getItemId()
                    + " item_name=" + safe(itemName)
                    + " target_each_gp=" + targetOrRecommendedGp
                    + " total_cost_gp=" + toBuyTotalCostGp
                    + " custom_target=" + (toBuyItem.getTargetBuyPriceGp() > 0L)
                    + " payload_price_fallback=" + (payloadRow != null)
            );
        }

        int buySlots = Math.max(0, availableSlots.size() - reservedSlots.size());
        if (buySlots <= 0)
        {
            java.util.List<AutoFlipBoardCard> sellOnlyCards = new java.util.ArrayList<>(java.util.Collections.nCopies(8, null));
            for (AutoFlipBoardCard sellCard : sellPriorityCards)
            {
                if (sellCard == null)
                {
                    continue;
                }

                int slot = sellCard.getSlotIndex();
                if (slot >= 0 && slot < sellOnlyCards.size())
                {
                    sellOnlyCards.set(slot, sellCard);
                }
            }
            for (AutoFlipBoardCard toBuyCard : toBuyPriorityCards)
            {
                if (toBuyCard != null && toBuyCard.getSlotIndex() >= 0 && toBuyCard.getSlotIndex() < sellOnlyCards.size())
                {
                    sellOnlyCards.set(toBuyCard.getSlotIndex(), toBuyCard);
                }
            }

            logAutoFlipVerbose(
                "AUTOFLIP_LOCAL_SELECT"
                    + " cards=" + sellOnlyCards.size()
                    + " slice_key=" + risk + ":" + hours + "h:" + budgetBand
                    + " matrix_key=sell_only"
                    + " budget_gp=" + budget
                    + " slots=0"
                    + " profile=" + profile
            );

            return sellOnlyCards;
        }

        // Priority cards occupy GE slots, but they must not consume ranked candidates.
        // Select and scale only the ranked rows that can actually render in open slots.
        slots = Math.max(1, Math.min(8, buySlots));

        String sliceKey = risk + ":" + hours + "h:" + budgetBand;
        if (universeRows == null || universeRows.isEmpty())
        {
            logAutoFlipVerbose("AUTOFLIP_LOCAL_SELECT missing_ranked_universe slice=" + sliceKey + " budget=" + budget + " slots=" + slots);
            java.util.List<AutoFlipBoardCard> priorityOnlyCards = new java.util.ArrayList<>(java.util.Collections.nCopies(8, null));
            for (AutoFlipBoardCard sellCard : sellPriorityCards)
            {
                if (sellCard != null && sellCard.getSlotIndex() >= 0 && sellCard.getSlotIndex() < priorityOnlyCards.size())
                {
                    priorityOnlyCards.set(sellCard.getSlotIndex(), sellCard);
                }
            }
            for (AutoFlipBoardCard toBuyCard : toBuyPriorityCards)
            {
                if (toBuyCard != null && toBuyCard.getSlotIndex() >= 0 && toBuyCard.getSlotIndex() < priorityOnlyCards.size())
                {
                    priorityOnlyCards.set(toBuyCard.getSlotIndex(), toBuyCard);
                }
            }
            return priorityOnlyCards;
        }

        java.util.Set<Integer> selectedItemIds = new java.util.HashSet<>();
        java.util.List<AutoFlipPayloadRow> rows = new java.util.ArrayList<>();
        for (AutoFlipPayloadRow universeRow : universeRows)
        {
            if (rows.size() >= slots)
            {
                break;
            }

            if (universeRow == null || universeRow.itemId <= 0 || universeRow.quantity <= 0)
            {
                continue;
            }

            if (!isAutoFlipItemAllowedForCurrentAccount(universeRow.itemId))
            {
                continue;
            }

            int canonicalItemId = canonicalizeAutoFlipInventoryItemId(universeRow.itemId);
            if (skippedItemIds.contains(canonicalItemId))
            {
                continue;
            }
            if (priorityItemIds.contains(canonicalItemId) || selectedItemIds.contains(universeRow.itemId))
            {
                continue;
            }

            rows.add(universeRow);
            selectedItemIds.add(universeRow.itemId);
        }

        if (rows.size() < slots && !skippedItemIds.isEmpty())
        {
            clearAutoFlipSkippedItemIds("refresh_wrap_all_items_skipped");
            skippedItemIds = getAutoFlipSkippedItemIdsSnapshot();
            rows.clear();
            selectedItemIds.clear();

            for (AutoFlipPayloadRow universeRow : universeRows)
            {
                if (rows.size() >= slots)
                {
                    break;
                }

                if (universeRow == null || universeRow.itemId <= 0 || universeRow.quantity <= 0)
                {
                    continue;
                }

                if (!isAutoFlipItemAllowedForCurrentAccount(universeRow.itemId))
                {
                    continue;
                }

                int canonicalItemId = canonicalizeAutoFlipInventoryItemId(universeRow.itemId);
                if (priorityItemIds.contains(canonicalItemId) || selectedItemIds.contains(universeRow.itemId))
                {
                    continue;
                }

                rows.add(universeRow);
                selectedItemIds.add(universeRow.itemId);
            }
        }

        logAutoFlipVerbose(
            "AUTOFLIP_LOCAL_SELECT_RANKED_PICKED"
                + " rows=" + summarizeAutoFlipPayloadRows(rows)
                + " priority_item_ids=" + priorityItemIds
                + " target_slots=" + slots
                + " budget_gp=" + budget
                + " slice_key=" + sliceKey
        );

        logAutoFlipVerbose(
            "AUTOFLIP_LOCAL_SELECT_RANKED"
                + " universe_rows=" + universeRows.size()
                + " selected_rows=" + rows.size()
                + " target_slots=" + slots
                + " budget_gp=" + budget
                + " slice_key=" + sliceKey
                + " profile=" + profile
        );

        rows = localPayloadScaleRowsToBudget(rows, Math.max(0L, budget - toBuyReservedCapitalGp));

        logAutoFlipVerbose(
            "AUTOFLIP_LOCAL_SELECT_RANKED_SCALED"
                + " rows=" + summarizeAutoFlipPayloadRows(rows)
                + " to_buy_reserved_gp=" + toBuyReservedCapitalGp
                + " budget_gp=" + budget
                + " slice_key=" + sliceKey
        );

        java.util.Map<Integer, AutoFlipPayloadRow> rowBySlot = new java.util.HashMap<>();
        int rowIndex = 0;
        for (Integer slot : availableSlots)
        {
            if (slot == null || reservedSlots.contains(slot))
            {
                continue;
            }

            while (rowIndex < rows.size() && rows.get(rowIndex) == null)
            {
                rowIndex++;
            }

            if (rowIndex >= rows.size())
            {
                break;
            }

            rowBySlot.put(slot, rows.get(rowIndex));
            rowIndex++;
        }

        java.util.List<AutoFlipBoardCard> cards = new java.util.ArrayList<>(java.util.Collections.nCopies(8, null));
        for (AutoFlipBoardCard sellCard : sellPriorityCards)
        {
            if (sellCard != null)
            {
                int slot = sellCard.getSlotIndex();
                if (slot >= 0 && slot < cards.size())
                {
                    cards.set(slot, sellCard);
                }
            }
        }
        for (AutoFlipBoardCard toBuyCard : toBuyPriorityCards)
        {
            if (toBuyCard != null)
            {
                int slot = toBuyCard.getSlotIndex();
                if (slot >= 0 && slot < cards.size())
                {
                    cards.set(slot, toBuyCard);
                }
            }
        }

        for (Integer slot : availableSlots)
        {
            if (slot == null || slot < 0 || slot >= cards.size())
            {
                continue;
            }

            if (reservedSlots.contains(slot))
            {
                continue;
            }

            AutoFlipPayloadRow row = rowBySlot.get(slot);
            if (row != null)
            {
                cards.set(slot, localPayloadCardFromRow(row, slot));
            }
        }

        logAutoFlipVerbose(
                "AUTOFLIP_LOCAL_SELECT_CARD_MAP"
                    + " row_by_slot=" + summarizeAutoFlipRowBySlot(rowBySlot)
                    + " ranked_rows=" + summarizeAutoFlipPayloadRows(rows)
                    + " reserved_slots=" + reservedSlots
                    + " available_slots=" + availableSlots
        );

        logAutoFlipVerbose(
            "AUTOFLIP_LOCAL_SELECT_FINAL_CARDS"
                + " cards=" + summarizeAutoFlipBoardCards(cards)
                + " available_slots=" + availableSlots
                + " reserved_slots=" + reservedSlots
        );

        logAutoFlipVerbose(
            "AUTOFLIP_LOCAL_SELECT"
                + " cards=" + cards.size()
                + " slice_key=" + sliceKey
                + " matrix_key=ranked_universe"
                + " budget_gp=" + budget
                + " slots=" + slots
                + " profile=" + profile
        );

        return cards;
    }

    static boolean shouldReserveAutoFlipPriorityItem(
        boolean accountEligible,
        boolean activeOfferPresent,
        boolean duplicatePriority)
    {
        return accountEligible && !activeOfferPresent && !duplicatePriority;
    }

    private Integer findNextAvailableGeSlotIndex(
        java.util.List<Integer> availableSlots,
        java.util.Set<Integer> reservedSlots,
        Integer preferredSlot
    )
    {
        if (availableSlots == null || availableSlots.isEmpty())
        {
            return null;
        }

        if (preferredSlot != null && preferredSlot >= 0)
        {
            for (Integer slot : availableSlots)
            {
                if (slot != null && slot.intValue() == preferredSlot.intValue() && (reservedSlots == null || !reservedSlots.contains(slot)))
                {
                    return slot;
                }
            }

            boolean pastPreferred = false;
            for (Integer slot : availableSlots)
            {
                if (slot == null)
                {
                    continue;
                }

                if (!pastPreferred)
                {
                    if (slot.intValue() == preferredSlot.intValue())
                    {
                        pastPreferred = true;
                    }
                    continue;
                }

                if (reservedSlots == null || !reservedSlots.contains(slot))
                {
                    return slot;
                }
            }
        }

        for (Integer slot : availableSlots)
        {
            if (slot != null && (reservedSlots == null || !reservedSlots.contains(slot)))
            {
                return slot;
            }
        }

        return null;
    }

    public boolean isAutoFlipItemAllowedForCurrentAccount(int itemId)
    {
        if (itemId <= 0)
        {
            return false;
        }

        if (!isAutoFlipFreeToPlayAccount())
        {
            return true;
        }

        return !isAutoFlipMembersOnlyItemCachedForDebug(itemId);
    }

    public boolean isAutoFlipFreeToPlayAccount()
    {
        try
        {
            if (client == null)
            {
                return true;
            }

            java.util.EnumSet<net.runelite.api.WorldType> worldTypes = client.getWorldType();
            if (worldTypes != null)
            {
                return !worldTypes.contains(net.runelite.api.WorldType.MEMBERS);
            }

            return true;
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("isAutoFlipFreeToPlayAccount", error);
            return false;
        }
    }

    private boolean isAutoFlipMembersOnlyItem(int itemId)
    {
        try
        {
            if (itemManager == null || itemId <= 0)
            {
                return false;
            }

            net.runelite.api.ItemComposition composition = itemManager.getItemComposition(itemId);
            return composition != null && composition.isMembers();
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("isAutoFlipMembersOnlyItem", error);
            return false;
        }
    }

    private boolean isAutoFlipMembersOnlyItemCachedForDebug(int itemId)
    {
        if (itemId <= 0)
        {
            return false;
        }

        Boolean cached = autoFlipMembersOnlyCacheByItemId.get(itemId);
        if (cached != null)
        {
            return cached.booleanValue();
        }

        if (client != null && !client.isClientThread())
        {
            // The ranked payload already splits F2P vs members pools. Avoid calling
            // ItemManager composition APIs from background warm workers.
            return false;
        }

        try
        {
            boolean membersOnly = isAutoFlipMembersOnlyItem(itemId);
            autoFlipMembersOnlyCacheByItemId.put(itemId, membersOnly);
            return membersOnly;
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    private long getAutoFlipEffectiveBudgetGp()
    {
        long cachedCash = autoFlipLastObservedCashStackGp;
        long liveCash = 0L;

        if (cachedCash <= 0L)
        {
            liveCash = client != null && client.isClientThread() ? getCurrentCashStackGp() : 0L;
            if (liveCash > 0L)
            {
                autoFlipLastObservedCashStackGp = liveCash;
                cachedCash = liveCash;
            }
        }

        long budget;
        if (autoFlipMenuUseCashStack)
        {
            // Important: when "Use current cash stack" is checked, never fall back to stale manual 5m.
            budget = cachedCash > 0L ? cachedCash : liveCash;
        }
        else
        {
            budget = autoFlipMenuManualBudgetGp > 0L ? autoFlipMenuManualBudgetGp : 2000000L;
        }

        budget = Math.max(1L, budget);

        logAutoFlipVerbose(
            "AUTOFLIP_EFFECTIVE_BUDGET"
                + " use_cash=" + autoFlipMenuUseCashStack
                + " cached_cash_gp=" + cachedCash
                + " live_cash_gp=" + liveCash
                + " manual_budget_gp=" + autoFlipMenuManualBudgetGp
                + " effective_budget_gp=" + budget
        );

        return budget;
    }

    private int getAutoFlipPayloadHourBucket(int hoursAway)
    {
        int h = Math.max(1, hoursAway);
        if (h <= 4) return 4;
        if (h <= 8) return 8;
        if (h <= 12) return 12;
        return 24;
    }

    private String getAutoFlipPayloadBudgetBand(long budget)
    {
        if (budget < 75000000L) return "low";
        if (budget < 175000000L) return "mid";
        if (budget < 400000000L) return "large";
        return "whale";
    }

    private String localPayloadSelectMatrixBoard(String matrixObject, long budget, int slots, String profile)
    {
        if (matrixObject == null || matrixObject.isEmpty())
        {
            return "";
        }

        java.util.List<AutoFlipJsonMember> members = localPayloadObjectMembers(matrixObject);
        AutoFlipJsonMember best = null;
        long bestSlotDelta = Long.MAX_VALUE;
        long bestBudgetDelta = Long.MAX_VALUE;

        for (AutoFlipJsonMember member : members)
        {
            String board = member.value;
            if (board == null || board.isEmpty())
            {
                continue;
            }

            String boardProfile = readJsonString(board, "preference_profile", "");
            if (!profile.equalsIgnoreCase(boardProfile))
            {
                continue;
            }

            int boardSlots = readJsonInt(board, "slot_count", 0);
            long boardBudget = readJsonLong(board, "budget_gp", 0L);
            if (boardSlots <= 0 || boardBudget <= 0L)
            {
                continue;
            }

            long slotDelta = Math.abs((long) boardSlots - Math.max(1, Math.min(8, slots)));
            long budgetDelta = Math.abs(boardBudget - budget);

            if (best == null || slotDelta < bestSlotDelta || (slotDelta == bestSlotDelta && budgetDelta < bestBudgetDelta))
            {
                best = member;
                bestSlotDelta = slotDelta;
                bestBudgetDelta = budgetDelta;
            }
        }

        return best == null ? "" : best.value;
    }

    private AutoFlipRecommendationContext buildAutoFlipRecommendationContext(
        String itemObject,
        String refObject,
        String sliceKey,
        String matrixKey,
        int rankIndex,
        int quantity,
        long budget,
        int hours,
        String budgetBand,
        String risk,
        String profile
    )
    {
        int itemId = readJsonInt(itemObject, "item_id", 0);
        long buyPrice = readJsonLong(itemObject, "buy_price_gp", readJsonLong(itemObject, "buy_price", 0L));
        long sellPrice = readJsonLong(itemObject, "sell_price_gp", readJsonLong(itemObject, "sell_price", 0L));
        int suggestedQuantity = Math.max(1, quantity);
        long generatedAtMs = autoFlipPayloadGeneratedAtMs > 0L
            ? autoFlipPayloadGeneratedAtMs
            : parseIsoTsMs(autoFlipPayloadGeneratedAt);
        String cacheBuildId = !autoFlipPayloadHash.isEmpty()
            ? autoFlipPayloadHash
            : sha256(autoFlipPayloadGeneratedAt + "|" + autoFlipPayloadSourceCacheBuilder).substring(0, 32);

        return new AutoFlipRecommendationContext(
            "",
            "plan_" + sha256(cacheBuildId + "|" + safe(sliceKey) + "|" + safe(matrixKey)).substring(0, 24),
            cacheBuildId,
            autoFlipPayloadHash,
            autoFlipPayloadGeneratedAt,
            generatedAtMs,
            0L,
            -1,
            rankIndex + 1,
            safe(sliceKey),
            safe(matrixKey),
            safe(risk),
            safe(budgetBand),
            safe(profile),
            safe(autoFlipPayloadAlgorithmVersion),
            safe(autoFlipPayloadExecutionPricingSource),
            safe(readJsonString(itemObject, "scoring_metadata", "").isEmpty()
                ? autoFlipPayloadSourceCacheBuilder
                : readJsonString(itemObject, "scoring_metadata", "")),
            Math.max(1L, budget),
            Math.max(1, hours),
            buyPrice,
            sellPrice,
            suggestedQuantity,
            Math.max(0L, buyPrice) * Math.max(1, suggestedQuantity),
            readJsonLong(itemObject, "execution_hourly_capacity", 0L),
            readJsonDouble(itemObject, "execution_throughput_ratio", 0.0D),
            readJsonDouble(itemObject, "execution_throughput_pressure", 0.0D),
            readJsonDouble(itemObject, "execution_volume_fit", 0.0D)
        );
    }

    private AutoFlipRecommendationContext finalizeAutoFlipRecommendationContext(
        AutoFlipRecommendationContext context,
        int boardSlot,
        AutoFlipPayloadRow row
    )
    {
        long shownTsMs = System.currentTimeMillis();
        AutoFlipRecommendationContext base = context == null
            ? new AutoFlipRecommendationContext(
                "",
                "",
                autoFlipPayloadHash,
                autoFlipPayloadHash,
                autoFlipPayloadGeneratedAt,
                autoFlipPayloadGeneratedAtMs,
                shownTsMs,
                boardSlot,
                0,
                "",
                "",
                "",
                getAutoFlipPayloadBudgetBand(getAutoFlipEffectiveBudgetGp()),
                "unknown",
                autoFlipPayloadAlgorithmVersion,
                autoFlipPayloadExecutionPricingSource,
                autoFlipPayloadSourceCacheBuilder,
                getAutoFlipEffectiveBudgetGp(),
                getAutoFlipPayloadHourBucket(autoFlipMenuHoursAway),
                row.buyPriceGp,
                row.sellPriceGp,
                row.quantity,
                row.capitalGp(),
                0L,
                0.0D,
                0.0D,
                0.0D
            )
            : context.withShownCard(boardSlot, shownTsMs, row.buyPriceGp, row.sellPriceGp, row.quantity, row.capitalGp());

        String recommendationId = "rec_" + sha256(
            accountKey
                + "|" + telemetrySessionId
                + "|" + safe(base.cacheBuildId)
                + "|" + safe(base.sliceKey)
                + "|" + safe(base.matrixKey)
                + "|" + boardSlot
                + "|" + row.itemId
                + "|" + row.buyPriceGp
                + "|" + row.sellPriceGp
                + "|" + row.quantity
                + "|" + shownTsMs
        ).substring(0, 32);

        return base.withRecommendationId(recommendationId);
    }

    private AutoFlipPayloadRow localPayloadRowFromJson(
        String obj,
        int index,
        int quantity,
        int hoursAway,
        AutoFlipRecommendationContext recommendationContext
    )
    {
        int itemId = readJsonInt(obj, "item_id", 0);
        if (itemId <= 0)
        {
            return null;
        }

        String itemName = readJsonString(obj, "item_name", "Item " + itemId);
        long buyPrice = readJsonLong(obj, "buy_price_gp", readJsonLong(obj, "buy_price", 0L));
        long sellPrice = readJsonLong(obj, "sell_price_gp", readJsonLong(obj, "sell_price", 0L));
        primeAutoFlipSellPriceCache(itemId, sellPrice);
        long profitEach = readJsonLong(obj, "profit_each_gp", readJsonLong(obj, "profit_each", 0L));
        String scoringMetadata = localPayloadExtractObjectField(obj, "scoring_metadata");
        int suggestedQty = readJsonInt(obj, "suggested_quantity", quantity);
        int liquidityQty = readJsonInt(obj, "liquidity_safe_quantity", 0);
        int frontierQty = readJsonInt(obj, "frontier_v3_liquidity_safe_quantity", 0);
        int timeLimitQty = readJsonInt(obj, "time_adjusted_buy_limit_quantity", 0);
        int buyLimitQty = readJsonInt(obj, "buy_limit_4h", 0);
        int buyLimitWindowQty = resolveAutoFlipBuyLimitWindowQuantity(buyLimitQty, hoursAway);
        int realisticQuantityCap = readJsonInt(
            obj,
            "realistic_quantity_cap",
            readJsonInt(obj, "strict_liquidity_quantity_cap", 0)
        );
        int maxQty = Math.max(Math.max(suggestedQty, liquidityQty), frontierQty);
        if (timeLimitQty > 0)
        {
            maxQty = Math.max(maxQty, timeLimitQty);
        }
        if (buyLimitWindowQty > 0)
        {
            if (timeLimitQty > 0)
            {
                maxQty = Math.min(maxQty, buyLimitWindowQty);
            }
            else
            {
                // Treat the backend suggestion as a starting point, not the ceiling.
                // Refresh-board rebalance needs room to expand up to the actual buy-limit window.
                maxQty = Math.max(maxQty, buyLimitWindowQty);
            }
        }
        if (realisticQuantityCap > 0)
        {
            maxQty = Math.max(maxQty, realisticQuantityCap);
        }
        long adjustedDeployableGp = readJsonLong(
            scoringMetadata,
            "execution_adjusted_max_deployable_gp",
            readJsonLong(scoringMetadata, "raw_max_deployable_gp", 0L)
        );
        if (adjustedDeployableGp > 0L && buyPrice > 0L)
        {
            long adjustedDeployableQty = adjustedDeployableGp / Math.max(1L, buyPrice);
            if (adjustedDeployableQty > 0L)
            {
                maxQty = Math.max(maxQty, (int) Math.min(Integer.MAX_VALUE, adjustedDeployableQty));
            }
        }
        int appliedQuantity = Math.max(1, Math.min(quantity, maxQty));

        double fill = readJsonDouble(obj, "fill_probability", 0.0D);
        double model = readJsonDouble(obj, "model_score", readJsonDouble(obj, "score", 0.0D));
        double holdHours = readJsonDouble(obj, "expected_hold_hours", 0.0D);
        String confidence = readJsonString(obj, "confidence_band", "");
        String reason = readJsonString(obj, "reason", model > 0.0D || fill > 0.0D
            ? "Model score " + String.format(java.util.Locale.US, "%.3f", model) + ", fill " + String.format(java.util.Locale.US, "%.1f", fill * 100.0D) + "%"
            : "Ranked recommendation pool item"
        );
        String marketUrl = readJsonString(obj, "market_url", getAutoFlipMarketItemUrl(itemId, itemName));
        String holdLabel = holdHours > 0.0D ? String.format(java.util.Locale.US, "%.1fh max", holdHours) : "unknown";

        return new AutoFlipPayloadRow(
            itemId,
            itemName,
            buyPrice,
            sellPrice,
            profitEach,
            appliedQuantity,
            Math.max(appliedQuantity, maxQty),
            fill,
            confidence,
            reason,
            marketUrl,
            holdLabel,
            recommendationContext
        );
    }

    private int resolveAutoFlipBuyLimitWindowQuantity(int buyLimitQty4h, int hoursAway)
    {
        if (buyLimitQty4h <= 0)
        {
            return 0;
        }

        int windows = Math.max(1, getAutoFlipPayloadHourBucket(hoursAway) / 4);
        long windowQuantity = (long) buyLimitQty4h * (long) windows;
        if (windowQuantity <= 0L)
        {
            return 0;
        }

        return (int) Math.min(Integer.MAX_VALUE, windowQuantity);
    }

    private java.util.List<AutoFlipPayloadRow> localPayloadScaleRowsToBudget(java.util.List<AutoFlipPayloadRow> rows, long budgetGp)
    {
        if (rows == null || rows.isEmpty() || budgetGp <= 0L)
        {
            return java.util.Collections.emptyList();
        }

        java.util.List<AutoFlipPayloadRow> orderedRows = orderAutoFlipPayloadRowsForBudgetAllocation(rows);
        java.util.List<AutoFlipPayloadRow> warmed = getAutoFlipBudgetWarmBufferSnapshot(orderedRows, budgetGp);
        if (warmed != null)
        {
            return warmed;
        }

        long hardCap = Math.max(1L, budgetGp);
        long target = Math.max(1L, (long) (hardCap * 0.99D));
        java.util.List<AutoFlipBudgetScalePlan> plans = new java.util.ArrayList<>();
        long baseSpent = 0L;
        for (int i = 0; i < orderedRows.size(); i++)
        {
            AutoFlipPayloadRow row = orderedRows.get(i);
            if (row == null || row.buyPriceGp <= 0L)
            {
                continue;
            }

            int capQuantity = Math.max(1, row.maxQuantity);
            int baseQuantity = Math.max(1, Math.min(row.quantity, capQuantity));
            long baseCapital = Math.max(0L, row.buyPriceGp) * (long) baseQuantity;
            if (baseCapital <= 0L)
            {
                continue;
            }

            double weightedBaseQuantity = (double) baseQuantity * getAutoFlipConfidenceQuantityWeight(row);
            plans.add(new AutoFlipBudgetScalePlan(i, row, baseQuantity, weightedBaseQuantity, capQuantity));
            baseSpent += baseCapital;
        }

        if (plans.isEmpty())
        {
            logAutoFlipVerbose("AUTOFLIP_LOCAL_SCALE rows_in=" + rows.size() + " rows_out=0 budget_gp=" + budgetGp + " spent_gp=0");
            return java.util.Collections.emptyList();
        }

        double scale = baseSpent <= 0L ? 0.0D : (double) target / (double) baseSpent;
        long spent = 0L;
        for (AutoFlipBudgetScalePlan plan : plans)
        {
            double scaledQuantity = plan.weightedBaseQuantity * scale;
            int floorQuantity = (int) Math.floor(scaledQuantity);
            plan.fractionalRemainder = scaledQuantity - (double) floorQuantity;
            plan.quantity = Math.max(0, Math.min(plan.capQuantity, floorQuantity));
            spent += plan.row.buyPriceGp * (long) plan.quantity;
        }

        if (spent > target)
        {
            java.util.List<AutoFlipBudgetScalePlan> reducible = new java.util.ArrayList<>(plans);
            reducible.sort((left, right) ->
            {
                int cmp = Double.compare(left.fractionalRemainder, right.fractionalRemainder);
                if (cmp != 0)
                {
                    return cmp;
                }

                return Integer.compare(right.orderIndex, left.orderIndex);
            });

            for (AutoFlipBudgetScalePlan plan : reducible)
            {
                while (plan.quantity > 0 && spent > target)
                {
                    plan.quantity--;
                    spent -= plan.row.buyPriceGp;
                }

                if (spent <= target)
                {
                    break;
                }
            }
        }

        if (spent < target)
        {
            java.util.List<AutoFlipBudgetScalePlan> distributable = new java.util.ArrayList<>(plans);
            distributable.sort((left, right) ->
            {
                int cmp = Double.compare(right.fractionalRemainder, left.fractionalRemainder);
                if (cmp != 0)
                {
                    return cmp;
                }

                return Integer.compare(left.orderIndex, right.orderIndex);
            });

            boolean progressed;
            do
            {
                progressed = false;
                for (AutoFlipBudgetScalePlan plan : distributable)
                {
                    if (spent >= target)
                    {
                        break;
                    }

                    if (plan.quantity >= plan.capQuantity)
                    {
                        continue;
                    }

                    long nextSpent = spent + plan.row.buyPriceGp;
                    if (nextSpent > target)
                    {
                        continue;
                    }

                    plan.quantity++;
                    spent = nextSpent;
                    progressed = true;
                }
            }
            while (progressed && spent < target);
        }

        if (spent < hardCap)
        {
            java.util.List<AutoFlipBudgetScalePlan> distributable = new java.util.ArrayList<>(plans);
            distributable.sort((left, right) ->
            {
                int cmp = Double.compare(right.fractionalRemainder, left.fractionalRemainder);
                if (cmp != 0)
                {
                    return cmp;
                }

                return Integer.compare(left.orderIndex, right.orderIndex);
            });

            boolean progressed;
            do
            {
                progressed = false;

                for (AutoFlipBudgetScalePlan plan : distributable)
                {
                    if (spent >= hardCap)
                    {
                        break;
                    }

                    if (plan.quantity >= plan.capQuantity)
                    {
                        continue;
                    }

                    long nextSpent = spent + plan.row.buyPriceGp;
                    if (nextSpent > hardCap)
                    {
                        continue;
                    }

                    plan.quantity++;
                    spent = nextSpent;
                    progressed = true;
                }
            }
            while (progressed && spent < hardCap);
        }

        java.util.List<AutoFlipPayloadRow> survivors = new java.util.ArrayList<>(plans.size());
        for (AutoFlipBudgetScalePlan plan : plans)
        {
            if (plan.quantity <= 0)
            {
                continue;
            }

            survivors.add(plan.row.copyWithQuantity(plan.quantity));
        }

        logAutoFlipVerbose(
            "AUTOFLIP_LOCAL_SCALE"
                + " rows_in=" + orderedRows.size()
                + " rows_out=" + survivors.size()
                + " budget_gp=" + budgetGp
                + " target_gp=" + target
                + " spent_gp=" + spent
        );

        rememberAutoFlipBudgetWarmBuffer(orderedRows, budgetGp, survivors);
        return survivors;
    }

    private java.util.List<AutoFlipPayloadRow> orderAutoFlipPayloadRowsForBudgetAllocation(java.util.List<AutoFlipPayloadRow> rows)
    {
        if (rows == null || rows.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        java.util.List<AutoFlipPayloadRow> ordered = new java.util.ArrayList<>(rows.size());
        for (AutoFlipPayloadRow row : rows)
        {
            if (row != null)
            {
                ordered.add(row);
            }
        }

        return ordered;
    }

    private synchronized java.util.List<AutoFlipPayloadRow> getAutoFlipBudgetWarmBufferSnapshot(java.util.List<AutoFlipPayloadRow> rows, long budgetGp)
    {
        if (autoFlipBudgetWarmBuffer == null || rows == null || rows.isEmpty())
        {
            return null;
        }

        String signature = buildAutoFlipBudgetWarmSignature(rows, budgetGp);
        if (!signature.equals(autoFlipBudgetWarmBuffer.signature) || autoFlipBudgetWarmBuffer.budgetGp != budgetGp)
        {
            return null;
        }

        return autoFlipBudgetWarmBuffer.snapshot;
    }

    private synchronized void rememberAutoFlipBudgetWarmBuffer(
        java.util.List<AutoFlipPayloadRow> rows,
        long budgetGp,
        java.util.List<AutoFlipPayloadRow> snapshot)
    {
        if (rows == null || rows.isEmpty() || snapshot == null || snapshot.isEmpty())
        {
            autoFlipBudgetWarmBuffer = null;
            return;
        }

        autoFlipBudgetWarmBuffer = new AutoFlipBudgetWarmBuffer(
            buildAutoFlipBudgetWarmSignature(rows, budgetGp),
            budgetGp,
            java.util.Collections.unmodifiableList(new java.util.ArrayList<>(snapshot))
        );
    }

    private String buildAutoFlipBudgetWarmSignature(java.util.List<AutoFlipPayloadRow> rows, long budgetGp)
    {
        if (rows == null || rows.isEmpty())
        {
            return "empty:" + budgetGp;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("budget=").append(budgetGp).append('|').append(rows.size());
        for (AutoFlipPayloadRow row : rows)
        {
            if (row == null)
            {
                continue;
            }

            sb.append('|')
                .append(row.itemId)
                .append(':')
                .append(row.buyPriceGp)
                .append(':')
                .append(row.quantity)
                .append(':')
                .append(row.maxQuantity)
                .append(':')
                .append(String.format(java.util.Locale.US, "%.6f", row.fillProbability))
                .append(':')
                .append(safe(row.confidenceBand));
        }

        return sha256(sb.toString());
    }

    private static final class AutoFlipBudgetWarmBuffer
    {
        private final String signature;
        private final long budgetGp;
        private final java.util.List<AutoFlipPayloadRow> snapshot;

        private AutoFlipBudgetWarmBuffer(String signature, long budgetGp, java.util.List<AutoFlipPayloadRow> snapshot)
        {
            this.signature = signature == null ? "" : signature;
            this.budgetGp = budgetGp;
            this.snapshot = snapshot == null ? java.util.Collections.emptyList() : snapshot;
        }
    }

    private double getAutoFlipConfidenceQuantityWeight(AutoFlipPayloadRow row)
    {
        if (row == null)
        {
            return 1.0D;
        }

        double confidence = row.fillProbability;
        if (!Double.isFinite(confidence) || confidence <= 0.0D)
        {
            String band = safe(row.confidenceBand).toLowerCase(java.util.Locale.ROOT);
            if ("high".equals(band))
            {
                confidence = 0.90D;
            }
            else if ("medium".equals(band))
            {
                confidence = 0.70D;
            }
            else if ("low".equals(band))
            {
                confidence = 0.50D;
            }
            else
            {
                confidence = 0.65D;
            }
        }

        confidence = Math.max(0.10D, Math.min(1.0D, confidence));
        return 0.50D + (confidence * 0.50D);
    }

    static boolean isAutoFlipPriorityCardAllowedForCurrentAccount(String riskLabel, boolean accountEligible)
    {
        return "TO_BUY".equalsIgnoreCase(safe(riskLabel)) || accountEligible;
    }

    private AutoFlipPayloadRow findAutoFlipPayloadRowByCanonicalItemId(java.util.List<AutoFlipPayloadRow> rows, int canonicalItemId)
    {
        if (rows == null || rows.isEmpty() || canonicalItemId <= 0)
        {
            return null;
        }

        for (AutoFlipPayloadRow row : rows)
        {
            if (row != null && canonicalizeAutoFlipInventoryItemId(row.itemId) == canonicalItemId)
            {
                return row;
            }
        }

        return null;
    }

    private String summarizeAutoFlipPayloadRows(java.util.List<AutoFlipPayloadRow> rows)
    {
        if (rows == null || rows.isEmpty())
        {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        int limit = Math.min(rows.size(), 8);
        for (int i = 0; i < limit; i++)
        {
            if (i > 0)
            {
                sb.append(" | ");
            }

            AutoFlipPayloadRow row = rows.get(i);
            if (row == null)
            {
                sb.append(i).append(":null");
                continue;
            }

            sb.append(i)
                .append(":")
                .append(row.itemId)
                .append(":")
                .append(safe(row.itemName))
                .append(" q=")
                .append(row.quantity)
                .append(" buy=")
                .append(row.buyPriceGp);
        }

        if (rows.size() > limit)
        {
            sb.append(" | ... total=").append(rows.size());
        }

        sb.append("]");
        return sb.toString();
    }

    private String summarizeAutoFlipBoardCards(java.util.List<AutoFlipBoardCard> cards)
    {
        if (cards == null || cards.isEmpty())
        {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (int i = 0; i < cards.size(); i++)
        {
            AutoFlipBoardCard card = cards.get(i);
            if (card == null || card.getItemId() <= 0)
            {
                continue;
            }

            if (!first)
            {
                sb.append(" | ");
            }
            first = false;

            sb.append(i)
                .append(":")
                .append(card.getItemId())
                .append(":")
                .append(safe(card.getItemName()));
        }

        if (first)
        {
            return "[]";
        }

        sb.append("]");
        return sb.toString();
    }

    private String summarizeAutoFlipRowBySlot(java.util.Map<Integer, AutoFlipPayloadRow> rowBySlot)
    {
        if (rowBySlot == null || rowBySlot.isEmpty())
        {
            return "{}";
        }

        java.util.List<Integer> slots = new java.util.ArrayList<>(rowBySlot.keySet());
        java.util.Collections.sort(slots);
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < slots.size(); i++)
        {
            if (i > 0)
            {
                sb.append(" | ");
            }

            Integer slot = slots.get(i);
            AutoFlipPayloadRow row = rowBySlot.get(slot);
            sb.append(slot)
                .append("=")
                .append(row == null ? "null" : row.itemId + ":" + safe(row.itemName));
        }

        sb.append("}");
        return sb.toString();
    }

    private AutoFlipBoardCard localPayloadCardFromRow(AutoFlipPayloadRow row, int slotIndex)
    {
        long expectedProfit = Math.max(0L, row.profitEachGp) * Math.max(0, row.quantity);
        AutoFlipRecommendationContext recommendationContext = finalizeAutoFlipRecommendationContext(row.recommendationContext, slotIndex, row);

        return new AutoFlipBoardCard(
            slotIndex,
            row.itemId,
            row.itemName,
            row.quantity,
            row.maxQuantity,
            row.buyPriceGp,
            row.sellPriceGp,
            row.profitEachGp,
            expectedProfit,
            row.maxHoldTimeLabel,
            row.confidenceBand,
            row.reason,
            row.marketUrl,
            row.fillProbability,
            recommendationContext
        );
    }

    private String localPayloadExtractObjectField(String json, String key)
    {
        if (json == null || json.isEmpty() || key == null)
        {
            return "";
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*\\{")
            .matcher(json);

        if (!matcher.find())
        {
            return "";
        }

        int objectStart = json.indexOf('{', matcher.start());
        return localPayloadExtractBalanced(json, objectStart, '{', '}');
    }

    private String localPayloadExtractArrayField(String json, String key)
    {
        int arrayStart = findJsonArrayStart(json, key);
        if (arrayStart < 0)
        {
            return "";
        }

        return localPayloadExtractBalanced(json, arrayStart, '[', ']');
    }

    private String localPayloadExtractBalanced(String json, int start, char open, char close)
    {
        if (json == null || start < 0 || start >= json.length() || json.charAt(start) != open)
        {
            return "";
        }

        boolean inString = false;
        boolean escaping = false;
        int depth = 0;

        for (int i = start; i < json.length(); i++)
        {
            char ch = json.charAt(i);

            if (escaping)
            {
                escaping = false;
                continue;
            }

            if (ch == '\\' && inString)
            {
                escaping = true;
                continue;
            }

            if (ch == '"')
            {
                inString = !inString;
                continue;
            }

            if (inString)
            {
                continue;
            }

            if (ch == open)
            {
                depth++;
            }
            else if (ch == close)
            {
                depth--;
                if (depth == 0)
                {
                    return json.substring(start, i + 1);
                }
            }
        }

        return "";
    }

    private java.util.List<String> localPayloadExtractArrayObjects(String arrayText)
    {
        java.util.List<String> objects = new java.util.ArrayList<>();
        if (arrayText == null || arrayText.isEmpty())
        {
            return objects;
        }

        boolean inString = false;
        boolean escaping = false;
        int depth = 0;
        int objectStart = -1;

        for (int i = 0; i < arrayText.length(); i++)
        {
            char ch = arrayText.charAt(i);

            if (escaping)
            {
                escaping = false;
                continue;
            }

            if (ch == '\\' && inString)
            {
                escaping = true;
                continue;
            }

            if (ch == '"')
            {
                inString = !inString;
                continue;
            }

            if (inString)
            {
                continue;
            }

            if (ch == '{')
            {
                if (depth == 0)
                {
                    objectStart = i;
                }
                depth++;
            }
            else if (ch == '}')
            {
                depth--;
                if (depth == 0 && objectStart >= 0)
                {
                    objects.add(arrayText.substring(objectStart, i + 1));
                    objectStart = -1;
                }
            }
        }

        return objects;
    }

    private java.util.List<AutoFlipJsonMember> localPayloadObjectMembers(String objectText)
    {
        java.util.List<AutoFlipJsonMember> members = new java.util.ArrayList<>();
        if (objectText == null || objectText.length() < 2)
        {
            return members;
        }

        int i = objectText.charAt(0) == '{' ? 1 : 0;
        while (i < objectText.length())
        {
            while (i < objectText.length() && Character.isWhitespace(objectText.charAt(i)))
            {
                i++;
            }

            if (i >= objectText.length() || objectText.charAt(i) == '}')
            {
                break;
            }

            if (objectText.charAt(i) != '"')
            {
                i++;
                continue;
            }

            StringBuilder key = new StringBuilder();
            i++;
            boolean escaping = false;
            while (i < objectText.length())
            {
                char ch = objectText.charAt(i);
                if (escaping)
                {
                    key.append(ch);
                    escaping = false;
                }
                else if (ch == '\\')
                {
                    escaping = true;
                }
                else if (ch == '"')
                {
                    break;
                }
                else
                {
                    key.append(ch);
                }
                i++;
            }

            while (i < objectText.length() && objectText.charAt(i) != ':')
            {
                i++;
            }

            if (i >= objectText.length())
            {
                break;
            }

            i++;
            while (i < objectText.length() && Character.isWhitespace(objectText.charAt(i)))
            {
                i++;
            }

            if (i >= objectText.length())
            {
                break;
            }

            char start = objectText.charAt(i);
            String value;
            if (start == '{')
            {
                value = localPayloadExtractBalanced(objectText, i, '{', '}');
                i += Math.max(1, value.length());
            }
            else if (start == '[')
            {
                value = localPayloadExtractBalanced(objectText, i, '[', ']');
                i += Math.max(1, value.length());
            }
            else
            {
                int valueStart = i;
                while (i < objectText.length() && objectText.charAt(i) != ',' && objectText.charAt(i) != '}')
                {
                    i++;
                }
                value = objectText.substring(valueStart, i).trim();
            }

            members.add(new AutoFlipJsonMember(key.toString(), value));

            while (i < objectText.length() && objectText.charAt(i) != ',')
            {
                if (objectText.charAt(i) == '}')
                {
                    break;
                }
                i++;
            }

            if (i < objectText.length() && objectText.charAt(i) == ',')
            {
                i++;
            }
        }

        return members;
    }
    private java.util.List<AutoFlipBoardCard> localPayloadReplaceBoardCard(
        java.util.List<AutoFlipBoardCard> baseCards,
        int requestedSlotIndex,
        int requestedItemId
    )
    {
        java.util.List<AutoFlipBoardCard> current = baseCards;
        if (current == null || current.isEmpty())
        {
            current = getAutoFlipBoardStateSnapshot();
        }

        if (current == null || current.isEmpty())
        {
            return buildAutoFlipBoardFromLocalPayload();
        }

        if (requestedSlotIndex >= 0 && isAutoFlipBoardSlotRetired(requestedSlotIndex))
        {
            java.util.List<AutoFlipBoardCard> retiredOnly = new java.util.ArrayList<>(current);
            if (requestedSlotIndex < retiredOnly.size())
            {
                retiredOnly.set(requestedSlotIndex, null);
            }
            logAutoFlipVerbose(
                "AUTOFLIP_LOCAL_REFRESH_ITEM_RETIRED"
                    + " slot=" + requestedSlotIndex
                    + " requested_item_id=" + requestedItemId
                    + " reason=retired_until_refresh"
            );
            return retiredOnly;
        }

        current = snapshotAutoFlipBoardCards(current);

        java.util.List<AutoFlipPayloadRow> universe = localPayloadCurrentRankedUniverseRows();
        if (universe == null || universe.isEmpty())
        {
            logAutoFlipVerbose("AUTOFLIP_LOCAL_REFRESH universe_empty=true");
            return current;
        }

        int replaceAt = -1;

        if (requestedItemId > 0 || requestedSlotIndex >= 0)
        {
            for (int i = 0; i < current.size(); i++)
            {
                AutoFlipBoardCard card = current.get(i);
                if (card == null)
                {
                    continue;
                }

                if ((requestedSlotIndex >= 0 && card.getSlotIndex() == requestedSlotIndex)
                    || (requestedItemId > 0 && card.getItemId() == requestedItemId))
                {
                    replaceAt = i;
                    break;
                }
            }
        }
        if (replaceAt < 0)
        {
            if (requestedSlotIndex >= 0 && requestedSlotIndex < current.size())
            {
                replaceAt = requestedSlotIndex;
            }
            else
            {
                replaceAt = 0;
            }
        }

        logAutoFlipVerbose(
            "AUTOFLIP_LOCAL_REFRESH_ITEM_TARGET"
                + " requested_slot=" + requestedSlotIndex
                + " requested_item_id=" + requestedItemId
                + " replace_at=" + replaceAt
                + " current_size=" + current.size()
        );

        AutoFlipBoardCard oldCard = current.get(replaceAt);
        int outputSlot = oldCard == null ? replaceAt : oldCard.getSlotIndex();
        java.util.List<AutoFlipBoardCard> next = new java.util.ArrayList<>(java.util.Collections.nCopies(Math.max(8, current.size()), null));
        for (AutoFlipBoardCard card : current)
        {
            if (card == null)
            {
                continue;
            }

            int slot = card.getSlotIndex();
            if (slot >= 0 && slot < next.size())
            {
                next.set(slot, card);
            }
        }

        java.util.Set<Integer> lockedItemIds = new java.util.HashSet<>();
        java.util.Set<Integer> skippedItemIds = getAutoFlipSkippedItemIdsSnapshot();
        for (AutoFlipBoardCard card : current)
        {
            if (card != null && card.getItemId() > 0)
            {
                lockedItemIds.add(canonicalizeAutoFlipInventoryItemId(card.getItemId()));
            }
        }

        AutoFlipPayloadRow replacement = pickAutoFlipRefreshReplacementRow(
            universe,
            oldCard,
            outputSlot,
            findAutoFlipRefreshStartCursorForCard(universe, oldCard),
            lockedItemIds,
            skippedItemIds,
            true
        );

        if (replacement == null)
        {
            replacement = pickAutoFlipRefreshReplacementRow(
                universe,
                oldCard,
                outputSlot,
                0,
                null,
                skippedItemIds,
                false
            );
        }

        if (replacement == null && !skippedItemIds.isEmpty())
        {
            clearAutoFlipSkippedItemIds("refresh_item_wrap_all_items_skipped");
            replacement = pickAutoFlipRefreshReplacementRow(
                universe,
                oldCard,
                outputSlot,
                0,
                null,
                getAutoFlipSkippedItemIdsSnapshot(),
                false
            );
        }

        if (replacement == null)
        {
            logAutoFlipVerbose("AUTOFLIP_LOCAL_REFRESH replacement_missing=true replace_slot=" + outputSlot);
            return current;
        }

        if (outputSlot >= 0 && outputSlot < next.size())
        {
            next.set(outputSlot, localPayloadCardFromRow(replacement, outputSlot));
        }

        // Keep single-slot refreshes slot-stable.
        // Rebuilding the whole board here can compact the list and shift later cards left,
        // which makes confirmed items appear to drift into the next slot.
        java.util.List<AutoFlipBoardCard> scaled = next;

        return scaled;
    }

    private java.util.List<AutoFlipBoardCard> localPayloadReplaceBoardCard(int requestedSlotIndex, int requestedItemId)
    {
        return localPayloadReplaceBoardCard(getAutoFlipBoardStateSnapshot(), requestedSlotIndex, requestedItemId);
    }

    private AutoFlipPayloadRow pickAutoFlipRefreshReplacementRow(
        java.util.List<AutoFlipPayloadRow> universe,
        AutoFlipBoardCard oldCard,
        int outputSlot,
        int startCursor,
        java.util.Set<Integer> lockedItemIds,
        java.util.Set<Integer> skippedItemIds,
        boolean respectLockedItems
    )
    {
        if (universe == null || universe.isEmpty())
        {
            return null;
        }

        int universeSize = universe.size();
        int cursor = universeSize <= 0 ? 0 : Math.abs(startCursor) % universeSize;

        AutoFlipPayloadRow replacement = pickAutoFlipRefreshReplacementRowPass(
            universe,
            oldCard,
            outputSlot,
            cursor,
            lockedItemIds,
            skippedItemIds,
            respectLockedItems
        );
        if (replacement != null)
        {
            return replacement;
        }
        return null;
    }

    private AutoFlipPayloadRow pickAutoFlipRefreshReplacementRowPass(
        java.util.List<AutoFlipPayloadRow> universe,
        AutoFlipBoardCard oldCard,
        int outputSlot,
        int cursor,
        java.util.Set<Integer> lockedItemIds,
        java.util.Set<Integer> skippedItemIds,
        boolean respectLockedItems
    )
    {
        if (universe == null || universe.isEmpty())
        {
            return null;
        }

        int universeSize = universe.size();
        int normalizedCursor = universeSize <= 0 ? 0 : Math.abs(cursor) % universeSize;

        for (int attempt = 0; attempt < universeSize; attempt++)
        {
            int universeIndex = (normalizedCursor + attempt) % universeSize;
            AutoFlipPayloadRow row = universe.get(universeIndex);

            if (row == null || row.itemId <= 0)
            {
                continue;
            }

            if (!isAutoFlipItemAllowedForCurrentAccount(row.itemId))
            {
                continue;
            }

            int canonicalItemId = canonicalizeAutoFlipInventoryItemId(row.itemId);
            if (respectLockedItems && lockedItemIds != null && lockedItemIds.contains(canonicalItemId))
            {
                continue;
            }

            if (skippedItemIds != null && skippedItemIds.contains(canonicalItemId))
            {
                continue;
            }

            if (oldCard != null && row.itemId == oldCard.getItemId())
            {
                continue;
            }

            int quantity = Math.max(1, row.quantity);
            AutoFlipPayloadRow next = row.copyWithQuantity(quantity);
            return next;
        }

        return null;
    }

    private static final class AutoFlipBudgetScalePlan
    {
        private final int orderIndex;
        private final AutoFlipPayloadRow row;
        private final int baseQuantity;
        private final double weightedBaseQuantity;
        private final int capQuantity;
        private int quantity;
        private double fractionalRemainder;

        private AutoFlipBudgetScalePlan(int orderIndex, AutoFlipPayloadRow row, int baseQuantity, double weightedBaseQuantity, int capQuantity)
        {
            this.orderIndex = orderIndex;
            this.row = row;
            this.baseQuantity = baseQuantity;
            this.weightedBaseQuantity = weightedBaseQuantity;
            this.capQuantity = capQuantity;
            this.quantity = 0;
            this.fractionalRemainder = 0.0D;
        }
    }
    private synchronized void rememberAutoFlipSkippedItem(int itemId)
    {
        if (itemId <= 0)
        {
            return;
        }

        autoFlipRecentlySkippedItemIds.add(itemId);
    }

    private synchronized java.util.Set<Integer> getAutoFlipSkippedItemIdsSnapshot()
    {
        return new java.util.HashSet<>(autoFlipRecentlySkippedItemIds);
    }

    private synchronized void clearAutoFlipSkippedItemIds(String reason)
    {
        autoFlipRecentlySkippedItemIds.clear();
        logAutoFlipVerbose("AUTOFLIP_SKIPPED_ITEM_CLEAR reason=" + safe(reason));
    }

    private synchronized boolean isAutoFlipBoardSlotRetired(int slotIndex)
    {
        return slotIndex >= 0
            && slotIndex < autoFlipRetiredBoardSlots.length
            && autoFlipRetiredBoardSlots[slotIndex];
    }

    private synchronized void clearAutoFlipRetiredBoardSlots(String reason)
    {
        java.util.Arrays.fill(autoFlipRetiredBoardSlots, false);
        logAutoFlipVerbose("AUTOFLIP_BOARD_SLOT_RETIRE_CLEAR reason=" + safe(reason));
    }

    private void retireAutoFlipBoardSlot(int slotIndex, String reason, int itemId, int quantitySold, int spent)
    {
        if (slotIndex < 0 || slotIndex >= autoFlipRetiredBoardSlots.length)
        {
            return;
        }

        boolean changed;
        synchronized (this)
        {
            changed = !autoFlipRetiredBoardSlots[slotIndex];
            autoFlipRetiredBoardSlots[slotIndex] = true;
        }

        logAutoFlipVerbose(
            "AUTOFLIP_BOARD_SLOT_RETIRED"
                + " slot=" + slotIndex
                + " reason=" + safe(reason)
                + " item_id=" + itemId
                + " quantity_sold=" + quantitySold
                + " spent=" + spent
                + " changed=" + changed
        );

        java.util.List<AutoFlipBoardCard> cards = getAutoFlipBoardCardsSnapshot();
        if (cards.isEmpty())
        {
            return;
        }

        java.util.List<AutoFlipBoardCard> updated = new java.util.ArrayList<>(cards);
        boolean removed = false;
        for (int i = 0; i < updated.size(); i++)
        {
            AutoFlipBoardCard card = updated.get(i);
            if (card != null && card.getSlotIndex() == slotIndex)
            {
                updated.set(i, null);
                removed = true;
            }
        }

        if (removed)
        {
            autoFlipBoardCards = java.util.Collections.unmodifiableList(updated);
            notifyAutoFlipSidePanelRefresh();
        }
    }

    private void maybeRetireAutoFlipBoardSlot(int slotIndex, int itemId, String state, int quantitySold, int spent)
    {
        String normalized = state == null ? "" : state.toLowerCase(java.util.Locale.ROOT);
        boolean terminal = normalized.contains("bought")
            || normalized.contains("sold")
            || normalized.contains("cancel")
            || normalized.contains("abort");
        boolean consumed = quantitySold > 0 || spent > 0;

        if (!terminal || !consumed)
        {
            logAutoFlipVerbose(
                "AUTOFLIP_BOARD_SLOT_RETIRE_SKIP"
                    + " slot=" + slotIndex
                    + " state=" + safe(state)
                    + " quantity_sold=" + quantitySold
                    + " spent=" + spent
                    + " terminal=" + terminal
                    + " consumed=" + consumed
            );
            return;
        }

        retireAutoFlipBoardSlot(slotIndex, "ge_offer_terminal", itemId, quantitySold, spent);
    }

    private java.util.List<AutoFlipBoardCard> filterAutoFlipBoardCardsForAvailableSlots(
        java.util.List<AutoFlipBoardCard> cards,
        String reason)
    {
        if (cards == null || cards.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        java.util.List<AutoFlipBoardCard> filtered = new java.util.ArrayList<>(java.util.Collections.nCopies(8, null));
        boolean changed = false;

        for (AutoFlipBoardCard card : cards)
        {
            if (card == null)
            {
                continue;
            }

            int slot = card.getSlotIndex();
            if (slot < 0 || slot >= filtered.size())
            {
                changed = true;
                logAutoFlipVerbose(
                    "AUTOFLIP_BOARD_CARD_PRUNED"
                        + " reason=" + safe(reason)
                        + " slot=" + slot
                        + " item_id=" + card.getItemId()
                        + " prune_reason=slot_out_of_range"
                );
                continue;
            }

            if (!isAutoFlipGeSlotAvailableForRecommendation(slot))
            {
                logAutoFlipVerbose(
                    "AUTOFLIP_BOARD_CARD_PRUNED"
                        + " reason=" + safe(reason)
                        + " slot=" + slot
                        + " item_id=" + card.getItemId()
                        + " prune_reason=slot_unavailable"
                );
                changed = true;
                continue;
            }

            filtered.set(slot, card);
        }

        return changed ? filtered : cards;
    }

    private java.util.List<AutoFlipPayloadRow> localPayloadCurrentRankedUniverseRows()
    {
        return localPayloadCurrentRankedUniverseRows(!isAutoFlipFreeToPlayAccount());
    }

    private int findAutoFlipRefreshStartCursorForCard(java.util.List<AutoFlipPayloadRow> universe, AutoFlipBoardCard card)
    {
        if (universe == null || universe.isEmpty() || card == null || card.getItemId() <= 0)
        {
            return 0;
        }

        for (int i = 0; i < universe.size(); i++)
        {
            AutoFlipPayloadRow row = universe.get(i);
            if (row != null && row.itemId == card.getItemId())
            {
                return i + 1;
            }
        }

        return 0;
    }

    private java.util.List<AutoFlipPayloadRow> localPayloadCurrentRankedUniverseRows(boolean membersOnly)
    {
        String payload = autoFlipPayloadJson;
        if (payload == null || payload.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        String risk = "balanced";
        int hours = getAutoFlipPayloadHourBucket(autoFlipMenuHoursAway);
        long budget = getAutoFlipEffectiveBudgetGp();
        String budgetBand = getAutoFlipPayloadBudgetBand(budget);

        String rankedUniverseArray;
        String payloadSource;
        String sliceKey = risk + ":" + hours + "h:" + budgetBand;
        if (membersOnly)
        {
            String slicesObject = localPayloadExtractObjectField(payload, "slices");
            if (slicesObject.isEmpty())
            {
                return java.util.Collections.emptyList();
            }

            String sliceObject = localPayloadExtractObjectField(slicesObject, sliceKey);
            if (sliceObject.isEmpty())
            {
                logAutoFlipVerbose("AUTOFLIP_LOCAL_REFRESH missing_slice=" + sliceKey);
                return java.util.Collections.emptyList();
            }

            rankedUniverseArray = localPayloadExtractArrayField(sliceObject, "ranked_universe");
            payloadSource = "slices/" + sliceKey + "/ranked_universe";
        }
        else
        {
            String f2pBundleObject = localPayloadExtractObjectField(payload, "f2p_bundle");
            if (f2pBundleObject.isEmpty())
            {
                logAutoFlipVerbose("AUTOFLIP_LOCAL_REFRESH missing_f2p_bundle=true");
                return java.util.Collections.emptyList();
            }

            rankedUniverseArray = localPayloadExtractArrayField(f2pBundleObject, "ranked_universe");
            payloadSource = "f2p_bundle/ranked_universe";
        }

        java.util.List<String> objects = localPayloadExtractArrayObjects(rankedUniverseArray);
        java.util.List<AutoFlipPayloadRow> rows = new java.util.ArrayList<>();

        int index = 0;
        for (String obj : objects)
        {
            int quantity = readJsonInt(obj, "suggested_quantity", 1);
            AutoFlipRecommendationContext context = buildAutoFlipRecommendationContext(
                obj,
                "",
                sliceKey,
                "ranked_universe",
                index,
                quantity,
                budget,
                hours,
                budgetBand,
                risk,
                payloadSource
            );
            AutoFlipPayloadRow row = localPayloadRowFromJson(obj, index, quantity, hours, context);
            if (row != null && row.itemId > 0)
            {
                rows.add(row);
            }
            index++;
        }

        return rows;
    }

    private java.util.List<AutoFlipBoardCard> localPayloadRescaleBoardCards(java.util.List<AutoFlipBoardCard> cards, long budgetGp)
    {
        if (cards == null || cards.isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        java.util.List<AutoFlipPayloadRow> rows = new java.util.ArrayList<>();
        java.util.List<AutoFlipBoardCard> orderedCards = new java.util.ArrayList<>(cards.size());
        long reservedBudgetGp = 0L;

        for (AutoFlipBoardCard card : cards)
        {
            orderedCards.add(card);
            if (card == null)
            {
                continue;
            }

            if (isAutoFlipBudgetRebalanceCard(card))
            {
                rows.add(localPayloadRowFromBoardCard(card));
            }
            else
            {
                reservedBudgetGp += Math.max(0L, card.getPlannedCapitalGp());
            }
        }

        if (rows.isEmpty())
        {
            return orderedCards;
        }

        long deployableBudgetGp = Math.max(0L, budgetGp - reservedBudgetGp);
        java.util.List<AutoFlipPayloadRow> scaledRows = localPayloadScaleRowsToBudget(rows, deployableBudgetGp);
        java.util.Map<Integer, AutoFlipPayloadRow> scaledByItemId = new java.util.HashMap<>();
        for (AutoFlipPayloadRow row : scaledRows)
        {
            if (row != null && row.itemId > 0)
            {
                scaledByItemId.put(canonicalizeAutoFlipInventoryItemId(row.itemId), row);
            }
        }

        java.util.List<AutoFlipBoardCard> out = new java.util.ArrayList<>(orderedCards.size());
        for (int i = 0; i < orderedCards.size(); i++)
        {
            AutoFlipBoardCard card = orderedCards.get(i);
            if (card == null)
            {
                out.add(null);
                continue;
            }

            if (!isAutoFlipBudgetRebalanceCard(card))
            {
                out.add(card);
                continue;
            }

            AutoFlipPayloadRow scaled = scaledByItemId.get(canonicalizeAutoFlipInventoryItemId(card.getItemId()));
            int quantity = scaled == null ? 1 : Math.max(1, scaled.quantity);
            AutoFlipPayloadRow adjusted = localPayloadRowFromBoardCard(card).copyWithQuantity(quantity);
            out.add(localPayloadCardFromRow(adjusted, card.getSlotIndex()));
        }

        logAutoFlipVerbose(
            "AUTOFLIP_LOCAL_REBALANCE_DISPLAYED_BOARD"
                + " cards=" + out.size()
                + " budget_gp=" + budgetGp
                + " reserved_gp=" + reservedBudgetGp
                + " deployable_gp=" + deployableBudgetGp
                + " rows=" + rows.size()
                + " planned_gp=" + summarizeAutoFlipPlannedBudget(out)
        );
        while (out.size() < 8)
        {
            out.add(null);
        }
        return out;
    }

    private boolean isAutoFlipBudgetRebalanceCard(AutoFlipBoardCard card)
    {
        if (card == null || card.getItemId() <= 0 || card.getBuyPriceGp() <= 0L)
        {
            return false;
        }

        String riskLabel = card.getRiskLabel() == null
            ? ""
            : card.getRiskLabel().trim();
        return !"SELL".equalsIgnoreCase(riskLabel)
            && !"TO_BUY".equalsIgnoreCase(riskLabel);
    }

    private long summarizeAutoFlipPlannedBudget(java.util.List<AutoFlipBoardCard> cards)
    {
        if (cards == null || cards.isEmpty())
        {
            return 0L;
        }

        long total = 0L;
        for (AutoFlipBoardCard card : cards)
        {
            if (card != null)
            {
                total += Math.max(0L, card.getPlannedCapitalGp());
            }
        }
        return total;
    }

    private AutoFlipPayloadRow localPayloadRowFromBoardCard(AutoFlipBoardCard card)
    {
        return new AutoFlipPayloadRow(
            card.getItemId(),
            card.getItemName(),
            card.getBuyPriceGp(),
            card.getSellPriceGp(),
            card.getProfitEachGp(),
            card.getQuantity(),
            Math.max(1, card.getMaxQuantity()),
            card.getConfidence(),
            card.getRiskLabel(),
            card.getReason(),
            card.getMarketUrl(),
            card.getMaxHoldTimeLabel(),
            card.getRecommendationContext()
        );
    }
    private String buildAutoFlipSettingsPayload()
    {
        long cash = client != null && client.isClientThread() ? getCurrentCashStackGp() : Math.max(0L, autoFlipLastObservedCashStackGp);
        long budget = autoFlipMenuUseCashStack ? cash : autoFlipMenuManualBudgetGp;

        return "{"
            + "\"hours_away\":" + autoFlipMenuHoursAway + ","
            + "\"budget_gp\":" + budget + ","
            + "\"use_current_cash_stack\":" + autoFlipMenuUseCashStack + ","
            + "\"cash_stack_gp\":" + cash
            + "}";
    }

    private void openAutoFlipMarketUrl(String url)
    {
        if (url == null || url.trim().isEmpty())
        {
            return;
        }

        LinkBrowser.browse(url.trim());
    }
    public long getAutoFlipBoardBudgetPlannedGp()
    {
        long total = 0L;
        java.util.List<AutoFlipBoardCard> cards = autoFlipBoardCards;

        if (cards == null || cards.isEmpty())
        {
            return Math.max(0L, autoFlipLastNonEmptyBoardBudgetPlannedGp);
        }

        for (AutoFlipBoardCard card : cards)
        {
            if (card == null)
            {
                continue;
            }

            total += card.getPlannedCapitalGp();
        }

        if (total > 0L)
        {
            autoFlipLastNonEmptyBoardBudgetPlannedGp = total;
            return total;
        }

        return Math.max(0L, autoFlipLastNonEmptyBoardBudgetPlannedGp);
    }

    public long getAutoFlipBoardTotalExpectedProfitGp()
    {
        long total = 0L;
        java.util.List<AutoFlipBoardCard> cards = autoFlipBoardCards;

        if (cards == null || cards.isEmpty())
        {
            return Math.max(0L, autoFlipLastNonEmptyBoardTotalExpectedProfitGp);
        }

        for (AutoFlipBoardCard card : cards)
        {
            if (card == null)
            {
                continue;
            }

            total += Math.max(0L, card.getTotalProfitGp());
        }

        if (total > 0L)
        {
            autoFlipLastNonEmptyBoardTotalExpectedProfitGp = total;
            return total;
        }

        return Math.max(0L, autoFlipLastNonEmptyBoardTotalExpectedProfitGp);
    }
    private volatile java.util.List<AutoFlipCardActionBounds> autoFlipCardActionBounds = java.util.Collections.emptyList();
    private volatile java.util.List<Rectangle> autoFlipCardBlockBounds = java.util.Collections.emptyList();
    private volatile java.util.List<AutoFlipGeMarketLinkBounds> autoFlipGeMarketLinkBounds = java.util.Collections.emptyList();
    // Native Buy/Sell pass-through holes need their own slot->item memory because they are not consumed.
    private volatile java.util.List<AutoFlipNativeButtonHoleBounds> autoFlipNativeButtonHoleBounds = java.util.Collections.emptyList();
    private volatile int autoFlipLastNativeButtonSlotIndex = -1;
    private volatile int autoFlipLastNativeButtonItemId = 0;
    private volatile long autoFlipLastNativeButtonRememberedAtMs = 0L;
    private volatile int autoFlipPendingGuidedSetupItemId = 0;
    private volatile String autoFlipPendingGuidedSetupItemName = "";
    private volatile boolean autoFlipLastSellTargetWasInventoryClick = false;
    private volatile long autoFlipPendingBuySearchSeedDeadlineMs = 0L;
    private volatile int autoFlipPendingBuySearchSeedAttempts = 0;
    private volatile Rectangle autoFlipQuantityChatboxButtonBounds = null;
    private volatile String autoFlipQuantityChatboxButtonLabel = "";
    private volatile int autoFlipQuantityChatboxQty = 0;
    private volatile boolean autoFlipQuantityPromptAutoFillLocked = false;
    private volatile boolean autoFlipQuantityPromptManualChoiceMade = false;
    private volatile int autoFlipQuantityPromptManualSelectedQty = 0;
    // AUTOFLIP_NATIVE_PROMPT_AUTOFILL_STATE_V12
    private volatile String autoFlipLastNativePromptAutoFillKind = "";
    private volatile String autoFlipLastNativePromptAutoFillValue = "";
    private volatile long autoFlipLastNativePromptAutoFillAtMs = 0L;
    private volatile long autoFlipLastNativePromptAutoFillCheckMs = 0L;
    // AUTOFLIP_GUIDED_SETUP_FLOW_STATE_V15
    private volatile String autoFlipGuidedSetupStage = "";
    private volatile long autoFlipGuidedSetupStageAtMs = 0L;
    // Tracks official guided setup state so native Confirm clicks can clear prompt/search ownership
    // even when the click lands outside the AutoFlip helper bounds.
    private volatile String autoFlipLastOfficialGuidedSetupState = "";
    private volatile int autoFlipLastGeSearchInjectedItemId = 0;
    private volatile String autoFlipLastGeSearchInjectedText = "";
    private volatile long autoFlipLastGeSearchInjectedAtMs = 0L;

    public void clearAutoFlipCardActionBounds()
    {
        autoFlipCardActionBounds = java.util.Collections.emptyList();
    }

    public void clearAutoFlipGeMarketLinkBounds()
    {
        autoFlipGeMarketLinkBounds = java.util.Collections.emptyList();
    }

    public void rememberAutoFlipGeMarketLinkBounds(int slotIndex, int itemId, String itemName, Rectangle bounds)
    {
        if (itemId <= 0 || bounds == null)
        {
            return;
        }

        java.util.List<AutoFlipGeMarketLinkBounds> existing = autoFlipGeMarketLinkBounds;
        java.util.List<AutoFlipGeMarketLinkBounds> updated = new java.util.ArrayList<>();
        if (existing != null)
        {
            for (AutoFlipGeMarketLinkBounds entry : existing)
            {
                if (entry != null && entry.slotIndex != slotIndex)
                {
                    updated.add(entry);
                }
            }
        }

        updated.add(new AutoFlipGeMarketLinkBounds(
            slotIndex,
            itemId,
            getAutoFlipMarketItemUrl(itemId, itemName),
            new Rectangle(bounds)
        ));
        autoFlipGeMarketLinkBounds = java.util.Collections.unmodifiableList(updated);
    }

    private boolean handleAutoFlipGeMarketLinkClick(int mouseX, int mouseY)
    {
        java.util.List<AutoFlipGeMarketLinkBounds> links = autoFlipGeMarketLinkBounds;
        if (!geWindowOpenForOverlay || links == null || links.isEmpty())
        {
            return false;
        }

        for (int i = links.size() - 1; i >= 0; i--)
        {
            AutoFlipGeMarketLinkBounds link = links.get(i);
            if (link != null && link.bounds != null && link.bounds.contains(mouseX, mouseY))
            {
                logAutoFlipMenuEvent("ge_slot_market_url_clicked_slot_" + link.slotIndex + "_item_" + link.itemId);
                openAutoFlipMarketUrl(link.marketUrl);
                return true;
            }
        }
        return false;
    }

    public void clearAutoFlipCardBlockBounds()
    {
        autoFlipCardBlockBounds = java.util.Collections.emptyList();
        autoFlipNativeButtonHoleBounds = java.util.Collections.emptyList();
    }

    public void rememberAutoFlipCardBlockBounds(Rectangle bounds)
    {
        if (bounds == null)
        {
            return;
        }

        java.util.List<Rectangle> existing = autoFlipCardBlockBounds;
        java.util.List<Rectangle> updated = new java.util.ArrayList<>();

        if (existing != null)
        {
            updated.addAll(existing);
        }

        updated.add(new Rectangle(bounds));
        autoFlipCardBlockBounds = java.util.Collections.unmodifiableList(updated);
    }
    public void rememberAutoFlipNativeButtonHoleBounds(int slotIndex, int itemId, Rectangle bounds)
    {
        if (bounds == null || itemId <= 0)
        {
            return;
        }

        java.util.List<AutoFlipNativeButtonHoleBounds> existing = autoFlipNativeButtonHoleBounds;
        java.util.List<AutoFlipNativeButtonHoleBounds> updated = new java.util.ArrayList<>();
        Rectangle newBounds = new Rectangle(bounds);

        if (existing != null)
        {
            for (AutoFlipNativeButtonHoleBounds entry : existing)
            {
                if (entry == null || entry.bounds == null)
                {
                    continue;
                }
                // Native Buy/Sell hole rectangles are reused across recommendations. Replace stale
                // entries for the same slot or same physical hole so the next recommendation does not
                // inherit the previous offer target item.
                if (entry.slotIndex == slotIndex || entry.bounds.equals(newBounds) || entry.bounds.intersects(newBounds))
                {
                    continue;
                }

                updated.add(entry);
            }
        }

        updated.add(new AutoFlipNativeButtonHoleBounds(slotIndex, itemId, newBounds));
        autoFlipNativeButtonHoleBounds = java.util.Collections.unmodifiableList(updated);
    }
    private AutoFlipNativeButtonHoleBounds findAutoFlipNativeButtonHoleBounds(int mouseX, int mouseY)
    {
        java.util.List<AutoFlipNativeButtonHoleBounds> bounds = autoFlipNativeButtonHoleBounds;
        if (bounds == null || bounds.isEmpty())
        {
            return null;
        }
        // Prefer the newest remembered native hole. Old entries can share the same screen
        // rectangle after moving from recommendation one to recommendation two.
        for (int i = bounds.size() - 1; i >= 0; i--)
        {
            AutoFlipNativeButtonHoleBounds entry = bounds.get(i);
            if (entry != null && entry.bounds != null && entry.bounds.contains(mouseX, mouseY))
            {
                return entry;
            }
        }

        return null;
    }

    private void rememberAutoFlipNativeButtonHoleSelection(int mouseX, int mouseY)
    {
        AutoFlipNativeButtonHoleBounds entry = findAutoFlipNativeButtonHoleBounds(mouseX, mouseY);
        if (entry == null || entry.itemId <= 0)
        {
            String title = getGeHeaderTextForOverlay();
                if (title == null || !title.startsWith("Grand Exchange: Set up offer"))
                {
                    autoFlipLastNativeButtonSlotIndex = -1;
                    autoFlipLastNativeButtonItemId = 0;
                    autoFlipLastNativeButtonRememberedAtMs = 0L;
                    autoFlipLastSellTargetWasInventoryClick = false;
                }
                return;
            }

        autoFlipLastNativeButtonSlotIndex = entry.slotIndex;
        autoFlipLastNativeButtonItemId = entry.itemId;
        autoFlipLastNativeButtonRememberedAtMs = System.currentTimeMillis();
        autoFlipLastSellTargetWasInventoryClick = false;

        AutoFlipBoardCard rememberedCard = getAutoFlipSetupTargetBoardCard();
        if (rememberedCard == null)
        {
            rememberedCard = findAutoFlipBoardCardBySlotIndexOrItemId(entry.slotIndex, entry.itemId);
        }
        if (rememberedCard != null)
        {
            autoFlipPendingGuidedSetupItemId = rememberedCard.getItemId();
            autoFlipPendingGuidedSetupItemName = rememberedCard.getItemName() == null ? "" : rememberedCard.getItemName();
            boolean buyRecommendation = !isAutoFlipSellPlanBoardCard(rememberedCard);
            if (buyRecommendation)
            {
                autoFlipPendingBuySearchSeedDeadlineMs = System.currentTimeMillis() + 5000L;
                autoFlipPendingBuySearchSeedAttempts = 0;
            }
            else
            {
                autoFlipPendingBuySearchSeedDeadlineMs = 0L;
                autoFlipPendingBuySearchSeedAttempts = 0;
            }
            injectAutoFlipGeSearchText(rememberedCard.getItemName(), rememberedCard.getItemId());
        }
    }

    private void retryAutoFlipPendingBuySearchSeedIfReady()
    {
        long deadline = autoFlipPendingBuySearchSeedDeadlineMs;
        if (deadline <= 0L)
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (now > deadline || autoFlipPendingGuidedSetupItemId <= 0
            || autoFlipPendingGuidedSetupItemName == null
            || autoFlipPendingGuidedSetupItemName.trim().isEmpty())
        {
            autoFlipPendingBuySearchSeedDeadlineMs = 0L;
            autoFlipPendingBuySearchSeedAttempts = 0;
            return;
        }

        Widget promptWidget = client == null ? null : client.getWidget(10616875);
        Widget inputWidget = client == null ? null : client.getWidget(10616876);
        if (promptWidget == null || inputWidget == null)
        {
            return;
        }

        String promptText = cleanWidgetText(promptWidget.getText()).toLowerCase(java.util.Locale.ROOT);
        if (!promptText.contains("what would you like to buy"))
        {
            return;
        }

        String targetName = autoFlipPendingGuidedSetupItemName.trim();
        String currentInput = cleanWidgetText(inputWidget.getText()).toLowerCase(java.util.Locale.ROOT);
        if (currentInput.contains(targetName.toLowerCase(java.util.Locale.ROOT))
            || getAutoFlipGuidedSetupTargetSearchResultBoundsByItemId() != null)
        {
            autoFlipPendingBuySearchSeedDeadlineMs = 0L;
            autoFlipPendingBuySearchSeedAttempts = 0;
            return;
        }

        autoFlipPendingBuySearchSeedAttempts++;
        injectAutoFlipGeSearchText(targetName, autoFlipPendingGuidedSetupItemId, true);
    }

    private AutoFlipBoardCard findAutoFlipBoardCardBySlotIndexOrItemId(int slotIndex, int itemId)
    {
        try
        {
            java.util.List<AutoFlipBoardCard> cards = getAutoFlipBoardCardsSnapshot();
            if (cards == null || cards.isEmpty())
            {
                return null;
            }

            for (AutoFlipBoardCard card : cards)
            {
                if (card == null)
                {
                    continue;
                }

                if (!isAutoFlipGeSlotUsableForCurrentAccount(card.getSlotIndex()))
                {
                    continue;
                }

                if (itemId > 0 && card.getItemId() == itemId)
                {
                    return card;
                }

                if (slotIndex >= 0 && card.getSlotIndex() == slotIndex)
                {
                    return card;
                }
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("findAutoFlipBoardCardBySlotIndexOrItemId", error);
        }

        return null;
    }

    public AutoFlipBoardCard getAutoFlipSetupTargetBoardCard()
    {
        java.util.List<AutoFlipBoardCard> cards = getAutoFlipBoardCardsSnapshot();
        if (cards == null || cards.isEmpty())
        {
            logAutoFlipVerbose(
                "AUTOFLIP_SETUP_TARGET_DEBUG"
                    + " result=null"
                    + " reason=no_cards"
                    + " remembered_item_id=" + autoFlipLastNativeButtonItemId
                    + " remembered_slot=" + autoFlipLastNativeButtonSlotIndex
                    + " inventory_click=" + autoFlipLastSellTargetWasInventoryClick
            );
            return null;
        }

        int rememberedItemId = autoFlipLastNativeButtonItemId;
        int rememberedSlot = autoFlipLastNativeButtonSlotIndex;

        logAutoFlipVerbose(
            "AUTOFLIP_SETUP_TARGET_DEBUG"
                + " phase=entry"
                + " remembered_item_id=" + rememberedItemId
                + " remembered_slot=" + rememberedSlot
                + " inventory_click=" + autoFlipLastSellTargetWasInventoryClick
                + " card_count=" + cards.size()
                + " cards=" + describeAutoFlipBoardCards(cards)
        );

        if (rememberedItemId > 0)
        {
            for (AutoFlipBoardCard card : cards)
            {
                if (card != null && card.getItemId() == rememberedItemId)
                {
                    if (!isAutoFlipGeSlotUsableForCurrentAccount(card.getSlotIndex()))
                    {
                        logAutoFlipVerbose(
                            "AUTOFLIP_SETUP_TARGET_DEBUG"
                                + " phase=skip_item_match"
                                + " reason=slot_unusable"
                                + " item_id=" + rememberedItemId
                                + " slot=" + card.getSlotIndex()
                                + " inventory_click=" + autoFlipLastSellTargetWasInventoryClick
                        );
                        continue;
                    }
                    logAutoFlipVerbose(
                        "AUTOFLIP_SETUP_TARGET_DEBUG"
                            + " result=item_match"
                            + " item_id=" + rememberedItemId
                            + " slot=" + card.getSlotIndex()
                            + " inventory_click=" + autoFlipLastSellTargetWasInventoryClick
                    );
                    return card;
                }
            }

            if (autoFlipLastSellTargetWasInventoryClick)
            {
                // Inventory-origin sells should resolve strictly by item id, not by the last GE slot.
                logAutoFlipVerbose(
                    "AUTOFLIP_SETUP_TARGET_DEBUG"
                        + " result=null"
                        + " reason=inventory_click_no_item_match"
                        + " remembered_item_id=" + rememberedItemId
                        + " remembered_slot=" + rememberedSlot
                        + " card_count=" + cards.size()
                );
                return null;
            }
        }

        if (rememberedSlot >= 0)
        {
            for (AutoFlipBoardCard card : cards)
            {
                if (card != null && card.getSlotIndex() == rememberedSlot)
                {
                    if (!isAutoFlipGeSlotUsableForCurrentAccount(card.getSlotIndex()))
                    {
                        logAutoFlipVerbose(
                            "AUTOFLIP_SETUP_TARGET_DEBUG"
                                + " phase=skip_slot_match"
                                + " reason=slot_unusable"
                                + " slot=" + rememberedSlot
                                + " item_id=" + card.getItemId()
                                + " inventory_click=" + autoFlipLastSellTargetWasInventoryClick
                        );
                        continue;
                    }
                    logAutoFlipVerbose(
                        "AUTOFLIP_SETUP_TARGET_DEBUG"
                            + " result=slot_match"
                            + " slot=" + rememberedSlot
                            + " item_id=" + card.getItemId()
                            + " inventory_click=" + autoFlipLastSellTargetWasInventoryClick
                    );
                    return card;
                }
            }
        }
        // Never fall back to slot 0 for setup screens. If the click did not come from
        // a remembered AutoFlip native Buy/Sell hole, no AutoFlip setup overlay should render.
        logAutoFlipVerbose(
            "AUTOFLIP_SETUP_TARGET_DEBUG"
                + " result=null"
                + " reason=no_match_no_fallback"
                + " remembered_item_id=" + rememberedItemId
                + " remembered_slot=" + rememberedSlot
                + " inventory_click=" + autoFlipLastSellTargetWasInventoryClick
        );
        return null;
    }
    private boolean isInsideAutoFlipCardBlockBounds(int mouseX, int mouseY)
    {
        java.util.List<Rectangle> bounds = autoFlipCardBlockBounds;
        if (bounds == null || bounds.isEmpty())
        {
            return false;
        }

        for (Rectangle entry : bounds)
        {
            if (entry != null && entry.contains(mouseX, mouseY))
            {
                return true;
            }
        }

        return false;
    }

    public void rememberAutoFlipCardActionBounds(int slotIndex, int itemId, String marketUrl, Rectangle refreshBounds, Rectangle marketBounds)
    {
        java.util.List<AutoFlipCardActionBounds> existing = autoFlipCardActionBounds;
        java.util.List<AutoFlipCardActionBounds> updated = new java.util.ArrayList<>();
        Rectangle newRefreshBounds = refreshBounds == null ? null : new Rectangle(refreshBounds);
        Rectangle newMarketBounds = marketBounds == null ? null : new Rectangle(marketBounds);

        if (existing != null)
        {
            updated.addAll(existing);
            for (java.util.Iterator<AutoFlipCardActionBounds> it = updated.iterator(); it.hasNext(); )
            {
                AutoFlipCardActionBounds entry = it.next();
                if (entry == null)
                {
                    continue;
                }
                // Board cards reuse the same physical action rectangles across recomputes.
                // Replace stale entries for the same slot or overlapping rectangle so the
                // current card owns the click target.
                if (entry.slotIndex == slotIndex
                    || (newRefreshBounds != null && (entry.refreshBounds.equals(newRefreshBounds) || entry.refreshBounds.intersects(newRefreshBounds)))
                    || (newMarketBounds != null && (entry.marketBounds.equals(newMarketBounds) || entry.marketBounds.intersects(newMarketBounds))))
                {
                    it.remove();
                }
            }
        }

        updated.add(new AutoFlipCardActionBounds(slotIndex, itemId, marketUrl, newRefreshBounds, newMarketBounds));
        autoFlipCardActionBounds = java.util.Collections.unmodifiableList(updated);
    }

    private AutoFlipCardActionBounds findAutoFlipCardActionBounds(int mouseX, int mouseY)
    {
        java.util.List<AutoFlipCardActionBounds> bounds = autoFlipCardActionBounds;
        if (bounds == null || bounds.isEmpty())
        {
            return null;
        }

        for (int i = bounds.size() - 1; i >= 0; i--)
        {
            AutoFlipCardActionBounds entry = bounds.get(i);
            if (entry == null)
            {
                continue;
            }

            if ((entry.refreshBounds != null && entry.refreshBounds.contains(mouseX, mouseY))
                || (entry.marketBounds != null && entry.marketBounds.contains(mouseX, mouseY)))
            {
                return entry;
            }
        }

        return null;
    }

    private boolean handleAutoFlipCardActionClick(int mouseX, int mouseY)
    {
        AutoFlipCardActionBounds bounds = findAutoFlipCardActionBounds(mouseX, mouseY);
        if (bounds == null)
        {
            return false;
        }

        if (bounds.refreshBounds != null && bounds.refreshBounds.contains(mouseX, mouseY))
        {
            if (bounds.itemId > 0)
            {
                logAutoFlipMenuEvent("skip_item_clicked");
                triggerAutoFlipSkipItem(bounds.slotIndex, bounds.itemId);
            }
            else
            {
                // AUTOFLIP_PLACEHOLDER_REFRESH_FALLBACK_V1
                // Placeholder cards have no real item id yet; refresh the board instead of sending item_id=0.
                logAutoFlipMenuEvent("skip_placeholder_board_clicked");
                triggerAutoFlipRefreshBoard();
            }
            return true;
        }

        if (bounds.marketBounds != null && bounds.marketBounds.contains(mouseX, mouseY))
        {
            logAutoFlipMenuEvent("market_url_clicked");
            openAutoFlipMarketUrl(bounds.marketUrl);
            return true;
        }

        return true;
    }

    private void clearAutoFlipOrdinaryBuyState()
    {
        if (isAutoFlipNativePricePromptVisible())
        {
            return;
        }

        autoFlipOrdinaryBuySetupItemId = 0;
        autoFlipOrdinaryBuySetupItemName = "";
        autoFlipOrdinaryBuySetupLastSeenMs = 0L;
        autoFlipOrdinaryBuyCurrentPriceGp = 0;
        autoFlipOrdinaryBuySuggestedPriceGp = 0;
        autoFlipOrdinaryBuyLastBoughtPriceGp = 0;
        autoFlipOrdinaryBuyLastBoughtAtMs = 0L;
        autoFlipOrdinaryBuyAutoFillItemId = 0;
        autoFlipOrdinaryBuyRecommendedButtonBounds = null;
        autoFlipOrdinaryBuyLastBoughtButtonBounds = null;
        autoFlipOrdinaryBuyTargetPriceButtonBounds = null;
        autoFlipOrdinaryBuyRecommendedButtonLabel = "";
        autoFlipOrdinaryBuyLastBoughtButtonLabel = "";
        autoFlipOrdinaryBuyTargetPriceButtonLabel = "";
        autoFlipOrdinaryBuyTargetPriceGp = 0;
        autoFlipOrdinaryBuyManualSelectedPriceGp = 0;
        autoFlipOrdinaryBuyPricePromptManualChoiceMade = false;
    }

    private void refreshAutoFlipTargetBuyPriceOption()
    {
        AutoFlipBoardCard card = getAutoFlipSetupTargetBoardCard();
        AutoFlipToBuyItem toBuyItem = card == null || !"TO_BUY".equalsIgnoreCase(card.getRiskLabel())
            ? null
            : findAutoFlipToBuyItem(card.getItemId());
        if (toBuyItem == null || toBuyItem.getTargetBuyPriceGp() <= 0L || client == null)
        {
            autoFlipOrdinaryBuyTargetPriceButtonBounds = null;
            autoFlipOrdinaryBuyTargetPriceButtonLabel = "";
            autoFlipOrdinaryBuyTargetPriceGp = 0;
            return;
        }

        Widget promptWidget = client.getWidget(10616875);
        Widget inputWidget = client.getWidget(10616876);
        String promptText = promptWidget == null ? "" : cleanWidgetText(promptWidget.getText());
        if (promptWidget == null || inputWidget == null
            || promptWidget.isHidden() || inputWidget.isHidden()
            || !isAutoFlipNativePricePromptText(promptText))
        {
            autoFlipOrdinaryBuyTargetPriceButtonBounds = null;
            return;
        }

        Rectangle anchor = inputWidget.getBounds();
        if (anchor == null || anchor.width <= 0)
        {
            autoFlipOrdinaryBuyTargetPriceButtonBounds = null;
            return;
        }

        int gap = 4;
        int width = Math.max(74, (anchor.width - gap * 2) / 3);
        int startX = anchor.x + Math.max(0, (anchor.width - (width * 3 + gap * 2)) / 2);
        int y = anchor.y + anchor.height + 3;
        autoFlipOrdinaryBuyRecommendedButtonBounds = new Rectangle(startX, y, width, 20);
        autoFlipOrdinaryBuyLastBoughtButtonBounds = new Rectangle(startX + width + gap, y, width, 20);
        autoFlipOrdinaryBuyTargetPriceButtonBounds = new Rectangle(startX + (width + gap) * 2, y, width, 20);
        autoFlipOrdinaryBuySuggestedPriceGp = (int) Math.min(Integer.MAX_VALUE,
            Math.max(1L, getAutoFlipCachedBuyPriceGp(card.getItemId())));
        autoFlipOrdinaryBuyLastBoughtPriceGp = (int) Math.min(Integer.MAX_VALUE,
            Math.max(1L, autoFlipLastBoughtPriceByItemId.getOrDefault(card.getItemId(), card.getBuyPriceGp())));
        autoFlipOrdinaryBuyTargetPriceGp = (int) Math.min(Integer.MAX_VALUE, toBuyItem.getTargetBuyPriceGp());
        autoFlipOrdinaryBuyRecommendedButtonLabel = "Autoflip " + formatGp(autoFlipOrdinaryBuySuggestedPriceGp);
        autoFlipOrdinaryBuyLastBoughtButtonLabel = "Last bought " + formatGp(autoFlipOrdinaryBuyLastBoughtPriceGp);
        autoFlipOrdinaryBuyTargetPriceButtonLabel = "Your Target Price " + formatGp(autoFlipOrdinaryBuyTargetPriceGp);
    }

    private void refreshAutoFlipOrdinaryBuySetupSelectionFromVisibleUi()
    {
        try
        {
            boolean buyVisible = autoFlipStateDetectorVisibleTextContainsAnyStrict("buy offer");
            boolean sellVisible = autoFlipStateDetectorVisibleTextContainsAnyStrict("sell offer");
            String geTitle = getGeHeaderTextForOverlay();
            if (geTitle == null || !geTitle.startsWith("Grand Exchange: Set up offer") || !buyVisible || sellVisible)
            {
                clearAutoFlipOrdinaryBuyState();
                return;
            }

            Widget offerContainer = client == null ? null : client.getWidget(net.runelite.api.widgets.ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
            int itemId = offerContainer == null ? 0 : canonicalizeAutoFlipInventoryItemId(offerContainer.getItemId());
            if (itemId <= 0)
            {
                String visibleName = autoFlipFindVisibleOrdinarySellItemName();
                itemId = resolveAutoFlipItemIdByName(visibleName);
            }
            if (itemId <= 0)
            {
                clearAutoFlipOrdinaryBuyState();
                return;
            }

            AutoFlipBoardCard guidedTarget = getAutoFlipSetupTargetBoardCard();
            if (guidedTarget != null && guidedTarget.getItemId() == itemId)
            {
                clearAutoFlipOrdinaryBuyState();
                return;
            }

            if (autoFlipOrdinaryBuySetupItemId != itemId)
            {
                clearAutoFlipOrdinaryBuyState();
                autoFlipOrdinaryBuySetupItemId = itemId;
                autoFlipOrdinaryBuySetupItemName = resolveAutoFlipItemName(itemId, "Item " + itemId);
            }
            autoFlipOrdinaryBuySetupLastSeenMs = System.currentTimeMillis();
            autoFlipSellPriceDebugItemNameById.put(itemId, autoFlipOrdinaryBuySetupItemName);
            requestAutoFlipApiBuyPrices(java.util.Collections.singletonList(itemId));
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("refreshAutoFlipOrdinaryBuySetupSelectionFromVisibleUi", error);
        }
    }

    boolean isAutoFlipOrdinaryBuyPricePromptOpenForOverlay()
    {
        if (!isAutoFlipOrdinaryBuySetupOpenForOverlay()) return false;
        String prompt = autoFlipStateDetectorActiveNativePromptTextCache == null ? "" : autoFlipStateDetectorActiveNativePromptTextCache.toLowerCase(java.util.Locale.ROOT);
        return prompt.contains("set a price") || prompt.contains("price for each item");
    }

    private boolean isAutoFlipOrdinaryBuySetupOpenForOverlay()
    {
        String title = getGeHeaderTextForOverlay();
        return autoFlipOrdinaryBuySetupItemId > 0
            && title != null && title.startsWith("Grand Exchange: Set up offer")
            && autoFlipStateDetectorVisibleTextContainsAnyStrict("buy offer")
            && !autoFlipStateDetectorVisibleTextContainsAnyStrict("sell offer");
    }

    boolean isAutoFlipOrdinaryBuyPriceMismatchForOverlay()
    {
        long recommended = getAutoFlipCachedBuyPriceGp(autoFlipOrdinaryBuySetupItemId);
        boolean buyVisible = autoFlipStateDetectorVisibleTextContainsAnyStrict("buy offer");
        boolean sellVisible = autoFlipStateDetectorVisibleTextContainsAnyStrict("sell offer");
        return AutoFlipOrdinaryBuyEligibility.isVisible(
            autoFlipOrdinaryBuySetupItemId,
            autoFlipApiBuyPriceReadyItemIds.contains(autoFlipOrdinaryBuySetupItemId),
            recommended,
            getGeHeaderTextForOverlay(),
            buyVisible,
            sellVisible,
            isAutoFlipOrdinaryBuyPricePromptOpenForOverlay(),
            autoFlipOrdinaryBuyCurrentPriceGp
        );
    }

    private boolean isAutoFlipNativePricePromptVisible()
    {
        try
        {
            if (client == null)
            {
                return false;
            }

            net.runelite.api.widgets.Widget promptWidget = client.getWidget(10616875);
            net.runelite.api.widgets.Widget inputWidget = client.getWidget(10616876);
            if (promptWidget == null || inputWidget == null)
            {
                return false;
            }

            Rectangle promptBounds = promptWidget.getBounds();
            Rectangle inputBounds = inputWidget.getBounds();
            if (promptWidget.isHidden()
                || inputWidget.isHidden()
                || promptBounds == null
                || inputBounds == null
                || promptBounds.width <= 0
                || promptBounds.height <= 0
                || inputBounds.width <= 0
                || inputBounds.height <= 0)
            {
                return false;
            }

            return isAutoFlipNativePricePromptText(cleanWidgetText(promptWidget.getText()));
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    private void refreshAutoFlipOrdinaryBuyPriceOption()
    {
        if (!isAutoFlipOrdinaryBuySetupOpenForOverlay() || client == null)
        {
            if (!isAutoFlipOrdinaryBuySetupOpenForOverlay()) clearAutoFlipOrdinaryBuyState();
            return;
        }

        int itemId = autoFlipOrdinaryBuySetupItemId;
        long suggested = getAutoFlipCachedBuyPriceGp(itemId);
        if (suggested <= 0L)
        {
            requestAutoFlipApiBuyPrices(java.util.Collections.singletonList(itemId));
            return;
        }

        Widget promptWidget = client.getWidget(10616875);
        Widget inputWidget = client.getWidget(10616876);
        boolean livePromptWidgets = promptWidget != null
            && inputWidget != null
            && !promptWidget.isHidden()
            && !inputWidget.isHidden()
            && promptWidget.getBounds() != null
            && inputWidget.getBounds() != null
            && promptWidget.getBounds().width > 0
            && promptWidget.getBounds().height > 0
            && inputWidget.getBounds().width > 0
            && inputWidget.getBounds().height > 0;
        String promptText = livePromptWidgets
            ? cleanWidgetText(promptWidget.getText()).toLowerCase(java.util.Locale.ROOT)
            : "";
        autoFlipStateDetectorActiveNativePromptTextCache = promptText;
        boolean pricePrompt = isAutoFlipNativePricePromptText(promptText);
        int promptGp = inputWidget == null ? 0 : extractAutoFlipGpFromText(cleanWidgetText(inputWidget.getText()));
        autoFlipOrdinaryBuyCurrentPriceGp = promptGp > 0 ? promptGp : readAutoFlipVisibleOrdinarySellCurrentPriceGp();
        autoFlipOrdinaryBuySuggestedPriceGp = (int) Math.min(Integer.MAX_VALUE, suggested);
        autoFlipOrdinaryBuyLastBoughtPriceGp = (int) Math.min(Integer.MAX_VALUE,
            autoFlipLastBoughtPriceByItemId.getOrDefault(itemId, suggested));
        autoFlipOrdinaryBuyLastBoughtAtMs = autoFlipLastBoughtAtMsByItemId.getOrDefault(itemId, System.currentTimeMillis());
        int manualSelectedPriceGp = autoFlipOrdinaryBuyPricePromptManualChoiceMade
            ? autoFlipOrdinaryBuyManualSelectedPriceGp
            : 0;
        int effectivePriceGp = manualSelectedPriceGp > 0
            ? manualSelectedPriceGp
            : autoFlipOrdinaryBuySuggestedPriceGp;


        autoFlipOrdinaryBuyRecommendedButtonBounds = null;
        autoFlipOrdinaryBuyLastBoughtButtonBounds = null;
        if (pricePrompt)
        {
            Rectangle anchor = inputWidget != null && !inputWidget.isHidden() ? inputWidget.getBounds() : (promptWidget == null ? null : promptWidget.getBounds());
            if (anchor != null && anchor.width > 0)
            {
                int gap = 6;
                int width = Math.max(92, (anchor.width - gap) / 2);
                int startX = anchor.x + Math.max(0, (anchor.width - (width * 2 + gap)) / 2);
                int y = anchor.y + anchor.height + 3;
                autoFlipOrdinaryBuyRecommendedButtonBounds = new Rectangle(startX, y, width, 20);
                autoFlipOrdinaryBuyLastBoughtButtonBounds = new Rectangle(startX + width + gap, y, width, 20);
                autoFlipOrdinaryBuyRecommendedButtonLabel = "Autoflip recommended " + formatGp(autoFlipOrdinaryBuySuggestedPriceGp);
                autoFlipOrdinaryBuyLastBoughtButtonLabel = "Last bought "
                    + formatAutoFlipMinutesAgo(Math.max(0L, System.currentTimeMillis() - autoFlipOrdinaryBuyLastBoughtAtMs))
                    + " " + formatGp(autoFlipOrdinaryBuyLastBoughtPriceGp);
            }
            if (AutoFlipOrdinaryBuyEligibility.shouldAutoFillSuggestedPrice(
                itemId,
                autoFlipOrdinaryBuyAutoFillItemId,
                pricePrompt,
                promptGp,
                suggested,
                autoFlipOrdinaryBuyPricePromptManualChoiceMade))
            {
                injectAutoFlipPriceChatboxValue(effectivePriceGp, "ordinary_buy_refresh_autofill");
                autoFlipOrdinaryBuyAutoFillItemId = itemId;
            }
        }
    }

    private void maintainAutoFlipOrdinarySellSetupSelection()
    {
        if (autoFlipOrdinarySellSetupItemId <= 0)
        {
            return;
        }

        String geTitle = getGeHeaderTextForOverlay();
        String promptText = autoFlipOrdinarySellLastPromptText == null
            ? ""
            : autoFlipOrdinarySellLastPromptText.toLowerCase(java.util.Locale.ROOT);
        boolean pricePromptOpen = promptText.contains("set a price")
            || promptText.contains("price for each item");
        boolean sellOfferVisible = autoFlipStateDetectorVisibleTextContainsAnyStrict("sell offer");
        boolean buyOfferVisible = autoFlipStateDetectorVisibleTextContainsAnyStrict("buy offer");
        long now = System.currentTimeMillis();
        if (geTitle != null && geTitle.startsWith("Grand Exchange: Set up offer") && sellOfferVisible && !buyOfferVisible)
        {
            boolean selectedItemDetailsVisible = autoFlipStateDetectorVisibleTextContainsAnyStrict(
                "actively traded price",
                "buy limit",
                "convenience fee",
                "used for",
                "used to"
            );

            if (!pricePromptOpen && !selectedItemDetailsVisible)
            {
                appendLine(
                    ORDINARY_SELL_DEBUG_LOG,
                    now()
                        + " maintain_clear_due_no_selected_item"
                        + " item_id=" + autoFlipOrdinarySellSetupItemId
                        + " item_name=" + safe(autoFlipOrdinarySellSetupItemName)
                        + " ge_title=" + safe(geTitle)
                        + " prompt_open=" + pricePromptOpen
                        + " sell_visible=" + sellOfferVisible
                        + " buy_visible=" + buyOfferVisible
                        + " details_visible=" + selectedItemDetailsVisible
                );
                clearAutoFlipOrdinarySellSetupState();
                return;
            }

            autoFlipOrdinarySellSetupLastSeenMs = now;
            appendLine(
                ORDINARY_SELL_DEBUG_LOG,
                now()
                    + " maintain_keep_alive"
                    + " item_id=" + autoFlipOrdinarySellSetupItemId
                    + " item_name=" + safe(autoFlipOrdinarySellSetupItemName)
                    + " ge_title=" + safe(geTitle)
                    + " prompt_open=" + pricePromptOpen
                    + " sell_visible=" + sellOfferVisible
                    + " buy_visible=" + buyOfferVisible
                    + " details_visible=" + selectedItemDetailsVisible
            );
            return;
        }

        if (buyOfferVisible)
        {
            appendLine(
                ORDINARY_SELL_DEBUG_LOG,
                now()
                    + " maintain_clear_due_buy_offer_visible"
                    + " item_id=" + autoFlipOrdinarySellSetupItemId
                    + " item_name=" + safe(autoFlipOrdinarySellSetupItemName)
                    + " ge_title=" + safe(geTitle)
                    + " prompt_open=" + pricePromptOpen
                    + " sell_visible=" + sellOfferVisible
                    + " buy_visible=" + buyOfferVisible
            );
            clearAutoFlipOrdinarySellSetupState();
            return;
        }

        if (geTitle != null && geTitle.startsWith("Grand Exchange: Set up offer") && pricePromptOpen && sellOfferVisible)
        {
            autoFlipOrdinarySellSetupLastSeenMs = now;
            appendLine(
                ORDINARY_SELL_DEBUG_LOG,
                now()
                    + " maintain_price_prompt_keep_alive"
                    + " item_id=" + autoFlipOrdinarySellSetupItemId
                    + " item_name=" + safe(autoFlipOrdinarySellSetupItemName)
                    + " ge_title=" + safe(geTitle)
                    + " prompt_open=" + pricePromptOpen
                    + " sell_visible=" + sellOfferVisible
                    + " buy_visible=" + buyOfferVisible
            );
            return;
        }

        if (now - autoFlipOrdinarySellSetupLastSeenMs > 1500L)
        {
            appendLine(
                ORDINARY_SELL_DEBUG_LOG,
                now()
                    + " maintain_clear_due_timeout"
                    + " item_id=" + autoFlipOrdinarySellSetupItemId
                    + " item_name=" + safe(autoFlipOrdinarySellSetupItemName)
                    + " last_seen_ms=" + autoFlipOrdinarySellSetupLastSeenMs
                    + " ge_title=" + safe(geTitle)
                    + " prompt_open=" + pricePromptOpen
                    + " sell_visible=" + sellOfferVisible
                    + " buy_visible=" + buyOfferVisible
            );
            clearAutoFlipOrdinarySellSetupState();
        }
    }

    private void clearAutoFlipOrdinarySellSetupState()
    {
        if (autoFlipLastSellTargetWasInventoryClick)
        {
            autoFlipPendingGuidedSetupItemId = 0;
            autoFlipPendingGuidedSetupItemName = "";
            autoFlipLastNativeButtonItemId = 0;
            autoFlipLastNativeButtonSlotIndex = -1;
            autoFlipLastNativeButtonRememberedAtMs = 0L;
        }
        autoFlipOrdinarySellSetupItemId = 0;
        autoFlipOrdinarySellSetupItemName = "";
        autoFlipOrdinarySellSetupLastSeenMs = 0L;
        autoFlipOrdinarySellSuggestedAutoFillItemId = 0;
        autoFlipOrdinarySellCurrentPriceGp = 0;
        autoFlipOrdinarySellCurrentPriceText = "";
        autoFlipOrdinarySellCurrentPriceSource = "";
        autoFlipOrdinarySellLastPromptText = "";
        autoFlipOrdinarySellSuggestedPriceGp = 0;
        autoFlipOrdinarySellLastSoldPriceGp = 0;
        autoFlipOrdinarySellLastSoldPriceUpdatedAtMs = 0L;
        autoFlipOrdinarySellSetupItemQuantity = 0;
        autoFlipOrdinarySellSuppressVisibleUiReseedUntilMs = 0L;
        autoFlipOrdinarySellSelectionLockUntilMs = 0L;
        autoFlipOrdinarySellForceWritePending = false;
        autoFlipOrdinarySellForceWriteGp = 0;
        autoFlipOrdinarySellPriceOptionVisible = false;
        autoFlipOrdinarySellPriceOptionGp = 0;
        autoFlipOrdinarySellPriceOptionWidget = null;
        autoFlipOrdinarySellRecommendedButtonBounds = null;
        autoFlipOrdinarySellRecommendedButtonLabel = "";
        autoFlipOrdinarySellLastSoldButtonBounds = null;
        autoFlipOrdinarySellLastSoldButtonLabel = "";
        autoFlipLastSellTargetWasInventoryClick = false;
        autoFlipStateDetectorActiveNativePromptTextCache = "";
        autoFlipStateDetectorLabelCache = "";
        hideAutoFlipOrdinarySellPriceOption();
    }

    private void refreshAutoFlipOrdinarySellSetupSelectionFromVisibleUi()
    {
        try
        {
            if (client == null)
            {
                return;
            }

            String geTitle = getGeHeaderTextForOverlay();
            if (geTitle == null || !geTitle.startsWith("Grand Exchange: Set up offer"))
            {
                return;
            }

            if (System.currentTimeMillis() < autoFlipOrdinarySellSuppressVisibleUiReseedUntilMs)
            {
                appendLine(
                    ORDINARY_SELL_DEBUG_LOG,
                    now()
                        + " visible_refresh_suppressed"
                        + " item_id=" + autoFlipOrdinarySellSetupItemId
                        + " item_name=" + safe(autoFlipOrdinarySellSetupItemName)
                        + " suppress_until_ms=" + autoFlipOrdinarySellSuppressVisibleUiReseedUntilMs
                        + " lock_until_ms=" + autoFlipOrdinarySellSelectionLockUntilMs
                        + " ge_title=" + safe(geTitle)
                );
                return;
            }

            autoFlipOrdinarySellSetupLastSeenMs = System.currentTimeMillis();

            int currentGeItemId = canonicalizeAutoFlipInventoryItemId(
                client.getVarpValue(net.runelite.api.VarPlayer.CURRENT_GE_ITEM)
            );
            boolean inventoryClickOwnsSelection = autoFlipLastSellTargetWasInventoryClick
                && autoFlipPendingGuidedSetupItemId > 0;
            int resolvedItemId = inventoryClickOwnsSelection
                ? canonicalizeAutoFlipInventoryItemId(autoFlipPendingGuidedSetupItemId)
                : currentGeItemId;
            String visibleName = inventoryClickOwnsSelection
                ? autoFlipPendingGuidedSetupItemName
                : autoFlipFindVisibleOrdinarySellItemName();
            if (resolvedItemId <= 0 && (visibleName == null || visibleName.trim().isEmpty()))
            {
                appendLine(
                    ORDINARY_SELL_DEBUG_LOG,
                    now()
                        + " visible_refresh_no_visible_name"
                        + " item_id=" + autoFlipOrdinarySellSetupItemId
                        + " item_name=" + safe(autoFlipOrdinarySellSetupItemName)
                        + " ge_title=" + safe(geTitle)
                );
                return;
            }

            if (resolvedItemId <= 0)
            {
                resolvedItemId = resolveAutoFlipItemIdByName(visibleName);
            }
            if (resolvedItemId <= 0)
            {
                appendLine(
                    ORDINARY_SELL_DEBUG_LOG,
                    now()
                        + " visible_refresh_unresolved_selection"
                        + " visible_name=" + safe(visibleName)
                        + " current_item_id=" + autoFlipOrdinarySellSetupItemId
                        + " current_item_name=" + safe(autoFlipOrdinarySellSetupItemName)
                        + " ge_title=" + safe(geTitle)
                );
                return;
            }

            String resolvedName = resolveAutoFlipItemName(resolvedItemId, visibleName);
            String normalizedVisibleName = resolvedName == null ? "" : resolvedName.trim();
            String currentVisibleName = autoFlipOrdinarySellSetupItemName == null
                ? ""
                : autoFlipOrdinarySellSetupItemName.trim();

            boolean selectionChanged = autoFlipOrdinarySellSetupItemId != resolvedItemId
                || !currentVisibleName.equalsIgnoreCase(normalizedVisibleName);
            if (selectionChanged)
            {
                appendLine(
                    ORDINARY_SELL_DEBUG_LOG,
                    now()
                        + " visible_refresh_selection_changed"
                        + " previous_item_id=" + autoFlipOrdinarySellSetupItemId
                        + " previous_item_name=" + safe(autoFlipOrdinarySellSetupItemName)
                        + " visible_name=" + safe(visibleName)
                        + " resolved_item_id=" + resolvedItemId
                        + " resolved_item_name=" + safe(resolveAutoFlipItemName(resolvedItemId, visibleName))
                        + " ge_title=" + safe(geTitle)
                );
                int pendingItemId = autoFlipPendingGuidedSetupItemId;
                String pendingItemName = autoFlipPendingGuidedSetupItemName;
                boolean preserveInventoryClickOwner = inventoryClickOwnsSelection;
                clearAutoFlipOrdinarySellSetupState();
                if (preserveInventoryClickOwner)
                {
                    autoFlipLastSellTargetWasInventoryClick = true;
                    autoFlipPendingGuidedSetupItemId = pendingItemId;
                    autoFlipPendingGuidedSetupItemName = pendingItemName;
                    autoFlipLastNativeButtonSlotIndex = -1;
                    autoFlipLastNativeButtonItemId = pendingItemId;
                    autoFlipLastNativeButtonRememberedAtMs = System.currentTimeMillis();
                }
            }

            autoFlipOrdinarySellSetupItemId = resolvedItemId;
            autoFlipOrdinarySellSetupItemName = resolvedName;
            autoFlipSellPriceDebugItemNameById.put(resolvedItemId, autoFlipOrdinarySellSetupItemName);
            autoFlipOrdinarySellSetupLastSeenMs = System.currentTimeMillis();
            requestAutoFlipApiSellPrice(resolvedItemId);
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("refreshAutoFlipOrdinarySellSetupSelectionFromVisibleUi", error);
        }
    }

    public boolean isAutoFlipOrdinarySellPriceMarkerVisibleForOverlay()
    {
        return isAutoFlipOrdinarySellPriceMarkerVisibleForOverlayInternal();
    }

    boolean isAutoFlipOrdinarySellPricePromptOpenForOverlay()
    {
        return isAutoFlipOrdinarySellPricePromptOpenForOverlayInternal();
    }

    boolean isAutoFlipOrdinarySellPriceMismatchForOverlay()
    {
        return isAutoFlipOrdinarySellPriceMismatchForOverlayInternal();
    }

    private boolean isAutoFlipOrdinarySellPriceMarkerVisibleForOverlayInternal()
    {
        int itemId = autoFlipOrdinarySellSetupItemId;
        String geTitle = getGeHeaderTextForOverlay();
        String promptText = autoFlipOrdinarySellLastPromptText == null ? "" : autoFlipOrdinarySellLastPromptText;
        boolean pricePromptOpen = isAutoFlipOrdinarySellPricePromptOpenForOverlayInternal();
        boolean visible = AutoFlipOrdinarySellEligibility.isVisible(
            itemId,
            isAutoFlipInventoryItem(itemId),
            autoFlipApiSellPriceReadyItemIds.contains(itemId),
            getAutoFlipCachedSellPriceGp(itemId),
            geTitle,
            pricePromptOpen,
            isAutoFlipVerboseRuntimeLoggingEnabled()
        );
        return visible && (pricePromptOpen
            || isAutoFlipOrdinarySellSetupOpenForOverlay()
            || isAutoFlipOrdinarySellPriceMismatchForOverlayInternal());
    }

    private boolean isAutoFlipOrdinarySellPricePromptOpenForOverlayInternal()
    {
        try
        {
            if (!isAutoFlipOrdinarySellSetupOpenForOverlay())
            {
                return false;
            }

            String promptText = autoFlipOrdinarySellLastPromptText == null || autoFlipOrdinarySellLastPromptText.trim().isEmpty()
                ? autoFlipStateDetectorActiveNativePromptTextCache
                : autoFlipOrdinarySellLastPromptText;
            String lower = promptText.toLowerCase(java.util.Locale.ROOT);
            return lower.contains("set a price")
                || lower.contains("price for each item");
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("isAutoFlipOrdinarySellPricePromptOpenForOverlay", error);
            return false;
        }
    }

    private boolean isAutoFlipOrdinarySellPriceMismatchForOverlayInternal()
    {
        try
        {
            if (!isAutoFlipOrdinarySellSetupOpenForOverlay())
            {
                return false;
            }

            int itemId = autoFlipOrdinarySellSetupItemId;
            long recommended = getAutoFlipCachedSellPriceGp(itemId);
            if (itemId <= 0 || recommended <= 0L)
            {
                return false;
            }

            int currentPriceGp = autoFlipOrdinarySellCurrentPriceGp;
            if (currentPriceGp <= 0)
            {
                return true;
            }

            return currentPriceGp != recommended;
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("isAutoFlipOrdinarySellPriceMismatchForOverlay", error);
            return false;
        }
    }

    private String resolveAutoFlipSellPriceDebugItemName(int itemId)
    {
        String capturedName = autoFlipSellPriceDebugItemNameById.get(itemId);
        if (capturedName != null && !capturedName.trim().isEmpty())
        {
            return capturedName.trim();
        }

        for (AutoFlipInventoryItem inventoryItem : autoFlipInventoryItems)
        {
            if (inventoryItem != null
                && isSameAutoFlipInventoryItemId(inventoryItem.getItemId(), itemId)
                && inventoryItem.getItemName() != null
                && !inventoryItem.getItemName().trim().isEmpty())
            {
                return inventoryItem.getItemName().trim();
            }
        }

        for (AutoFlipToBuyItem toBuyItem : autoFlipToBuyItems)
        {
            if (toBuyItem != null
                && isSameAutoFlipInventoryItemId(toBuyItem.getItemId(), itemId)
                && toBuyItem.getItemName() != null
                && !toBuyItem.getItemName().trim().isEmpty())
            {
                return toBuyItem.getItemName().trim();
            }
        }

        return resolveAutoFlipItemName(itemId, "Unknown item (" + itemId + ")");
    }

    private void refreshAutoFlipOrdinarySellPriceOption()
    {
        if (!isAutoFlipOrdinarySellPriceMarkerVisibleForOverlay() || client == null)
        {
            hideAutoFlipOrdinarySellPriceOption();
            return;
        }

        Widget promptWidget = client.getWidget(10616875);
        Widget inputWidget = client.getWidget(10616876);
        String promptText = promptWidget == null
            ? ""
            : cleanWidgetText(promptWidget.getText()).toLowerCase(java.util.Locale.ROOT);
        String inputText = inputWidget == null
            ? ""
            : cleanWidgetText(inputWidget.getText()).toLowerCase(java.util.Locale.ROOT);
        autoFlipStateDetectorActiveNativePromptTextCache = promptText;
        boolean pricePrompt = promptText.contains("set a price")
            || promptText.contains("price for each item");
        if (promptText.isEmpty() && pricePrompt)
        {
            promptText = "set a price for each item";
        }
        autoFlipOrdinarySellLastPromptText = promptText;

        int inputPriceGp = inputWidget == null ? 0 : extractAutoFlipGpFromText(inputText);
        if (inputPriceGp > 0)
        {
            autoFlipOrdinarySellCurrentPriceText = inputText;
            autoFlipOrdinarySellCurrentPriceGp = inputPriceGp;
            autoFlipOrdinarySellCurrentPriceSource = "prompt";
        }
        else
        {
            int visiblePriceGp = readAutoFlipVisibleOrdinarySellCurrentPriceGp();
            autoFlipOrdinarySellCurrentPriceGp = visiblePriceGp;
            autoFlipOrdinarySellCurrentPriceText = visiblePriceGp > 0
                ? Integer.toString(visiblePriceGp) + " coins"
                : inputText;
            autoFlipOrdinarySellCurrentPriceSource = visiblePriceGp > 0
                ? "offerModel"
                : (getAutoFlipCachedSellPriceGp(autoFlipOrdinarySellSetupItemId) > 0L ? "cache" : "unknown");
        }

        if (autoFlipOrdinarySellSetupItemId <= 0 || autoFlipOrdinarySellCurrentPriceGp <= 0)
        {
            hideAutoFlipOrdinarySellPriceOption();
            return;
        }

        int gp = (int) Math.min(Integer.MAX_VALUE, getAutoFlipCachedSellPriceGp(autoFlipOrdinarySellSetupItemId));
        if (gp <= 0)
        {
            hideAutoFlipOrdinarySellPriceOption();
            return;
        }

        Rectangle promptAnchor = null;
        if (inputWidget != null && !inputWidget.isHidden())
        {
            promptAnchor = inputWidget.getBounds();
        }
        if ((promptAnchor == null || promptAnchor.width <= 0 || promptAnchor.height <= 0)
            && promptWidget != null
            && !promptWidget.isHidden())
        {
            promptAnchor = promptWidget.getBounds();
        }

        autoFlipOrdinarySellRecommendedButtonBounds = null;
        autoFlipOrdinarySellRecommendedButtonLabel = "";
        autoFlipOrdinarySellLastSoldButtonBounds = null;
        autoFlipOrdinarySellLastSoldButtonLabel = "";

        if (promptAnchor != null && promptAnchor.width > 0 && promptAnchor.height > 0)
        {
            int buttonHeight = 20;
            int buttonGap = 6;
            int minButtonWidth = 116;
            int suggestedButtonWidth = Math.max(minButtonWidth, (promptAnchor.width - buttonGap) / 2);
            int lastSoldButtonWidth = suggestedButtonWidth;
            int totalWidth = suggestedButtonWidth + buttonGap + lastSoldButtonWidth;
            if (totalWidth > promptAnchor.width)
            {
                suggestedButtonWidth = Math.max(92, (promptAnchor.width - buttonGap) / 2);
                lastSoldButtonWidth = suggestedButtonWidth;
                totalWidth = suggestedButtonWidth + buttonGap + lastSoldButtonWidth;
            }

            int startX = promptAnchor.x + Math.max(0, (promptAnchor.width - totalWidth) / 2);
            int buttonY = promptAnchor.y + promptAnchor.height + 3;

            autoFlipOrdinarySellRecommendedButtonBounds = new Rectangle(startX, buttonY, suggestedButtonWidth, buttonHeight);
            autoFlipOrdinarySellRecommendedButtonLabel = "Autoflip recommended " + formatGp(gp);
            long lastSoldUpdatedAtMs = 0L;
            int lastSoldGp = 0;
            AutoFlipMarketSnapshot lastSoldSnapshot = autoFlipOrdinarySellSnapshotByItemId.get(autoFlipOrdinarySellSetupItemId);
            if (lastSoldSnapshot == null)
            {
                requestAutoFlipOrdinarySellMarketSnapshot(autoFlipOrdinarySellSetupItemId, autoFlipOrdinarySellSetupItemName);
            }
            if (lastSoldSnapshot != null && lastSoldSnapshot.currentSellPriceGp > 0L)
            {
                lastSoldGp = (int) Math.min(Integer.MAX_VALUE, lastSoldSnapshot.currentSellPriceGp);
                lastSoldUpdatedAtMs = parseIsoTsMs(lastSoldSnapshot.priceUpdatedAt);
            }
            if (lastSoldGp <= 0)
            {
                lastSoldGp = autoFlipOrdinarySellLastSoldPriceGp > 0 ? autoFlipOrdinarySellLastSoldPriceGp : gp;
            }
            if (lastSoldUpdatedAtMs <= 0L)
            {
                lastSoldUpdatedAtMs = autoFlipOrdinarySellLastSoldPriceUpdatedAtMs > 0L
                    ? autoFlipOrdinarySellLastSoldPriceUpdatedAtMs
                    : System.currentTimeMillis();
            }

            autoFlipOrdinarySellLastSoldPriceGp = lastSoldGp;
            autoFlipOrdinarySellLastSoldPriceUpdatedAtMs = lastSoldUpdatedAtMs;
            autoFlipOrdinarySellLastSoldButtonBounds = new Rectangle(
                startX + suggestedButtonWidth + buttonGap,
                buttonY,
                lastSoldButtonWidth,
                buttonHeight
            );
            autoFlipOrdinarySellLastSoldButtonLabel =
                "Last sold " + formatAutoFlipMinutesAgo(Math.max(0L, System.currentTimeMillis() - lastSoldUpdatedAtMs))
                    + " " + formatGp(lastSoldGp);
        }

        try
        {
            AutoFlipMarketSnapshot sellSnapshot = autoFlipOrdinarySellSnapshotByItemId.get(autoFlipOrdinarySellSetupItemId);
            if (sellSnapshot == null)
            {
                requestAutoFlipOrdinarySellMarketSnapshot(autoFlipOrdinarySellSetupItemId, autoFlipOrdinarySellSetupItemName);
            }

            autoFlipOrdinarySellSuggestedPriceGp = gp;
            autoFlipOrdinarySellPriceOptionVisible = true;
            autoFlipOrdinarySellPriceOptionGp = gp;

            if (pricePrompt
                && inputWidget != null
                && autoFlipOrdinarySellSuggestedAutoFillItemId != autoFlipOrdinarySellSetupItemId)
            {
                injectAutoFlipOrdinarySellSuggestedPriceOnce(autoFlipOrdinarySellSetupItemId, gp, false);
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("refreshAutoFlipOrdinarySellPriceOption", error);
            hideAutoFlipOrdinarySellPriceOption();
        }
    }

    private void hideAutoFlipOrdinarySellPriceOption()
    {
        autoFlipOrdinarySellPriceOptionVisible = false;
        autoFlipOrdinarySellPriceOptionGp = 0;
        autoFlipOrdinarySellSuggestedPriceGp = 0;
        autoFlipOrdinarySellLastSoldPriceGp = 0;
        autoFlipOrdinarySellLastSoldPriceUpdatedAtMs = 0L;
        autoFlipOrdinarySellSuggestedAutoFillItemId = 0;
        autoFlipOrdinarySellCurrentPriceGp = 0;
        autoFlipOrdinarySellCurrentPriceText = "";
        autoFlipOrdinarySellCurrentPriceSource = "";
        autoFlipOrdinarySellRecommendedButtonBounds = null;
        autoFlipOrdinarySellRecommendedButtonLabel = "";
        autoFlipOrdinarySellLastSoldButtonBounds = null;
        autoFlipOrdinarySellLastSoldButtonLabel = "";
    }

    private void requestAutoFlipOrdinarySellMarketSnapshot(int itemId, String fallbackName)
    {
        if (itemId <= 0 || !autoFlipOrdinarySellSnapshotRequestItemIds.add(itemId))
        {
            return;
        }

        ensurePricePrefetchExecutor();
        pricePrefetchExecutor.submit(() ->
        {
            try
            {
                AutoFlipMarketSnapshot snapshot = fetchAutoFlipMarketSnapshot(itemId, fallbackName);
                if (snapshot != null)
                {
                    autoFlipOrdinarySellSnapshotByItemId.put(itemId, snapshot);
                }
            }
            catch (Throwable error)
            {
                logAutoFlipUiError("requestAutoFlipOrdinarySellMarketSnapshot", error);
            }
            finally
            {
                autoFlipOrdinarySellSnapshotRequestItemIds.remove(itemId);
            }
        });
    }

    private void injectAutoFlipOrdinarySellSuggestedPriceOnce(int itemId, int gp, boolean force)
    {
        if (itemId <= 0 || gp <= 0)
        {
            return;
        }

        long now = System.currentTimeMillis();
        logAutoFlipVerbose(
            "AUTOFLIP_ORDINARY_SELL_INJECT_REQUEST"
                + " item_id=" + itemId
                + " gp=" + gp
                + " force=" + force
                + " suggested_item_id=" + autoFlipOrdinarySellSuggestedAutoFillItemId
                + " last_injected_gp=" + autoFlipLastInjectedPriceGp
                + " last_injected_at_ms=" + autoFlipLastInjectedPriceAtMs
                + " now_ms=" + now
        );
        if (!force
            && autoFlipOrdinarySellSuggestedAutoFillItemId == itemId
            && autoFlipLastInjectedPriceGp == gp
            && now - autoFlipLastInjectedPriceAtMs < 60_000L)
        {
            return;
        }

        injectAutoFlipPriceChatboxValue(gp, "ordinary_sell_suggested_price");
        autoFlipOrdinarySellSuggestedAutoFillItemId = itemId;
        autoFlipLastInjectedPriceGp = gp;
        autoFlipLastInjectedPriceAtMs = now;
    }

    private String formatAutoFlipMinutesAgo(long ageMs)
    {
        if (ageMs <= 0L)
        {
            return "0m ago";
        }

        long minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(ageMs);
        if (minutes <= 0L)
        {
            return "0m ago";
        }
        if (minutes < 60L)
        {
            return minutes + "m ago";
        }

        long hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(ageMs);
        if (hours < 24L)
        {
            return hours + "h ago";
        }

        long days = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(ageMs);
        return days + "d ago";
    }

    public boolean refreshAutoFlipPriceChatboxInjectState()
    {
        try
        {
            autoFlipPriceChatboxButtonBounds = null;
            autoFlipPriceChatboxButtonLabel = "";
            autoFlipPriceChatboxGp = 0;

            if (!isAutoFlipOverlayActive())
            {
                return false;
            }

            if (isAutoFlipOrdinarySellPricePromptOpenForOverlay())
            {
                int sellGp = autoFlipOrdinarySellSuggestedPriceGp > 0
                    ? autoFlipOrdinarySellSuggestedPriceGp
                    : (int) Math.min(Integer.MAX_VALUE, getAutoFlipCachedSellPriceGp(autoFlipOrdinarySellSetupItemId));
                if (sellGp <= 0)
                {
                    sellGp = autoFlipOrdinarySellLastSoldPriceGp > 0 ? autoFlipOrdinarySellLastSoldPriceGp : 0;
                }
                if (sellGp > 0)
                {
                    autoFlipPriceChatboxGp = sellGp;
                    autoFlipPriceChatboxButtonLabel = "Set AutoFlip Price: " + formatGp(sellGp) + " gp";
                    autoFlipPriceChatboxButtonBounds = getConfiguredAutoFlipPriceChatboxInjectBounds();
                    return autoFlipPriceChatboxButtonBounds != null
                        && autoFlipPriceChatboxButtonBounds.width > 0
                        && autoFlipPriceChatboxButtonBounds.height > 0;
                }
            }

            AutoFlipBoardCard card = getAutoFlipSetupTargetBoardCard();
            if (card == null)
            {
                return false;
            }

            long boardPrice = getAutoFlipBoardCardExpectedSetupPrice(card);
            if (boardPrice <= 0L)
            {
                return false;
            }

            int gp = (int) Math.min((long) Integer.MAX_VALUE, boardPrice);
            autoFlipPriceChatboxGp = gp;
            autoFlipPriceChatboxButtonLabel = "Set AutoFlip Price: " + formatGp(gp) + " gp";
            autoFlipPriceChatboxButtonBounds = getConfiguredAutoFlipPriceChatboxInjectBounds();

            return autoFlipPriceChatboxButtonBounds != null
                && autoFlipPriceChatboxButtonBounds.width > 0
                && autoFlipPriceChatboxButtonBounds.height > 0;
        }
        catch (Exception ex)
        {
            autoFlipPriceChatboxButtonBounds = null;
            autoFlipPriceChatboxButtonLabel = "";
            autoFlipPriceChatboxGp = 0;
            return false;
        }
    }

    public boolean refreshAutoFlipQuantityChatboxInjectState()
    {
        try
        {
            if (client == null || !autoFlipOverlayActive)
            {
                autoFlipQuantityChatboxButtonBounds = null;
                autoFlipQuantityChatboxButtonLabel = "";
                autoFlipQuantityChatboxQty = 0;
                autoFlipQuantityPromptAutoFillLocked = false;
                return false;
            }

            AutoFlipBoardCard card = getAutoFlipSetupTargetBoardCard();
            if (card == null || card.getQuantity() <= 0)
            {
                autoFlipQuantityChatboxButtonBounds = null;
                autoFlipQuantityChatboxButtonLabel = "";
                autoFlipQuantityChatboxQty = 0;
                autoFlipQuantityPromptAutoFillLocked = false;
                return false;
            }

            net.runelite.api.widgets.Widget promptWidget = client.getWidget(10616875);
            net.runelite.api.widgets.Widget inputWidget = client.getWidget(10616876);
            net.runelite.api.widgets.Widget optionWidget = client.getWidget(10616871);

                if (promptWidget == null || inputWidget == null || optionWidget == null)
                {
                    autoFlipQuantityChatboxButtonBounds = null;
                    autoFlipQuantityChatboxButtonLabel = "";
                    autoFlipQuantityChatboxQty = 0;
                    autoFlipQuantityPromptManualChoiceMade = false;
                    autoFlipQuantityPromptManualSelectedQty = 0;
                    autoFlipQuantityPromptAutoFillLocked = false;
                    return false;
                }

            String promptText = cleanWidgetText(promptWidget.getText()).toLowerCase(java.util.Locale.ROOT);
            if (!(promptText.contains("quantity") || promptText.contains("amount") || promptText.contains("how many")))
            {
                autoFlipQuantityChatboxButtonBounds = null;
                autoFlipQuantityChatboxButtonLabel = "";
                autoFlipQuantityChatboxQty = 0;
                autoFlipQuantityPromptManualChoiceMade = false;
                autoFlipQuantityPromptManualSelectedQty = 0;
                autoFlipQuantityPromptAutoFillLocked = false;
                return false;
            }

            String detectorState = getAutoFlipStateDetectorLabelForOverlay();
            if (!"state_7_quantity_prompt_open".equals(detectorState)
                && !"state_7b_to_buy_quantity_prompt_open".equals(detectorState))
            {
                autoFlipQuantityChatboxButtonBounds = null;
                autoFlipQuantityChatboxButtonLabel = "";
                autoFlipQuantityChatboxQty = 0;
                autoFlipQuantityPromptManualChoiceMade = false;
                autoFlipQuantityPromptManualSelectedQty = 0;
                autoFlipQuantityPromptAutoFillLocked = false;
                return false;
            }

            Rectangle bounds = inputWidget.getBounds();
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
            {
                autoFlipQuantityChatboxButtonBounds = null;
                autoFlipQuantityChatboxButtonLabel = "";
                autoFlipQuantityChatboxQty = 0;
                autoFlipQuantityPromptManualChoiceMade = false;
                autoFlipQuantityPromptManualSelectedQty = 0;
                autoFlipQuantityPromptAutoFillLocked = false;
                return false;
            }

            int qty = Math.max(1, card.getQuantity());
            // Use the independent quantity configured rectangle.
            autoFlipQuantityChatboxButtonBounds = getConfiguredAutoFlipQuantityChatboxInjectBounds();
            autoFlipQuantityChatboxButtonLabel = "AF Qty: " + qty;
            autoFlipQuantityChatboxQty = qty;
            return true;
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("refreshAutoFlipQuantityChatboxInjectState", error);
            autoFlipQuantityChatboxButtonBounds = null;
            autoFlipQuantityChatboxButtonLabel = "";
            autoFlipQuantityChatboxQty = 0;
            autoFlipQuantityPromptManualChoiceMade = false;
            autoFlipQuantityPromptManualSelectedQty = 0;
            autoFlipQuantityPromptAutoFillLocked = false;
            return false;
        }
    }

    public Rectangle getAutoFlipQuantityChatboxButtonBounds()
    {
        refreshAutoFlipQuantityChatboxInjectState();
        Rectangle bounds = autoFlipQuantityChatboxButtonBounds;
        return bounds == null ? null : new Rectangle(bounds);
    }

    public String getAutoFlipQuantityChatboxButtonLabel()
    {
        refreshAutoFlipQuantityChatboxInjectState();
        return autoFlipQuantityChatboxButtonLabel == null ? "" : autoFlipQuantityChatboxButtonLabel;
    }
    private boolean handleAutoFlipQuantityChatboxInjectClick(int mouseX, int mouseY)
    {
        try
        {
            Rectangle bounds = getAutoFlipQuantityChatboxButtonBounds();
            if (bounds == null || !containsWithPadding(bounds, mouseX, mouseY, 6))
            {
                return false;
            }

            int qty = Math.max(0, autoFlipQuantityChatboxQty);
            if (qty <= 0)
            {
                return false;
            }

            autoFlipQuantityPromptAutoFillLocked = true;
            autoFlipQuantityPromptManualChoiceMade = true;
            autoFlipQuantityPromptManualSelectedQty = qty;
            injectAutoFlipQuantityChatboxValue(qty, "quantity_chip_button");
            return true;
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("handleAutoFlipQuantityChatboxInjectClick", error);
            return false;
        }
    }

    private void injectAutoFlipQuantityChatboxValue(int qty, String source)
    {
        if (qty <= 0)
        {
            return;
        }

        Runnable task = () ->
        {
            try
            {
                String value = Integer.toString(qty);
                net.runelite.api.widgets.Widget promptWidget = client == null ? null : client.getWidget(10616875);
                net.runelite.api.widgets.Widget inputWidget = client == null ? null : client.getWidget(10616876);

                boolean liveQuantityPrompt = promptWidget != null
                    && inputWidget != null
                    && !promptWidget.isHidden()
                    && !inputWidget.isHidden()
                    && promptWidget.getBounds() != null
                    && inputWidget.getBounds() != null
                    && promptWidget.getBounds().width > 0
                    && promptWidget.getBounds().height > 0
                    && inputWidget.getBounds().width > 0
                    && inputWidget.getBounds().height > 0
                    && (
                        cleanWidgetText(promptWidget.getText()).toLowerCase(java.util.Locale.ROOT).contains("how many")
                            || cleanWidgetText(promptWidget.getText()).toLowerCase(java.util.Locale.ROOT).contains("quantity")
                            || cleanWidgetText(promptWidget.getText()).toLowerCase(java.util.Locale.ROOT).contains("amount")
                    );
                if (!liveQuantityPrompt)
                {
                    String promptText = promptWidget == null ? "" : cleanWidgetText(promptWidget.getText());
                    logAutoFlipVerbose(
                        "AUTOFLIP_QUANTITY_CHATBOX_INJECT_BLOCKED"
                            + " qty=" + value
                            + " prompt=" + safe(promptText)
                            + " promptWidget=" + (promptWidget != null)
                            + " inputWidget=" + (inputWidget != null)
                    );
                    return;
                }

                if (client != null)
                {
                    client.setVarcStrValue(359, value);
                }

                if (inputWidget != null)
                {
                    inputWidget.setText("<col=000000>" + value + "*");
                    inputWidget.revalidate();
                }

                logAutoFlipVerbose("AUTOFLIP_QUANTITY_CHATBOX_INJECT_APPLIED qty=" + value + " inputWidget=" + (inputWidget != null));
            }
            catch (Throwable error)
            {
                logAutoFlipUiError("injectAutoFlipQuantityChatboxValue", error);
            }
        };

        try
        {
            if (clientThread != null)
            {
                clientThread.invoke(task);
            }
            else
            {
                task.run();
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("injectAutoFlipQuantityChatboxValueInvoke", error);
        }
    }

    public Rectangle getAutoFlipPriceChatboxButtonBounds()
    {
        refreshAutoFlipPriceChatboxInjectState();
        Rectangle bounds = autoFlipPriceChatboxButtonBounds;
        return bounds == null ? null : new Rectangle(bounds);
    }

    public String getAutoFlipPriceChatboxButtonLabel()
    {
        refreshAutoFlipPriceChatboxInjectState();
        return autoFlipPriceChatboxButtonLabel == null ? "" : autoFlipPriceChatboxButtonLabel;
    }

    public Rectangle getAutoFlipOrdinarySellRecommendedButtonBounds()
    {
        Rectangle bounds = isAutoFlipOrdinaryBuyPricePromptOpenForOverlay() || autoFlipOrdinaryBuyTargetPriceButtonBounds != null
            ? autoFlipOrdinaryBuyRecommendedButtonBounds
            : autoFlipOrdinarySellRecommendedButtonBounds;
        return bounds == null ? null : new Rectangle(bounds);
    }

    public String getAutoFlipOrdinarySellRecommendedButtonLabel()
    {
        return isAutoFlipOrdinaryBuyPricePromptOpenForOverlay() || autoFlipOrdinaryBuyTargetPriceButtonBounds != null
            ? autoFlipOrdinaryBuyRecommendedButtonLabel
            : (autoFlipOrdinarySellRecommendedButtonLabel == null ? "" : autoFlipOrdinarySellRecommendedButtonLabel);
    }

    public Rectangle getAutoFlipOrdinarySellLastSoldButtonBounds()
    {
        Rectangle bounds = isAutoFlipOrdinaryBuyPricePromptOpenForOverlay() || autoFlipOrdinaryBuyTargetPriceButtonBounds != null
            ? autoFlipOrdinaryBuyLastBoughtButtonBounds
            : autoFlipOrdinarySellLastSoldButtonBounds;
        return bounds == null ? null : new Rectangle(bounds);
    }

    public String getAutoFlipOrdinarySellLastSoldButtonLabel()
    {
        return isAutoFlipOrdinaryBuyPricePromptOpenForOverlay() || autoFlipOrdinaryBuyTargetPriceButtonBounds != null
            ? autoFlipOrdinaryBuyLastBoughtButtonLabel
            : (autoFlipOrdinarySellLastSoldButtonLabel == null ? "" : autoFlipOrdinarySellLastSoldButtonLabel);
    }

    public Rectangle getAutoFlipOrdinaryBuyTargetPriceButtonBounds()
    {
        Rectangle bounds = autoFlipOrdinaryBuyTargetPriceButtonBounds;
        return bounds == null ? null : new Rectangle(bounds);
    }

    public String getAutoFlipOrdinaryBuyTargetPriceButtonLabel()
    {
        return autoFlipOrdinaryBuyTargetPriceButtonLabel == null ? "" : autoFlipOrdinaryBuyTargetPriceButtonLabel;
    }

    public boolean isAutoFlipTargetBuyPricePromptOpenForOverlay()
    {
        return autoFlipOrdinaryBuyTargetPriceButtonBounds != null
            && autoFlipOrdinaryBuyTargetPriceGp > 0
            && isAutoFlipNativePricePromptText(getAutoFlipStateDetectorActiveNativePromptText());
    }

    private PriceChatboxGpTarget findAutoFlipPriceChatboxGpTarget(net.runelite.api.widgets.Widget root)
    {
        if (root == null)
        {
            return null;
        }

        PriceChatboxGpTarget own = parseAutoFlipPriceChatboxGpTarget(root);
        if (own != null)
        {
            return own;
        }

        PriceChatboxGpTarget found = findAutoFlipPriceChatboxGpTargetInArray(root.getStaticChildren());
        if (found != null)
        {
            return found;
        }

        found = findAutoFlipPriceChatboxGpTargetInArray(root.getDynamicChildren());
        if (found != null)
        {
            return found;
        }

        return findAutoFlipPriceChatboxGpTargetInArray(root.getNestedChildren());
    }

    private PriceChatboxGpTarget findAutoFlipPriceChatboxGpTargetInArray(net.runelite.api.widgets.Widget[] children)
    {
        if (children == null || children.length == 0)
        {
            return null;
        }

        for (net.runelite.api.widgets.Widget child : children)
        {
            PriceChatboxGpTarget found = findAutoFlipPriceChatboxGpTarget(child);
            if (found != null)
            {
                return found;
            }
        }

        return null;
    }

    private PriceChatboxGpTarget parseAutoFlipPriceChatboxGpTarget(net.runelite.api.widgets.Widget widget)
    {
        if (widget == null)
        {
            return null;
        }

        String text = cleanWidgetText(widget.getText());
        if (text == null || text.isEmpty())
        {
            return null;
        }

        String lower = text.toLowerCase();
        if (!lower.contains("gp") || (!lower.contains("wiki") && !lower.contains("insta") && !lower.contains("set")))
        {
            return null;
        }

        int gp = extractAutoFlipGpFromText(text);
        Rectangle bounds = widget.getBounds();

        if (gp <= 0 || bounds == null || bounds.width <= 0 || bounds.height <= 0)
        {
            return null;
        }

        // AUTOFLIP_PRICE_CHATBOX_TREE_SCAN_HELPERS_V1
        if (isAutoFlipVerboseRuntimeLoggingEnabled())
        {
            logAutoFlipVerbose("AUTOFLIP_PRICE_CHATBOX_TREE_SCAN found=true gp=" + gp + " text=" + text + " bounds=" + bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height);
        }
        return new PriceChatboxGpTarget(gp, new Rectangle(bounds));
    }

    private static final class PriceChatboxGpTarget
    {
        private final int gp;
        private final Rectangle bounds;

        private PriceChatboxGpTarget(int gp, Rectangle bounds)
        {
            this.gp = gp;
            this.bounds = bounds;
        }
    }
    // AUTOFLIP_PRICE_CHATBOX_LOG_THROTTLE_STATE_V1
    private volatile int autoFlipLastPriceChatboxFallbackLogGp = 0;
    private volatile long autoFlipLastPriceChatboxFallbackLogMs = 0L;

    private boolean shouldLogAutoFlipPriceChatboxFallback(int gp)
    {
        long now = System.currentTimeMillis();
        if (gp != autoFlipLastPriceChatboxFallbackLogGp || now - autoFlipLastPriceChatboxFallbackLogMs > 5000L)
        {
            autoFlipLastPriceChatboxFallbackLogGp = gp;
            autoFlipLastPriceChatboxFallbackLogMs = now;
            return true;
        }
        return false;
    }

    private int extractAutoFlipActivelyTradedPrice(String text)
    {
        if (text == null)
        {
            return 0;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("actively\\s+traded\\s+price\\s*:\\s*(\\d[\\d,]*)", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(text);

        if (!matcher.find())
        {
            return 0;
        }

        try
        {
            return Integer.parseInt(matcher.group(1).replace(",", ""));
        }
        catch (Exception ignored)
        {
            return 0;
        }
    }
    private int extractAutoFlipGpFromText(String text)
    {
        if (text == null)
        {
            return 0;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("(\\d[\\d,]*)\\s*(?:gp|coins?)", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(text);

        int value = 0;
        while (matcher.find())
        {
            try
            {
                value = Integer.parseInt(matcher.group(1).replace(",", ""));
            }
            catch (Exception ignored)
            {
                value = 0;
            }
        }

        return value;
    }

    private Rectangle getConfiguredAutoFlipPriceChatboxInjectBounds()
    {
        // Price helper uses only independent price config keys.
        return new Rectangle(
            readIntConfig("setup.price.chatbox.inject.x", 291),
            readIntConfig("setup.price.chatbox.inject.y", 436),
            Math.max(1, readIntConfig("setup.price.chatbox.inject.w", 150)),
            Math.max(1, readIntConfig("setup.price.chatbox.inject.h", 20))
        );
    }

    private Rectangle getConfiguredAutoFlipQuantityChatboxInjectBounds()
    {
        // Anchor to the live native quantity prompt when possible so the helper
        // stays beside the prompt instead of drifting into the item-description box.
        if (client != null)
        {
            try
            {
                net.runelite.api.widgets.Widget promptWidget = client.getWidget(10616875);
                net.runelite.api.widgets.Widget inputWidget = client.getWidget(10616876);
                if (promptWidget != null && inputWidget != null)
                {
                    String promptText = cleanWidgetText(promptWidget.getText()).toLowerCase(java.util.Locale.ROOT);
                    if (!(promptText.contains("how many") || promptText.contains("quantity")))
                    {
                        return null;
                    }

                    Rectangle promptBounds = promptWidget.getBounds();
                    Rectangle inputBounds = inputWidget.getBounds();
                if (promptBounds != null
                        && inputBounds != null
                        && promptBounds.width > 0
                        && promptBounds.height > 0
                        && inputBounds.width > 0
                        && inputBounds.height > 0)
                    {
                        if (!isAutoFlipGeSetupPromptActiveForOverlay())
                        {
                            return null;
                        }

                        int buttonWidth = Math.max(132, Math.min(180, inputBounds.width + 26));
                        int buttonHeight = Math.max(18, Math.min(22, inputBounds.height));

                        // Anchor under the quantity value line instead of centering on
                        // the prompt text so the chip stays out of the question area.
                        int x = inputBounds.x + (inputBounds.width - buttonWidth) / 2;
                        int y = inputBounds.y + inputBounds.height + 10;

                        int leftLimit = promptBounds.x + 6;
                        int rightLimit = promptBounds.x + promptBounds.width - buttonWidth - 6;
                        if (x < leftLimit)
                        {
                            x = leftLimit;
                        }
                        else if (x > rightLimit)
                        {
                            x = rightLimit;
                        }

                        return new Rectangle(x, y, buttonWidth, buttonHeight);
                    }
                }
            }
            catch (Throwable ignored)
            {
                // Fall back to the configured rectangle below.
            }
        }

        return new Rectangle(
            readIntConfig("setup.quantity.chatbox.inject.x", 291),
            readIntConfig("setup.quantity.chatbox.inject.y", 436),
            Math.max(1, readIntConfig("setup.quantity.chatbox.inject.w", 150)),
            Math.max(1, readIntConfig("setup.quantity.chatbox.inject.h", 20))
        );
    }


    private boolean isInsideConfiguredAutoFlipPriceChatboxInjectButton(int mouseX, int mouseY)
    {
        Rectangle bounds = getConfiguredAutoFlipPriceChatboxInjectBounds();
        return bounds != null && bounds.contains(mouseX, mouseY);
    }
    private boolean handleAutoFlipPriceChatboxConfiguredInjectClick(int mouseX, int mouseY)
    {
        return handleAutoFlipOrdinaryBuyPromptButtonClick(mouseX, mouseY)
            || handleAutoFlipOrdinarySellPromptButtonClick(mouseX, mouseY);
    }
    private boolean handleAutoFlipPriceChatboxInjectClick(int mouseX, int mouseY)
    {
        return handleAutoFlipOrdinaryBuyPromptButtonClick(mouseX, mouseY)
            || handleAutoFlipOrdinarySellPromptButtonClick(mouseX, mouseY);
    }

    private boolean handleAutoFlipOrdinaryBuyPromptButtonClick(int mouseX, int mouseY)
    {
        boolean targetPrompt = autoFlipOrdinaryBuyTargetPriceButtonBounds != null
            && isAutoFlipNativePricePromptText(getAutoFlipStateDetectorActiveNativePromptText());
        if (!isAutoFlipOrdinaryBuyPricePromptOpenForOverlay() && !targetPrompt) return false;
        if (containsWithPadding(autoFlipOrdinaryBuyTargetPriceButtonBounds, mouseX, mouseY, 6)
            && autoFlipOrdinaryBuyTargetPriceGp > 0)
        {
            autoFlipOrdinaryBuyPricePromptManualChoiceMade = true;
            autoFlipOrdinaryBuyManualSelectedPriceGp = autoFlipOrdinaryBuyTargetPriceGp;
            injectAutoFlipPriceChatboxValue(autoFlipOrdinaryBuyTargetPriceGp, "ordinary_buy_target_button");
            return true;
        }
        if (containsWithPadding(autoFlipOrdinaryBuyRecommendedButtonBounds, mouseX, mouseY, 6)
            && autoFlipOrdinaryBuySuggestedPriceGp > 0)
        {
            autoFlipOrdinaryBuyPricePromptManualChoiceMade = true;
            autoFlipOrdinaryBuyManualSelectedPriceGp = autoFlipOrdinaryBuySuggestedPriceGp;
            injectAutoFlipPriceChatboxValue(autoFlipOrdinaryBuySuggestedPriceGp, "ordinary_buy_recommended_button");
            return true;
        }
        if (containsWithPadding(autoFlipOrdinaryBuyLastBoughtButtonBounds, mouseX, mouseY, 6)
            && autoFlipOrdinaryBuyLastBoughtPriceGp > 0)
        {
            autoFlipOrdinaryBuyPricePromptManualChoiceMade = true;
            autoFlipOrdinaryBuyManualSelectedPriceGp = autoFlipOrdinaryBuyLastBoughtPriceGp;
            injectAutoFlipPriceChatboxValue(autoFlipOrdinaryBuyLastBoughtPriceGp, "ordinary_buy_last_bought_button");
            return true;
        }
        return false;
    }

    private boolean handleAutoFlipOrdinarySellPromptButtonClick(int mouseX, int mouseY)
    {
        try
        {
            appendLine(
                ORDINARY_SELL_DEBUG_LOG,
                now()
                    + " click_seen"
                    + " mouseX=" + mouseX
                    + " mouseY=" + mouseY
                    + " price_option_visible=" + autoFlipOrdinarySellPriceOptionVisible
                    + " prompt_open=" + isAutoFlipOrdinarySellPricePromptOpenForOverlay()
                    + " current_text=" + safe(autoFlipOrdinarySellCurrentPriceText)
                    + " current_gp=" + autoFlipOrdinarySellCurrentPriceGp
                    + " recommended_bounds=" + safeRectangle(autoFlipOrdinarySellRecommendedButtonBounds)
                    + " last_sold_bounds=" + safeRectangle(autoFlipOrdinarySellLastSoldButtonBounds)
            );

            if (!isAutoFlipOrdinarySellPricePromptOpenForOverlay()
                && !autoFlipOrdinarySellPriceOptionVisible
                && autoFlipOrdinarySellRecommendedButtonBounds == null
                && autoFlipOrdinarySellLastSoldButtonBounds == null)
            {
                return false;
            }

            if (autoFlipOrdinarySellRecommendedButtonBounds == null || autoFlipOrdinarySellLastSoldButtonBounds == null)
            {
                refreshAutoFlipOrdinarySellPriceOption();
            }

            Rectangle recommended = autoFlipOrdinarySellRecommendedButtonBounds;
            if (containsWithPadding(recommended, mouseX, mouseY, 6) && autoFlipOrdinarySellSetupItemId > 0)
            {
                int gp = Math.max(0, autoFlipOrdinarySellSuggestedPriceGp > 0
                    ? autoFlipOrdinarySellSuggestedPriceGp
                    : (int) Math.min(Integer.MAX_VALUE, getAutoFlipCachedSellPriceGp(autoFlipOrdinarySellSetupItemId)));
                if (gp > 0)
                {
                    autoFlipOrdinarySellForceWritePending = true;
                    autoFlipOrdinarySellForceWriteGp = gp;
                    appendLine(
                        ORDINARY_SELL_DEBUG_LOG,
                        now()
                            + " chip=recommended"
                            + " item_id=" + autoFlipOrdinarySellSetupItemId
                            + " gp=" + gp
                            + " prompt_open=" + isAutoFlipOrdinarySellPricePromptOpenForOverlay()
                            + " current_text=" + safe(autoFlipOrdinarySellCurrentPriceText)
                            + " current_gp=" + autoFlipOrdinarySellCurrentPriceGp
                            + " force_pending=" + autoFlipOrdinarySellForceWritePending
                    );
                    if (isAutoFlipVerboseRuntimeLoggingEnabled())
                    {
                        logAutoFlipVerbose(
                            "AUTOFLIP_ORDINARY_SELL_CHIP_CLICK"
                                + " chip=recommended"
                                + " item_id=" + autoFlipOrdinarySellSetupItemId
                                + " gp=" + gp
                                + " mouseX=" + mouseX
                                + " mouseY=" + mouseY
                                + " bounds=" + safeRectangle(recommended)
                        );
                    }
                    injectAutoFlipOrdinarySellSuggestedPriceOnce(autoFlipOrdinarySellSetupItemId, gp, true);
                    refreshAutoFlipPriceChatboxInjectState();
                    return true;
                }
            }

            Rectangle lastSold = autoFlipOrdinarySellLastSoldButtonBounds;
            if (containsWithPadding(lastSold, mouseX, mouseY, 6) && autoFlipOrdinarySellLastSoldPriceGp > 0)
            {
                autoFlipOrdinarySellForceWritePending = true;
                autoFlipOrdinarySellForceWriteGp = autoFlipOrdinarySellLastSoldPriceGp;
                appendLine(
                    ORDINARY_SELL_DEBUG_LOG,
                    now()
                        + " chip=lastSold"
                        + " item_id=" + autoFlipOrdinarySellSetupItemId
                        + " gp=" + autoFlipOrdinarySellLastSoldPriceGp
                        + " prompt_open=" + isAutoFlipOrdinarySellPricePromptOpenForOverlay()
                        + " current_text=" + safe(autoFlipOrdinarySellCurrentPriceText)
                        + " current_gp=" + autoFlipOrdinarySellCurrentPriceGp
                        + " force_pending=" + autoFlipOrdinarySellForceWritePending
                );
                if (isAutoFlipVerboseRuntimeLoggingEnabled())
                {
                    logAutoFlipVerbose(
                        "AUTOFLIP_ORDINARY_SELL_CHIP_CLICK"
                            + " chip=lastSold"
                            + " item_id=" + autoFlipOrdinarySellSetupItemId
                            + " gp=" + autoFlipOrdinarySellLastSoldPriceGp
                            + " mouseX=" + mouseX
                            + " mouseY=" + mouseY
                            + " bounds=" + safeRectangle(lastSold)
                    );
                }
                injectAutoFlipOrdinarySellSuggestedPriceOnce(autoFlipOrdinarySellSetupItemId, autoFlipOrdinarySellLastSoldPriceGp, true);
                refreshAutoFlipPriceChatboxInjectState();
                return true;
            }

            appendLine(
                ORDINARY_SELL_DEBUG_LOG,
                now()
                    + " click_miss"
                    + " mouseX=" + mouseX
                    + " mouseY=" + mouseY
                    + " prompt_open=" + isAutoFlipOrdinarySellPricePromptOpenForOverlay()
                    + " price_option_visible=" + autoFlipOrdinarySellPriceOptionVisible
                    + " recommended_bounds=" + safeRectangle(recommended)
                    + " last_sold_bounds=" + safeRectangle(lastSold)
            );
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("handleAutoFlipOrdinarySellPromptButtonClick", error);
        }

        return false;
    }

    private boolean containsWithPadding(Rectangle bounds, int mouseX, int mouseY, int padding)
    {
        if (bounds == null)
        {
            return false;
        }

        Rectangle padded = new Rectangle(
            bounds.x - padding,
            bounds.y - padding,
            bounds.width + (padding * 2),
            bounds.height + (padding * 2)
        );
        return padded.contains(mouseX, mouseY);
    }

    private String safeRectangle(Rectangle bounds)
    {
        if (bounds == null)
        {
            return "null";
        }

        return bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height;
    }
    public void maybeAutoFillNativeGePromptIfNeeded()
    {
        // AUTOFLIP_NATIVE_PROMPT_AUTOFILL_V12
        // Fill native OSRS quantity/price prompts once. Do not auto-submit Enter and do not auto-click.
        long now = System.currentTimeMillis();
        if (now - autoFlipLastNativePromptAutoFillCheckMs < 250L)
        {
            return;
        }
        autoFlipLastNativePromptAutoFillCheckMs = now;

        Runnable task = () ->
        {
            try
            {
                if (client == null)
                {
                    return;
                }

                net.runelite.api.widgets.Widget promptWidget = client.getWidget(10616875);
                net.runelite.api.widgets.Widget inputWidget = client.getWidget(10616876);

                if (promptWidget == null || inputWidget == null)
                {
                    autoFlipLastNativePromptAutoFillKind = "";
                    autoFlipLastNativePromptAutoFillValue = "";
                    return;
                }

                Rectangle promptBounds = promptWidget.getBounds();
                Rectangle inputBounds = inputWidget.getBounds();
                if (promptWidget.isHidden()
                    || inputWidget.isHidden()
                    || promptBounds == null
                    || inputBounds == null
                    || promptBounds.width <= 0
                    || promptBounds.height <= 0
                    || inputBounds.width <= 0
                    || inputBounds.height <= 0)
                {
                    autoFlipLastNativePromptAutoFillKind = "";
                    autoFlipLastNativePromptAutoFillValue = "";
                    return;
                }

                String promptText = cleanWidgetText(promptWidget.getText()).toLowerCase(java.util.Locale.ROOT);
                // Trust only the native chat prompt widget. The setup overlay always contains labels like
                // "Quantity", so global visible-text scanning can falsely trigger quantity autofill.
                boolean quantityPrompt = promptText.contains("how many")
                    || promptText.contains("quantity")
                    || promptText.contains("amount");

                boolean pricePrompt = isAutoFlipNativePricePromptText(promptText);

                String kind;
                int value;

                if (quantityPrompt && !pricePrompt)
                {
                    kind = "quantity";
                    refreshAutoFlipQuantityChatboxInjectState();
                    value = autoFlipQuantityChatboxQty;
                }
                else if (pricePrompt)
                {
                    kind = "price";
                    refreshAutoFlipPriceChatboxInjectState();
                    value = autoFlipOrdinaryBuyPricePromptManualChoiceMade
                        && autoFlipOrdinaryBuyManualSelectedPriceGp > 0
                        ? autoFlipOrdinaryBuyManualSelectedPriceGp
                        : autoFlipPriceChatboxGp;
                }
                else
                {
                    autoFlipLastNativePromptAutoFillKind = "";
                    autoFlipLastNativePromptAutoFillValue = "";
                    return;
                }

                String autoFillStage = autoFlipGuidedSetupStage == null ? "" : autoFlipGuidedSetupStage;
                boolean ordinarySellPricePromptOpen = isAutoFlipOrdinarySellPricePromptOpenForOverlay();
                // Ordinary sell prompts are not part of the guided setup flow, so they should
                // autofill even when the guided stage cache is empty or still points at a prior flow.
                if (!ordinarySellPricePromptOpen)
                {
                    // AUTOFLIP_NATIVE_PROMPT_AUTOFILL_STAGE_GUARD_V16
                    // Prompt-open official states now set the cached prompt-owner string before autofill.
                    // Use that explicit owner so the native prompt writer does not recurse through routing.
                    if ("quantity".equals(kind) && !"quantity_enter".equals(autoFillStage))
                    {
                        return;
                    }
                    if ("price".equals(kind) && !"price_enter".equals(autoFillStage))
                    {
                        return;
                    }
                }

                if (value <= 0)
                {
                    return;
                }

                String valueText = Integer.toString(value);
                String currentInputText = cleanWidgetText(inputWidget.getText()).toLowerCase(java.util.Locale.ROOT);
                long fillNow = System.currentTimeMillis();

                int currentInputGp = extractAutoFlipGpFromText(currentInputText);
                boolean alreadyVisible = currentInputGp == value;
                boolean manualQuantityOverride = autoFlipQuantityPromptManualChoiceMade
                    && autoFlipQuantityPromptManualSelectedQty > 0
                    && autoFlipQuantityPromptManualSelectedQty != value;
                boolean quantityAutoFillLocked = "quantity".equals(kind) && autoFlipQuantityPromptAutoFillLocked;
                boolean recentlyFilledSame = kind.equals(autoFlipLastNativePromptAutoFillKind)
                    && valueText.equals(autoFlipLastNativePromptAutoFillValue)
                    && (fillNow - autoFlipLastNativePromptAutoFillAtMs) < 2000L;
                boolean forceWrite = ordinarySellPricePromptOpen
                    && "price".equals(kind)
                    && autoFlipOrdinarySellForceWritePending
                    && autoFlipOrdinarySellForceWriteGp == value;

                if ("quantity".equals(kind) && manualQuantityOverride)
                {
                    autoFlipQuantityPromptAutoFillLocked = true;
                    if (isAutoFlipVerboseRuntimeLoggingEnabled())
                    {
                        logAutoFlipVerbose(
                            "AUTOFLIP_NATIVE_PROMPT_AUTOFILL_SKIPPED"
                                + " kind=" + kind
                                + " reason=manual_choice"
                                + " value=" + valueText
                                + " selected_qty=" + autoFlipQuantityPromptManualSelectedQty
                                + " current_input_gp=" + currentInputGp
                                + " current_input_text=" + safe(currentInputText)
                        );
                    }
                    return;
                }

                if ("quantity".equals(kind) && quantityAutoFillLocked)
                {
                    return;
                }

                if ("quantity".equals(kind) && currentInputGp > 0 && currentInputGp != value)
                {
                    autoFlipQuantityPromptManualChoiceMade = true;
                    autoFlipQuantityPromptManualSelectedQty = currentInputGp;
                    autoFlipQuantityPromptAutoFillLocked = true;
                    if (isAutoFlipVerboseRuntimeLoggingEnabled())
                    {
                        logAutoFlipVerbose(
                            "AUTOFLIP_NATIVE_PROMPT_AUTOFILL_SKIPPED"
                                + " kind=" + kind
                                + " reason=manual_override"
                                + " value=" + valueText
                                + " current_input_gp=" + currentInputGp
                                + " current_input_text=" + safe(currentInputText)
                        );
                    }
                    return;
                }

                if (!forceWrite && (alreadyVisible || recentlyFilledSame))
                {
                    return;
                }
                client.setVarcStrValue(359, valueText);
                inputWidget.setText("<col=000000>" + valueText + "*");
                inputWidget.revalidate();
                autoFlipLastNativePromptAutoFillKind = kind;
                autoFlipLastNativePromptAutoFillValue = valueText;
                autoFlipLastNativePromptAutoFillAtMs = fillNow;
                if ("quantity".equals(kind))
                {
                    autoFlipQuantityPromptManualChoiceMade = false;
                    autoFlipQuantityPromptManualSelectedQty = value;
                    autoFlipQuantityPromptAutoFillLocked = true;
                }
                if (forceWrite)
                {
                    autoFlipOrdinarySellForceWritePending = false;
                    autoFlipOrdinarySellForceWriteGp = 0;
                    appendLine(
                        ORDINARY_SELL_DEBUG_LOG,
                        now()
                            + " write=applied"
                            + " kind=" + kind
                            + " value=" + valueText
                            + " prompt_open=" + ordinarySellPricePromptOpen
                            + " current_input_gp=" + currentInputGp
                            + " current_text=" + safe(currentInputText)
                    );
                }

                if (isAutoFlipVerboseRuntimeLoggingEnabled())
                {
                    logAutoFlipVerbose(
                        "AUTOFLIP_NATIVE_PROMPT_AUTOFILL_APPLIED"
                            + " kind=" + kind
                            + " value=" + valueText
                            + " forceWrite=" + forceWrite
                    );
                }
            }
            catch (Throwable error)
            {
                logAutoFlipUiError("maybeAutoFillNativeGePromptIfNeeded", error);
            }
        };

        try
        {
            if (clientThread != null)
            {
                clientThread.invoke(task);
            }
            else
            {
                task.run();
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("maybeAutoFillNativeGePromptIfNeededInvoke", error);
        }
    }
    // AUTOFLIP_GUIDED_SETUP_FLOW_METHODS_V15
    private boolean isAutoFlipGeSetupWindowActive()
    {
        try
        {
            return isAutoFlipGeSetupPromptActiveForOverlay();
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("isAutoFlipGeSetupWindowActive", error);
            return false;
        }
    }

    private boolean isAutoFlipGeSetupPromptActiveForOverlay()
    {
        try
        {
            AutoFlipBoardCard target = getAutoFlipSetupTargetBoardCard();
            if (target == null)
            {
                return false;
            }

            String geTitle = cleanWidgetText(getGeHeaderTextForOverlay()).toLowerCase(java.util.Locale.ROOT);
            if (geTitle.startsWith("grand exchange: set up offer"))
            {
                return true;
            }

            if (!geTitle.startsWith("grand exchange"))
            {
                return false;
            }

            String promptText = getAutoFlipStateDetectorActiveNativePromptText();
            if (promptText == null)
            {
                return false;
            }

            String lowerPrompt = promptText.toLowerCase(java.util.Locale.ROOT);
            return lowerPrompt.contains("set a price")
                || lowerPrompt.contains("price for each item")
                || lowerPrompt.contains("how many")
                || lowerPrompt.contains("quantity")
                || lowerPrompt.contains("amount");
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("isAutoFlipGeSetupPromptActiveForOverlay", error);
            return false;
        }
    }

        private String getAutoFlipNativePromptKind()
    {
        // AUTOFLIP_GUIDED_SETUP_WIPE_REMODEL_V2_PROMPT_KIND
        // Strict native prompt classifier only.
        // Previous-search / hot-search rows are visual noise and must never own item-search stage.
        try
        {
            if (client == null)
            {
                return "";
            }

            net.runelite.api.widgets.Widget promptWidget = client.getWidget(10616875);
            if (promptWidget == null)
            {
                return "";
            }

            String promptText = cleanWidgetText(promptWidget.getText()).toLowerCase(java.util.Locale.ROOT);

            boolean quantityPrompt = promptText.contains("how many")
                || promptText.contains("quantity")
                || promptText.contains("amount");

            if (quantityPrompt)
            {
                return "quantity_enter";
            }

            boolean pricePrompt = promptText.contains("set a price")
                || promptText.contains("price for each item");

            if (pricePrompt)
            {
                return "price_enter";
            }

            boolean itemPrompt = promptText.contains("what would you like to buy")
                || promptText.contains("start typing the name of an item");

            if (itemPrompt)
            {
                return "item_enter";
            }

            if (isAutoFlipSelectedItemDetailPanelVisible())
            {
                return "selected_item";
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("getAutoFlipNativePromptKind", error);
        }

        return "";
    }
    private String normalizeAutoFlipGuidedSetupItemName(String value)
    {
        try
        {
            String text = cleanWidgetText(value == null ? "" : value).toLowerCase(java.util.Locale.ROOT);
            text = text.replace("(members)", "");
            text = text.replace("members", "");
            return text.trim().replaceAll("\\s+", " ");
        }
        catch (Throwable error)
        {
            return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT).trim();
        }
    }
    private boolean isAutoFlipGuidedSetupTargetItemTextVisible()
    {
        try
        {
            AutoFlipBoardCard card = getAutoFlipSetupTargetBoardCard();
            if (card == null)
            {
                return false;
            }

            String targetName = normalizeAutoFlipGuidedSetupItemName(card.getItemName());
            if (targetName.isEmpty())
            {
                return false;
            }

            return autoFlipVisibleWidgetTextContainsAny(targetName);
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("isAutoFlipGuidedSetupTargetItemTextVisible", error);
            return false;
        }
    }
    private boolean isAutoFlipSelectedItemDetailPanelVisible()
    {
        try
        {
            if (!isAutoFlipGeSetupWindowActive())
            {
                return false;
            }

            AutoFlipBoardCard card = getAutoFlipSetupTargetBoardCard();
            if (card == null)
            {
                return false;
            }

            String targetName = normalizeAutoFlipGuidedSetupItemName(card.getItemName());
            if (targetName.isEmpty())
            {
                return false;
            }

            return autoFlipVisibleWidgetTextContainsAny(targetName)
                && autoFlipVisibleWidgetTextContainsAny(
                    "actively traded price",
                    "buy limit",
                    "login to a members",
                    "members' server",
                    "members object"
                );
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("isAutoFlipSelectedItemDetailPanelVisible", error);
            return false;
        }
    }
    private boolean isAutoFlipGuidedSetupTargetItemSelected()
    {
        try
        {
            if (!isAutoFlipGeSetupWindowActive())
            {
                return false;
            }

            AutoFlipBoardCard card = getAutoFlipSetupTargetBoardCard();
            if (card == null)
            {
                return false;
            }

            String targetName = normalizeAutoFlipGuidedSetupItemName(card.getItemName());
            if (targetName.isEmpty())
            {
                return false;
            }

            String promptKind = getAutoFlipNativePromptKind();
            // Selected item matching must preserve meaningful variants like (unf), (p), (p+), and (p++).
            // Only membership display text is ignored. Broad whole-UI contains(targetName) is not enough.
            boolean selectedDetailsVisible = autoFlipVisibleWidgetTextContainsAny(
                    "actively traded price",
                    "buy limit",
                    "login to a members",
                    "members' server",
                    "members object"
                )
                && autoFlipVisibleSelectedItemNameMatchesTarget(targetName);

            if (selectedDetailsVisible)
            {
                return true;
            }

            return "quantity_enter".equals(promptKind) || "price_enter".equals(promptKind);
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("isAutoFlipGuidedSetupTargetItemSelected", error);
            return false;
        }
    }

    private boolean autoFlipVisibleSelectedItemNameMatchesTarget(String targetName)
    {
        try
        {
            if (client == null || targetName == null || targetName.trim().isEmpty())
            {
                return false;
            }

            String normalizedTarget = autoFlipNormalizeSelectedItemDisplayName(targetName);
            if (normalizedTarget.isEmpty())
            {
                return false;
            }

            net.runelite.api.widgets.Widget[] roots = client.getWidgetRoots();
            if (roots == null)
            {
                return false;
            }

            for (net.runelite.api.widgets.Widget root : roots)
            {
                if (autoFlipWidgetTreeHasExactSelectedItemName(root, normalizedTarget))
                {
                    return true;
                }
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("autoFlipVisibleSelectedItemNameMatchesTarget", error);
        }

        return false;
    }

    private boolean autoFlipWidgetTreeHasExactSelectedItemName(net.runelite.api.widgets.Widget widget, String normalizedTarget)
    {
        if (widget == null || normalizedTarget == null || normalizedTarget.isEmpty())
        {
            return false;
        }

        try
        {
            String rawText = cleanWidgetText(widget.getText());
            if (!rawText.isEmpty())
            {
                String candidate = autoFlipNormalizeSelectedItemDisplayName(rawText);
                if (normalizedTarget.equals(candidate))
                {
                    return true;
                }
            }

            net.runelite.api.widgets.Widget[] dynamicChildren = widget.getDynamicChildren();
            if (dynamicChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : dynamicChildren)
                {
                    if (autoFlipWidgetTreeHasExactSelectedItemName(child, normalizedTarget))
                    {
                        return true;
                    }
                }
            }

            net.runelite.api.widgets.Widget[] staticChildren = widget.getStaticChildren();
            if (staticChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : staticChildren)
                {
                    if (autoFlipWidgetTreeHasExactSelectedItemName(child, normalizedTarget))
                    {
                        return true;
                    }
                }
            }

            net.runelite.api.widgets.Widget[] nestedChildren = widget.getNestedChildren();
            if (nestedChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : nestedChildren)
                {
                    if (autoFlipWidgetTreeHasExactSelectedItemName(child, normalizedTarget))
                    {
                        return true;
                    }
                }
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("autoFlipWidgetTreeHasExactSelectedItemName", error);
        }

        return false;
    }

    private String autoFlipNormalizeSelectedItemDisplayName(String value)
    {
        if (value == null)
        {
            return "";
        }

        String normalized = cleanWidgetText(value).toLowerCase(java.util.Locale.ROOT);
        normalized = normalized.replaceAll("\\s+", " ").trim();
        normalized = normalized.replaceAll("\\s*\\(members\\)\\s*$", "").trim();
        normalized = normalized.replaceAll("\\s+members\\s*$", "").trim();
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }
    private boolean isAutoFlipGuidedSetupPromptAutoFilled(String kind)
    {
        try
        {
            if (kind == null || kind.trim().isEmpty())
            {
                return false;
            }

            String expectedKind = kind.trim();
            String lastKind = autoFlipLastNativePromptAutoFillKind == null ? "" : autoFlipLastNativePromptAutoFillKind.trim();
            String lastValue = autoFlipLastNativePromptAutoFillValue == null ? "" : autoFlipLastNativePromptAutoFillValue.trim();
            if (!expectedKind.equals(lastKind) || lastValue.isEmpty())
            {
                return false;
            }

            long ageMs = System.currentTimeMillis() - autoFlipLastNativePromptAutoFillAtMs;
            if (autoFlipLastNativePromptAutoFillAtMs <= 0L || ageMs < 0L || ageMs > 10000L)
            {
                return false;
            }

            if (client == null)
            {
                return true;
            }

            net.runelite.api.widgets.Widget inputWidget = client.getWidget(10616876);
            if (inputWidget == null)
            {
                return true;
            }

            String currentInputText = cleanWidgetText(inputWidget.getText()).toLowerCase(java.util.Locale.ROOT);
            String expectedValue = lastValue.toLowerCase(java.util.Locale.ROOT);
            return currentInputText.contains(expectedValue);
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("isAutoFlipGuidedSetupPromptAutoFilled", error);
            return false;
        }
    }
        public String getAutoFlipGuidedSetupStage()
    {
        // AUTOFLIP_OFFICIAL_STATE_ROUTING_4A4B_HISTORY_V1_STAGE_OWNER
        // Official source of truth: getAutoFlipStateDetectorLabelForOverlay().
        // This method maps visible states to existing green-box stages only.
        // It must not infer progress from previous-search rows, selected-item stale widgets,
        // or chat history. It must not inject values. Existing click/enter owners keep insertion behavior.
        try
        {
            if (!isGeWindowOpenForOverlay())
            {
                clearAutoFlipGuidedSetupState();
                return "";
            }

            String officialState = getAutoFlipStateDetectorLabelForOverlay();
            if (officialState == null)
            {
                officialState = "";
            }
            // If the official state just left confirm-ready, a native Confirm click completed the offer.
            // Clear stale prompt/search ownership before the next item search state tries to seed text.
            if ("state_10_correct_item_qty_price_ok".equals(autoFlipLastOfficialGuidedSetupState)
                && !"state_10_correct_item_qty_price_ok".equals(officialState))
            {
                markAutoFlipGeSessionInventoryCacheDirty("confirm_state_exited");
                clearAutoFlipPostConfirmPromptSearchOwnership();
            }
            autoFlipLastOfficialGuidedSetupState = officialState;

            switch (officialState)
            {
                case "state_4a_item_search_first_time":
                {
                    Rectangle targetSearchResultBounds = getAutoFlipGuidedSetupTargetSearchResultBoundsByItemId();
                    if (targetSearchResultBounds == null)
                    {
                        retryAutoFlipSetupSearchInjection();
                        return "item_enter";
                    }
                    return "item_pick";
                }

                case "state_4b_item_search_previous_search_visible":
                {
                    // State 4b inherits RuneLite/Jagex previous-search rows. Force one native full-name
                    // search seed so the target item replaces stale previous-search display text.
                    retryAutoFlipSetupSearchInjection(true);

                    Rectangle targetSearchResultBounds = getAutoFlipGuidedSetupTargetSearchResultBoundsByItemId();
                    if (targetSearchResultBounds == null)
                    {
                        return "item_enter";
                    }
                    return "item_pick";
                }

                case "state_5_wrong_item_selected":
                    return "";

                case "state_6_correct_item_qty_price_wrong":
                    return "quantity_click";

                case "state_7_quantity_prompt_open":
                case "state_7b_to_buy_quantity_prompt_open":
                    setAutoFlipGuidedSetupStage("quantity_enter");
                    maybeAutoFillNativeGePromptIfNeeded();
                    return "quantity_enter";

                case "state_8_correct_item_qty_ok_price_wrong":
                    return "price_click";

                case "state_9_price_prompt_open":
                    setAutoFlipGuidedSetupStage("price_enter");
                    maybeAutoFillNativeGePromptIfNeeded();
                    return "price_enter";

                case "state_10_correct_item_qty_price_ok":
                    return "confirm_click";

                default:
                    return "";
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("getAutoFlipGuidedSetupStage_officialStateRoutingRenumberedV2", error);
            return "";
        }
    }


    private void setAutoFlipGuidedSetupStage(String stage)
    {
        String next = stage == null ? "" : stage;
        if (!next.equals(autoFlipGuidedSetupStage))
        {
            autoFlipGuidedSetupStage = next;
            autoFlipGuidedSetupStageAtMs = System.currentTimeMillis();
            if (isAutoFlipVerboseRuntimeLoggingEnabled())
            {
                logAutoFlipVerbose("AUTOFLIP_GUIDED_SETUP_STAGE stage=" + autoFlipGuidedSetupStage);
            }
            return;
        }

        autoFlipGuidedSetupStage = next;
        if (autoFlipGuidedSetupStageAtMs <= 0L)
        {
            autoFlipGuidedSetupStageAtMs = System.currentTimeMillis();
        }
    }
        private void recordAutoFlipGuidedSetupEnterIfNeeded()
    {
        // AUTOFLIP_GUIDED_SETUP_WIPE_REMODEL_V2_ENTER_OWNER
        // Enter confirms only stages that explicitly expect Enter.
        try
        {
            if (!isAutoFlipGeSetupWindowActive())
            {
                clearAutoFlipGuidedSetupState();
                return;
            }

            String stage = autoFlipGuidedSetupStage == null ? "" : autoFlipGuidedSetupStage;
            if (isAutoFlipVerboseRuntimeLoggingEnabled())
            {
                logAutoFlipVerbose("AUTOFLIP_GUIDED_SETUP_ENTER stage=" + stage);
            }

            if ("quantity_enter".equals(stage))
            {
                setAutoFlipGuidedSetupStage("price_click");
                return;
            }

            if ("price_enter".equals(stage))
            {
                setAutoFlipGuidedSetupStage("confirm_click");
                return;
            }

            if ("item_enter".equals(stage))
            {
                if (isAutoFlipGuidedSetupTargetItemSelected())
                {
                    setAutoFlipGuidedSetupStage("quantity_click");
                }
                return;
            }

            if ("confirm_click".equals(stage))
            {
                return;
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("recordAutoFlipGuidedSetupEnterIfNeeded", error);
        }
    }

    public Rectangle getAutoFlipGuidedSetupBlockingBounds()
    {
        try
        {
            String stage = getAutoFlipGuidedSetupStage();
            if (!"item_enter".equals(stage) && !"quantity_enter".equals(stage) && !"price_enter".equals(stage))
            {
                return null;
            }

            Rectangle headerBounds = getGeHeaderBoundsForOverlay();
            if (headerBounds == null)
            {
                return null;
            }

            if ("item_enter".equals(stage))
            {
                return new Rectangle(
                    headerBounds.x + readIntConfig("setup.item.icon.x", 56),
                    headerBounds.y + readIntConfig("setup.item.icon.y", 48),
                    readIntConfig("setup.item.icon.w", 42),
                    readIntConfig("setup.item.icon.h", 40)
                );
            }

            if ("quantity_enter".equals(stage))
            {
                return new Rectangle(
                    headerBounds.x + readIntConfig("setup.quantity.quick.x", 178),
                    headerBounds.y + readIntConfig("setup.quantity.quick.y", 186),
                    readIntConfig("setup.quantity.quick.w", 62),
                    readIntConfig("setup.quantity.quick.h", 28)
                );
            }

            if ("price_click".equals(stage) || "price_enter".equals(stage))
            {
                Rectangle dynamicPriceBounds = getAutoFlipGeOfferPriceButtonBounds();
                if (dynamicPriceBounds != null)
                {
                    return dynamicPriceBounds;
                }
            }

            return new Rectangle(
                headerBounds.x + readIntConfig("setup.quick.x", 345),
                headerBounds.y + readIntConfig("setup.quick.y", 177),
                readIntConfig("setup.quick.w", 33),
                readIntConfig("setup.quick.h", 23)
            );
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("getAutoFlipGuidedSetupBlockingBounds", error);
            return null;
        }
    }


    private Rectangle getAutoFlipGeOfferPriceButtonBounds()
    {
        try
        {
            if (client == null)
            {
                return null;
            }

            Widget offerContainer = client.getWidget(net.runelite.api.widgets.ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
            if (offerContainer == null || offerContainer.isHidden())
            {
                return null;
            }

            Widget priceButton = findAutoFlipWidgetByAnyText(
                offerContainer,
                0,
                "...",
                "enter price",
                "price per item",
                "custom price"
            );

            if (priceButton == null)
            {
                return null;
            }

            Rectangle bounds = priceButton.getBounds();
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
            {
                return null;
            }

            return new Rectangle(
                Math.max(0, bounds.x - 2),
                Math.max(0, bounds.y - 2),
                bounds.width + 4,
                bounds.height + 4
            );
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("getAutoFlipGeOfferPriceButtonBounds", error);
            return null;
        }
    }

    private Widget findAutoFlipWidgetByAnyText(Widget widget, int depth, String... needles)
    {
        if (widget == null || needles == null || needles.length == 0 || depth > 16)
        {
            return null;
        }

        try
        {
            if (!widget.isHidden())
            {
                String text = cleanWidgetText(widget.getText());
                if (text != null)
                {
                    String lower = text.toLowerCase(java.util.Locale.ROOT).trim();
                    for (String needle : needles)
                    {
                        if (needle == null)
                        {
                            continue;
                        }

                        String expected = needle.toLowerCase(java.util.Locale.ROOT).trim();
                        if (!expected.isEmpty() && lower.equals(expected))
                        {
                            return widget;
                        }
                    }
                }
            }

            Widget[] dynamicChildren = widget.getDynamicChildren();
            if (dynamicChildren != null)
            {
                for (Widget child : dynamicChildren)
                {
                    Widget found = findAutoFlipWidgetByAnyText(child, depth + 1, needles);
                    if (found != null)
                    {
                        return found;
                    }
                }
            }

            Widget[] staticChildren = widget.getStaticChildren();
            if (staticChildren != null)
            {
                for (Widget child : staticChildren)
                {
                    Widget found = findAutoFlipWidgetByAnyText(child, depth + 1, needles);
                    if (found != null)
                    {
                        return found;
                    }
                }
            }

            Widget[] nestedChildren = widget.getNestedChildren();
            if (nestedChildren != null)
            {
                for (Widget child : nestedChildren)
                {
                    Widget found = findAutoFlipWidgetByAnyText(child, depth + 1, needles);
                    if (found != null)
                    {
                        return found;
                    }
                }
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("findAutoFlipWidgetByAnyText", error);
        }

        return null;
    }

    // AUTOFLIP_GUIDED_SETUP_FLOW_V17
    // Native Confirm may not pass through AutoFlip helper bounds. When the official state leaves
    // confirm-ready, clear only guided prompt/search ownership so the next offer can seed its own
    // target item name. Do not clear board recommendations, budget, settings, or card data.
    private String describeAutoFlipBoardCards(java.util.List<AutoFlipBoardCard> cards)
    {
        if (cards == null || cards.isEmpty())
        {
            return "[]";
        }

        StringBuilder out = new StringBuilder();
        out.append("[");
        for (int i = 0; i < cards.size(); i++)
        {
            if (i > 0)
            {
                out.append(" | ");
            }

            AutoFlipBoardCard card = cards.get(i);
            if (card == null)
            {
                out.append(i).append(":null");
                continue;
            }

            out.append(i)
                .append(":slot=").append(card.getSlotIndex())
                .append(",item_id=").append(card.getItemId())
                .append(",name=").append(safe(card.getItemName()));
        }
        out.append("]");
        return out.toString();
    }
    private void clearAutoFlipPostConfirmPromptSearchOwnership()
    {
        autoFlipGuidedSetupStage = "";
        autoFlipGuidedSetupStageAtMs = 0L;

        autoFlipLastGeSearchInjectedItemId = 0;
        autoFlipLastGeSearchInjectedText = "";
        autoFlipLastGeSearchInjectedAtMs = 0L;

        autoFlipItemSearchSeededItemId = 0;
        autoFlipItemSearchSeededText = "";

        autoFlipLastNativePromptAutoFillKind = "";
        autoFlipLastNativePromptAutoFillValue = "";
        autoFlipLastNativePromptAutoFillAtMs = 0L;
        autoFlipLastNativePromptAutoFillCheckMs = 0L;

        logAutoFlipVerbose("AUTOFLIP_POST_CONFIRM_PROMPT_SEARCH_RESET");
    }
    private void clearAutoFlipGuidedSetupState()
    {
        // AUTOFLIP_GUIDED_SETUP_WIPE_REMODEL_V2_RESET_OWNER
        // Hard reset workflow ownership. Preserve the remembered target item seed; clear stale writes.
        autoFlipGeSearchInjectSeq++;

        autoFlipGuidedSetupStage = "";
        autoFlipGuidedSetupStageAtMs = 0L;

        autoFlipLastGeSearchInjectedItemId = 0;
        autoFlipLastGeSearchInjectedText = "";
        autoFlipLastGeSearchInjectedAtMs = 0L;

        autoFlipLastNativePromptAutoFillKind = "";
        autoFlipLastNativePromptAutoFillValue = "";
        autoFlipLastNativePromptAutoFillAtMs = 0L;
        autoFlipLastNativePromptAutoFillCheckMs = 0L;

        autoFlipQuantityChatboxButtonBounds = null;
        autoFlipQuantityChatboxButtonLabel = "";
        autoFlipQuantityChatboxQty = 0;
        autoFlipQuantityPromptAutoFillLocked = false;
        autoFlipQuantityPromptManualChoiceMade = false;
        autoFlipQuantityPromptManualSelectedQty = 0;
    }
        public void recordAutoFlipGuidedSetupClickIfNeeded(int mouseX, int mouseY)
    {
        // AUTOFLIP_GUIDED_SETUP_WIPE_REMODEL_V2_CLICK_OWNER
        // User click owner. Only explicit helper clicks advance click-owned stages.
        try
        {
            if (!isAutoFlipGeSetupWindowActive())
            {
                clearAutoFlipGuidedSetupState();
                return;
            }

            String stage = autoFlipGuidedSetupStage == null ? "" : autoFlipGuidedSetupStage;
            if (stage.isEmpty())
            {
                stage = getAutoFlipGuidedSetupStage();
            }

            Rectangle headerBounds = getGeHeaderBoundsForOverlay();
            if (headerBounds == null)
            {
                return;
            }

            Rectangle qtyBounds = new Rectangle(
                headerBounds.x + readIntConfig("setup.quantity.quick.x", 178),
                headerBounds.y + readIntConfig("setup.quantity.quick.y", 186),
                readIntConfig("setup.quantity.quick.w", 62),
                readIntConfig("setup.quantity.quick.h", 28)
            );
            Rectangle priceBounds = new Rectangle(
                headerBounds.x + readIntConfig("setup.quick.x", 345),
                headerBounds.y + readIntConfig("setup.quick.y", 177),
                readIntConfig("setup.quick.w", 33),
                readIntConfig("setup.quick.h", 23)
            );
            Rectangle confirmBounds = new Rectangle(
                headerBounds.x + readIntConfig("setup.confirm.x", 161),
                headerBounds.y + readIntConfig("setup.confirm.y", 244),
                readIntConfig("setup.confirm.w", 152),
                readIntConfig("setup.confirm.h", 39)
            );

            if ("item_enter".equals(stage))
            {
                if (isAutoFlipGuidedSetupTargetItemSelected())
                {
                    setAutoFlipGuidedSetupStage("quantity_click");
                }
                return;
            }

            if ("quantity_click".equals(stage) && qtyBounds.contains(mouseX, mouseY))
            {
                setAutoFlipGuidedSetupStage("quantity_enter");
                maybeAutoFillNativeGePromptIfNeeded();
                return;
            }

            if ("price_click".equals(stage) && priceBounds.contains(mouseX, mouseY))
            {
                setAutoFlipGuidedSetupStage("price_enter");
                maybeAutoFillNativeGePromptIfNeeded();
                return;
            }

            if ("confirm_click".equals(stage) && confirmBounds.contains(mouseX, mouseY))
            {
                markAutoFlipGeSessionInventoryCacheDirty("confirm_clicked");
                if (autoFlipPendingGuidedSetupItemId > 0)
                {
                }

                clearAutoFlipGuidedSetupState();
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("recordAutoFlipGuidedSetupClickIfNeeded", error);
        }
    }








    private boolean autoFlipVisibleWidgetTextContainsAny(String... needles)
    {
        try
        {
            if (client == null || needles == null || needles.length == 0)
            {
                return false;
            }

            net.runelite.api.widgets.Widget[] roots = client.getWidgetRoots();
            if (roots == null)
            {
                return false;
            }

            for (net.runelite.api.widgets.Widget root : roots)
            {
                if (autoFlipWidgetTreeTextContainsAny(root, needles))
                {
                    return true;
                }
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("autoFlipVisibleWidgetTextContainsAny", error);
        }

        return false;
    }

    private String autoFlipFindVisibleOrdinarySellItemName()
    {
        try
        {
            if (client == null)
            {
                return "";
            }

            Widget offerContainer = client.getWidget(net.runelite.api.widgets.ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
            if (offerContainer != null && !offerContainer.isHidden())
            {
                int offerItemId = offerContainer.getItemId();
                if (offerItemId > 0)
                {
                    return resolveAutoFlipSellPriceDebugItemName(offerItemId);
                }
            }

            Widget offerText = client.getWidget(net.runelite.api.widgets.InterfaceID.GRAND_EXCHANGE, 27);
            if (offerText != null && !offerText.isHidden())
            {
                int textItemId = offerText.getItemId();
                if (textItemId > 0)
                {
                    return resolveAutoFlipSellPriceDebugItemName(textItemId);
                }
            }

            net.runelite.api.widgets.Widget[] roots = client.getWidgetRoots();
            if (roots == null)
            {
                return "";
            }

            for (net.runelite.api.widgets.Widget root : roots)
            {
                String candidate = autoFlipFindVisibleOrdinarySellItemName(root, 0);
                if (candidate != null && !candidate.trim().isEmpty())
                {
                    return candidate.trim();
                }
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("autoFlipFindVisibleOrdinarySellItemName", error);
        }

        return "";
    }

    private String autoFlipFindVisibleOrdinarySellItemName(net.runelite.api.widgets.Widget widget, int depth)
    {
        if (widget == null || depth > 16)
        {
            return "";
        }

        try
        {
            String rawText = cleanWidgetText(widget.getText());
            if (!rawText.isEmpty())
            {
                String candidate = autoFlipNormalizeSelectedItemDisplayName(rawText);
                if (candidate.length() >= 3 && !autoFlipIsGenericOrdinarySellLabel(candidate))
                {
                    int resolvedItemId = resolveAutoFlipItemIdByName(candidate);
                    if (resolvedItemId > 0)
                    {
                        return resolveAutoFlipItemName(resolvedItemId, candidate);
                    }
                }
            }

            net.runelite.api.widgets.Widget[] dynamicChildren = widget.getDynamicChildren();
            if (dynamicChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : dynamicChildren)
                {
                    String candidate = autoFlipFindVisibleOrdinarySellItemName(child, depth + 1);
                    if (!candidate.isEmpty())
                    {
                        return candidate;
                    }
                }
            }

            net.runelite.api.widgets.Widget[] staticChildren = widget.getStaticChildren();
            if (staticChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : staticChildren)
                {
                    String candidate = autoFlipFindVisibleOrdinarySellItemName(child, depth + 1);
                    if (!candidate.isEmpty())
                    {
                        return candidate;
                    }
                }
            }

            net.runelite.api.widgets.Widget[] nestedChildren = widget.getNestedChildren();
            if (nestedChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : nestedChildren)
                {
                    String candidate = autoFlipFindVisibleOrdinarySellItemName(child, depth + 1);
                    if (!candidate.isEmpty())
                    {
                        return candidate;
                    }
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        return "";
    }

    private boolean autoFlipIsGenericOrdinarySellLabel(String candidate)
    {
        if (candidate == null)
        {
            return true;
        }

        String lower = candidate.toLowerCase(java.util.Locale.ROOT);
        return lower.isEmpty()
            || lower.equals("sell offer")
            || lower.equals("grand exchange")
            || lower.equals("set up offer")
            || lower.contains("actively traded price")
            || lower.contains("convenience fee")
            || lower.contains("price per item")
            || lower.contains("quantity")
            || lower.contains("confirm")
            || lower.contains("history")
            || lower.contains("empty")
            || lower.contains("walk here");
    }

    private int readAutoFlipVisibleOrdinarySellCurrentPriceGp()
    {
        try
        {
            if (client == null)
            {
                return 0;
            }

            Widget offerText = client.getWidget(net.runelite.api.widgets.InterfaceID.GRAND_EXCHANGE, 27);
            if (offerText != null && !offerText.isHidden())
            {
                int visibleGp = readAutoFlipVisibleOrdinarySellCurrentPriceGp(offerText, 0);
                if (visibleGp > 0)
                {
                    return visibleGp;
                }
            }

            Widget offerContainer = client.getWidget(net.runelite.api.widgets.ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
            if (offerContainer == null || offerContainer.isHidden())
            {
                return readAutoFlipCurrentOrdinarySellPriceFromOffers();
            }

            int visibleGp = readAutoFlipVisibleOrdinarySellCurrentPriceGp(offerContainer, 0);
            if (visibleGp > 0)
            {
                return visibleGp;
            }

            visibleGp = readAutoFlipVisibleOrdinarySellCurrentPriceGpFromWidgetRoots();
            if (visibleGp > 0)
            {
                return visibleGp;
            }

            return readAutoFlipCurrentOrdinarySellPriceFromOffers();
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("readAutoFlipVisibleOrdinarySellCurrentPriceGp", error);
            return 0;
        }
    }

    private volatile long autoFlipLastNonEmptyBoardBudgetPlannedGp = 0L;
    private volatile long autoFlipLastNonEmptyBoardTotalExpectedProfitGp = 0L;

    private int readAutoFlipCurrentOrdinarySellPriceFromOffers()
    {
        try
        {
            if (client == null || autoFlipOrdinarySellSetupItemId <= 0)
            {
                return 0;
            }

            GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
            if (offers == null)
            {
                return 0;
            }

            int fallbackPrice = 0;
            for (GrandExchangeOffer offer : offers)
            {
                if (offer == null)
                {
                    continue;
                }

                int offerItemId = Math.max(0, offer.getItemId());
                int offerPrice = clampAutoFlipGpToInt(offer.getPrice());
                if (offerPrice <= 0)
                {
                    continue;
                }

                if (offerItemId == autoFlipOrdinarySellSetupItemId)
                {
                    return offerPrice;
                }

                if (fallbackPrice <= 0)
                {
                    fallbackPrice = offerPrice;
                }
            }

            return fallbackPrice;
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("readAutoFlipCurrentOrdinarySellPriceFromOffers", error);
            return 0;
        }
    }

    private int readAutoFlipVisibleOrdinarySellCurrentPriceGp(Widget widget, int depth)
    {
        if (widget == null || depth > 16)
        {
            return 0;
        }

        try
        {
            if (!widget.isHidden())
            {
                String text = cleanWidgetText(widget.getText());
                if (text != null)
                {
                    String lower = text.toLowerCase(java.util.Locale.ROOT).trim();
                    boolean priceLike = lower.contains("coins")
                        || lower.contains("gp")
                        || lower.contains("no fee expected")
                        || lower.contains("price per item");
                    if (priceLike)
                    {
                        int gp = extractAutoFlipGpFromText(lower);
                        if (gp > 0)
                        {
                            return gp;
                        }
                    }
                }
            }

            Widget[] dynamicChildren = widget.getDynamicChildren();
            if (dynamicChildren != null)
            {
                for (Widget child : dynamicChildren)
                {
                    int gp = readAutoFlipVisibleOrdinarySellCurrentPriceGp(child, depth + 1);
                    if (gp > 0)
                    {
                        return gp;
                    }
                }
            }

            Widget[] staticChildren = widget.getStaticChildren();
            if (staticChildren != null)
            {
                for (Widget child : staticChildren)
                {
                    int gp = readAutoFlipVisibleOrdinarySellCurrentPriceGp(child, depth + 1);
                    if (gp > 0)
                    {
                        return gp;
                    }
                }
            }

            Widget[] nestedChildren = widget.getNestedChildren();
            if (nestedChildren != null)
            {
                for (Widget child : nestedChildren)
                {
                    int gp = readAutoFlipVisibleOrdinarySellCurrentPriceGp(child, depth + 1);
                    if (gp > 0)
                    {
                        return gp;
                    }
                }
            }
        }
        catch (Throwable ignored)
        {
        }

        return 0;
    }

    private int readAutoFlipVisibleOrdinarySellCurrentPriceGpFromWidgetRoots()
    {
        try
        {
            if (client == null)
            {
                return 0;
            }

            Widget[] roots = client.getWidgetRoots();
            if (roots == null || roots.length == 0)
            {
                return 0;
            }

            for (Widget root : roots)
            {
                int gp = readAutoFlipVisibleOrdinarySellCurrentPriceGp(root, 0);
                if (gp > 0)
                {
                    return gp;
                }
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("readAutoFlipVisibleOrdinarySellCurrentPriceGpFromWidgetRoots", error);
        }

        return 0;
    }

    private boolean autoFlipWidgetTreeTextContainsAny(net.runelite.api.widgets.Widget widget, String... needles)
    {
        if (widget == null || needles == null)
        {
            return false;
        }

        try
        {
            String text = cleanWidgetText(widget.getText()).toLowerCase(java.util.Locale.ROOT);
            if (!text.isEmpty())
            {
                for (String needle : needles)
                {
                    if (needle != null && !needle.trim().isEmpty() && text.contains(needle.toLowerCase(java.util.Locale.ROOT)))
                    {
                        return true;
                    }
                }
            }

            net.runelite.api.widgets.Widget[] dynamicChildren = widget.getDynamicChildren();
            if (dynamicChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : dynamicChildren)
                {
                    if (autoFlipWidgetTreeTextContainsAny(child, needles))
                    {
                        return true;
                    }
                }
            }

            net.runelite.api.widgets.Widget[] staticChildren = widget.getStaticChildren();
            if (staticChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : staticChildren)
                {
                    if (autoFlipWidgetTreeTextContainsAny(child, needles))
                    {
                        return true;
                    }
                }
            }

            net.runelite.api.widgets.Widget[] nestedChildren = widget.getNestedChildren();
            if (nestedChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : nestedChildren)
                {
                    if (autoFlipWidgetTreeTextContainsAny(child, needles))
                    {
                        return true;
                    }
                }
            }
        }
        catch (Throwable ignored)
        {
            return false;
        }

        return false;
    }
    public void retryAutoFlipSetupSearchInjection()
    {
        retryAutoFlipSetupSearchInjection(false);
    }

    private void retryAutoFlipSetupSearchInjection(boolean forceSearchSeedOverwrite)
    {
        AutoFlipBoardCard card = getAutoFlipSetupTargetBoardCard();
        if (card == null || card.getItemId() <= 0)
        {
            int pendingItemId = autoFlipPendingGuidedSetupItemId;
            String pendingItemName = autoFlipPendingGuidedSetupItemName;
            if (pendingItemId > 0 && pendingItemName != null && !pendingItemName.trim().isEmpty())
            {
                injectAutoFlipGeSearchText(pendingItemName, pendingItemId, forceSearchSeedOverwrite);
                return;
            }

            return;
        }

        injectAutoFlipGeSearchText(card.getItemName(), card.getItemId(), forceSearchSeedOverwrite);
    }
    public Rectangle getAutoFlipGuidedSetupSearchInputBounds()
    {
        try
        {
            if (client == null)
            {
                return null;
            }

            net.runelite.api.widgets.Widget inputWidget = client.getWidget(10616876);
            if (inputWidget == null)
            {
                return null;
            }

            Rectangle bounds = inputWidget.getBounds();
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
            {
                return null;
            }

            return new Rectangle(bounds);
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("getAutoFlipGuidedSetupSearchInputBounds", error);
            return null;
        }
    }

    private Rectangle getAutoFlipGuidedSetupTargetSearchResultBoundsByItemId()
    {
        try
        {
            // Only return the exact target result when it is actually inside the visible GE search-result viewport.
            // This prevents offscreen scrolled results from enabling item_pick and prevents the fake fallback square.
            AutoFlipBoardCard targetCard = getAutoFlipSetupTargetBoardCard();
            if (targetCard == null || targetCard.getItemId() <= 0 || client == null)
            {
                return null;
            }

            Rectangle input = getAutoFlipGuidedSetupSearchInputBounds();
            if (input == null || input.width <= 0 || input.height <= 0)
            {
                return null;
            }

            Rectangle itemBounds = findAutoFlipGeSearchResultWidgetBoundsByItemId(targetCard.getItemId());
            if (itemBounds == null || itemBounds.width <= 0 || itemBounds.height <= 0)
            {
                return null;
            }

            int itemCenterX = itemBounds.x + (itemBounds.width / 2);
            int itemCenterY = itemBounds.y + (itemBounds.height / 2);

            int minX = input.x + readIntConfig("setup.item.result.visible.min.x", -12);
            int maxX = input.x + input.width + readIntConfig("setup.item.result.visible.max.x.extra", 12);

            int minY = input.y + readIntConfig("setup.item.result.visible.min.y", 18);
            int maxY = input.y + readIntConfig("setup.item.result.visible.max.y", 155);

            if (itemCenterX < minX || itemCenterX > maxX || itemCenterY < minY || itemCenterY > maxY)
            {
                return null;
            }

            int width = Math.max(itemBounds.width, readIntConfig("setup.item.result.w", 145));
            int height = Math.max(itemBounds.height, readIntConfig("setup.item.result.h", 30));
            return new Rectangle(
                Math.max(0, itemBounds.x),
                Math.max(0, itemBounds.y),
                width,
                height
            );
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("getAutoFlipGuidedSetupTargetSearchResultBoundsByItemId", error);
            return null;
        }
    }
    private Rectangle findAutoFlipGeSearchResultWidgetBoundsByItemId(int targetItemId)
    {
        if (targetItemId <= 0 || client == null)
        {
            return null;
        }

        try
        {
            Rectangle found = null;

            net.runelite.api.widgets.Widget directSearchResult = client.getWidget(162, 52);
            found = findAutoFlipGeSearchResultWidgetBoundsByItemId(directSearchResult, targetItemId, 0);
            if (found != null)
            {
return found;
            }

            net.runelite.api.widgets.Widget packedSearchResult = client.getWidget(10616884);
            found = findAutoFlipGeSearchResultWidgetBoundsByItemId(packedSearchResult, targetItemId, 0);
            if (found != null)
            {
return found;
            }

            net.runelite.api.widgets.Widget[] roots = client.getWidgetRoots();
            if (roots == null)
            {
                return null;
            }

            for (net.runelite.api.widgets.Widget root : roots)
            {
                found = findAutoFlipGeSearchResultWidgetBoundsByItemId(root, targetItemId, 0);
                if (found != null)
                {
return found;
                }
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("findAutoFlipGeSearchResultWidgetBoundsByItemId", error);
        }

        return null;
    }

    private Rectangle findAutoFlipGeSearchResultWidgetBoundsByItemId(net.runelite.api.widgets.Widget widget, int targetItemId, int depth)
    {
        if (widget == null || targetItemId <= 0 || depth > 40)
        {
            return null;
        }

        try
        {
            int widgetId = widget.getId();
            int group = widgetId >>> 16;
            int child = widgetId & 0xFFFF;
            boolean isSearchResultItemWidget = group == 162 && child == 52 && widget.getItemId() == targetItemId;

            if (!widget.isHidden() && isSearchResultItemWidget)
            {
                Rectangle bounds = widget.getBounds();
                if (bounds != null && bounds.width > 0 && bounds.height > 0)
                {
                    return new Rectangle(bounds);
                }
            }

            Rectangle found = findAutoFlipGeSearchResultWidgetArrayBoundsByItemId(widget.getChildren(), targetItemId, depth + 1);
            if (found != null)
            {
                return found;
            }

            found = findAutoFlipGeSearchResultWidgetArrayBoundsByItemId(widget.getStaticChildren(), targetItemId, depth + 1);
            if (found != null)
            {
                return found;
            }

            found = findAutoFlipGeSearchResultWidgetArrayBoundsByItemId(widget.getDynamicChildren(), targetItemId, depth + 1);
            if (found != null)
            {
                return found;
            }

            return findAutoFlipGeSearchResultWidgetArrayBoundsByItemId(widget.getNestedChildren(), targetItemId, depth + 1);
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private Rectangle findAutoFlipGeSearchResultWidgetArrayBoundsByItemId(net.runelite.api.widgets.Widget[] widgets, int targetItemId, int depth)
    {
        if (widgets == null || targetItemId <= 0 || depth > 40)
        {
            return null;
        }

        for (net.runelite.api.widgets.Widget widget : widgets)
        {
            Rectangle found = findAutoFlipGeSearchResultWidgetBoundsByItemId(widget, targetItemId, depth);
            if (found != null)
            {
                return found;
            }
        }

        return null;
    }
    public Rectangle getAutoFlipGuidedSetupSearchResultBounds()
    {
        try
        {
            return getAutoFlipGuidedSetupTargetSearchResultBoundsByItemId();
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("getAutoFlipGuidedSetupSearchResultBounds", error);
            return null;
        }
    }

    private void injectAutoFlipGeSearchText(String itemName, int itemId)
    {
        injectAutoFlipGeSearchText(itemName, itemId, false);
    }

    private void injectAutoFlipGeSearchText(String itemName, int itemId, boolean forceSearchSeedOverwrite)
    {
        // One-shot item-search seed: run the native GE search script with the full item name.
        // State 4b may force one overwrite of stale native previous-search display text.
        if (itemName == null || itemName.trim().isEmpty() || itemId <= 0)
        {
            return;
        }

        ++autoFlipGeSearchInjectSeq;
        final String value = itemName.trim();
        final int requestedItemId = itemId;
        final boolean forceSeedOverwrite = forceSearchSeedOverwrite;

        Runnable task = () ->
        {
            try
            {
                if (client == null)
                {
                    return;
                }


                net.runelite.api.widgets.Widget promptWidget = client.getWidget(10616875);
                net.runelite.api.widgets.Widget inputWidget = client.getWidget(10616876);
                if (promptWidget == null || inputWidget == null)
                {
                    return;
                }

                String promptText = cleanWidgetText(promptWidget.getText()).toLowerCase(java.util.Locale.ROOT);
                boolean quantityOrPricePromptVisible = promptText.contains("how many")
                    || promptText.contains("quantity")
                    || promptText.contains("set a price")
                    || promptText.contains("price for each item")
                    || autoFlipVisibleWidgetTextContainsAny(
                        "how many do you wish to buy",
                        "set a price for each item",
                        "set a price",
                        "price for each item"
                    );


                String lowerFull = value.toLowerCase(java.util.Locale.ROOT);
                String currentInputText = cleanWidgetText(inputWidget.getText()).toLowerCase(java.util.Locale.ROOT);

                if (!forceSeedOverwrite
                    && requestedItemId == autoFlipItemSearchSeededItemId
                    && value.equals(autoFlipItemSearchSeededText)
                    && currentInputText.contains(lowerFull))
                {
                    return;
                }

                // If the player has already typed the item or otherwise changed the search,
                // never fight them by writing it again.
                if (currentInputText.contains(lowerFull))
                {
                    return;
                }

                Object[] scriptArgs = inputWidget.getOnKeyListener();
                if (scriptArgs == null)
                {
                    return;
                }

                client.setVarcStrValue(VarClientID.MESLAYERINPUT, value);
                client.setVarcIntValue(VarClientID.MESLAYERMODE, AUTOFLIP_GE_SEARCH_MODE);
                client.runScript(scriptArgs);

                autoFlipItemSearchSeededItemId = requestedItemId;
                autoFlipItemSearchSeededText = value;
                autoFlipLastGeSearchInjectedItemId = requestedItemId;
                autoFlipLastGeSearchInjectedText = value;
                autoFlipLastGeSearchInjectedAtMs = System.currentTimeMillis();


                if (isAutoFlipVerboseRuntimeLoggingEnabled())
                {
                    logAutoFlipVerbose(
                        "AUTOFLIP_ITEM_SEARCH_SEED_ONESHOT"
                            + " item_id=" + requestedItemId
                            + " seed=" + value
                            + " native_script=true"
                    );
                }
            }
            catch (Throwable error)
            {
                logAutoFlipUiError("injectAutoFlipGeSearchText", error);
            }
        };

        try
        {
            if (clientThread != null)
            {
                clientThread.invoke(task);
            }
            else
            {
                task.run();
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("injectAutoFlipGeSearchTextInvoke", error);
        }
    }

    static boolean isAutoFlipNativePricePromptText(String promptText)
    {
        if (promptText == null)
        {
            return false;
        }

        String normalized = promptText.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("set a price") || normalized.contains("price for each item");
    }

    private void injectAutoFlipPriceChatboxValue(int gp, String source)
    {
        if (gp <= 0)
        {
            return;
        }

        Runnable task = () ->
        {
            try
            {
                String value = Integer.toString(gp);
                net.runelite.api.widgets.Widget promptWidget = client == null ? null : client.getWidget(10616875);
                net.runelite.api.widgets.Widget inputWidget = client == null ? null : client.getWidget(10616876);

                boolean livePricePrompt = promptWidget != null
                    && inputWidget != null
                    && !promptWidget.isHidden()
                    && !inputWidget.isHidden()
                    && promptWidget.getBounds() != null
                    && inputWidget.getBounds() != null
                    && promptWidget.getBounds().width > 0
                    && promptWidget.getBounds().height > 0
                    && inputWidget.getBounds().width > 0
                    && inputWidget.getBounds().height > 0
                    && isAutoFlipNativePricePromptText(cleanWidgetText(promptWidget.getText()));
                if (!livePricePrompt)
                {
                    String promptText = promptWidget == null ? "" : cleanWidgetText(promptWidget.getText());
                    if (isAutoFlipVerboseRuntimeLoggingEnabled())
                    {
                        logAutoFlipVerbose(
                            "AUTOFLIP_PRICE_CHATBOX_INJECT_BLOCKED"
                                + " gp=" + value
                                + " prompt=" + safe(promptText)
                                + " promptWidget=" + (promptWidget != null)
                                + " inputWidget=" + (inputWidget != null)
                        );
                    }
                    return;
                }

                if (client != null)
                {
                    // AUTOFLIP_PRICE_CHATBOX_VARC_359_V1
                    client.setVarcStrValue(359, value);
                }

                if (inputWidget != null)
                {
                    // AUTOFLIP_CHATBOX_INJECT_WIDGET_TEXT_REPAIR_V1
                    // Keep this as fill-only. Do not auto-submit Enter.
                    inputWidget.setText("<col=000000>" + value + "*");
                    inputWidget.revalidate();
                }

                if (isAutoFlipVerboseRuntimeLoggingEnabled())
                {
                    logAutoFlipVerbose("AUTOFLIP_PRICE_CHATBOX_INJECT_APPLIED gp=" + value + " inputWidget=" + (inputWidget != null));
                }
            }
            catch (Throwable error)
            {
                logAutoFlipUiError("injectAutoFlipPriceChatboxValue", error);
            }
        };

        try
        {
            if (clientThread != null)
            {
                clientThread.invoke(task);
            }
            else
            {
                task.run();
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("injectAutoFlipPriceChatboxValueInvoke", error);
        }
    }
    private static final class AutoFlipCardActionBounds
    {
        private final int slotIndex;
        private final int itemId;
        private final String marketUrl;
        private final Rectangle refreshBounds;
        private final Rectangle marketBounds;

        private AutoFlipCardActionBounds(int slotIndex, int itemId, String marketUrl, Rectangle refreshBounds, Rectangle marketBounds)
        {
            this.slotIndex = slotIndex;
            this.itemId = itemId;
            this.marketUrl = marketUrl;
            this.refreshBounds = refreshBounds;
            this.marketBounds = marketBounds;
        }
    }

    private static final class AutoFlipGeOfferSlotSnapshot
    {
        private final int slot;
        private final int itemId;
        private final String state;
        private final int totalQuantity;
        private final int quantitySold;
        private final int price;
        private final int spent;
        private final boolean empty;
        private final boolean terminal;
        private final long observedAtMs;

        private AutoFlipGeOfferSlotSnapshot(
            int slot,
            int itemId,
            String state,
            int totalQuantity,
            int quantitySold,
            int price,
            int spent,
            boolean empty,
            boolean terminal,
            long observedAtMs)
        {
            this.slot = slot;
            this.itemId = itemId;
            this.state = state == null ? "UNKNOWN" : state;
            this.totalQuantity = totalQuantity;
            this.quantitySold = quantitySold;
            this.price = price;
            this.spent = spent;
            this.empty = empty;
            this.terminal = terminal;
            this.observedAtMs = observedAtMs;
        }

        private static AutoFlipGeOfferSlotSnapshot empty(int slot)
        {
            return new AutoFlipGeOfferSlotSnapshot(slot, 0, "EMPTY", 0, 0, 0, 0, true, false, 0L);
        }
    }

    private static final class AutoFlipGeMarketLinkBounds
    {
        private final int slotIndex;
        private final int itemId;
        private final String marketUrl;
        private final Rectangle bounds;

        private AutoFlipGeMarketLinkBounds(int slotIndex, int itemId, String marketUrl, Rectangle bounds)
        {
            this.slotIndex = slotIndex;
            this.itemId = itemId;
            this.marketUrl = marketUrl;
            this.bounds = bounds;
        }
    }

    public java.awt.image.BufferedImage getAutoFlipInventoryItemImage(int itemId)
    {
        try
        {
            if (itemManager == null || itemId <= 0)
            {
                return null;
            }

            return itemManager.getImage(itemId);
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("getAutoFlipInventoryItemImage", error);
            return null;
        }
    }
    public java.awt.image.BufferedImage getAutoFlipCoinStackImage(long amountGp)
    {
        try
        {
            if (itemManager == null)
            {
                return null;
            }

            int quantity = (int)Math.max(1L, Math.min(Integer.MAX_VALUE, amountGp));
            return cleanAutoFlipCoinStackImage(itemManager.getImage(995, quantity, true));
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("getAutoFlipCoinStackImage", error);
            return null;
        }
    }
    private java.awt.image.BufferedImage cleanAutoFlipCoinStackImage(java.awt.image.BufferedImage src)
    {
        if (src == null)
        {
            return null;
        }

        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(src.getWidth(), src.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < src.getHeight(); y++)
        {
            for (int x = 0; x < src.getWidth(); x++)
            {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xff;
                int r = (argb >>> 16) & 0xff;
                int g = (argb >>> 8) & 0xff;
                int b = argb & 0xff;

                if (a > 0 && r <= 2 && g <= 2 && b <= 2)
                {
                    out.setRGB(x, y, 0x00000000);
                }
                else
                {
                    out.setRGB(x, y, argb);
                }
            }
        }

        return out;
    }
    public static final class AutoFlipInventoryItem
    {
        private final int itemId;
        private final String itemName;
        private final int quantity;
        private final String status;
        private final String source;
        private final String addedAt;
        private final long assessedUnitValueGp;
        private final long patienceBaselineGp;
        private final long patienceResultGp;

        private AutoFlipInventoryItem(int itemId, String itemName, int quantity, String status, String source, String addedAt, long assessedUnitValueGp)
        {
            this(
                itemId,
                itemName,
                quantity,
                status,
                source,
                addedAt,
                assessedUnitValueGp,
                Math.max(0L, assessedUnitValueGp) * Math.max(0, quantity),
                0L
            );
        }

        private AutoFlipInventoryItem(int itemId, String itemName, int quantity, String status, String source, String addedAt, long assessedUnitValueGp, long patienceBaselineGp, long patienceResultGp)
        {
            this.itemId = itemId;
            this.itemName = itemName;
            this.quantity = quantity;
            this.status = status;
            this.source = source;
            this.addedAt = addedAt;
            this.assessedUnitValueGp = assessedUnitValueGp;
            this.patienceBaselineGp = Math.max(0L, patienceBaselineGp);
            this.patienceResultGp = Math.max(0L, patienceResultGp);
        }

        public int getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public int getQuantity() { return quantity; }
        public String getStatus() { return status; }
        public String getSource() { return source; }
        public String getAddedAt() { return addedAt; }
        public long getAssessedUnitValueGp() { return assessedUnitValueGp; }
        public long getHeldCapitalGp() { return Math.max(0L, assessedUnitValueGp) * Math.max(0, quantity); }
        public long getPatienceBaselineGp() { return patienceBaselineGp; }
        public long getPatienceResultGp() { return patienceResultGp; }
    }

    public static final class AutoFlipCurrentOfferSnapshot
    {
        private final int slot;
        private final int itemId;
        private final String itemName;
        private final String side;
        private final String state;
        private final int offeredPriceGp;
        private final int offeredQuantity;
        private final int soldQuantity;
        private final int remainingQuantity;
        private final long spentOrReceivedGp;
        private final long ageSeconds;

        private AutoFlipCurrentOfferSnapshot(
            int slot,
            int itemId,
            String itemName,
            String side,
            String state,
            int offeredPriceGp,
            int offeredQuantity,
            int soldQuantity,
            int remainingQuantity,
            long spentOrReceivedGp,
            long ageSeconds
        )
        {
            this.slot = Math.max(0, slot);
            this.itemId = Math.max(0, itemId);
            this.itemName = itemName == null || itemName.trim().isEmpty() ? ("Item " + itemId) : itemName.trim();
            this.side = side == null || side.trim().isEmpty() ? "UNKNOWN" : side.trim().toUpperCase(java.util.Locale.ROOT);
            this.state = state == null || state.trim().isEmpty() ? "UNKNOWN" : state.trim().toUpperCase(java.util.Locale.ROOT);
            this.offeredPriceGp = Math.max(0, offeredPriceGp);
            this.offeredQuantity = Math.max(0, offeredQuantity);
            this.soldQuantity = Math.max(0, soldQuantity);
            this.remainingQuantity = Math.max(0, remainingQuantity);
            this.spentOrReceivedGp = Math.max(0L, spentOrReceivedGp);
            this.ageSeconds = Math.max(0L, ageSeconds);
        }

        public int getSlot() { return slot; }
        public int getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public String getSide() { return side; }
        public String getState() { return state; }
        public int getOfferedPriceGp() { return offeredPriceGp; }
        public int getOfferedQuantity() { return offeredQuantity; }
        public int getSoldQuantity() { return soldQuantity; }
        public int getRemainingQuantity() { return remainingQuantity; }
        public long getSpentOrReceivedGp() { return spentOrReceivedGp; }
        public long getAgeSeconds() { return ageSeconds; }
        public String getDisplaySummary()
        {
            String action = "BUY".equalsIgnoreCase(side) ? "Bought" : "Sold";
            return action + " " + soldQuantity + " / " + offeredQuantity;
        }
    }

    private static final class AutoFlipMarketSnapshot
    {
        private final int itemId;
        private final String itemName;
        private final long currentBuyPriceGp;
        private final long currentSellPriceGp;
        private final long marketBuyPriceGp;
        private final long marketSellPriceGp;
        private final long executionBuyPriceGp;
        private final long executionSellPriceGp;
        private final long dayLowGp;
        private final long weekLowGp;
        private final long monthLowGp;
        private final long dayHighGp;
        private final long weekHighGp;
        private final long monthHighGp;
        private final String priceSource;
        private final String priceReason;
        private final String priceUpdatedAt;

        private AutoFlipMarketSnapshot(
            int itemId,
            String itemName,
            long currentBuyPriceGp,
            long currentSellPriceGp,
            long marketBuyPriceGp,
            long marketSellPriceGp,
            long executionBuyPriceGp,
            long executionSellPriceGp,
            long dayLowGp,
            long weekLowGp,
            long monthLowGp,
            long dayHighGp,
            long weekHighGp,
            long monthHighGp,
            String priceSource,
            String priceReason,
            String priceUpdatedAt
        )
        {
            this.itemId = itemId;
            this.itemName = itemName == null || itemName.trim().isEmpty() ? ("Item " + itemId) : itemName.trim();
            this.currentBuyPriceGp = Math.max(0L, currentBuyPriceGp);
            this.currentSellPriceGp = Math.max(0L, currentSellPriceGp);
            this.marketBuyPriceGp = Math.max(0L, marketBuyPriceGp);
            this.marketSellPriceGp = Math.max(0L, marketSellPriceGp);
            this.executionBuyPriceGp = Math.max(0L, executionBuyPriceGp);
            this.executionSellPriceGp = Math.max(0L, executionSellPriceGp);
            this.dayLowGp = Math.max(0L, dayLowGp);
            this.weekLowGp = Math.max(0L, weekLowGp);
            this.monthLowGp = Math.max(0L, monthLowGp);
            this.dayHighGp = Math.max(0L, dayHighGp);
            this.weekHighGp = Math.max(0L, weekHighGp);
            this.monthHighGp = Math.max(0L, monthHighGp);
            this.priceSource = priceSource == null ? "" : priceSource.trim();
            this.priceReason = priceReason == null ? "" : priceReason.trim();
            this.priceUpdatedAt = priceUpdatedAt == null ? "" : priceUpdatedAt.trim();
        }
    }

    public static final class AutoFlipToBuyItem
    {
        private final int itemId;
        private final String itemName;
        private final int quantity;
        private final String status;
        private final String source;
        private final String addedAt;
        private final long targetBuyPriceGp;
        private final int buyWindowHours;
        private final long autoGoodBuyPriceGp;
        private final long currentBuyPriceGp;
        private final long currentSellPriceGp;
        private final long executionBuyPriceGp;
        private final long executionSellPriceGp;
        private final long marketBuyPriceGp;
        private final long marketSellPriceGp;
        private final String priceSource;
        private final String priceReason;
        private final long priceUpdatedAtMs;

        private AutoFlipToBuyItem(
            int itemId,
            String itemName,
            int quantity,
            String status,
            String source,
            String addedAt,
            long targetBuyPriceGp,
            int buyWindowHours,
            long autoGoodBuyPriceGp,
            long currentBuyPriceGp,
            long currentSellPriceGp,
            long executionBuyPriceGp,
            long executionSellPriceGp,
            long marketBuyPriceGp,
            long marketSellPriceGp,
            String priceSource,
            String priceReason,
            long priceUpdatedAtMs
        )
        {
            this.itemId = itemId;
            this.itemName = itemName == null || itemName.trim().isEmpty() ? ("Item " + itemId) : itemName.trim();
            this.quantity = Math.max(1, quantity);
            this.status = status == null || status.trim().isEmpty() ? "WATCH" : status.trim().toUpperCase(java.util.Locale.ROOT);
            this.source = source == null || source.trim().isEmpty() ? "local" : source.trim();
            this.addedAt = addedAt == null ? "" : addedAt.trim();
            this.targetBuyPriceGp = Math.max(0L, targetBuyPriceGp);
            this.buyWindowHours = Math.max(0, buyWindowHours);
            this.autoGoodBuyPriceGp = Math.max(0L, autoGoodBuyPriceGp);
            this.currentBuyPriceGp = Math.max(0L, currentBuyPriceGp);
            this.currentSellPriceGp = Math.max(0L, currentSellPriceGp);
            this.executionBuyPriceGp = Math.max(0L, executionBuyPriceGp);
            this.executionSellPriceGp = Math.max(0L, executionSellPriceGp);
            this.marketBuyPriceGp = Math.max(0L, marketBuyPriceGp);
            this.marketSellPriceGp = Math.max(0L, marketSellPriceGp);
            this.priceSource = priceSource == null ? "" : priceSource.trim();
            this.priceReason = priceReason == null ? "" : priceReason.trim();
            this.priceUpdatedAtMs = Math.max(0L, priceUpdatedAtMs);
        }

        private static AutoFlipToBuyItem create(int itemId, String itemName, String addedAt, AutoFlipMarketSnapshot snapshot, long nowMs)
        {
            long autoGoodBuyPriceGp = snapshot == null
                ? 0L
                : snapshot.executionBuyPriceGp > 0L
                    ? snapshot.executionBuyPriceGp
                    : snapshot.currentBuyPriceGp > 0L
                        ? snapshot.currentBuyPriceGp
                        : snapshot.marketBuyPriceGp;

            return new AutoFlipToBuyItem(
                itemId,
                itemName,
                1,
                "WATCH",
                "local",
                addedAt,
                0L,
                0,
                autoGoodBuyPriceGp,
                snapshot == null ? 0L : snapshot.currentBuyPriceGp,
                snapshot == null ? 0L : snapshot.currentSellPriceGp,
                snapshot == null ? 0L : snapshot.executionBuyPriceGp,
                snapshot == null ? 0L : snapshot.executionSellPriceGp,
                snapshot == null ? 0L : snapshot.marketBuyPriceGp,
                snapshot == null ? 0L : snapshot.marketSellPriceGp,
                snapshot == null ? "" : snapshot.priceSource,
                snapshot == null ? "" : snapshot.priceReason,
                nowMs
            );
        }

        private AutoFlipToBuyItem withCopiedSettingsFrom(AutoFlipToBuyItem other)
        {
            if (other == null)
            {
                return this;
            }

            return new AutoFlipToBuyItem(
                itemId,
                itemName,
                quantity,
                status,
                source,
                addedAt,
                other.targetBuyPriceGp,
                other.buyWindowHours,
                autoGoodBuyPriceGp,
                currentBuyPriceGp,
                currentSellPriceGp,
                executionBuyPriceGp,
                executionSellPriceGp,
                marketBuyPriceGp,
                marketSellPriceGp,
                priceSource,
                priceReason,
                priceUpdatedAtMs
            );
        }

        private AutoFlipToBuyItem withCustomSettings(long targetBuyPriceGp, int buyWindowHours)
        {
            return withCustomSettings(targetBuyPriceGp, buyWindowHours, quantity);
        }

        private AutoFlipToBuyItem withCustomSettings(long targetBuyPriceGp, int buyWindowHours, int nextQuantity)
        {
            return new AutoFlipToBuyItem(
                itemId,
                itemName,
                Math.max(1, nextQuantity),
                status,
                source,
                addedAt,
                targetBuyPriceGp,
                buyWindowHours,
                autoGoodBuyPriceGp,
                currentBuyPriceGp,
                currentSellPriceGp,
                executionBuyPriceGp,
                executionSellPriceGp,
                marketBuyPriceGp,
                marketSellPriceGp,
                priceSource,
                priceReason,
                priceUpdatedAtMs
            );
        }

        private AutoFlipToBuyItem withMarketSnapshot(AutoFlipMarketSnapshot snapshot)
        {
            if (snapshot == null)
            {
                return this;
            }

            long nextAutoGoodBuyPriceGp = targetBuyPriceGp > 0L
                ? autoGoodBuyPriceGp
                : snapshot.executionBuyPriceGp > 0L
                    ? snapshot.executionBuyPriceGp
                    : snapshot.currentBuyPriceGp > 0L
                        ? snapshot.currentBuyPriceGp
                        : snapshot.marketBuyPriceGp;

            if (buyWindowHours > 0)
            {
                long windowLow = resolveWindowLowPrice(snapshot, buyWindowHours);
                if (windowLow > 0L)
                {
                    nextAutoGoodBuyPriceGp = windowLow;
                }
            }

            return new AutoFlipToBuyItem(
                itemId,
                snapshot.itemName,
                quantity,
                status,
                source,
                addedAt,
                targetBuyPriceGp,
                buyWindowHours,
                nextAutoGoodBuyPriceGp,
                snapshot.currentBuyPriceGp,
                snapshot.currentSellPriceGp,
                snapshot.executionBuyPriceGp,
                snapshot.executionSellPriceGp,
                snapshot.marketBuyPriceGp,
                snapshot.marketSellPriceGp,
                snapshot.priceSource,
                snapshot.priceReason,
                System.currentTimeMillis()
            );
        }

        private static long resolveWindowLowPrice(AutoFlipMarketSnapshot snapshot, int buyWindowHours)
        {
            if (snapshot == null || buyWindowHours <= 0)
            {
                return 0L;
            }

            if (buyWindowHours <= 24)
            {
                return snapshot.dayLowGp > 0L ? snapshot.dayLowGp : snapshot.currentBuyPriceGp;
            }

            if (buyWindowHours <= 168)
            {
                return snapshot.weekLowGp > 0L ? snapshot.weekLowGp : snapshot.dayLowGp > 0L ? snapshot.dayLowGp : snapshot.currentBuyPriceGp;
            }

            return snapshot.monthLowGp > 0L ? snapshot.monthLowGp : snapshot.weekLowGp > 0L ? snapshot.weekLowGp : snapshot.currentBuyPriceGp;
        }

        private boolean isSameMarketSnapshot(AutoFlipToBuyItem other)
        {
            if (other == null)
            {
                return false;
            }

            return currentBuyPriceGp == other.currentBuyPriceGp
                && currentSellPriceGp == other.currentSellPriceGp
                && executionBuyPriceGp == other.executionBuyPriceGp
                && executionSellPriceGp == other.executionSellPriceGp
                && marketBuyPriceGp == other.marketBuyPriceGp
                && marketSellPriceGp == other.marketSellPriceGp
                && autoGoodBuyPriceGp == other.autoGoodBuyPriceGp
                && java.util.Objects.equals(priceSource, other.priceSource)
                && java.util.Objects.equals(priceReason, other.priceReason);
        }

        public long getEffectiveBuyThresholdGp()
        {
            if (targetBuyPriceGp > 0L)
            {
                return targetBuyPriceGp;
            }

            if (autoGoodBuyPriceGp > 0L)
            {
                return autoGoodBuyPriceGp;
            }

            if (executionBuyPriceGp > 0L)
            {
                return executionBuyPriceGp;
            }

            if (marketBuyPriceGp > 0L)
            {
                return marketBuyPriceGp;
            }

            return currentBuyPriceGp;
        }

        public boolean isBuyReady()
        {
            long threshold = getEffectiveBuyThresholdGp();
            return currentBuyPriceGp > 0L && threshold > 0L && currentBuyPriceGp <= threshold;
        }

        public String getRequirementLabel()
        {
            if (targetBuyPriceGp > 0L)
            {
                return quantity > 1
                    ? "Target Buy (each): " + String.format(java.util.Locale.US, "%,d", targetBuyPriceGp) + " gp"
                    : "Target Buy: " + String.format(java.util.Locale.US, "%,d", targetBuyPriceGp) + " gp";
            }

            if (buyWindowHours > 0)
            {
                return "Best in " + buyWindowHours + "h";
            }

            if (autoGoodBuyPriceGp > 0L)
            {
                return "Auto Buy ≤ " + autoGoodBuyPriceGp + " gp";
            }

            return "Auto buy when price is good";
        }

        public String getSignalLabel()
        {
            if (isBuyReady())
            {
                return "BUY NOW";
            }

            return targetBuyPriceGp > 0L || buyWindowHours > 0 ? "WATCH" : "AUTO";
        }

        public long getTargetBuyTotalCostGp()
        {
            long each = getEffectiveBuyThresholdGp();
            if (each <= 0L)
            {
                each = targetBuyPriceGp;
            }

            if (each <= 0L)
            {
                return 0L;
            }

            return Math.max(0L, each) * Math.max(1, quantity);
        }

        public String getTargetBuyTotalCostLabel()
        {
            long totalCostGp = getTargetBuyTotalCostGp();
            return totalCostGp > 0L ? "Total cost: " + String.format(java.util.Locale.US, "%,d", totalCostGp) + " gp" : "";
        }

        private String toJsonLine()
        {
            StringBuilder sb = new StringBuilder(256);
            sb.append('{');
            sb.append("\"item_id\":").append(itemId).append(',');
            sb.append("\"item_name\":").append(jsonStringLiteral(itemName)).append(',');
            sb.append("\"quantity\":").append(quantity).append(',');
            sb.append("\"status\":").append(jsonStringLiteral(status)).append(',');
            sb.append("\"source\":").append(jsonStringLiteral(source)).append(',');
            sb.append("\"added_at\":").append(jsonStringLiteral(addedAt)).append(',');
            sb.append("\"target_buy_price_gp\":").append(targetBuyPriceGp).append(',');
            sb.append("\"buy_window_hours\":").append(buyWindowHours).append(',');
            sb.append("\"auto_good_buy_price_gp\":").append(autoGoodBuyPriceGp).append(',');
            sb.append("\"current_buy_price_gp\":").append(currentBuyPriceGp).append(',');
            sb.append("\"current_sell_price_gp\":").append(currentSellPriceGp).append(',');
            sb.append("\"execution_buy_price_gp\":").append(executionBuyPriceGp).append(',');
            sb.append("\"execution_sell_price_gp\":").append(executionSellPriceGp).append(',');
            sb.append("\"market_buy_price_gp\":").append(marketBuyPriceGp).append(',');
            sb.append("\"market_sell_price_gp\":").append(marketSellPriceGp).append(',');
            sb.append("\"price_source\":").append(jsonStringLiteral(priceSource)).append(',');
            sb.append("\"price_reason\":").append(jsonStringLiteral(priceReason)).append(',');
            sb.append("\"price_updated_at_ms\":").append(priceUpdatedAtMs);
            sb.append('}');
            return sb.toString();
        }

        public int getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public int getQuantity() { return quantity; }
        public String getStatus() { return status; }
        public String getSource() { return source; }
        public String getAddedAt() { return addedAt; }
        public long getTargetBuyPriceGp() { return targetBuyPriceGp; }
        public int getBuyWindowHours() { return buyWindowHours; }
        public long getAutoGoodBuyPriceGp() { return autoGoodBuyPriceGp; }
        public long getCurrentBuyPriceGp() { return currentBuyPriceGp; }
        public long getCurrentSellPriceGp() { return currentSellPriceGp; }
        public long getExecutionBuyPriceGp() { return executionBuyPriceGp; }
        public long getExecutionSellPriceGp() { return executionSellPriceGp; }
        public long getMarketBuyPriceGp() { return marketBuyPriceGp; }
        public long getMarketSellPriceGp() { return marketSellPriceGp; }
        public String getPriceSource() { return priceSource; }
        public String getPriceReason() { return priceReason; }
        public long getPriceUpdatedAtMs() { return priceUpdatedAtMs; }
    }

    public static final class AutoFlipMarketSearchResult
    {
        private final int itemId;
        private final String itemName;
        private final String slug;
        private final long buyPriceGp;
        private final long sellPriceGp;
        private final long executionBuyPriceGp;
        private final long executionSellPriceGp;
        private final String priceSource;
        private final String priceReason;
        private final String marketUrl;

        private AutoFlipMarketSearchResult(
            int itemId,
            String itemName,
            String slug,
            long buyPriceGp,
            long sellPriceGp,
            long executionBuyPriceGp,
            long executionSellPriceGp,
            String priceSource,
            String priceReason,
            String marketUrl
        )
        {
            this.itemId = itemId;
            this.itemName = itemName == null || itemName.trim().isEmpty() ? ("Item " + itemId) : itemName.trim();
            this.slug = slug == null ? "" : slug.trim();
            this.buyPriceGp = Math.max(0L, buyPriceGp);
            this.sellPriceGp = Math.max(0L, sellPriceGp);
            this.executionBuyPriceGp = Math.max(0L, executionBuyPriceGp);
            this.executionSellPriceGp = Math.max(0L, executionSellPriceGp);
            this.priceSource = priceSource == null ? "" : priceSource.trim();
            this.priceReason = priceReason == null ? "" : priceReason.trim();
            this.marketUrl = marketUrl == null ? "" : marketUrl.trim();
        }

        public int getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public String getSlug() { return slug; }
        public long getBuyPriceGp() { return buyPriceGp; }
        public long getSellPriceGp() { return sellPriceGp; }
        public long getExecutionBuyPriceGp() { return executionBuyPriceGp; }
        public long getExecutionSellPriceGp() { return executionSellPriceGp; }
        public String getPriceSource() { return priceSource; }
        public String getPriceReason() { return priceReason; }
        public String getMarketUrl() { return marketUrl; }
    }

    public static final class AutoFlipSellGuidance
    {
        private final int slotIndex;
        private final int itemId;
        private final String itemName;
        private final int holdQuantity;
        private final long sellPriceGp;
        private final boolean presentInInventory;
        private final int inventoryQuantity;

        private AutoFlipSellGuidance(int slotIndex, int itemId, String itemName, int holdQuantity, long sellPriceGp, boolean presentInInventory, int inventoryQuantity)
        {
            this.slotIndex = slotIndex;
            this.itemId = itemId;
            this.itemName = itemName == null || itemName.trim().isEmpty() ? ("item #" + itemId) : itemName.trim();
            this.holdQuantity = Math.max(0, holdQuantity);
            this.sellPriceGp = Math.max(0L, sellPriceGp);
            this.presentInInventory = presentInInventory;
            this.inventoryQuantity = Math.max(0, inventoryQuantity);
        }

        public int getSlotIndex() { return slotIndex; }
        public int getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public int getHoldQuantity() { return holdQuantity; }
        public long getSellPriceGp() { return sellPriceGp; }
        public boolean isPresentInInventory() { return presentInInventory; }
        public int getInventoryQuantity() { return inventoryQuantity; }
    }

    private static final class AutoFlipJsonMember
    {
        private final String key;
        private final String value;

        private AutoFlipJsonMember(String key, String value)
        {
            this.key = key;
            this.value = value;
        }
    }

    private static final class AutoFlipRecommendationContext
    {
        private final String recommendationId;
        private final String planId;
        private final String cacheBuildId;
        private final String payloadHash;
        private final String payloadGeneratedAt;
        private final long recommendationGeneratedTsMs;
        private final long recommendationShownTsMs;
        private final int boardSlot;
        private final int rank;
        private final String sliceKey;
        private final String matrixKey;
        private final String mode;
        private final String budgetBand;
        private final String profile;
        private final String optimizerVersion;
        private final String executionPricingSource;
        private final String sourceCache;
        private final long userSelectedBudgetGp;
        private final int userSelectedHoursAway;
        private final long suggestedBuyPriceGp;
        private final long suggestedSellPriceGp;
        private final int suggestedQuantity;
        private final long suggestedTotalBudgetGp;
        private final long hourlyVolumeCapacity;
        private final double throughputRatio;
        private final double throughputPressure;
        private final double volumeFit;

        private AutoFlipRecommendationContext(
            String recommendationId,
            String planId,
            String cacheBuildId,
            String payloadHash,
            String payloadGeneratedAt,
            long recommendationGeneratedTsMs,
            long recommendationShownTsMs,
            int boardSlot,
            int rank,
            String sliceKey,
            String matrixKey,
            String mode,
            String budgetBand,
            String profile,
            String optimizerVersion,
            String executionPricingSource,
            String sourceCache,
            long userSelectedBudgetGp,
            int userSelectedHoursAway,
            long suggestedBuyPriceGp,
            long suggestedSellPriceGp,
            int suggestedQuantity,
            long suggestedTotalBudgetGp,
            long hourlyVolumeCapacity,
            double throughputRatio,
            double throughputPressure,
            double volumeFit
        )
        {
            this.recommendationId = recommendationId == null ? "" : recommendationId;
            this.planId = planId == null ? "" : planId;
            this.cacheBuildId = cacheBuildId == null ? "" : cacheBuildId;
            this.payloadHash = payloadHash == null ? "" : payloadHash;
            this.payloadGeneratedAt = payloadGeneratedAt == null ? "" : payloadGeneratedAt;
            this.recommendationGeneratedTsMs = Math.max(0L, recommendationGeneratedTsMs);
            this.recommendationShownTsMs = Math.max(0L, recommendationShownTsMs);
            this.boardSlot = boardSlot;
            this.rank = Math.max(0, rank);
            this.sliceKey = sliceKey == null ? "" : sliceKey;
            this.matrixKey = matrixKey == null ? "" : matrixKey;
            this.mode = mode == null ? "" : mode;
            this.budgetBand = budgetBand == null ? "" : budgetBand;
            this.profile = profile == null ? "" : profile;
            this.optimizerVersion = optimizerVersion == null ? "" : optimizerVersion;
            this.executionPricingSource = executionPricingSource == null ? "" : executionPricingSource;
            this.sourceCache = sourceCache == null ? "" : sourceCache;
            this.userSelectedBudgetGp = Math.max(0L, userSelectedBudgetGp);
            this.userSelectedHoursAway = Math.max(0, userSelectedHoursAway);
            this.suggestedBuyPriceGp = Math.max(0L, suggestedBuyPriceGp);
            this.suggestedSellPriceGp = Math.max(0L, suggestedSellPriceGp);
            this.suggestedQuantity = Math.max(0, suggestedQuantity);
            this.suggestedTotalBudgetGp = Math.max(0L, suggestedTotalBudgetGp);
            this.hourlyVolumeCapacity = Math.max(0L, hourlyVolumeCapacity);
            this.throughputRatio = Math.max(0.0D, throughputRatio);
            this.throughputPressure = Math.max(0.0D, throughputPressure);
            this.volumeFit = Math.max(0.0D, volumeFit);
        }

        private AutoFlipRecommendationContext withShownCard(
            int boardSlot,
            long shownTsMs,
            long buyPriceGp,
            long sellPriceGp,
            int quantity,
            long totalBudgetGp
        )
        {
            return new AutoFlipRecommendationContext(
                recommendationId,
                planId,
                cacheBuildId,
                payloadHash,
                payloadGeneratedAt,
                recommendationGeneratedTsMs,
                shownTsMs,
                boardSlot,
                rank,
                sliceKey,
                matrixKey,
                mode,
                budgetBand,
                profile,
                optimizerVersion,
                executionPricingSource,
                sourceCache,
                userSelectedBudgetGp,
                userSelectedHoursAway,
                buyPriceGp,
                sellPriceGp,
                quantity,
                totalBudgetGp,
                hourlyVolumeCapacity,
                throughputRatio,
                throughputPressure,
                volumeFit
            );
        }

        private AutoFlipRecommendationContext withRecommendationId(String id)
        {
            return new AutoFlipRecommendationContext(
                id,
                planId,
                cacheBuildId,
                payloadHash,
                payloadGeneratedAt,
                recommendationGeneratedTsMs,
                recommendationShownTsMs,
                boardSlot,
                rank,
                sliceKey,
                matrixKey,
                mode,
                budgetBand,
                profile,
                optimizerVersion,
                executionPricingSource,
                sourceCache,
                userSelectedBudgetGp,
                userSelectedHoursAway,
                suggestedBuyPriceGp,
                suggestedSellPriceGp,
                suggestedQuantity,
                suggestedTotalBudgetGp,
                hourlyVolumeCapacity,
                throughputRatio,
                throughputPressure,
                volumeFit
            );
        }

        private AutoFlipRecommendationContext copyWithSuggestedQuantity(int quantity)
        {
            return new AutoFlipRecommendationContext(
                recommendationId,
                planId,
                cacheBuildId,
                payloadHash,
                payloadGeneratedAt,
                recommendationGeneratedTsMs,
                recommendationShownTsMs,
                boardSlot,
                rank,
                sliceKey,
                matrixKey,
                mode,
                budgetBand,
                profile,
                optimizerVersion,
                executionPricingSource,
                sourceCache,
                userSelectedBudgetGp,
                userSelectedHoursAway,
                suggestedBuyPriceGp,
                suggestedSellPriceGp,
                quantity,
                Math.max(0L, suggestedBuyPriceGp) * Math.max(0, quantity),
                hourlyVolumeCapacity,
                throughputRatio,
                throughputPressure,
                volumeFit
            );
        }
    }

    private static final class AutoFlipPayloadRow
    {
        private final int itemId;
        private final String itemName;
        private final long buyPriceGp;
        private final long sellPriceGp;
        private final long profitEachGp;
        private final int quantity;
        private final int maxQuantity;
        private final double fillProbability;
        private final String confidenceBand;
        private final String reason;
        private final String marketUrl;
        private final String maxHoldTimeLabel;
        private final AutoFlipRecommendationContext recommendationContext;

        private AutoFlipPayloadRow(
            int itemId,
            String itemName,
            long buyPriceGp,
            long sellPriceGp,
            long profitEachGp,
            int quantity,
            int maxQuantity,
            double fillProbability,
            String confidenceBand,
            String reason,
            String marketUrl,
            String maxHoldTimeLabel,
            AutoFlipRecommendationContext recommendationContext
        )
        {
            this.itemId = itemId;
            this.itemName = itemName;
            this.buyPriceGp = buyPriceGp;
            this.sellPriceGp = sellPriceGp;
            this.profitEachGp = profitEachGp;
            this.quantity = quantity;
            this.maxQuantity = maxQuantity;
            this.fillProbability = fillProbability;
            this.confidenceBand = confidenceBand;
            this.reason = reason;
            this.marketUrl = marketUrl;
            this.maxHoldTimeLabel = maxHoldTimeLabel;
            this.recommendationContext = recommendationContext;
        }

        private long capitalGp()
        {
            return Math.max(0L, buyPriceGp) * Math.max(0, quantity);
        }

        private double roi()
        {
            return buyPriceGp > 0L ? (double) profitEachGp / (double) buyPriceGp : 0.0D;
        }

        private AutoFlipPayloadRow copyWithQuantity(int newQuantity)
        {
            return new AutoFlipPayloadRow(
                itemId,
                itemName,
                buyPriceGp,
                sellPriceGp,
                profitEachGp,
                Math.max(1, newQuantity),
                Math.max(maxQuantity, newQuantity),
                fillProbability,
                confidenceBand,
                reason,
                marketUrl,
                maxHoldTimeLabel,
                recommendationContext == null ? null : recommendationContext.copyWithSuggestedQuantity(Math.max(1, newQuantity))
            );
        }
    }
    private static final class AutoFlipNativeButtonHoleBounds
    {
        private final int slotIndex;
        private final int itemId;
        private final Rectangle bounds;

        private AutoFlipNativeButtonHoleBounds(int slotIndex, int itemId, Rectangle bounds)
        {
            this.slotIndex = slotIndex;
            this.itemId = itemId;
            this.bounds = bounds;
        }
    }

    // AUTOFLIP_STATE_DETECTOR_LABEL_V5_PLUGIN_METHOD



// AUTOFLIP_HISTORY_EXACT_WIDGET_TITLE_V2_METHOD
    private boolean autoFlipStateDetectorAnyWidgetTextContainsStrict(String needle)
    {
        try
        {
            if (client == null || needle == null || needle.trim().isEmpty())
            {
                return false;
            }

            String expected = needle.toLowerCase(java.util.Locale.ROOT).trim();

            for (int groupId = 0; groupId <= 700; groupId++)
            {
                Widget root = client.getWidget(groupId, 0);
                if (autoFlipStateDetectorWidgetTreeContainsStrict(root, expected, 0))
                {
                    return true;
                }
            }
        }
        catch (Exception ignored)
        {
        }

        return false;
    }

    private boolean autoFlipStateDetectorWidgetTreeContainsStrict(Widget widget, String expected, int depth)
    {
        if (widget == null || expected == null || depth > 10)
        {
            return false;
        }

        try
        {
            if (!widget.isHidden())
            {
                String text = widget.getText();
                if (text != null)
                {
                    String cleaned = text.replaceAll("<[^>]*>", " ")
                        .replace('\u00A0', ' ')
                        .replaceAll("\\s+", " ")
                        .trim()
                        .toLowerCase(java.util.Locale.ROOT);

                    if (cleaned.contains(expected))
                    {
                        return true;
                    }
                }
            }

            Widget[] children = widget.getChildren();
            if (children != null)
            {
                for (Widget child : children)
                {
                    if (autoFlipStateDetectorWidgetTreeContainsStrict(child, expected, depth + 1))
                    {
                        return true;
                    }
                }
            }

            Widget[] dynamicChildren = widget.getDynamicChildren();
            if (dynamicChildren != null)
            {
                for (Widget child : dynamicChildren)
                {
                    if (autoFlipStateDetectorWidgetTreeContainsStrict(child, expected, depth + 1))
                    {
                        return true;
                    }
                }
            }

            Widget[] staticChildren = widget.getStaticChildren();
            if (staticChildren != null)
            {
                for (Widget child : staticChildren)
                {
                    if (autoFlipStateDetectorWidgetTreeContainsStrict(child, expected, depth + 1))
                    {
                        return true;
                    }
                }
            }

            Widget[] nestedChildren = widget.getNestedChildren();
            if (nestedChildren != null)
            {
                for (Widget child : nestedChildren)
                {
                    if (autoFlipStateDetectorWidgetTreeContainsStrict(child, expected, depth + 1))
                    {
                        return true;
                    }
                }
            }
        }
        catch (Exception ignored)
        {
        }

        return false;
    }


    public String getAutoFlipStateDetectorLabelForOverlay()
    {
        // AUTOFLIP_STATE_DETECTOR_LABEL_V21_OFFICIAL_STATES_4A4B_HISTORY
        // Official visible-state detector. It must not inject input or advance workflow.
        try
        {
            if (client != null && !client.isClientThread())
            {
                return autoFlipStateDetectorLabelCache == null ? "" : autoFlipStateDetectorLabelCache;
            }

            String geTitle = getGeHeaderTextForOverlay();
            String geTitleLower = geTitle == null ? "" : geTitle.toLowerCase(java.util.Locale.ROOT);

            // AUTOFLIP_FINAL_EXACT_HISTORY_DETECT_V1
            // Use exact History title detection only. Do not use broad "history" widget text.
            boolean historyWindow = geTitleLower.contains("grand exchange") && geTitleLower.contains("history")
                || autoFlipStateDetectorAnyWidgetTextContainsStrict("grand exchange trade history");
            if (historyWindow)
            {
                return "state_h_ge_history_tab_open";
            }

            if (!isGeWindowOpenForOverlay())
            {
                return "state_0_ge_not_open";
            }

            boolean ordinarySellSetupOpen = isAutoFlipOrdinarySellSetupOpenForOverlay();
            if (ordinarySellSetupOpen)
            {
                return isAutoFlipOrdinarySellPricePromptOpenForOverlay()
                    ? "state_11_ordinary_sell_price_prompt_open"
                    : "state_10b_ordinary_sell_setup_open";
            }

            if (!isAutoFlipOverlayActive())
            {
                return "state_1_plugin_disabled";
            }

            boolean setupWindow = isAutoFlipGeSetupPromptActiveForOverlay();
            String activePromptText = getAutoFlipStateDetectorActiveNativePromptText();

            // State 4: item-search prompt. First search and previous-search are now one state.
            // This must beat recommendations and stale selected-item/detail widgets.
            boolean itemSearchPromptOpen = activePromptText.contains("what would you like to buy")
                || autoFlipStateDetectorVisibleTextContainsAnyStrict("what would you like to buy?");
            if (itemSearchPromptOpen)
            {
                boolean previousSearchVisible = autoFlipStateDetectorVisibleTextContainsAnyStrict("previous search:")
                    || autoFlipStateDetectorVisibleTextContainsAnyStrict("previous search");
                return previousSearchVisible
                    ? "state_4b_item_search_previous_search_visible"
                    : "state_4a_item_search_first_time";
            }

            if (activePromptText.contains("how many do you wish to buy")
                || activePromptText.contains("how many")
                || activePromptText.contains("quantity"))
            {
                AutoFlipBoardCard quantityPromptCard = getAutoFlipSetupTargetBoardCard();
                if (quantityPromptCard != null && "TO_BUY".equalsIgnoreCase(quantityPromptCard.getRiskLabel()))
                {
                    return "state_7b_to_buy_quantity_prompt_open";
                }
                return "state_7_quantity_prompt_open";
            }

            if (activePromptText.contains("set a price for each item")
                || activePromptText.contains("set a price for each"))
            {
                return "state_9_price_prompt_open";
            }

            AutoFlipBoardCard target = getAutoFlipSetupTargetBoardCard();
            if (setupWindow && target != null)
            {
                boolean anySelectedItemDetails = autoFlipStateDetectorVisibleTextContainsAnyStrict(
                    "actively traded price",
                    "buy limit",
                    "login to a members",
                    "members' server",
                    "members object"
                );
                boolean targetItemSelected = isAutoFlipGuidedSetupTargetItemSelected();

                if (anySelectedItemDetails && !targetItemSelected)
                {
                    return "state_5_wrong_item_selected";
                }

                if (targetItemSelected)
                {
                    boolean quantityCorrect = isAutoFlipStateDetectorQuantityFieldCorrect(target);
                    boolean priceCorrect = isAutoFlipStateDetectorPriceFieldCorrect(target);

                    if (quantityCorrect && priceCorrect)
                    {
                        return "state_10_correct_item_qty_price_ok";
                    }

                    if (quantityCorrect)
                    {
                        return "state_8_correct_item_qty_ok_price_wrong";
                    }

                    return "state_6_correct_item_qty_price_wrong";
                }
            }

            java.util.List<AutoFlipBoardCard> cards = getAutoFlipBoardCardsSnapshot();
            if (cards != null && !cards.isEmpty())
            {
                return "state_3_recommendations_populated";
            }

            return "state_2_plugin_enabled_empty_no_board";
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("getAutoFlipStateDetectorLabelForOverlay", error);
            return "unknown state";
        }
    }

    private boolean isAutoFlipOrdinarySellSetupOpenForOverlay()
    {
        try
        {
            String geTitle = getGeHeaderTextForOverlay();
            if (geTitle == null)
            {
                return false;
            }

            String geTitleLower = geTitle.toLowerCase(java.util.Locale.ROOT);
            if (!geTitleLower.startsWith("grand exchange: set up offer"))
            {
                return false;
            }

            boolean sellOfferVisible = autoFlipStateDetectorVisibleTextContainsAnyStrict("sell offer");
            boolean buyOfferVisible = autoFlipStateDetectorVisibleTextContainsAnyStrict("buy offer");
            if (!sellOfferVisible || buyOfferVisible)
            {
                return false;
            }

            if (autoFlipOrdinarySellSetupItemId > 0
                && autoFlipOrdinarySellSetupLastSeenMs > 0L
                && System.currentTimeMillis() - autoFlipOrdinarySellSetupLastSeenMs < 1500L)
            {
                return true;
            }

            String promptText = autoFlipOrdinarySellLastPromptText == null ? "" : autoFlipOrdinarySellLastPromptText;
            return promptText.contains("set a price")
                || promptText.contains("price for each item")
                || geTitleLower.contains("sell offer")
                || geTitleLower.contains("set up offer");
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("isAutoFlipOrdinarySellSetupOpenForOverlay", error);
            return false;
        }
    }

    private String getAutoFlipStateDetectorActiveNativePromptText()
    {
        try
        {
            if (client == null)
            {
                autoFlipStateDetectorActiveNativePromptTextCache = "";
                return "";
            }

            if (!client.isClientThread())
            {
                return autoFlipStateDetectorActiveNativePromptTextCache;
            }

            net.runelite.api.widgets.Widget promptWidget = client.getWidget(10616875);
            net.runelite.api.widgets.Widget inputWidget = client.getWidget(10616876);
            if (promptWidget == null || inputWidget == null)
            {
                autoFlipStateDetectorActiveNativePromptTextCache = "";
                return "";
            }

            if (promptWidget.isHidden() || inputWidget.isHidden())
            {
                autoFlipStateDetectorActiveNativePromptTextCache = "";
                return "";
            }

            Rectangle promptBounds = promptWidget.getBounds();
            Rectangle inputBounds = inputWidget.getBounds();
            if (promptBounds == null || inputBounds == null
                || promptBounds.width <= 0 || promptBounds.height <= 0
                || inputBounds.width <= 0 || inputBounds.height <= 0)
            {
                autoFlipStateDetectorActiveNativePromptTextCache = "";
                return "";
            }

            String promptText = cleanWidgetText(promptWidget.getText()).toLowerCase(java.util.Locale.ROOT);
            if (promptText == null)
            {
                autoFlipStateDetectorActiveNativePromptTextCache = "";
                return "";
            }

            autoFlipStateDetectorActiveNativePromptTextCache = promptText.trim();
            return autoFlipStateDetectorActiveNativePromptTextCache;
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("getAutoFlipStateDetectorActiveNativePromptText", error);
            autoFlipStateDetectorActiveNativePromptTextCache = "";
            return "";
        }
    }

    private boolean isAutoFlipStateDetectorQuantityFieldCorrect(AutoFlipBoardCard target)
    {
        try
        {
            if (target == null || target.getQuantity() <= 0)
            {
                return false;
            }

            return autoFlipStateDetectorSetupFieldContains(target.getQuantity(), false);
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("isAutoFlipStateDetectorQuantityFieldCorrect", error);
            return false;
        }
    }

    private boolean isAutoFlipStateDetectorPriceFieldCorrect(AutoFlipBoardCard target)
    {
        try
        {
            if (target == null)
            {
                return false;
            }

            long expectedPrice = getAutoFlipBoardCardExpectedSetupPrice(target);
            if (expectedPrice <= 0L)
            {
                return false;
            }

            return autoFlipStateDetectorSetupFieldContains(expectedPrice, true);
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("isAutoFlipStateDetectorPriceFieldCorrect", error);
            return false;
        }
    }

    private boolean autoFlipStateDetectorSetupFieldContains(long expectedValue, boolean price)
    {
        if (client == null || expectedValue <= 0L)
        {
            return false;
        }

        if (!client.isClientThread())
        {
            return false;
        }

        try
        {
            Rectangle header = getGeHeaderBoundsForOverlay();
            if (header == null)
            {
                return false;
            }

            String prefix = price ? "setup.price.value" : "setup.quantity.value";
            Rectangle field = new Rectangle(
                header.x + readAutoFlipSetupConfigInt(prefix + ".x", price ? 248 : 34),
                header.y + readAutoFlipSetupConfigInt(prefix + ".y", 150),
                readAutoFlipSetupConfigInt(prefix + ".w", price ? 210 : 188),
                readAutoFlipSetupConfigInt(prefix + ".h", 27)
            );

            net.runelite.api.widgets.Widget offerContainer = client.getWidget(
                net.runelite.api.widgets.ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER
            );
            if (offerContainer == null || offerContainer.isHidden())
            {
                return false;
            }

            if (autoFlipStateDetectorWidgetFieldContains(offerContainer, field, expectedValue, 0))
            {
                return true;
            }

            return price && autoFlipStateDetectorWidgetTreeContainsDigits(offerContainer, expectedValue, 0);
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("autoFlipStateDetectorSetupFieldContains", error);
        }

        return false;
    }

    private boolean autoFlipStateDetectorWidgetTreeContainsDigits(
        net.runelite.api.widgets.Widget widget,
        long expectedValue,
        int depth
    )
    {
        if (widget == null || expectedValue <= 0L || depth > 16)
        {
            return false;
        }

        try
        {
            if (!widget.isHidden())
            {
                String text = cleanWidgetText(widget.getText());
                if (text != null && !text.trim().isEmpty())
                {
                    String digits = text.replaceAll("[^0-9]", "");
                    if (!digits.isEmpty())
                    {
                        try
                        {
                            if (Long.parseLong(digits) == expectedValue)
                            {
                                return true;
                            }
                        }
                        catch (NumberFormatException ignored)
                        {
                        }
                    }
                }
            }

            net.runelite.api.widgets.Widget[] dynamicChildren = widget.getDynamicChildren();
            if (dynamicChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : dynamicChildren)
                {
                    if (autoFlipStateDetectorWidgetTreeContainsDigits(child, expectedValue, depth + 1))
                    {
                        return true;
                    }
                }
            }

            net.runelite.api.widgets.Widget[] staticChildren = widget.getStaticChildren();
            if (staticChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : staticChildren)
                {
                    if (autoFlipStateDetectorWidgetTreeContainsDigits(child, expectedValue, depth + 1))
                    {
                        return true;
                    }
                }
            }

            net.runelite.api.widgets.Widget[] nestedChildren = widget.getNestedChildren();
            if (nestedChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : nestedChildren)
                {
                    if (autoFlipStateDetectorWidgetTreeContainsDigits(child, expectedValue, depth + 1))
                    {
                        return true;
                    }
                }
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("autoFlipStateDetectorWidgetTreeContainsDigits", error);
        }

        return false;
    }

    private boolean autoFlipStateDetectorWidgetFieldContains(
        net.runelite.api.widgets.Widget widget,
        Rectangle field,
        long expectedValue,
        int depth
    )
    {
        if (widget == null || field == null || depth > 16)
        {
            return false;
        }

        try
        {
            if (!widget.isHidden())
            {
                Rectangle bounds = widget.getBounds();
                if (bounds != null && bounds.width > 0 && bounds.height > 0
                    && field.contains(bounds.getCenterX(), bounds.getCenterY()))
                {
                    String text = cleanWidgetText(widget.getText());
                    if (text != null && !text.trim().isEmpty())
                    {
                        String digits = text.replaceAll("[^0-9]", "");
                        if (!digits.isEmpty())
                        {
                            try
                            {
                                if (Long.parseLong(digits) == expectedValue)
                                {
                                    return true;
                                }
                            }
                            catch (NumberFormatException ignored)
                            {
                                // A malformed or oversized widget value is not a match.
                            }
                        }
                    }
                }
            }

            net.runelite.api.widgets.Widget[] dynamicChildren = widget.getDynamicChildren();
            if (dynamicChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : dynamicChildren)
                {
                    if (autoFlipStateDetectorWidgetFieldContains(child, field, expectedValue, depth + 1))
                    {
                        return true;
                    }
                }
            }

            net.runelite.api.widgets.Widget[] staticChildren = widget.getStaticChildren();
            if (staticChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : staticChildren)
                {
                    if (autoFlipStateDetectorWidgetFieldContains(child, field, expectedValue, depth + 1))
                    {
                        return true;
                    }
                }
            }

            net.runelite.api.widgets.Widget[] nestedChildren = widget.getNestedChildren();
            if (nestedChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : nestedChildren)
                {
                    if (autoFlipStateDetectorWidgetFieldContains(child, field, expectedValue, depth + 1))
                    {
                        return true;
                    }
                }
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("autoFlipStateDetectorWidgetFieldContains", error);
        }

        return false;
    }

    private boolean autoFlipStateDetectorVisibleTextContainsAnyStrict(String... needles)
    {
        try
        {
            if (client == null || needles == null || needles.length == 0)
            {
                return false;
            }

            if (!client.isClientThread())
            {
                return false;
            }

            net.runelite.api.widgets.Widget[] roots = client.getWidgetRoots();
            if (roots == null)
            {
                return false;
            }

            for (net.runelite.api.widgets.Widget root : roots)
            {
                if (autoFlipStateDetectorWidgetTextContainsAnyStrict(root, needles, 0))
                {
                    return true;
                }
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("autoFlipStateDetectorVisibleTextContainsAnyStrict", error);
        }

        return false;
    }

    private boolean autoFlipStateDetectorWidgetTextContainsAnyStrict(net.runelite.api.widgets.Widget widget, String[] needles, int depth)
    {
        if (widget == null || needles == null || depth > 16)
        {
            return false;
        }

        try
        {
            if (!widget.isHidden())
            {
                String text = cleanWidgetText(widget.getText()).toLowerCase(java.util.Locale.ROOT);
                if (!text.isEmpty())
                {
                    for (String needle : needles)
                    {
                        if (needle != null && !needle.trim().isEmpty() && text.contains(needle.toLowerCase(java.util.Locale.ROOT)))
                        {
                            return true;
                        }
                    }
                }
            }

            net.runelite.api.widgets.Widget[] dynamicChildren = widget.getDynamicChildren();
            if (dynamicChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : dynamicChildren)
                {
                    if (autoFlipStateDetectorWidgetTextContainsAnyStrict(child, needles, depth + 1))
                    {
                        return true;
                    }
                }
            }

            net.runelite.api.widgets.Widget[] staticChildren = widget.getStaticChildren();
            if (staticChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : staticChildren)
                {
                    if (autoFlipStateDetectorWidgetTextContainsAnyStrict(child, needles, depth + 1))
                    {
                        return true;
                    }
                }
            }

            net.runelite.api.widgets.Widget[] nestedChildren = widget.getNestedChildren();
            if (nestedChildren != null)
            {
                for (net.runelite.api.widgets.Widget child : nestedChildren)
                {
                    if (autoFlipStateDetectorWidgetTextContainsAnyStrict(child, needles, depth + 1))
                    {
                        return true;
                    }
                }
            }
        }
        catch (Throwable error)
        {
            logAutoFlipUiError("autoFlipStateDetectorWidgetTextContainsAnyStrict", error);
        }

        return false;
    }


    public static final class AutoFlipBoardCard
    {
        private final int slotIndex;
        private final int itemId;
        private final String itemName;
        private final int quantity;
        private final int maxQuantity;
        private final long buyPriceGp;
        private final long sellPriceGp;
        private final long profitEachGp;
        private final long totalProfitGp;
        private final String maxHoldTimeLabel;
        private final String riskLabel;
        private final String reason;
        private final String marketUrl;
        private final double confidence;
        private final AutoFlipRecommendationContext recommendationContext;

        private AutoFlipBoardCard(
            int slotIndex,
            int itemId,
            String itemName,
            int quantity,
            int maxQuantity,
            long buyPriceGp,
            long sellPriceGp,
            long profitEachGp,
            long totalProfitGp,
            String maxHoldTimeLabel,
            String riskLabel,
            String reason,
            String marketUrl,
            double confidence,
            AutoFlipRecommendationContext recommendationContext
        )
        {
            this(
                slotIndex,
                itemId,
                itemName,
                quantity,
                maxQuantity,
                buyPriceGp,
                sellPriceGp,
                profitEachGp,
                totalProfitGp,
                maxHoldTimeLabel,
                riskLabel,
                reason,
                marketUrl,
                confidence,
                recommendationContext,
                true
            );
        }

        private AutoFlipBoardCard(
            int slotIndex,
            int itemId,
            String itemName,
            int quantity,
            int maxQuantity,
            long buyPriceGp,
            long sellPriceGp,
            long profitEachGp,
            long totalProfitGp,
            String maxHoldTimeLabel,
            String riskLabel,
            String reason,
            String marketUrl,
            double confidence,
            AutoFlipRecommendationContext recommendationContext,
            boolean normalizeMaxQuantity)
        {
            this.slotIndex = slotIndex;
            this.itemId = itemId;
            this.itemName = itemName;
            this.quantity = quantity;
            this.maxQuantity = Math.max(1, normalizeMaxQuantity ? Math.max(quantity, maxQuantity) : maxQuantity);
            this.buyPriceGp = buyPriceGp;
            this.sellPriceGp = sellPriceGp;
            this.profitEachGp = profitEachGp;
            this.totalProfitGp = totalProfitGp;
            this.maxHoldTimeLabel = maxHoldTimeLabel;
            this.riskLabel = riskLabel;
            this.reason = reason;
            this.marketUrl = marketUrl;
            this.confidence = confidence;
            this.recommendationContext = recommendationContext;
        }

        public int getSlotIndex() { return slotIndex; }
        public int getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public int getQuantity() { return quantity; }
        public int getMaxQuantity() { return maxQuantity; }
        public long getBuyPriceGp() { return buyPriceGp; }
        public long getSellPriceGp() { return sellPriceGp; }
        public long getPlannedCapitalGp() { return Math.max(0L, buyPriceGp) * Math.max(0, quantity); }
        public long getProfitEachGp() { return profitEachGp; }
        public long getTotalProfitGp() { return totalProfitGp; }
        public String getMaxHoldTimeLabel() { return maxHoldTimeLabel; }
        public String getRiskLabel() { return riskLabel; }
        public String getReason() { return reason; }
        public String getMarketUrl() { return marketUrl; }
        public double getConfidence() { return confidence; }
        public AutoFlipRecommendationContext getRecommendationContext() { return recommendationContext; }
    }

}







