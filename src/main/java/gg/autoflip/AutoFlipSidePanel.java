package gg.autoflip;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSplitPane;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.Scrollable;
import javax.imageio.ImageIO;
import net.runelite.client.ui.PluginPanel;

public class AutoFlipSidePanel extends PluginPanel
{
    private static final class InventoryCardsPanel extends JPanel implements Scrollable
    {
        @Override
        public Dimension getPreferredScrollableViewportSize()
        {
            return new Dimension(INVENTORY_VIEW_WIDTH, Math.max(410, getPreferredSize().height));
        }

        @Override
        public Dimension getPreferredSize()
        {
            Dimension d = super.getPreferredSize();
            return new Dimension(INVENTORY_VIEW_WIDTH, Math.max(d.height, 1));
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
        {
            return 90;
        }

        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight()
        {
            return false;
        }
    }
    private static final Color BG = new Color(24, 24, 24);
    private static final Color CARD = new Color(34, 31, 27);
    private static final Color BORDER = new Color(82, 68, 48);
    private static final Color GOLD = new Color(230, 184, 72);
    private static final Color GREEN = new Color(92, 222, 72);
    private static final Color MUTED = new Color(165, 165, 158);
    private static final Color TEXT = new Color(235, 232, 220);
    private static final Color PURPLE = new Color(104, 64, 220);
    private static final Color RED = new Color(170, 45, 45);
    private static final String TO_BUY_POPUP_BACKGROUND_RESOURCE = "/gg/autoflip/Main.png";
    private static final int INVENTORY_VIEW_WIDTH = PluginPanel.PANEL_WIDTH - 8;
    private static final int TO_BUY_SEARCH_WINDOW_WIDTH = 1440;

    private final AutoFlipPlugin plugin;
    private final JPanel cardsPanel = new InventoryCardsPanel();
    private final JPanel inventoryTabCardsPanel = new InventoryCardsPanel();
    private JTabbedPane mainTabs;
    private final JLabel inventoryTitle = new JLabel("AUTOFLIP INVENTORY");
    // AUTOFLIP_PATCH_INVENTORY_EYE_GLOW_V2
    private JButton autoFlipInventoryBankViewButton;
    // AUTOFLIP_PATCH_BANK_OPEN_VISUAL_DIAG_V1
    private long autoFlipLastBankButtonVisualLogMs = 0L;

    private final JLabel heldCount = new JLabel("0 held");
    private final JLabel nextAction = new JLabel("Shift + right-click an item");
    private final JLabel apiStatus = new JLabel("Local hold list");
    private final JComboBox<String> sortBox = new JComboBox<>(new String[] {"Date Added", "Held Capital"});
    private final JPanel toBuyCardsPanel = new InventoryCardsPanel();
    private final JLabel toBuyTitle = new JLabel("AUTOFLIP TO-BUY");
    private final JLabel toBuyCount = new JLabel("0 watched");
    private final JLabel toBuyStatus = new JLabel("Market search ready");
    private final JLabel toBuyNextAction = new JLabel("Search any GE item");
    private final JTextField toBuySearchField = new JTextField();
    private JDialog toBuySearchWindowDialog;
    private final JTextField toBuySearchWindowField = new JTextField();
    private final JPanel toBuySearchWindowResultsPanel = new JPanel();
    private final JPanel toBuySearchWindowWatchlistPanel = new JPanel();
    private final JLabel toBuySearchWindowStatus = new JLabel("Search any GE item");
    private final JPanel currentOffersCardsPanel = new InventoryCardsPanel();
    private final JLabel currentOffersStatus = new JLabel("Live GE snapshot");
    private final DecimalFormat gpFormat = new DecimalFormat("#,###");
    private boolean smartSellMode = false;
    private int smartSellSelectedItemId = -1;
    private int toBuySelectedItemId = -1;
    private int toBuySearchWindowEditorItemId = -1;
    private int currentOffersSelectedSlot = -1;
    private final java.util.Map<Integer, String> smartSellInstructions = new java.util.HashMap<>();
    private static final java.nio.file.Path SMART_SELL_SETTINGS_PATH = java.nio.file.Paths.get(
        System.getProperty("user.home", "."),
        ".runelite",
        "plugin-data",
        "autoflip",
        "smart_sell_settings.properties"
    );
    private boolean smartSellSettingsLoaded = false;
    private java.util.List<AutoFlipPlugin.AutoFlipMarketSearchResult> toBuySearchResults = java.util.Collections.emptyList();
    private java.util.List<AutoFlipPlugin.AutoFlipMarketSearchResult> toBuySearchWindowResults = java.util.Collections.emptyList();
    private int toBuySearchWindowSelectedIndex = -1;
    private int toBuySearchWindowSelectedItemId = -1;
    private javax.swing.Timer toBuySearchWindowDebounceTimer;
    private final java.util.Map<Integer, String> toBuyPriceDrafts = new java.util.HashMap<>();
    private final java.util.Map<Integer, String> toBuyQuantityDrafts = new java.util.HashMap<>();
    private final java.util.Map<Integer, String> toBuyHoursDrafts = new java.util.HashMap<>();

    private static final class PopupBackgroundPanel extends JPanel
    {
        private final BufferedImage backgroundImage;

        private PopupBackgroundPanel(BufferedImage backgroundImage)
        {
            this.backgroundImage = backgroundImage;
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            if (backgroundImage == null)
            {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            try
            {
                g2.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), null);
                // Keep the artwork atmospheric while guaranteeing text contrast.
                g2.setColor(new Color(0, 0, 0, 190));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            finally
            {
                g2.dispose();
            }
        }
    }

    private static final class PopupSurfacePanel extends JPanel
    {
        private final Color surfaceColor;
        private final int cornerRadius;

        private PopupSurfacePanel(Color surfaceColor, int cornerRadius)
        {
            this.surfaceColor = surfaceColor;
            this.cornerRadius = Math.max(0, cornerRadius);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g.create();
            try
            {
                g2.setRenderingHint(
                    java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON
                );
                g2.setColor(surfaceColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius);
            }
            finally
            {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    public AutoFlipSidePanel(AutoFlipPlugin plugin)
    {
        super(false);
        this.plugin = plugin;

        setLayout(new BorderLayout());
        setBackground(BG);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(BG);

        body.add(buildHeader());
        body.add(space(8));
        body.add(buildAccount());
        body.add(space(8));
        body.add(buildCurrentOffersPanel());
        body.add(space(8));
        body.add(Box.createVerticalGlue());
        body.add(buildActions());
        body.add(space(8));
        body.add(buildFooter());

        JPanel inventoryOnly = buildInventoryOnlyTab();
        JPanel toBuyOnly = buildToBuyOnlyTab();

        mainTabs = new JTabbedPane();
        mainTabs.setBackground(BG);
        mainTabs.setForeground(TEXT);
        mainTabs.addTab("Home", body);
        mainTabs.addTab("Inventory", inventoryOnly);
        mainTabs.addTab("To-Buy", toBuyOnly);

        add(mainTabs, BorderLayout.CENTER);
        javax.swing.Timer currentOffersTimer = new javax.swing.Timer(1200, e -> refreshCurrentOffersPanel());
        currentOffersTimer.setRepeats(true);
        currentOffersTimer.start();
        refreshFromPlugin();
        refreshSoon();
    }

    private JPanel buildInventoryOnlyTab()
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel header = rowPanel();
        header.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 26));
        header.setMinimumSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 26));
        header.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 26));

        JLabel title = sectionLabel("Inventory");

        JButton smartSell = button("Smart Sell");
        smartSell.setPreferredSize(new Dimension(96, 24));
        smartSell.setMaximumSize(new Dimension(96, 24));
        smartSell.setToolTipText("Choose how AutoFlip should sell one held item");
        smartSell.addActionListener(e -> {
            smartSellMode = true;
            smartSellSelectedItemId = -1;
            refreshFromPlugin();
        });

        JButton sellCache = button("Sell Cache");
        sellCache.setPreferredSize(new Dimension(96, 24));
        sellCache.setMaximumSize(new Dimension(96, 24));
        sellCache.setToolTipText("Inspect the live sell-price cache");
        sellCache.addActionListener(e -> openSellPriceCacheDialog());

        header.add(title);
        header.add(Box.createHorizontalGlue());
        header.add(smartSell);
        header.add(space(5));
        header.add(sellCache);

        inventoryTabCardsPanel.setLayout(new BoxLayout(inventoryTabCardsPanel, BoxLayout.Y_AXIS));
        inventoryTabCardsPanel.setBackground(BG);
        inventoryTabCardsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JScrollPane inventoryScroll = new JScrollPane(inventoryTabCardsPanel);
        inventoryScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        inventoryScroll.getViewport().setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH, 665));
        inventoryScroll.getViewport().setMinimumSize(new Dimension(INVENTORY_VIEW_WIDTH, 160));
        inventoryScroll.setBorder(BorderFactory.createEmptyBorder());
        inventoryScroll.setBackground(BG);
        inventoryScroll.getViewport().setBackground(BG);
        inventoryScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        inventoryScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        inventoryScroll.setWheelScrollingEnabled(true);
        inventoryScroll.getVerticalScrollBar().setUnitIncrement(18);
        inventoryScroll.getVerticalScrollBar().setBlockIncrement(90);
        inventoryScroll.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 665));
        inventoryScroll.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 665));
        inventoryScroll.setMinimumSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 160));

        p.add(header);
        p.add(space(0));
        p.add(inventoryScroll);
        p.add(Box.createVerticalGlue());
        return p;
    }

    private JPanel buildToBuyOnlyTab()
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG);
        p.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel header = rowPanel();
        header.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 26));
        header.setMinimumSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 26));
        header.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 26));

        JLabel title = sectionLabel("To-Buy");
        toBuyTitle.setText("AUTOFLIP TO-BUY");
        toBuyCount.setForeground(GREEN);
        toBuyCount.setFont(toBuyCount.getFont().deriveFont(Font.BOLD, 11f));
        toBuyStatus.setForeground(MUTED);
        toBuyStatus.setFont(toBuyStatus.getFont().deriveFont(Font.BOLD, 11f));

        JButton openSearch = button("Add & Edit");
        openSearch.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 32));
        openSearch.setMinimumSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 32));
        openSearch.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 32));
        openSearch.setToolTipText("Open the draggable GE search window and edit To-Buy items");
        openSearch.addActionListener(e -> openToBuySearchWindow());

        header.add(title);
        header.add(Box.createHorizontalGlue());
        header.add(toBuyCount);
        header.add(space(8));
        header.add(toBuyStatus);
        header.add(space(5));

        JPanel searchLauncher = cardPanel();
        searchLauncher.setLayout(new BoxLayout(searchLauncher, BoxLayout.Y_AXIS));
        searchLauncher.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 60));
        searchLauncher.setMinimumSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 60));
        searchLauncher.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 60));

        JLabel searchHint = new JLabel("Open the standalone GE picker");
        searchHint.setForeground(MUTED);
        searchHint.setFont(searchHint.getFont().deriveFont(Font.BOLD, 11f));
        searchHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        openSearch.setAlignmentX(Component.LEFT_ALIGNMENT);

        searchLauncher.add(searchHint);
        searchLauncher.add(space(6));
        searchLauncher.add(openSearch);

        toBuyCardsPanel.setLayout(new BoxLayout(toBuyCardsPanel, BoxLayout.Y_AXIS));
        toBuyCardsPanel.setBackground(BG);
        toBuyCardsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JScrollPane scroll = new JScrollPane(toBuyCardsPanel);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.getViewport().setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH, 665));
        scroll.getViewport().setMinimumSize(new Dimension(INVENTORY_VIEW_WIDTH, 160));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setWheelScrollingEnabled(true);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getVerticalScrollBar().setBlockIncrement(90);
        scroll.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 665));
        scroll.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 665));

        p.add(header);
        p.add(space(6));
        p.add(searchLauncher);
        p.add(space(6));
        p.add(scroll);
        p.add(space(8));
        p.add(buildToBuyHelp());
        p.add(space(8));
        p.add(buildToBuyNextAction());
        p.add(Box.createVerticalGlue());
        p.add(buildFooter());
        return p;
    }

    private JPanel buildHeader()
    {
        JPanel p = rowPanel();
        JLabel title = new JLabel("AutoFlip.gg");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));

        JLabel small = new JLabel("Menu");
        small.setForeground(MUTED);
        small.setFont(small.getFont().deriveFont(Font.BOLD, 11f));

        p.add(title);
        p.add(Box.createHorizontalGlue());
        p.add(small);
        return p;
    }

    private JPanel buildAccount()
    {
        JPanel p = cardPanel();
        p.setLayout(new BorderLayout(8, 0));

        JLabel account = new JLabel("<html><b>Not signed in</b><br><span style='color:#aaa'>Sync held items later</span></html>");
        account.setForeground(TEXT);

        JButton signIn = button("Sign In");
        signIn.setEnabled(false);
        signIn.setToolTipText("Account login will be wired later.");

        p.add(account, BorderLayout.CENTER);
        p.add(signIn, BorderLayout.EAST);
        return p;
    }

    private JPanel buildActions()
    {
        JPanel p = cardPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JButton optimize = button("Optimize Board");
        JButton refresh = button("Refresh Board");
        JButton pull = button("Pull Cache");
        JButton debug = button("Debug");

        optimize.addActionListener(e -> {
            apiStatus.setText("API: Optimizing...");
            plugin.openAutoFlipSidePanelOptimizeBoard();
            refreshSoon();
        });

        refresh.addActionListener(e -> {
            apiStatus.setText("API: Refreshing...");
            plugin.openAutoFlipSidePanelRefreshBoard();
            refreshSoon();
        });

        pull.addActionListener(e -> {
            apiStatus.setText("API: Pulling cache...");
            plugin.maybeRefreshAutoFlipBoardCache();
            refreshSoon();
        });

        debug.addActionListener(e -> openTelemetryDebugDialog());

        p.add(optimize);
        p.add(space(5));
        p.add(refresh);
        p.add(space(5));
        p.add(pull);
        p.add(space(5));
        p.add(debug);
        return p;
    }

    private JPanel buildCurrentOffersPanel()
    {
        JPanel p = cardPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JPanel header = rowPanel();
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = sectionLabel("CURRENT OFFERS");
        currentOffersStatus.setForeground(MUTED);
        currentOffersStatus.setFont(currentOffersStatus.getFont().deriveFont(Font.BOLD, 11f));

        header.add(title);
        header.add(Box.createHorizontalGlue());
        header.add(currentOffersStatus);

        currentOffersCardsPanel.setLayout(new BoxLayout(currentOffersCardsPanel, BoxLayout.Y_AXIS));
        currentOffersCardsPanel.setBackground(CARD);
        currentOffersCardsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        currentOffersCardsPanel.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 480));
        currentOffersCardsPanel.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 480));
        currentOffersCardsPanel.setMinimumSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 360));

        p.add(header);
        p.add(space(6));
        p.add(currentOffersCardsPanel);
        refreshCurrentOffersPanel();
        return p;
    }

    private void openTelemetryDebugDialog()
    {
        Window parent = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(parent, "AutoFlip Telemetry Debug", Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());
        dialog.setMinimumSize(new Dimension(760, 620));
        dialog.setPreferredSize(new Dimension(760, 620));
        dialog.getContentPane().setBackground(BG);
        JPanel content = buildTelemetryDebugContent();
        dialog.add(content, BorderLayout.CENTER);
        Object timerObject = content.getClientProperty("autoflip.telemetry.refreshTimer");
        if (timerObject instanceof javax.swing.Timer)
        {
            javax.swing.Timer refreshTimer = (javax.swing.Timer) timerObject;
            dialog.addWindowListener(new java.awt.event.WindowAdapter()
            {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e)
                {
                    refreshTimer.stop();
                }

                @Override
                public void windowClosing(java.awt.event.WindowEvent e)
                {
                    refreshTimer.stop();
                }
            });
        }
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private void openSellPriceCacheDialog()
    {
        Window parent = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(parent, "AutoFlip Sell Price Cache", Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());
        dialog.setMinimumSize(new Dimension(760, 620));
        dialog.setPreferredSize(new Dimension(760, 620));
        dialog.getContentPane().setBackground(BG);
        JPanel content = buildSellPriceCacheContent();
        dialog.add(content, BorderLayout.CENTER);
        Object timerObject = content.getClientProperty("autoflip.sellCache.refreshTimer");
        if (timerObject instanceof javax.swing.Timer)
        {
            javax.swing.Timer refreshTimer = (javax.swing.Timer) timerObject;
            dialog.addWindowListener(new java.awt.event.WindowAdapter()
            {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e)
                {
                    refreshTimer.stop();
                }

                @Override
                public void windowClosing(java.awt.event.WindowEvent e)
                {
                    refreshTimer.stop();
                }
            });
        }
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private JPanel buildTelemetryDebugContent()
    {
        JPanel root = new JPanel(new BorderLayout(10, 0));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JTextArea jsonView = new JTextArea();
        jsonView.setEditable(false);
        jsonView.setLineWrap(false);
        jsonView.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        jsonView.setBackground(new Color(10, 10, 10));
        jsonView.setForeground(TEXT);
        if (jsonView.getCaret() instanceof javax.swing.text.DefaultCaret)
        {
            ((javax.swing.text.DefaultCaret) jsonView.getCaret()).setUpdatePolicy(
                javax.swing.text.DefaultCaret.NEVER_UPDATE
            );
        }
        jsonView.setCaretPosition(0);

        JLabel status = new JLabel("Ready");
        status.setForeground(MUTED);
        JLabel pendingCount = new JLabel("Pending events: 0");
        pendingCount.setForeground(MUTED);
        JLabel currentCount = new JLabel("Current batch events: 0");
        currentCount.setForeground(MUTED);
        JLabel accountMode = new JLabel("Account: unknown");
        accountMode.setForeground(MUTED);
        JLabel nextSendLabel = new JLabel("Next send: unknown");
        nextSendLabel.setForeground(MUTED);

        JButton copyButton = button("Copy");
        copyButton.setHorizontalAlignment(SwingConstants.CENTER);

        final String[] selectedRawJson = new String[] { "" };
        final String[] selectedLabel = new String[] { "Current Batch" };
        final String[] selectedSource = new String[] { "current" };

        copyButton.addActionListener(e -> {
            String text = selectedRawJson[0] == null ? "" : selectedRawJson[0];
            if (!text.isEmpty())
            {
                try
                {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                    status.setText("Copied " + selectedLabel[0]);
                }
                catch (Throwable error)
                {
                    status.setText("Copy failed");
                }
            }
        });

        JPanel sidebar = buildTelemetryDebugSidebar(
            jsonView,
            status,
            pendingCount,
            currentCount,
            accountMode,
            nextSendLabel,
            selectedRawJson,
            selectedLabel,
            selectedSource
        );
        JPanel viewer = buildTelemetryDebugViewer(jsonView, status, nextSendLabel, copyButton);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, viewer);
        splitPane.setResizeWeight(0.33);
        splitPane.setDividerSize(8);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        root.add(splitPane, BorderLayout.CENTER);

        refreshTelemetryDebugSelection(
            jsonView,
            status,
            pendingCount,
            currentCount,
            accountMode,
            nextSendLabel,
            selectedRawJson,
            selectedLabel,
            selectedSource
        );

        javax.swing.Timer refreshTimer = new javax.swing.Timer(1000, e -> {
            String source = selectedSource[0] == null ? "" : selectedSource[0];
            if ("current".equals(source) || "pending".equals(source) || "sell_cache".equals(source)
                || "buy_cache".equals(source) || "inventory_cache".equals(source)
                || "members_current_list".equals(source) || "f2p_current_list".equals(source))
            {
                refreshTelemetryDebugSelection(
                    jsonView,
                    status,
                    pendingCount,
                    currentCount,
                    accountMode,
                    nextSendLabel,
                    selectedRawJson,
                    selectedLabel,
                    selectedSource
                );
            }
        });
        refreshTimer.start();
        root.putClientProperty("autoflip.telemetry.refreshTimer", refreshTimer);

        return root;
    }

    private JPanel buildSellPriceCacheContent()
    {
        JPanel root = new JPanel(new BorderLayout(10, 0));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTextArea jsonView = new JTextArea();
        jsonView.setEditable(false);
        jsonView.setLineWrap(false);
        jsonView.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        jsonView.setBackground(new Color(10, 10, 10));
        jsonView.setForeground(TEXT);
        if (jsonView.getCaret() instanceof javax.swing.text.DefaultCaret)
        {
            ((javax.swing.text.DefaultCaret) jsonView.getCaret()).setUpdatePolicy(
                javax.swing.text.DefaultCaret.NEVER_UPDATE
            );
        }
        jsonView.setCaretPosition(0);

        JLabel status = new JLabel("Ready");
        status.setForeground(MUTED);

        JButton copyButton = button("Copy");
        copyButton.setHorizontalAlignment(SwingConstants.CENTER);
        copyButton.addActionListener(e -> {
            String text = jsonView.getText() == null ? "" : jsonView.getText();
            if (!text.isEmpty())
            {
                try
                {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                    status.setText("Copied sell cache");
                }
                catch (Throwable error)
                {
                    status.setText("Copy failed");
                }
            }
        });

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JPanel topRow = new JPanel(new BorderLayout(8, 0));
        topRow.setOpaque(false);
        JLabel title = new JLabel("JSON VIEWER");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        topRow.add(title, BorderLayout.WEST);
        topRow.add(copyButton, BorderLayout.EAST);
        top.add(topRow);
        top.add(space(3));
        top.add(new JLabel("Live sell price cache"));
        root.add(top, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(jsonView);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        root.add(scrollPane, BorderLayout.CENTER);

        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        footer.add(status);
        root.add(footer, BorderLayout.SOUTH);

        Runnable refresh = () -> {
            String raw = plugin == null ? "" : plugin.getAutoFlipLiveSellPriceCachePseudoJson();
            setLiveViewerTextPreservingScroll(jsonView, raw);
            status.setText("Sell cache loaded");
        };
        refresh.run();

        javax.swing.Timer refreshTimer = new javax.swing.Timer(1000, e -> refresh.run());
        refreshTimer.start();
        root.putClientProperty("autoflip.sellCache.refreshTimer", refreshTimer);

        return root;
    }

    private JPanel buildTelemetryDebugSidebar(
        JTextArea jsonView,
        JLabel status,
        JLabel pendingCount,
        JLabel currentCount,
        JLabel accountMode,
        JLabel nextSendLabel,
        String[] selectedRawJson,
        String[] selectedLabel,
        String[] selectedSource
    )
    {
        JPanel sidebar = cardPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(255, 1));

        JLabel currentTitle = new JLabel("CURRENT BATCH");
        currentTitle.setFont(currentTitle.getFont().deriveFont(Font.BOLD, 16f));
        currentTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(currentTitle);
        sidebar.add(space(6));

        JButton currentButton = button("Show current JSON");
        currentButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        currentButton.setHorizontalAlignment(SwingConstants.LEFT);
        currentButton.addActionListener(e -> refreshTelemetryDebugCurrentSelection(
            jsonView,
            status,
            pendingCount,
            currentCount,
            accountMode,
            nextSendLabel,
            selectedRawJson,
            selectedLabel,
            selectedSource
        ));
        sidebar.add(currentButton);
        sidebar.add(space(6));
        currentCount.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(currentCount);

        sidebar.add(space(10));

        JLabel sellCacheTitle = new JLabel("SELL CACHE");
        sellCacheTitle.setFont(sellCacheTitle.getFont().deriveFont(Font.BOLD, 16f));
        sellCacheTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(sellCacheTitle);
        sidebar.add(space(6));

        JButton sellCacheButton = button("Show current Sell cache");
        sellCacheButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        sellCacheButton.setHorizontalAlignment(SwingConstants.LEFT);
        sellCacheButton.addActionListener(e -> refreshTelemetryDebugSellCacheSelection(
            jsonView,
            status,
            nextSendLabel,
            selectedRawJson,
            selectedLabel,
            selectedSource
        ));
        sidebar.add(sellCacheButton);
        sidebar.add(space(10));

        JLabel buyCacheTitle = new JLabel("BUY CACHE");
        buyCacheTitle.setFont(buyCacheTitle.getFont().deriveFont(Font.BOLD, 16f));
        buyCacheTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(buyCacheTitle);
        sidebar.add(space(6));

        JButton buyCacheButton = button("Show current Buy cache");
        buyCacheButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        buyCacheButton.setHorizontalAlignment(SwingConstants.LEFT);
        buyCacheButton.addActionListener(e -> refreshTelemetryDebugBuyCacheSelection(
            jsonView, status, nextSendLabel, selectedRawJson, selectedLabel, selectedSource
        ));
        sidebar.add(buyCacheButton);
        sidebar.add(space(10));

        JLabel inventoryCacheTitle = new JLabel("INVENTORY CACHE");
        inventoryCacheTitle.setFont(inventoryCacheTitle.getFont().deriveFont(Font.BOLD, 16f));
        inventoryCacheTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(inventoryCacheTitle);
        sidebar.add(space(6));

        JButton inventoryCacheButton = button("Show current Inventory cache");
        inventoryCacheButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        inventoryCacheButton.setHorizontalAlignment(SwingConstants.LEFT);
        inventoryCacheButton.addActionListener(e -> refreshTelemetryDebugInventoryCacheSelection(
            jsonView, status, nextSendLabel, selectedRawJson, selectedLabel, selectedSource
        ));
        sidebar.add(inventoryCacheButton);
        sidebar.add(space(10));

        JLabel currentListsTitle = new JLabel("CURRENT LISTS");
        currentListsTitle.setFont(currentListsTitle.getFont().deriveFont(Font.BOLD, 16f));
        currentListsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(currentListsTitle);
        sidebar.add(space(6));

        JButton membersListButton = button("Show current Members list");
        membersListButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        membersListButton.setHorizontalAlignment(SwingConstants.LEFT);
        membersListButton.addActionListener(e -> refreshTelemetryDebugMembersCurrentListSelection(
            jsonView,
            status,
            nextSendLabel,
            selectedRawJson,
            selectedLabel,
            selectedSource
        ));
        sidebar.add(membersListButton);
        sidebar.add(space(5));

        JButton f2pListButton = button("Show current F2P list");
        f2pListButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        f2pListButton.setHorizontalAlignment(SwingConstants.LEFT);
        f2pListButton.addActionListener(e -> refreshTelemetryDebugF2pCurrentListSelection(
            jsonView,
            status,
            nextSendLabel,
            selectedRawJson,
            selectedLabel,
            selectedSource
        ));
        sidebar.add(f2pListButton);
        sidebar.add(space(10));

        JLabel pendingTitle = new JLabel("PENDING");
        pendingTitle.setFont(pendingTitle.getFont().deriveFont(Font.BOLD, 16f));
        pendingTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(pendingTitle);
        sidebar.add(space(6));

        JButton pendingButton = button("Show pending JSON");
        pendingButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        pendingButton.setHorizontalAlignment(SwingConstants.LEFT);
        pendingButton.addActionListener(e -> refreshTelemetryDebugPendingSelection(
            jsonView,
            status,
            pendingCount,
            currentCount,
            accountMode,
            nextSendLabel,
            selectedRawJson,
            selectedLabel,
            selectedSource
        ));
        sidebar.add(pendingButton);
        sidebar.add(space(6));
        pendingCount.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(pendingCount);

        sidebar.add(space(10));

        JLabel historyTitle = new JLabel("SENT HISTORY");
        historyTitle.setFont(historyTitle.getFont().deriveFont(Font.BOLD, 16f));
        historyTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(historyTitle);
        sidebar.add(space(6));

        JPanel list = new JPanel();
        list.setBackground(CARD);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setAlignmentX(Component.LEFT_ALIGNMENT);

        List<AutoFlipTelemetryArchiveEntry> entries = plugin == null
            ? java.util.Collections.emptyList()
            : plugin.getAutoFlipTelemetrySentHistoryEntries(25);
        if (entries.isEmpty())
        {
            JLabel empty = new JLabel("No sent batches yet.");
            empty.setForeground(MUTED);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            list.add(empty);
        }
        else
        {
            for (AutoFlipTelemetryArchiveEntry entry : entries)
            {
                JButton item = button(entry.getDisplayLabel());
                item.setAlignmentX(Component.LEFT_ALIGNMENT);
                item.setHorizontalAlignment(SwingConstants.LEFT);
                item.addActionListener(e -> selectTelemetryJson(
                    jsonView,
                    status,
                    selectedRawJson,
                    selectedLabel,
                    selectedSource,
                    entry.getDisplayLabel(),
                    entry.getPayloadJson(),
                    entry.getEventCount(),
                    "history"
                ));
                list.add(item);
                list.add(space(4));
            }
        }

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(scrollPane);

        return sidebar;
    }

    private JPanel buildTelemetryDebugViewer(JTextArea jsonView, JLabel status, JLabel nextSendLabel, JButton copyButton)
    {
        JPanel viewer = cardPanel();
        viewer.setLayout(new BorderLayout(0, 8));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JPanel topRow = new JPanel(new BorderLayout(8, 0));
        topRow.setOpaque(false);
        JLabel title = new JLabel("JSON VIEWER");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        topRow.add(title, BorderLayout.WEST);
        topRow.add(copyButton, BorderLayout.EAST);
        top.add(topRow);

        nextSendLabel.setForeground(MUTED);
        nextSendLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(space(3));
        top.add(nextSendLabel);
        viewer.add(top, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(jsonView);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        viewer.add(scrollPane, BorderLayout.CENTER);

        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        status.setForeground(MUTED);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        footer.add(status);
        viewer.add(footer, BorderLayout.SOUTH);
        return viewer;
    }

    private void selectTelemetryJson(
        JTextArea jsonView,
        JLabel status,
        String[] selectedRawJson,
        String[] selectedLabel,
        String[] selectedSource,
        String label,
        String rawJson,
        int eventCount,
        String source
    )
    {
        String pretty = formatTelemetryPseudoCode(rawJson);
        jsonView.setText(pretty);
        jsonView.setCaretPosition(0);
        selectedRawJson[0] = rawJson == null ? "" : rawJson;
        selectedLabel[0] = label == null ? "Selected JSON" : label;
        selectedSource[0] = source == null ? "history" : source;
        status.setText(selectedLabel[0] + " loaded" + (eventCount >= 0 ? " (" + eventCount + " events)" : ""));
    }

    private void refreshTelemetryDebugSelection(
        JTextArea jsonView,
        JLabel status,
        JLabel pendingCount,
        JLabel currentCount,
        JLabel accountMode,
        JLabel nextSendLabel,
        String[] selectedRawJson,
        String[] selectedLabel,
        String[] selectedSource
    )
    {
        String source = selectedSource[0] == null ? "current" : selectedSource[0];
        if ("pending".equals(source))
        {
            refreshTelemetryDebugPendingSelection(
                jsonView,
                status,
                pendingCount,
                currentCount,
                accountMode,
                nextSendLabel,
                selectedRawJson,
                selectedLabel,
                selectedSource
            );
            return;
        }

        if ("current".equals(source))
        {
            refreshTelemetryDebugCurrentSelection(
                jsonView,
                status,
                pendingCount,
                currentCount,
                accountMode,
                nextSendLabel,
                selectedRawJson,
                selectedLabel,
                selectedSource
            );
            return;
        }

        if ("sell_cache".equals(source))
        {
            refreshTelemetryDebugSellCacheSelection(
                jsonView,
                status,
                nextSendLabel,
                selectedRawJson,
                selectedLabel,
                selectedSource
            );
            return;
        }

        if ("buy_cache".equals(source))
        {
            refreshTelemetryDebugBuyCacheSelection(jsonView, status, nextSendLabel,
                selectedRawJson, selectedLabel, selectedSource);
            return;
        }

        if ("inventory_cache".equals(source))
        {
            refreshTelemetryDebugInventoryCacheSelection(jsonView, status, nextSendLabel,
                selectedRawJson, selectedLabel, selectedSource);
            return;
        }

        if ("members_current_list".equals(source))
        {
            refreshTelemetryDebugMembersCurrentListSelection(
                jsonView,
                status,
                nextSendLabel,
                selectedRawJson,
                selectedLabel,
                selectedSource
            );
            return;
        }

        if ("f2p_current_list".equals(source))
        {
            refreshTelemetryDebugF2pCurrentListSelection(
                jsonView,
                status,
                nextSendLabel,
                selectedRawJson,
                selectedLabel,
                selectedSource
            );
        }
    }

    private void refreshTelemetryDebugInventoryCacheSelection(
        JTextArea jsonView, JLabel status, JLabel nextSendLabel,
        String[] selectedRawJson, String[] selectedLabel, String[] selectedSource)
    {
        String readable = plugin == null ? "" : plugin.getAutoFlipGeSessionInventoryCachePseudoJson();
        String fullDiagnostic = plugin == null ? "" : plugin.getAutoFlipGeSessionInventoryCacheFullDiagnosticLog();
        setLiveViewerTextPreservingScroll(jsonView, readable);
        selectedRawJson[0] = fullDiagnostic == null ? "" : fullDiagnostic;
        selectedLabel[0] = "Current Inventory Cache Full Diagnostic";
        selectedSource[0] = "inventory_cache";
        status.setText("Unique inventory IDs loaded; Copy exports lifecycle diagnostic");
        if (nextSendLabel != null)
        {
            nextSendLabel.setText("Live GE-session inventory-cache diagnostic");
        }
    }

    private void refreshTelemetryDebugMembersCurrentListSelection(
        JTextArea jsonView,
        JLabel status,
        JLabel nextSendLabel,
        String[] selectedRawJson,
        String[] selectedLabel,
        String[] selectedSource)
    {
        String readable = plugin == null ? "" : plugin.getAutoFlipMembersCurrentListPseudoJson();
        String fullDiagnostic = plugin == null ? "" : plugin.getAutoFlipMembersCurrentListFullDiagnosticLog();
        setLiveViewerTextPreservingScroll(jsonView, readable);
        selectedRawJson[0] = fullDiagnostic == null ? "" : fullDiagnostic;
        selectedLabel[0] = "Current Members List Full Diagnostic";
        selectedSource[0] = "members_current_list";
        status.setText("Readable members list loaded; Copy exports full diagnostic");
        if (nextSendLabel != null)
        {
            nextSendLabel.setText("Live members-list diagnostic");
        }
    }

    private void refreshTelemetryDebugF2pCurrentListSelection(
        JTextArea jsonView,
        JLabel status,
        JLabel nextSendLabel,
        String[] selectedRawJson,
        String[] selectedLabel,
        String[] selectedSource)
    {
        String readable = plugin == null ? "" : plugin.getAutoFlipF2pCurrentListPseudoJson();
        String fullDiagnostic = plugin == null ? "" : plugin.getAutoFlipF2pCurrentListFullDiagnosticLog();
        setLiveViewerTextPreservingScroll(jsonView, readable);
        selectedRawJson[0] = fullDiagnostic == null ? "" : fullDiagnostic;
        selectedLabel[0] = "Current F2P List Full Diagnostic";
        selectedSource[0] = "f2p_current_list";
        status.setText("Readable F2P list loaded; Copy exports full diagnostic");
        if (nextSendLabel != null)
        {
            nextSendLabel.setText("Live F2P-list diagnostic");
        }
    }

    private void refreshTelemetryDebugBuyCacheSelection(
        JTextArea jsonView, JLabel status, JLabel nextSendLabel,
        String[] selectedRawJson, String[] selectedLabel, String[] selectedSource)
    {
        String readable = plugin == null ? "" : plugin.getAutoFlipLiveBuyPriceCachePseudoJson();
        String fullDiagnostic = plugin == null ? "" : plugin.getAutoFlipLiveBuyPriceCacheFullDiagnosticLog();
        setLiveViewerTextPreservingScroll(jsonView, readable);
        selectedRawJson[0] = fullDiagnostic == null ? "" : fullDiagnostic;
        selectedLabel[0] = "Current Buy Cache Full Diagnostic";
        selectedSource[0] = "buy_cache";
        status.setText("Readable buy cache loaded; Copy exports full diagnostic");
        if (nextSendLabel != null) nextSendLabel.setText("Live buy-cache diagnostic");
    }

    private void refreshTelemetryDebugSellCacheSelection(
        JTextArea jsonView,
        JLabel status,
        JLabel nextSendLabel,
        String[] selectedRawJson,
        String[] selectedLabel,
        String[] selectedSource
    )
    {
        String readable = plugin == null ? "" : plugin.getAutoFlipLiveSellPriceCachePseudoJson();
        String fullDiagnostic = plugin == null ? "" : plugin.getAutoFlipLiveSellPriceCacheFullDiagnosticLog();
        setLiveViewerTextPreservingScroll(jsonView, readable);
        selectedRawJson[0] = fullDiagnostic == null ? "" : fullDiagnostic;
        selectedLabel[0] = "Current Sell Cache Full Diagnostic";
        selectedSource[0] = "sell_cache";
        status.setText("Readable sell cache loaded; Copy exports full diagnostic");
        if (nextSendLabel != null)
        {
            nextSendLabel.setText("Live sell-cache diagnostic");
        }
    }

    private void setLiveViewerTextPreservingScroll(JTextArea textArea, String text)
    {
        if (textArea == null)
        {
            return;
        }

        String nextText = text == null ? "" : text;
        if (nextText.equals(textArea.getText()))
        {
            return;
        }

        Component ancestor = SwingUtilities.getAncestorOfClass(JScrollPane.class, textArea);
        if (!(ancestor instanceof JScrollPane))
        {
            textArea.setText(nextText);
            textArea.setCaretPosition(0);
            return;
        }

        JScrollPane scrollPane = (JScrollPane) ancestor;
        javax.swing.JScrollBar vertical = scrollPane.getVerticalScrollBar();
        javax.swing.JScrollBar horizontal = scrollPane.getHorizontalScrollBar();
        int oldVerticalValue = vertical.getValue();
        boolean wasAtBottom = AutoFlipScrollPosition.isAtBottom(
            oldVerticalValue,
            vertical.getVisibleAmount(),
            vertical.getMaximum()
        );
        int oldHorizontalValue = horizontal == null ? 0 : horizontal.getValue();
        int oldCaretPosition = textArea.getCaretPosition();

        textArea.setText(nextText);
        textArea.setCaretPosition(Math.min(oldCaretPosition, nextText.length()));
        SwingUtilities.invokeLater(() -> {
            vertical.setValue(
                AutoFlipScrollPosition.restoredValue(
                    oldVerticalValue,
                    wasAtBottom,
                    vertical.getVisibleAmount(),
                    vertical.getMaximum()
                )
            );
            if (horizontal != null)
            {
                horizontal.setValue(Math.min(
                    oldHorizontalValue,
                    Math.max(0, horizontal.getMaximum() - horizontal.getVisibleAmount())
                ));
            }
        });
    }

    private void refreshTelemetryDebugCurrentSelection(
        JTextArea jsonView,
        JLabel status,
        JLabel pendingCount,
        JLabel currentCount,
        JLabel accountMode,
        JLabel nextSendLabel,
        String[] selectedRawJson,
        String[] selectedLabel,
        String[] selectedSource
    )
    {
        String rawJson = plugin == null ? "" : plugin.getAutoFlipTelemetryCurrentPackageJson();
        int eventCount = plugin == null ? 0 : plugin.getAutoFlipTelemetryCurrentPackageEventCount();
        currentCount.setText("Current batch events: " + eventCount);
        if (accountMode != null && plugin != null)
        {
            accountMode.setText("Account: " + (plugin.isAutoFlipFreeToPlayAccount() ? "F2P" : "Members"));
        }
        if (pendingCount != null && plugin != null)
        {
            pendingCount.setText("Pending events: " + plugin.getAutoFlipTelemetryPendingSummaryCount());
        }
        if (nextSendLabel != null && plugin != null)
        {
            nextSendLabel.setText(formatTelemetryCountdown(plugin));
        }
        selectTelemetryJson(
            jsonView,
            status,
        selectedRawJson,
        selectedLabel,
        selectedSource,
        "Current Batch",
        rawJson,
            eventCount,
            "current"
        );
    }

    private void refreshTelemetryDebugPendingSelection(
        JTextArea jsonView,
        JLabel status,
        JLabel pendingCount,
        JLabel currentCount,
        JLabel accountMode,
        JLabel nextSendLabel,
        String[] selectedRawJson,
        String[] selectedLabel,
        String[] selectedSource
    )
    {
        String rawJson = plugin == null ? "" : plugin.getAutoFlipTelemetryPendingSummaryJson();
        int itemCount = plugin == null ? 0 : plugin.getAutoFlipTelemetryPendingSummaryCount();
        pendingCount.setText("Pending events: " + itemCount);
        if (accountMode != null && plugin != null)
        {
            accountMode.setText("Account: " + (plugin.isAutoFlipFreeToPlayAccount() ? "F2P" : "Members"));
        }
        if (currentCount != null && plugin != null)
        {
            currentCount.setText("Current batch events: " + plugin.getAutoFlipTelemetryCurrentPackageEventCount());
        }
        if (nextSendLabel != null)
        {
            nextSendLabel.setText("Next send: n/a");
        }
        selectTelemetryJson(
            jsonView,
            status,
            selectedRawJson,
            selectedLabel,
            selectedSource,
            "Pending",
            rawJson,
            itemCount,
            "pending"
        );
    }

    private String formatTelemetryCountdown(AutoFlipPlugin plugin)
    {
        if (plugin == null)
        {
            return "Next send: unknown";
        }

        if (plugin.isAutoFlipTelemetrySendInProgress())
        {
            return "Next send: sending now...";
        }

        long remainingMs = plugin.getAutoFlipTelemetryNextSendCountdownMs();
        if (remainingMs <= 0L)
        {
            return "Next send: ready";
        }

        return "Next send in: " + formatTelemetryDuration(remainingMs / 1000L);
    }

    private String formatTelemetryPseudoCode(String rawJson)
    {
        if (rawJson == null)
        {
            return "";
        }

        String json = rawJson.trim();
        if (json.isEmpty())
        {
            return "";
        }

        String schema = readJsonString(json, "schema", "");
        if ("autoflip.plugin_pending_slot_summary.v1".equals(schema))
        {
            return formatTelemetryPendingPseudoCode(json);
        }

        List<String> eventObjects = extractJsonArrayObjects(json, "events");
        List<List<String>> groupedEvents = groupTelemetryEventObjects(eventObjects);
        StringBuilder out = new StringBuilder(json.length() + 256);
        out.append("batch_id = ").append(readJsonString(json, "batch_id", "unknown")).append('\n');
        out.append("event_count = ").append(readJsonInt(json, "event_count", eventObjects.size())).append('\n');
        out.append("event_groups = ").append(groupedEvents.size()).append('\n');
        out.append("session_id = ").append(readJsonString(json, "session_id", "unknown")).append('\n');
        out.append("player_hash = ").append(readJsonString(json, "player_hash", "unknown")).append('\n');
        out.append("start_time = ").append(formatTelemetryTime(readJsonLong(json, "recommendation_generated_ts_ms", readJsonLong(json, "event_ts_ms", 0L)))).append('\n');
        out.append("end_time = ").append(formatTelemetryTime(readJsonLong(json, "recommendation_shown_ts_ms", readJsonLong(json, "event_ts_ms", 0L)))).append('\n');
        out.append("guessed_time = ").append(formatTelemetryTime(readJsonLong(json, "recommendation_shown_ts_ms", readJsonLong(json, "recommendation_generated_ts_ms", 0L)))).append('\n');
        out.append("last_seen = ").append(formatTelemetryTime(readJsonLong(json, "last_seen_ts_ms", readJsonLong(json, "last_seen_open_ts_ms", readJsonLong(json, "event_ts_ms", 0L))))).append('\n');
        out.append("total_time = ").append(formatTelemetryDuration(readJsonLong(json, "recommendation_age_seconds", 0L))).append('\n');
        out.append('\n');
        out.append("events:\n");

        if (groupedEvents.isEmpty())
        {
            out.append("  (no events)\n");
            return out.toString().trim();
        }

        for (int i = 0; i < groupedEvents.size(); i++)
        {
            appendTelemetryEventGroupPseudoCode(out, groupedEvents.get(i), i + 1);
        }

        return out.toString().trim();
    }

    private void appendTelemetryEventGroupPseudoCode(StringBuilder out, List<String> group, int index)
    {
        if (group == null || group.isEmpty())
        {
            return;
        }

        String primary = group.get(0);
        int itemId = readJsonInt(primary, "item_id", readJsonInt(primary, "id", 0));
        String itemName = resolveTelemetryItemName(primary, itemId);
        String side = "";
        int slot = -1;
        int qty = 0;
        int totalQty = 0;
        int filledQty = 0;
        long startMs = Long.MAX_VALUE;
        long endMs = 0L;
        long guessedMs = 0L;
        long lastSeenMs = 0L;

        for (String event : group)
        {
            String eventSide = readJsonString(event, "side", "");
            if (side.isEmpty() && !eventSide.isEmpty())
            {
                side = eventSide;
            }

            if (slot < 0)
            {
                slot = readJsonInt(event, "ge_slot", readJsonInt(event, "slot", -1));
            }

            if (qty <= 0)
            {
                qty = firstPositiveInt(
                    readJsonInt(event, "quantity", 0),
                    readJsonInt(event, "suggested_quantity", 0),
                    readJsonInt(event, "offered_quantity", 0)
                );
            }

            totalQty = Math.max(totalQty, readJsonInt(event, "total_quantity", 0));
            filledQty = Math.max(filledQty, readJsonInt(event, "quantity_sold", 0));

            long eventStart = firstPositiveLong(
                readJsonLong(event, "started_ts_ms", 0L),
                readJsonLong(event, "start_ts_ms", 0L),
                readJsonLong(event, "placed_ts_ms", 0L),
                readJsonLong(event, "trade_time_ms", 0L),
                readJsonLong(event, "recommendation_generated_ts_ms", 0L),
                readJsonLong(event, "event_ts_ms", 0L)
            );
            long eventEnd = firstPositiveLong(
                readJsonLong(event, "ended_ts_ms", 0L),
                readJsonLong(event, "end_ts_ms", 0L),
                readJsonLong(event, "last_seen_ts_ms", 0L),
                readJsonLong(event, "final_observed_ts_ms", 0L),
                readJsonLong(event, "recommendation_shown_ts_ms", 0L),
                readJsonLong(event, "event_ts_ms", 0L)
            );
            long eventGuessed = firstPositiveLong(
                readJsonLong(event, "guessed_ts_ms", 0L),
                readJsonLong(event, "recommendation_shown_ts_ms", 0L),
                readJsonLong(event, "recommendation_generated_ts_ms", 0L)
            );
            long eventLastSeen = firstPositiveLong(
                readJsonLong(event, "last_seen_ts_ms", 0L),
                readJsonLong(event, "last_seen_open_ts_ms", 0L),
                readJsonLong(event, "final_observed_ts_ms", 0L),
                readJsonLong(event, "event_ts_ms", 0L)
            );

            if (eventStart > 0L)
            {
                startMs = Math.min(startMs, eventStart);
            }
            if (eventEnd > 0L)
            {
                endMs = Math.max(endMs, eventEnd);
            }
            if (eventGuessed > 0L)
            {
                guessedMs = eventGuessed;
            }
            if (eventLastSeen > 0L)
            {
                lastSeenMs = Math.max(lastSeenMs, eventLastSeen);
            }
        }

        if (startMs == Long.MAX_VALUE)
        {
            startMs = 0L;
        }

        out.append("  ").append(index).append(". ").append(itemName);
        if (itemId > 0)
        {
            out.append(" (id=").append(itemId).append(")");
        }
        if (slot >= 0)
        {
            out.append(" slot=").append(slot);
        }
        out.append('\n');
        out.append("     related_events = ").append(group.size()).append('\n');
        if (!side.isEmpty())
        {
            out.append("     side = ").append(side).append('\n');
        }
        if (qty > 0)
        {
            out.append("     qty = ").append(qty).append('\n');
        }
        if (startMs > 0L)
        {
            out.append("     start_time = ").append(formatTelemetryTime(startMs)).append('\n');
        }
        if (endMs > 0L)
        {
            out.append("     end_time = ").append(formatTelemetryTime(endMs)).append('\n');
        }
        if (guessedMs > 0L)
        {
            out.append("     guessed_time = ").append(formatTelemetryTime(guessedMs)).append('\n');
        }
        if (lastSeenMs > 0L)
        {
            out.append("     last_seen = ").append(formatTelemetryTime(lastSeenMs)).append('\n');
        }
        if (startMs > 0L && endMs > 0L && endMs >= startMs)
        {
            out.append("     total_time = ").append(formatTelemetryDuration((endMs - startMs) / 1000L)).append('\n');
        }
        if (totalQty > 0 || filledQty > 0)
        {
            out.append("     progress = ").append(filledQty).append("/").append(totalQty).append('\n');
        }
        out.append("     events:\n");

        for (int i = 0; i < group.size(); i++)
        {
            appendTelemetrySingleEventPseudoCode(out, group.get(i), i + 1, "       ");
        }

        out.append('\n');
    }

    private void appendTelemetrySingleEventPseudoCode(StringBuilder out, String event, int index, String indent)
    {
        int itemId = readJsonInt(event, "item_id", readJsonInt(event, "id", 0));
        String eventType = readJsonString(event, "event_type", readJsonString(event, "event", "event"));
        String state = readJsonString(event, "state", "");
        String side = readJsonString(event, "side", "");
        int slot = readJsonInt(event, "ge_slot", readJsonInt(event, "slot", -1));
        int qty = readJsonInt(event, "quantity", readJsonInt(event, "suggested_quantity", 0));
        int totalQty = readJsonInt(event, "total_quantity", 0);
        int filledQty = readJsonInt(event, "quantity_sold", 0);
        long startMs = firstPositiveLong(
            readJsonLong(event, "started_ts_ms", 0L),
            readJsonLong(event, "start_ts_ms", 0L),
            readJsonLong(event, "placed_ts_ms", 0L),
            readJsonLong(event, "trade_time_ms", 0L),
            readJsonLong(event, "recommendation_generated_ts_ms", 0L),
            readJsonLong(event, "event_ts_ms", 0L)
        );
        long endMs = firstPositiveLong(
            readJsonLong(event, "ended_ts_ms", 0L),
            readJsonLong(event, "end_ts_ms", 0L),
            readJsonLong(event, "last_seen_ts_ms", 0L),
            readJsonLong(event, "final_observed_ts_ms", 0L),
            readJsonLong(event, "recommendation_shown_ts_ms", 0L),
            readJsonLong(event, "event_ts_ms", 0L)
        );
        long guessedMs = firstPositiveLong(
            readJsonLong(event, "guessed_ts_ms", 0L),
            readJsonLong(event, "recommendation_shown_ts_ms", 0L),
            readJsonLong(event, "recommendation_generated_ts_ms", 0L)
        );
        long lastSeenMs = firstPositiveLong(
            readJsonLong(event, "last_seen_ts_ms", 0L),
            readJsonLong(event, "last_seen_open_ts_ms", 0L),
            readJsonLong(event, "final_observed_ts_ms", 0L),
            readJsonLong(event, "event_ts_ms", 0L)
        );

        out.append(indent).append(index).append(". ").append(humanizeTelemetryAction(eventType, state)).append('\n');
        out.append(indent).append("   event_type = ").append(eventType).append('\n');
        if (!side.isEmpty())
        {
            out.append(indent).append("   side = ").append(side).append('\n');
        }
        if (slot >= 0)
        {
            out.append(indent).append("   slot = ").append(slot).append('\n');
        }
        if (itemId > 0)
        {
            out.append(indent).append("   item_id = ").append(itemId).append('\n');
        }
        if (qty > 0)
        {
            out.append(indent).append("   qty = ").append(qty).append('\n');
        }
        if (startMs > 0L)
        {
            out.append(indent).append("   start_time = ").append(formatTelemetryTime(startMs)).append('\n');
        }
        if (endMs > 0L)
        {
            out.append(indent).append("   end_time = ").append(formatTelemetryTime(endMs)).append('\n');
        }
        if (guessedMs > 0L)
        {
            out.append(indent).append("   guessed_time = ").append(formatTelemetryTime(guessedMs)).append('\n');
        }
        if (lastSeenMs > 0L)
        {
            out.append(indent).append("   last_seen = ").append(formatTelemetryTime(lastSeenMs)).append('\n');
        }
        if (startMs > 0L && endMs > 0L && endMs >= startMs)
        {
            out.append(indent).append("   total_time = ").append(formatTelemetryDuration((endMs - startMs) / 1000L)).append('\n');
        }
        if (totalQty > 0 || filledQty > 0)
        {
            out.append(indent).append("   progress = ").append(filledQty).append("/").append(totalQty).append('\n');
        }
    }

    private List<List<String>> groupTelemetryEventObjects(List<String> eventObjects)
    {
        java.util.LinkedHashMap<String, java.util.List<String>> groups = new java.util.LinkedHashMap<>();
        for (String event : eventObjects)
        {
            String key = telemetryEventGroupKey(event);
            groups.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(event);
        }
        return new java.util.ArrayList<>(groups.values());
    }

    private String telemetryEventGroupKey(String event)
    {
        int itemId = readJsonInt(event, "item_id", readJsonInt(event, "id", 0));
        String side = readJsonString(event, "side", "").toLowerCase(java.util.Locale.ROOT);
        int qty = firstPositiveInt(
            readJsonInt(event, "quantity", 0),
            readJsonInt(event, "suggested_quantity", 0),
            readJsonInt(event, "offered_quantity", 0)
        );
        long observedMs = firstPositiveLong(
            readJsonLong(event, "trade_time_ms", 0L),
            readJsonLong(event, "placed_ts_ms", 0L),
            readJsonLong(event, "event_ts_ms", 0L)
        );
        long bucket = observedMs <= 0L ? 0L : (observedMs / 2000L);
        return itemId + "|" + side + "|" + qty + "|" + bucket;
    }

    private String formatTelemetryPendingPseudoCode(String json)
    {
        List<String> slotObjects = extractJsonArrayObjects(json, "slots");
        StringBuilder out = new StringBuilder(json.length() + 256);
        out.append("batch_id = ").append(readJsonString(json, "batch_id", "unknown")).append('\n');
        out.append("pending_count = ").append(readJsonInt(json, "pending_count", slotObjects.size())).append('\n');
        out.append("session_id = ").append(readJsonString(json, "session_id", "unknown")).append('\n');
        out.append("player_hash = ").append(readJsonString(json, "player_hash", "unknown")).append('\n');
        out.append("observed_time = ").append(formatTelemetryTime(readJsonLong(json, "observed_ts_ms", 0L))).append('\n');
        out.append('\n');
        out.append("pending:\n");

        if (slotObjects.isEmpty())
        {
            out.append("  (no pending slots)\n");
            return out.toString().trim();
        }

        for (int i = 0; i < slotObjects.size(); i++)
        {
            String slotJson = slotObjects.get(i);
            int slot = readJsonInt(slotJson, "slot", -1);
            int itemId = readJsonInt(slotJson, "item_id", 0);
            String itemName = resolveTelemetryItemName(slotJson, itemId);
            String action = readJsonString(slotJson, "action", "unknown");
            String status = readJsonString(slotJson, "status", "unknown");
            String state = readJsonString(slotJson, "state", "unknown");
            long placedMs = readJsonLong(slotJson, "placed_ts_ms", 0L);
            long lastSeenMs = readJsonLong(slotJson, "last_seen_ts_ms", 0L);
            long detectedMs = readJsonLong(slotJson, "time_detected_ts_ms", 0L);
            long guessedMs = readJsonLong(slotJson, "guessed_ts_ms", 0L);
            long totalSeconds = firstPositiveLong(
                readJsonLong(slotJson, "time_in_trade_upper_bound_seconds", 0L),
                readJsonLong(slotJson, "time_in_trade_lower_bound_seconds", 0L)
            );
            int offeredPrice = readJsonInt(slotJson, "offered_price", 0);
            int offeredQuantity = readJsonInt(slotJson, "offered_quantity", 0);
            int filledQuantity = readJsonInt(slotJson, "filled_quantity", 0);
            int spentOrReceivedGp = readJsonInt(slotJson, "spent_or_received_gp", 0);

            out.append("  ").append(i + 1).append(". ").append(itemName);
            if (itemId > 0)
            {
                out.append(" (id=").append(itemId).append(")");
            }
            if (slot >= 0)
            {
                out.append(" slot=").append(slot);
            }
            out.append('\n');
            out.append("     action = ").append(action).append('\n');
            out.append("     status = ").append(status).append('\n');
            out.append("     state = ").append(state).append('\n');
            if (offeredPrice > 0)
            {
                out.append("     price = ").append(offeredPrice).append('\n');
            }
            if (offeredQuantity > 0)
            {
                out.append("     qty = ").append(offeredQuantity).append('\n');
            }
            if (placedMs > 0)
            {
                out.append("     start_time = ").append(formatTelemetryTime(placedMs)).append('\n');
            }
            if (detectedMs > 0)
            {
                out.append("     guessed_time = ").append(formatTelemetryTime(detectedMs)).append('\n');
            }
            if (lastSeenMs > 0)
            {
                out.append("     last_seen = ").append(formatTelemetryTime(lastSeenMs)).append('\n');
            }
            if (placedMs > 0 && lastSeenMs > 0 && lastSeenMs >= placedMs)
            {
                out.append("     total_time = ").append(formatTelemetryDuration((lastSeenMs - placedMs) / 1000L)).append('\n');
            }
            else if (totalSeconds > 0)
            {
                out.append("     total_time = ").append(formatTelemetryDuration(totalSeconds)).append('\n');
            }
            if (filledQuantity > 0 || spentOrReceivedGp > 0)
            {
                out.append("     progress = ").append(filledQuantity);
                if (offeredQuantity > 0)
                {
                    out.append("/").append(offeredQuantity);
                }
                out.append('\n');
                out.append("     gp = ").append(spentOrReceivedGp).append('\n');
            }
            out.append('\n');
        }

        return out.toString().trim();
    }

    private String resolveTelemetryItemName(String eventJson, int itemId)
    {
        String itemName = readJsonString(eventJson, "item_name", "");
        if (!itemName.isEmpty())
        {
            return itemName;
        }

        itemName = readJsonString(eventJson, "name", "");
        if (!itemName.isEmpty())
        {
            return itemName;
        }

        if (plugin != null && itemId > 0)
        {
            String resolved = plugin.getAutoFlipItemNameForDebug(itemId);
            if (resolved != null && !resolved.trim().isEmpty())
            {
                return resolved.trim();
            }
        }

        if (itemId > 0)
        {
            return "item #" + itemId;
        }

        return "unknown item";
    }

    private int firstPositiveInt(int... values)
    {
        if (values == null)
        {
            return 0;
        }

        for (int value : values)
        {
            if (value > 0)
            {
                return value;
            }
        }

        return 0;
    }

    private long firstPositiveLong(long... values)
    {
        if (values == null)
        {
            return 0L;
        }

        for (long value : values)
        {
            if (value > 0L)
            {
                return value;
            }
        }

        return 0L;
    }

    private String formatTelemetryTime(long valueMs)
    {
        if (valueMs <= 0L)
        {
            return "unknown";
        }

        try
        {
            return java.time.Instant.ofEpochMilli(valueMs).toString();
        }
        catch (Throwable ignored)
        {
            return Long.toString(valueMs);
        }
    }

    private String formatTelemetryDuration(long valueSeconds)
    {
        if (valueSeconds <= 0L)
        {
            return "0s";
        }

        long hours = valueSeconds / 3600L;
        long minutes = (valueSeconds % 3600L) / 60L;
        long seconds = valueSeconds % 60L;
        StringBuilder out = new StringBuilder();
        if (hours > 0L)
        {
            out.append(hours).append("h ");
        }
        if (minutes > 0L || hours > 0L)
        {
            out.append(minutes).append("m ");
        }
        out.append(seconds).append("s");
        return out.toString().trim();
    }

    private String humanizeTelemetryAction(String eventType, String state)
    {
        String type = eventType == null ? "" : eventType.trim().toLowerCase(java.util.Locale.ROOT);
        String st = state == null ? "" : state.trim().toLowerCase(java.util.Locale.ROOT);
        if (type.contains("cancel") || st.contains("cancel"))
        {
            return "offer canceled";
        }
        if (type.contains("buy") && type.contains("complete"))
        {
            return "buy completed";
        }
        if (type.contains("sell") && type.contains("complete"))
        {
            return "sell completed";
        }
        if (type.contains("place") || type.contains("offer"))
        {
            return "order placed";
        }
        if (type.isEmpty() && st.isEmpty())
        {
            return "event";
        }
        return type.isEmpty() ? st : type;
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

    private void appendTelemetryIndent(StringBuilder out, int indent)
    {
        for (int i = 0; i < indent; i++)
        {
            out.append("  ");
        }
    }


    // AUTOFLIP_PATCH_INVENTORY_EYE_GLOW_V2
    // AUTOFLIP_PATCH_BANK_SYMBOL_DOLLAR_V1
    private void updateAutoFlipInventoryBankViewButtonState()
    {
        if (autoFlipInventoryBankViewButton == null)
        {
            return;
        }

        boolean bankOpen = false;
        try
        {
            bankOpen = plugin != null && plugin.isAutoFlipBankOpenForSidePanel();
        }
        catch (Throwable ignored)
        {
            bankOpen = false;
        }

        autoFlipInventoryBankViewButton.setFocusPainted(false);
        autoFlipInventoryBankViewButton.setOpaque(true);
        autoFlipInventoryBankViewButton.setContentAreaFilled(true);
        autoFlipInventoryBankViewButton.setFont(autoFlipInventoryBankViewButton.getFont().deriveFont(Font.BOLD, 15f));

        if (bankOpen)
        {
            autoFlipInventoryBankViewButton.setForeground(GOLD);
            autoFlipInventoryBankViewButton.setBackground(new Color(76, 54, 8));
            autoFlipInventoryBankViewButton.setBorder(BorderFactory.createLineBorder(new Color(255, 214, 79), 2));
            autoFlipInventoryBankViewButton.setToolTipText("Bank open: show AutoFlip Inventory items in bank");
            autoFlipInventoryBankViewButton.setText("<html><span style='color:#FFD64F; font-weight:bold;'>$</span></html>");
        }
        else
        {
            autoFlipInventoryBankViewButton.setForeground(new Color(90, 90, 90));
            autoFlipInventoryBankViewButton.setBackground(new Color(22, 22, 22));
            autoFlipInventoryBankViewButton.setBorder(BorderFactory.createLineBorder(new Color(46, 46, 46), 1));
            autoFlipInventoryBankViewButton.setToolTipText("Open your bank to show AutoFlip Inventory items");
            autoFlipInventoryBankViewButton.setText("<html><span style='color:#6A6A6A; font-weight:bold;'>$</span></html>");
        }

        autoFlipInventoryBankViewButton.revalidate();
        long autoFlipBankButtonNow = System.currentTimeMillis();
        if (plugin.isAutoFlipVerboseRuntimeLoggingEnabled()
            && autoFlipBankButtonNow - autoFlipLastBankButtonVisualLogMs > 1500L)
        {
            autoFlipLastBankButtonVisualLogMs = autoFlipBankButtonNow;
            System.out.println(
                "AUTOFLIP_BANK_BUTTON_STATE bankOpen=" + bankOpen
                + " text=" + String.valueOf(autoFlipInventoryBankViewButton.getText())
                + " fg=" + String.valueOf(autoFlipInventoryBankViewButton.getForeground())
                + " bg=" + String.valueOf(autoFlipInventoryBankViewButton.getBackground())
            );
        }
        autoFlipInventoryBankViewButton.repaint();
    }

    private JPanel buildInventoryHeader()
    {
        JPanel p = rowPanel();
        inventoryTitle.setForeground(GOLD);
        inventoryTitle.setFont(inventoryTitle.getFont().deriveFont(Font.BOLD, 14f));
        heldCount.setForeground(GREEN);

        // AUTOFLIP_PATCH_BANK_TAG_EYE_CLEAN_V1
        autoFlipInventoryBankViewButton = new JButton("Ã¢â€”Å½");
        JButton bankView = autoFlipInventoryBankViewButton;
        bankView.setForeground(GOLD);
        bankView.setBackground(new Color(30, 30, 30));
        bankView.setFocusPainted(false);
        bankView.setBorder(BorderFactory.createEmptyBorder(0, 0, 1, 0));
        bankView.setMargin(new java.awt.Insets(0, 0, 0, 0));
        bankView.setPreferredSize(new Dimension(22, 18));
        bankView.setMinimumSize(new Dimension(22, 18));
        bankView.setMaximumSize(new Dimension(22, 18));
        bankView.setToolTipText("Show AutoFlip Inventory items in bank");
        bankView.addActionListener(e -> plugin.openAutoFlipInventoryBankView());
        updateAutoFlipInventoryBankViewButtonState();

        p.add(inventoryTitle);
        p.add(Box.createHorizontalGlue());
        p.add(bankView);
        p.add(Box.createHorizontalStrut(4));
        p.add(heldCount);
        return p;
    }

    private JPanel buildSortRow()
    {
        JPanel p = rowPanel();
        p.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 16, 23));
        p.setMinimumSize(new Dimension(PluginPanel.PANEL_WIDTH - 16, 23));
        p.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 16, 23));

        JLabel label = new JLabel("Sort by");
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(11f));

        sortBox.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 90, 23));
        sortBox.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 90, 23));
        sortBox.setFocusable(false);
        sortBox.addActionListener(e -> refreshFromPlugin());

        p.add(label);
        p.add(Box.createHorizontalStrut(6));
        p.add(sortBox);
        return p;
    }

    private JPanel buildInventoryHelp()
    {
        JPanel p = cardPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel title = sectionLabel("WHAT LIVES HERE");
        JLabel text = new JLabel("<html><span style='color:#aaa'>Expensive bank-sale items or price-dropped buys held until AutoFlip says it is time to sell.</span></html>");
        text.setForeground(MUTED);

        p.add(title);
        p.add(space(5));
        p.add(text);
        return p;
    }

    private JPanel buildNextAction()
    {
        JPanel p = cardPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(sectionLabel("NEXT BEST ACTION"));
        p.add(space(5));

        nextAction.setForeground(GREEN);
        nextAction.setFont(nextAction.getFont().deriveFont(Font.BOLD, 13f));
        p.add(nextAction);
        return p;
    }

    private JPanel buildFooter()
    {
        JPanel p = rowPanel();
        apiStatus.setForeground(GREEN);

        JLabel version = new JLabel("v1.0.0");
        version.setForeground(MUTED);

        p.add(apiStatus);
        p.add(Box.createHorizontalGlue());
        p.add(version);
        return p;
    }

    private void refreshCurrentOffersPanel()
    {
        if (currentOffersCardsPanel == null)
        {
            return;
        }

        currentOffersCardsPanel.removeAll();
        java.util.List<AutoFlipPlugin.AutoFlipCurrentOfferSnapshot> offers = plugin == null
            ? java.util.Collections.emptyList()
            : plugin.getAutoFlipCurrentOffersSnapshot();

        AutoFlipPlugin.AutoFlipCurrentOfferSnapshot[] bySlot = new AutoFlipPlugin.AutoFlipCurrentOfferSnapshot[8];
        if (offers != null)
        {
            for (AutoFlipPlugin.AutoFlipCurrentOfferSnapshot offer : offers)
            {
                if (offer == null)
                {
                    continue;
                }

                int slot = offer.getSlot();
                if (slot >= 0 && slot < bySlot.length)
                {
                    bySlot[slot] = offer;
                }
            }
        }

        int usedSlots = 0;
        if (currentOffersSelectedSlot >= bySlot.length || (currentOffersSelectedSlot >= 0 && bySlot[currentOffersSelectedSlot] == null))
        {
            currentOffersSelectedSlot = -1;
        }

        currentOffersCardsPanel.setLayout(new BoxLayout(currentOffersCardsPanel, BoxLayout.Y_AXIS));
        currentOffersCardsPanel.setBackground(CARD);
        for (int slot = 0; slot < bySlot.length; slot++)
        {
            AutoFlipPlugin.AutoFlipCurrentOfferSnapshot offer = bySlot[slot];
            boolean selected = currentOffersSelectedSlot == slot;
            if (offer != null)
            {
                usedSlots++;
                currentOffersCardsPanel.add(buildCurrentOfferCard(slot, offer, selected));
            }
            else
            {
                currentOffersCardsPanel.add(buildCurrentOfferEmptyCard(slot, selected));
            }
            if (slot + 1 < bySlot.length)
            {
                currentOffersCardsPanel.add(space(6));
            }
        }

        currentOffersCardsPanel.revalidate();
        currentOffersCardsPanel.repaint();
        if (currentOffersStatus != null)
        {
            currentOffersStatus.setText(usedSlots + "/8 used");
        }
    }

    private JPanel buildCurrentOfferEmptyCard(int slotIndex, boolean selected)
    {
        JPanel p = cardPanel();
        p.setLayout(new BorderLayout(8, 0));
        p.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 48));
        p.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, 48));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createLineBorder(selected ? GOLD : new Color(88, 76, 58), 1));

        JLabel text = new JLabel("<html><div style='text-align:left'><b>Slot " + (slotIndex + 1) + "</b> <span style='color:#aaa'>Empty</span></div></html>");
        text.setForeground(MUTED);
        text.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        p.add(text, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildCurrentOfferCard(int slotIndex, AutoFlipPlugin.AutoFlipCurrentOfferSnapshot offer, boolean selected)
    {
        JPanel p = cardPanel();
        p.setLayout(new BorderLayout(8, 0));
        p.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, selected ? 92 : 54));
        p.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH + 10, selected ? 92 : 54));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createLineBorder(selected ? GOLD : new Color(197, 138, 29), 1));

        JLabel icon = new JLabel();
        icon.setPreferredSize(new Dimension(24, 24));
        icon.setMinimumSize(new Dimension(24, 24));
        icon.setMaximumSize(new Dimension(24, 24));
        icon.setHorizontalAlignment(SwingConstants.CENTER);
        icon.setVerticalAlignment(SwingConstants.CENTER);
        java.awt.image.BufferedImage itemImage = plugin == null ? null : plugin.getAutoFlipInventoryItemImage(offer.getItemId());
        if (itemImage != null)
        {
            icon.setIcon(new javax.swing.ImageIcon(itemImage));
        }
        icon.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        p.add(icon, BorderLayout.WEST);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(5, 0, 4, 0));

        JLabel slot = new JLabel("Slot " + (slotIndex + 1));
        slot.setForeground(GOLD);
        slot.setFont(slot.getFont().deriveFont(Font.BOLD, 10f));
        slot.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel name = new JLabel(offer.getItemName());
        name.setForeground(TEXT);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 12f));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel summary = new JLabel(offer.getDisplaySummary());
        summary.setForeground(GREEN);
        summary.setFont(summary.getFont().deriveFont(Font.BOLD, 10f));
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);

        center.add(slot);
        center.add(name);
        center.add(summary);

        if (selected)
        {
            JLabel details = new JLabel("<html><span style='color:#aaa'>"
                + "Price each: " + offer.getOfferedPriceGp()
                + " gp • Remaining: " + offer.getRemainingQuantity()
                + " • Total: " + offer.getOfferedQuantity()
                + " • " + offer.getAgeSeconds() + "s ago"
                + "</span></html>");
            details.setForeground(MUTED);
            details.setFont(details.getFont().deriveFont(Font.PLAIN, 10f));
            details.setAlignmentX(Component.LEFT_ALIGNMENT);
            center.add(details);
        }
        p.add(center, BorderLayout.CENTER);

        JLabel arrow = new JLabel(">");
        arrow.setForeground(MUTED);
        arrow.setFont(arrow.getFont().deriveFont(Font.BOLD, 14f));
        arrow.setVerticalAlignment(SwingConstants.CENTER);
        p.add(arrow, BorderLayout.EAST);

        java.awt.event.MouseAdapter select = new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                currentOffersSelectedSlot = currentOffersSelectedSlot == slotIndex ? -1 : slotIndex;
                refreshCurrentOffersPanel();
            }
        };
        registerCurrentOfferClickHandlers(p, select);

        return p;
    }

    private void registerCurrentOfferClickHandlers(java.awt.Component component, java.awt.event.MouseAdapter handler)
    {
        if (component == null || handler == null)
        {
            return;
        }

        component.addMouseListener(handler);
        if (component instanceof Container)
        {
            for (java.awt.Component child : ((Container) component).getComponents())
            {
                registerCurrentOfferClickHandlers(child, handler);
            }
        }
    }

    public void refreshFromPlugin()
    {
        
        // AUTOFLIP_INVENTORY_SORT_REPAIR_V1
SwingUtilities.invokeLater(() -> {
            List<AutoFlipPlugin.AutoFlipInventoryItem> raw = plugin.getAutoFlipInventorySnapshot();
            List<AutoFlipPlugin.AutoFlipInventoryItem> items = new ArrayList<>();
            if (raw != null)
            {
                for (AutoFlipPlugin.AutoFlipInventoryItem item : raw)
                {
                    if (item != null)
                    {
                        items.add(item);
                    }
                }
            }

            String sort = String.valueOf(sortBox.getSelectedItem());
            if ("Held Capital".equals(sort))
            {
                items.sort(
                    Comparator.comparingInt((AutoFlipPlugin.AutoFlipInventoryItem item) ->
                            AutoFlipInventoryLifecyclePolicy.displayRank(item.getStatus()))
                        .thenComparing(Comparator.comparingLong(AutoFlipPlugin.AutoFlipInventoryItem::getHeldCapitalGp).reversed())
                        .thenComparing(AutoFlipPlugin.AutoFlipInventoryItem::getAddedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AutoFlipPlugin.AutoFlipInventoryItem::getItemName, String.CASE_INSENSITIVE_ORDER)
                );
            }
            else
            {
                items.sort(
                    Comparator.comparingInt((AutoFlipPlugin.AutoFlipInventoryItem item) ->
                            AutoFlipInventoryLifecyclePolicy.displayRank(item.getStatus()))
                        .thenComparing(AutoFlipPlugin.AutoFlipInventoryItem::getAddedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Comparator.comparingLong(AutoFlipPlugin.AutoFlipInventoryItem::getHeldCapitalGp).reversed())
                        .thenComparing(AutoFlipPlugin.AutoFlipInventoryItem::getItemName, String.CASE_INSENSITIVE_ORDER)
                );
            }

            cardsPanel.removeAll();

            JPanel readyNotifier = buildReadyToSellNotifier(items);
            if (readyNotifier != null)
            {
                cardsPanel.add(readyNotifier);
                cardsPanel.add(space(6));
            }

            int index = 0;
            long totalHeldCapital = 0L;
            for (AutoFlipPlugin.AutoFlipInventoryItem item : items)
            {
                totalHeldCapital += Math.max(0L, item.getHeldCapitalGp());
                cardsPanel.add(buildHeldItemRow(index + 1, item));
                cardsPanel.add(space(6));
                index++;
            }

            if (index == 0)
            {
                cardsPanel.add(buildEmptyRow());
            }

            inventoryTitle.setText("AUTOFLIP INVENTORY");
            heldCount.setText(index + " held");
            nextAction.setText(index > 0 ? "Held capital: " + formatGp(totalHeldCapital) : "Shift + right-click an item");
            apiStatus.setText("Local hold list");
            refreshCurrentOffersPanel();

            cardsPanel.revalidate();
            cardsPanel.repaint();
            populateInventoryCards(inventoryTabCardsPanel, items);
            refreshToBuyCards();
            revalidate();
            repaint();
        });
    }

    private void populateInventoryCards(JPanel targetPanel, List<AutoFlipPlugin.AutoFlipInventoryItem> items)
    {
        JScrollPane scrollPane = findScrollPaneAncestor(targetPanel);
        int scrollY = scrollPane == null ? -1 : scrollPane.getVerticalScrollBar().getValue();
        targetPanel.removeAll();
        cleanupCompletedSmartSellInstructions(items);

        
        JPanel readyNotifier = buildReadyToSellNotifier(items);
        if (readyNotifier != null)
        {
            targetPanel.add(readyNotifier);
            targetPanel.add(space(6));
        }
if (items.isEmpty())
        {
            targetPanel.add(buildEmptyRow());
        }
        else
        {
            for (int index = 0; index < items.size(); index++)
            {
                targetPanel.add(buildHeldItemRow(index + 1, items.get(index)));
                targetPanel.add(space(6));
            }
        }

        targetPanel.revalidate();
        targetPanel.repaint();
        if (scrollPane != null && scrollY >= 0)
        {
            final int restored = scrollY;
            SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(restored));
        }
    }

    private void refreshToBuyCards()
    {
        SwingUtilities.invokeLater(() -> {
            List<AutoFlipPlugin.AutoFlipToBuyItem> items = plugin.getAutoFlipToBuySnapshot();
            populateToBuyCards(items, java.util.Collections.emptyList());
        });
    }

    private void populateToBuyCards(List<AutoFlipPlugin.AutoFlipToBuyItem> watchlist, List<AutoFlipPlugin.AutoFlipMarketSearchResult> searchResults)
    {
        JScrollPane scrollPane = findScrollPaneAncestor(toBuyCardsPanel);
        int scrollY = scrollPane == null ? -1 : scrollPane.getVerticalScrollBar().getValue();

        toBuyCardsPanel.removeAll();
        List<AutoFlipPlugin.AutoFlipToBuyItem> items = watchlist == null ? java.util.Collections.emptyList() : watchlist;

        JPanel readyNotifier = buildReadyToBuyNotifier(items);
        if (readyNotifier != null)
        {
            toBuyCardsPanel.add(readyNotifier);
            toBuyCardsPanel.add(space(6));
        }

        if (items.isEmpty())
        {
            toBuyCardsPanel.add(buildToBuyEmptyRow());
        }
        else
        {
            for (AutoFlipPlugin.AutoFlipToBuyItem item : items)
            {
                if (item != null)
                {
                    toBuyCardsPanel.add(buildToBuyItemRow(item));
                    toBuyCardsPanel.add(space(6));
                }
            }
        }

        if (toBuyCardsPanel.getComponentCount() == 0)
        {
            toBuyCardsPanel.add(buildToBuyEmptyRow());
        }

        toBuyTitle.setText("AUTOFLIP TO-BUY");
        toBuyCount.setText(items.size() + " watched");
        toBuyStatus.setText(items.isEmpty() ? "Search items in separate window" : "Watchlist ready");
        toBuyNextAction.setText(resolveToBuyNextAction(items));
        toBuyCardsPanel.revalidate();
        toBuyCardsPanel.repaint();
        if (scrollPane != null && scrollY >= 0)
        {
            final int restored = scrollY;
            SwingUtilities.invokeLater(() -> scrollPane.getVerticalScrollBar().setValue(restored));
        }
    }

    private void refreshToBuySearchAsync(String query)
    {
        final String safeQuery = query == null ? "" : query.trim();
        toBuyStatus.setText(safeQuery.isEmpty() ? "Search all GE items" : "Searching \"" + safeQuery + "\"...");

        Thread worker = new Thread(() -> {
            try
            {
                List<AutoFlipPlugin.AutoFlipMarketSearchResult> results = safeQuery.isEmpty()
                    ? java.util.Collections.emptyList()
                    : plugin.searchAutoFlipMarketItems(safeQuery, 18);
                SwingUtilities.invokeLater(() -> {
                    toBuySearchResults = results == null ? java.util.Collections.emptyList() : results;
                    populateToBuyCards(plugin.getAutoFlipToBuySnapshot(), toBuySearchResults);
                });
            }
            catch (Exception error)
            {
                SwingUtilities.invokeLater(() -> {
                    toBuyStatus.setText("Search failed");
                    toBuySearchResults = java.util.Collections.emptyList();
                    populateToBuyCards(plugin.getAutoFlipToBuySnapshot(), toBuySearchResults);
                });
            }
        }, "autoflip-to-buy-search");
        worker.setDaemon(true);
        worker.start();
    }

    private String resolveToBuyNextAction(List<AutoFlipPlugin.AutoFlipToBuyItem> items)
    {
        if (items == null || items.isEmpty())
        {
            return "Search any GE item";
        }

        for (AutoFlipPlugin.AutoFlipToBuyItem item : items)
        {
            if (item != null && item.isBuyReady())
            {
                return "Buy now: " + item.getItemName() + " at " + formatGp(item.getCurrentBuyPriceGp());
            }
        }

        AutoFlipPlugin.AutoFlipToBuyItem best = null;
        for (AutoFlipPlugin.AutoFlipToBuyItem item : items)
        {
            if (item == null)
            {
                continue;
            }

            if (best == null)
            {
                best = item;
                continue;
            }

            long leftGap = Math.max(0L, item.getEffectiveBuyThresholdGp() - item.getCurrentBuyPriceGp());
            long rightGap = Math.max(0L, best.getEffectiveBuyThresholdGp() - best.getCurrentBuyPriceGp());
            if (leftGap < rightGap)
            {
                best = item;
            }
        }

        return best == null ? "Search any GE item" : "Watching " + best.getItemName() + " for a better buy";
    }
    private void ensureSmartSellSettingsLoaded()
    {
        if (smartSellSettingsLoaded)
        {
            return;
        }

        smartSellSettingsLoaded = true;

        try
        {
            if (!java.nio.file.Files.exists(SMART_SELL_SETTINGS_PATH))
            {
                return;
            }

            java.util.Properties props = new java.util.Properties();
            try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(SMART_SELL_SETTINGS_PATH, java.nio.charset.StandardCharsets.UTF_8))
            {
                props.load(reader);
            }

            for (String key : props.stringPropertyNames())
            {
                try
                {
                    int itemId = Integer.parseInt(key.trim());
                    String value = props.getProperty(key, "").trim();
                    if (itemId > 0 && !value.isEmpty())
                    {
                        smartSellInstructions.put(itemId, value);
                    }
                }
                catch (NumberFormatException ignored)
                {
                }
            }
        }
        catch (Throwable ignored)
        {
        }
    }

    private void persistSmartSellInstructions()
    {
        try
        {
            java.nio.file.Path parent = SMART_SELL_SETTINGS_PATH.getParent();
            if (parent != null)
            {
                java.nio.file.Files.createDirectories(parent);
            }

            java.util.Properties props = new java.util.Properties();
            for (java.util.Map.Entry<Integer, String> entry : smartSellInstructions.entrySet())
            {
                if (entry == null || entry.getKey() == null || entry.getKey() <= 0)
                {
                    continue;
                }

                String value = entry.getValue();
                if (value == null || value.trim().isEmpty())
                {
                    continue;
                }

                props.setProperty(String.valueOf(entry.getKey()), value.trim());
            }

            try (java.io.Writer writer = java.nio.file.Files.newBufferedWriter(
                SMART_SELL_SETTINGS_PATH,
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE))
            {
                props.store(writer, "AutoFlip Smart Sell settings");
            }
        }
        catch (Throwable ignored)
        {
        }
    }
    private boolean isReadyToSell(AutoFlipPlugin.AutoFlipInventoryItem item, String savedInstruction)
    {
        if (item == null || item.getPatienceResultGp() > 0L || "SOLD".equals(item.getStatus()) || savedInstruction == null || !savedInstruction.startsWith("Target Sell:"))
        {
            return false;
        }

        long targetSellEachGp = extractSmartSellTargetGp(savedInstruction);
        long currentGeEachGp = Math.max(0L, item.getAssessedUnitValueGp());

        return targetSellEachGp > 0L && currentGeEachGp >= targetSellEachGp;
    }

    private JPanel buildReadyToSellNotifier(java.util.List<AutoFlipPlugin.AutoFlipInventoryItem> items)
    {
        ensureSmartSellSettingsLoaded();

        if (items == null || items.isEmpty())
        {
            return null;
        }

        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBackground(new Color(24, 80, 34, 145));
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(92, 222, 72), 2),
            BorderFactory.createEmptyBorder(6, 7, 6, 7)
        ));
        panel.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH, 52));
        panel.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH, 52));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel("Ready To Sell");
        label.setForeground(new Color(92, 222, 72));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        panel.add(label);
        panel.add(Box.createHorizontalStrut(8));

        int shown = 0;
        for (AutoFlipPlugin.AutoFlipInventoryItem item : items)
        {
            if (item == null || item.getPatienceResultGp() > 0L)
            {
                continue;
            }

            String savedInstruction = smartSellInstructions.get(item.getItemId());
            if (!isReadyToSell(item, savedInstruction))
            {
                continue;
            }

            JLabel icon = new JLabel();
            icon.setOpaque(true);
            icon.setBackground(new Color(30, 120, 45, 130));
            icon.setBorder(BorderFactory.createLineBorder(new Color(92, 222, 72), 1));
            icon.setPreferredSize(new Dimension(34, 34));
            icon.setMinimumSize(new Dimension(34, 34));
            icon.setMaximumSize(new Dimension(34, 34));
            icon.setHorizontalAlignment(JLabel.CENTER);
            icon.setVerticalAlignment(JLabel.CENTER);
            icon.setToolTipText(item.getItemName() + " is ready to sell");

            java.awt.image.BufferedImage image = plugin.getAutoFlipInventoryItemImage(item.getItemId());
            if (image != null)
            {
                icon.setIcon(new javax.swing.ImageIcon(image));
            }

            panel.add(icon);
            panel.add(Box.createHorizontalStrut(5));

            shown++;
            if (shown >= 5)
            {
                break;
            }
        }

        return shown > 0 ? panel : null;
    }

    private JPanel buildReadyToBuyNotifier(java.util.List<AutoFlipPlugin.AutoFlipToBuyItem> items)
    {
        if (items == null || items.isEmpty())
        {
            return null;
        }

        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBackground(new Color(24, 80, 34, 145));
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(92, 222, 72), 2),
            BorderFactory.createEmptyBorder(6, 7, 6, 7)
        ));
        panel.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH, 56));
        panel.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH, 56));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel("Buy Ready");
        label.setForeground(new Color(92, 222, 72));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        panel.add(label);
        panel.add(Box.createHorizontalStrut(8));

        int shown = 0;
        for (AutoFlipPlugin.AutoFlipToBuyItem item : items)
        {
            if (item == null || !item.isBuyReady())
            {
                continue;
            }

            JLabel icon = new JLabel();
            icon.setOpaque(true);
            icon.setBackground(new Color(30, 120, 45, 130));
            icon.setBorder(BorderFactory.createLineBorder(new Color(92, 222, 72), 1));
            icon.setPreferredSize(new Dimension(34, 34));
            icon.setMinimumSize(new Dimension(34, 34));
            icon.setMaximumSize(new Dimension(34, 34));
            icon.setHorizontalAlignment(JLabel.CENTER);
            icon.setVerticalAlignment(JLabel.CENTER);
            icon.setToolTipText(item.getItemName() + " is ready to buy");

            java.awt.image.BufferedImage image = plugin.getAutoFlipInventoryItemImage(item.getItemId());
            if (image != null)
            {
                icon.setIcon(new javax.swing.ImageIcon(image));
            }

            panel.add(icon);
            panel.add(Box.createHorizontalStrut(5));

            shown++;
            if (shown >= 5)
            {
                break;
            }
        }

        return shown > 0 ? panel : null;
    }

    private JPanel buildReadyToSellBlock(AutoFlipPlugin.AutoFlipInventoryItem item, String savedInstruction)
    {
        long targetSellEachGp = extractSmartSellTargetGp(savedInstruction);
        long targetSellStackGp = multiplySmartSellStackGp(targetSellEachGp, item.getQuantity());

        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.setPreferredSize(new Dimension(236, 72));
        block.setMaximumSize(new Dimension(236, 72));
        block.setMinimumSize(new Dimension(236, 72));

        JLabel top = new JLabel("Ready To Sell");
        top.setForeground(new Color(92, 222, 72));
        top.setFont(top.getFont().deriveFont(Font.BOLD, 15f));
        top.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel stackLine = buildCashStackLine("Target Sell:", targetSellStackGp, true, "Target Sell Each: " + gpFormat.format(targetSellEachGp) + " gp");
        stackLine.setPreferredSize(new Dimension(230, 40));
        stackLine.setMaximumSize(new Dimension(230, 40));
        stackLine.setMinimumSize(new Dimension(230, 40));

        block.add(top);
        block.add(stackLine);
        return block;
    }
    private void cleanupCompletedSmartSellInstructions(java.util.List<AutoFlipPlugin.AutoFlipInventoryItem> items)
    {
        ensureSmartSellSettingsLoaded();

        if (items == null || items.isEmpty())
        {
            return;
        }

        boolean changed = false;

        for (AutoFlipPlugin.AutoFlipInventoryItem item : items)
        {
            if (item == null)
            {
                continue;
            }

            if ((item.getPatienceResultGp() > 0L || "SOLD".equals(item.getStatus())) && smartSellInstructions.remove(item.getItemId()) != null)
            {
                changed = true;
            }
        }

        if (changed)
        {
            persistSmartSellInstructions();
        }
    }
    private javax.swing.Icon createAutoFlipMarketLogoIcon(int size)
    {
        int s = Math.max(14, size);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(s, s, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();

        try
        {
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(18, 65, 34, 220));
            g.fillRoundRect(0, 0, s - 1, s - 1, 4, 4);
            g.setColor(new Color(92, 222, 72, 235));
            g.setStroke(new java.awt.BasicStroke(1.4f));
            g.drawRoundRect(0, 0, s - 1, s - 1, 4, 4);

            java.awt.Polygon a = new java.awt.Polygon();
            a.addPoint(s / 2, 3);
            a.addPoint(s - 4, s - 3);
            a.addPoint(s - 7, s - 3);
            a.addPoint(s - 9, s - 8);
            a.addPoint(7, s - 8);
            a.addPoint(5, s - 3);
            a.addPoint(2, s - 3);

            g.setColor(new Color(214, 139, 60, 245));
            g.fillPolygon(a);

            g.setColor(new Color(255, 222, 117, 245));
            g.drawLine(7, s - 9, s - 7, s - 9);
        }
        finally
        {
            g.dispose();
        }

        return new javax.swing.ImageIcon(img);
    }
    private JPanel buildHeldItemRow(int slot, AutoFlipPlugin.AutoFlipInventoryItem item)
    {
        JPanel p = cardPanel();
        p.setLayout(new BorderLayout(0, 0));

        boolean selectedForSmartSell = smartSellSelectedItemId == item.getItemId();
        int rowHeight = selectedForSmartSell ? 226 : 118;

        int rowWidth = INVENTORY_VIEW_WIDTH;
        p.setPreferredSize(new Dimension(rowWidth, rowHeight));
        p.setMinimumSize(new Dimension(rowWidth, rowHeight));
        p.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH, selectedForSmartSell ? 250 : 132));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (smartSellMode)
        {
            p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(selectedForSmartSell ? GOLD : new Color(120, 102, 48)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
            ));
            p.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
            p.addMouseListener(new java.awt.event.MouseAdapter()
            {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e)
                {
                    smartSellSelectedItemId = item.getItemId();
                    refreshFromPlugin();
                }
            });
        }

        ensureSmartSellSettingsLoaded();
        String savedInstruction = smartSellInstructions.get(item.getItemId());
        boolean hasSmartSellInstruction = savedInstruction != null && !savedInstruction.trim().isEmpty();
        boolean readyToSell = isReadyToSell(item, savedInstruction);

        JPanel rowContent = new JPanel();
        rowContent.setOpaque(false);
        rowContent.setLayout(new BoxLayout(rowContent, BoxLayout.Y_AXIS));
        rowContent.setPreferredSize(new Dimension(rowWidth - 8, selectedForSmartSell ? 100 : 96));
        rowContent.setMinimumSize(new Dimension(rowWidth - 8, selectedForSmartSell ? 100 : 96));
        rowContent.setMaximumSize(new Dimension(rowWidth - 8, selectedForSmartSell ? 110 : 104));
        rowContent.setAlignmentX(Component.LEFT_ALIGNMENT);

        String rightStatus = readyToSell ? "<span style='font-size:11px;line-height:12px;color:#5cde48'>READY</span>" : (hasSmartSellInstruction ? "<span style='font-size:10px;line-height:11px;color:#5cde48'>SMART</span>" : "<span style='font-size:12px;line-height:13px'>" + html(item.getStatus()) + "</span>");
        JLabel qty = new JLabel("<html><div style='text-align:right;color:#5cde48'>" + rightStatus + "<br><span style='color:#aaa'>x" + gpFormat.format(item.getQuantity()) + "</span></div></html>");
        qty.setForeground(GREEN);
        qty.setHorizontalAlignment(JLabel.RIGHT);
        qty.setAlignmentY(Component.TOP_ALIGNMENT);
        qty.setPreferredSize(new Dimension(46, 52));
        qty.setMinimumSize(new Dimension(46, 52));

        JButton market = new JButton(createAutoFlipMarketLogoIcon(18));
        market.setForeground(new Color(92, 222, 72));
        market.setBackground(new Color(30, 30, 30));
        market.setFocusPainted(false);
        market.setPreferredSize(new Dimension(18, 18));
        market.setMinimumSize(new Dimension(18, 18));
        market.setMaximumSize(new Dimension(18, 18));
        market.setMargin(new java.awt.Insets(0, 0, 0, 0));
        market.setHorizontalAlignment(JButton.CENTER);
        market.setVerticalAlignment(JButton.CENTER);
        market.setAlignmentY(Component.TOP_ALIGNMENT);
        market.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        market.setToolTipText("Open AutoFlip market page");
        market.addActionListener(e -> plugin.openAutoFlipMarketForItem(item.getItemId(), item.getItemName()));
        JButton remove = new JButton("X");
        remove.setForeground(Color.WHITE);
        remove.setBackground(RED);
        remove.setFocusPainted(false);
        remove.setPreferredSize(new Dimension(18, 18));
        remove.setMinimumSize(new Dimension(18, 18));
        remove.setMaximumSize(new Dimension(18, 18));
        remove.setMargin(new java.awt.Insets(0, 0, 0, 0));
        remove.setHorizontalAlignment(JButton.CENTER);
        remove.setVerticalAlignment(JButton.CENTER);
        remove.setAlignmentY(Component.TOP_ALIGNMENT);
        remove.setBorder(BorderFactory.createEmptyBorder(0, 0, 1, 0));
        remove.setToolTipText("Remove from AutoFlip Inventory");
        remove.addActionListener(e -> {
            plugin.removeAutoFlipInventoryItem(item.getItemId());
            smartSellInstructions.remove(item.getItemId());
            persistSmartSellInstructions();
            if (smartSellSelectedItemId == item.getItemId())
            {
                smartSellSelectedItemId = -1;
            }
            refreshFromPlugin();
        });

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        right.setPreferredSize(new Dimension(88, 52));
        right.setMinimumSize(new Dimension(88, 52));
        right.setMaximumSize(new Dimension(88, 52));
        right.add(qty);
        right.add(Box.createHorizontalStrut(1));
        right.add(market);
        right.add(Box.createHorizontalStrut(1));
        right.add(remove);

        JPanel titleRow = new JPanel(new BorderLayout(4, 0));
        titleRow.setOpaque(false);
        titleRow.setPreferredSize(new Dimension(rowWidth - 8, 30));
        titleRow.setMinimumSize(new Dimension(rowWidth - 8, 30));
        titleRow.setMaximumSize(new Dimension(rowWidth - 8, 30));

        JLabel fullNameLabel = new JLabel(item.getItemName());
        fullNameLabel.setForeground(TEXT);
        fullNameLabel.setFont(fullNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        fullNameLabel.setToolTipText(item.getItemName());

        titleRow.add(fullNameLabel, BorderLayout.CENTER);
        titleRow.add(right, BorderLayout.EAST);

        JPanel detailRow = new JPanel(new BorderLayout(6, 0));
        detailRow.setOpaque(false);
        detailRow.setPreferredSize(new Dimension(rowWidth - 8, 78));
        detailRow.setMinimumSize(new Dimension(rowWidth - 8, 78));
        detailRow.setMaximumSize(new Dimension(rowWidth - 8, 84));

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(38, 72));
        iconLabel.setMinimumSize(new Dimension(38, 72));
        iconLabel.setHorizontalAlignment(JLabel.CENTER);
        iconLabel.setVerticalAlignment(JLabel.TOP);
        iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 3));

        java.awt.image.BufferedImage itemImage = plugin.getAutoFlipInventoryItemImage(item.getItemId());
        if (itemImage != null)
        {
            iconLabel.setIcon(new javax.swing.ImageIcon(itemImage));
        }

        JPanel detailContent = new JPanel();
        detailContent.setOpaque(false);
        detailContent.setLayout(new BoxLayout(detailContent, BoxLayout.Y_AXIS));
        detailContent.setPreferredSize(new Dimension(rowWidth - 54, 72));
        detailContent.setMinimumSize(new Dimension(rowWidth - 54, 72));
        detailContent.setMaximumSize(new Dimension(rowWidth - 54, 78));

        if ("SOLD".equals(item.getStatus()) && item.getPatienceResultGp() <= 0L)
        {
            detailContent.add(buildSoldCompleteBlock());
        }
        else if (item.getPatienceResultGp() > 0L)
        {
            detailContent.add(buildPatienceEarnedBlock(item.getPatienceResultGp()));
        }
        else if (readyToSell)
        {
            detailContent.add(buildReadyToSellBlock(item, savedInstruction));
        }
        else if (hasSmartSellInstruction && savedInstruction.startsWith("Remaining Time:"))

        {

            detailContent.add(buildSmartSellTimeGoalBlock(item, savedInstruction));

        }

        else if (hasSmartSellInstruction && savedInstruction.startsWith("Target Sell:"))

        {
            long targetSellEachGp = extractSmartSellTargetGp(savedInstruction);
            long targetSellStackGp = multiplySmartSellStackGp(targetSellEachGp, item.getQuantity());
            detailContent.add(buildCashStackLine("Target Sell:", targetSellStackGp, true, "Target Sell Each: " + gpFormat.format(targetSellEachGp) + " gp"));
        }
        else if (!hasSmartSellInstruction && item.getHeldCapitalGp() > 0L)
        {
            detailContent.add(buildHoldSellValueBlock(item));
        }
        else
        {
            JLabel fallback = new JLabel(hasSmartSellInstruction ? savedInstruction : "Holding until Sell Value is achievable.");
            fallback.setForeground(hasSmartSellInstruction ? GREEN : MUTED);
            fallback.setFont(fallback.getFont().deriveFont(Font.BOLD, 11f));
            fallback.setToolTipText(fallback.getText());
            detailContent.add(fallback);
        }

        detailRow.add(iconLabel, BorderLayout.WEST);
        detailRow.add(detailContent, BorderLayout.CENTER);

        rowContent.add(titleRow);
        rowContent.add(detailRow);

        p.add(rowContent, BorderLayout.NORTH);

        if (selectedForSmartSell)
        {
            p.add(buildSmartSellEditor(item), BorderLayout.CENTER);
        }

        return p;
    }

    private JPanel buildCashStackLine(String labelText, long amountGp, boolean targetSell, String tooltipText)
    {
        JPanel line = new JPanel();
        line.setOpaque(false);
        line.setLayout(new BoxLayout(line, BoxLayout.X_AXIS));
        line.setPreferredSize(new Dimension(260, 52));
        line.setMaximumSize(new Dimension(260, 52));
        line.setMinimumSize(new Dimension(260, 52));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.setBorder(BorderFactory.createEmptyBorder(targetSell ? 8 : 2, 0, 0, 0));

        JLabel label = new JLabel(labelText);
        label.setForeground(targetSell ? TEXT : MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, targetSell ? 12f : 11f));

        JLabel stack = new JLabel();
        stack.setOpaque(false);
        stack.setBorder(BorderFactory.createEmptyBorder());
        stack.setHorizontalAlignment(JLabel.LEFT);
        stack.setVerticalAlignment(JLabel.CENTER);
        stack.setIconTextGap(0);
        stack.setToolTipText(tooltipText);

        java.awt.image.BufferedImage coins = plugin.getAutoFlipCoinStackImage(amountGp);
        if (coins != null)
        {
            javax.swing.ImageIcon icon = new javax.swing.ImageIcon(coins);
            stack.setIcon(icon);
            stack.setText("");

            int stackWidth = Math.max(84, icon.getIconWidth() + 14);
            int stackHeight = Math.max(42, icon.getIconHeight() + 10);
            Dimension stackSize = new Dimension(stackWidth, stackHeight);
            stack.setPreferredSize(stackSize);
            stack.setMinimumSize(stackSize);
            stack.setMaximumSize(stackSize);
        }
        else
        {
            stack.setText(formatSmartSellCashStackLabel(amountGp));
            stack.setForeground(amountGp >= 10_000_000L ? GREEN : GOLD);
            stack.setFont(stack.getFont().deriveFont(Font.BOLD, 12f));

            Dimension fallbackSize = new Dimension(90, 42);
            stack.setPreferredSize(fallbackSize);
            stack.setMinimumSize(fallbackSize);
            stack.setMaximumSize(fallbackSize);
        }

        line.add(label);
        line.add(Box.createHorizontalStrut(targetSell ? 2 : 3));
        line.add(stack);
        return line;
    }

    private JPanel buildSoldCompleteBlock()
    {
        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.setPreferredSize(new Dimension(236, 72));
        block.setMaximumSize(new Dimension(236, 72));
        block.setMinimumSize(new Dimension(236, 72));

        JLabel top = new JLabel("Sold");
        top.setForeground(GREEN);
        top.setFont(top.getFont().deriveFont(Font.BOLD, 15f));
        top.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel middle = new JLabel("No extra profit earned.");
        middle.setForeground(TEXT);
        middle.setFont(middle.getFont().deriveFont(Font.BOLD, 11f));
        middle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel bottom = new JLabel("AutoFlip closed this hold.");
        bottom.setForeground(MUTED);
        bottom.setFont(bottom.getFont().deriveFont(Font.BOLD, 10f));
        bottom.setAlignmentX(Component.LEFT_ALIGNMENT);

        block.add(top);
        block.add(Box.createVerticalStrut(4));
        block.add(middle);
        block.add(bottom);
        return block;
    }
    private JPanel buildPatienceEarnedBlock(long earnedGp)
    {
        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.setPreferredSize(new Dimension(236, 72));
        block.setMaximumSize(new Dimension(236, 72));
        block.setMinimumSize(new Dimension(236, 72));

        JLabel top = new JLabel("Your patience has earned you");
        top.setForeground(TEXT);
        top.setFont(top.getFont().deriveFont(Font.BOLD, 11f));
        top.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel stackLine = buildCashStackLine("", Math.max(0L, earnedGp), false, "Extra earned: " + gpFormat.format(Math.max(0L, earnedGp)) + " gp");
        stackLine.setPreferredSize(new Dimension(230, 38));
        stackLine.setMaximumSize(new Dimension(230, 38));
        stackLine.setMinimumSize(new Dimension(230, 38));

        JLabel bottom = new JLabel("extra.");
        bottom.setForeground(TEXT);
        bottom.setFont(bottom.getFont().deriveFont(Font.BOLD, 11f));
        bottom.setAlignmentX(Component.LEFT_ALIGNMENT);

        block.add(top);
        block.add(stackLine);
        block.add(bottom);
        return block;
    }

    private JPanel buildToBuySearchResultRow(AutoFlipPlugin.AutoFlipMarketSearchResult result)
    {
        return buildToBuySearchResultRow(result, INVENTORY_VIEW_WIDTH);
    }

    private JPanel buildToBuySearchResultRow(AutoFlipPlugin.AutoFlipMarketSearchResult result, int width)
    {
        boolean popupRow = width > INVENTORY_VIEW_WIDTH;
        JPanel p = popupRow ? popupCardPanel() : cardPanel();
        p.setLayout(new BorderLayout(0, 0));
        if (!popupRow)
        {
            p.setBackground(CARD);
        }
        int safeWidth = Math.max(360, width);
        boolean localSearchRow = "local_item_search".equalsIgnoreCase(result.getPriceSource());
        boolean selected = toBuySearchWindowSelectedItemId == result.getItemId();
        int rowHeight = localSearchRow ? (selected ? 164 : 150) : 100;
        p.setPreferredSize(new Dimension(safeWidth, rowHeight));
        p.setMinimumSize(new Dimension(safeWidth, rowHeight));
        p.setMaximumSize(new Dimension(safeWidth, rowHeight + 8));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(selected ? GREEN : BORDER, 1),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JPanel rowContent = new JPanel();
        rowContent.setOpaque(false);
        rowContent.setLayout(new BoxLayout(rowContent, BoxLayout.Y_AXIS));
        rowContent.setPreferredSize(new Dimension(safeWidth - 8, localSearchRow ? (selected ? 144 : 130) : 92));
        rowContent.setMinimumSize(new Dimension(safeWidth - 8, localSearchRow ? (selected ? 144 : 130) : 92));
        rowContent.setMaximumSize(new Dimension(safeWidth - 8, localSearchRow ? (selected ? 144 : 130) : 104));
        rowContent.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel titleRow = new JPanel(new BorderLayout(4, 0));
        titleRow.setOpaque(false);
        titleRow.setPreferredSize(new Dimension(safeWidth - 8, localSearchRow ? 28 : 26));
        titleRow.setMaximumSize(new Dimension(safeWidth - 8, localSearchRow ? 28 : 26));

        JLabel fullNameLabel = new JLabel(result.getItemName());
        fullNameLabel.setForeground(TEXT);
        fullNameLabel.setFont(fullNameLabel.getFont().deriveFont(Font.BOLD, localSearchRow ? 15f : 14f));
        titleRow.add(fullNameLabel, BorderLayout.WEST);

        JLabel qty = new JLabel(localSearchRow
            ? "<html><div style='text-align:right;color:#5cde48'><span style='font-size:11px'>MATCH</span><br><span style='color:#aaa'>" + html(result.getSlug()) + "</span></div></html>"
            : "<html><div style='text-align:right;color:#5cde48'><span style='font-size:11px'>SEARCH</span><br><span style='color:#aaa'>" + html(result.getSlug()) + "</span></div></html>");
        qty.setHorizontalAlignment(JLabel.RIGHT);
        qty.setPreferredSize(new Dimension(localSearchRow ? 140 : 110, 30));
        titleRow.add(qty, BorderLayout.EAST);

        JPanel detailRow = new JPanel(new BorderLayout(6, 0));
        detailRow.setOpaque(false);
        detailRow.setPreferredSize(new Dimension(safeWidth - 8, localSearchRow ? (selected ? 110 : 96) : 58));
        detailRow.setMaximumSize(new Dimension(safeWidth - 8, localSearchRow ? (selected ? 110 : 96) : 58));

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(localSearchRow ? 52 : 38, localSearchRow ? 60 : 52));
        iconLabel.setHorizontalAlignment(JLabel.CENTER);
        iconLabel.setVerticalAlignment(JLabel.TOP);
        java.awt.image.BufferedImage itemImage = plugin.getAutoFlipInventoryItemImage(result.getItemId());
        if (itemImage != null)
        {
            iconLabel.setIcon(new javax.swing.ImageIcon(itemImage));
        }

        JPanel detailContent = new JPanel();
        detailContent.setOpaque(false);
        detailContent.setLayout(new BoxLayout(detailContent, BoxLayout.Y_AXIS));
        detailContent.setPreferredSize(new Dimension(safeWidth - 54, localSearchRow ? (selected ? 100 : 88) : 52));
        detailContent.setMaximumSize(new Dimension(safeWidth - 54, localSearchRow ? (selected ? 100 : 88) : 52));

        JLabel top = new JLabel(localSearchRow ? "Ready to add" : "Buy " + formatGp(Math.max(0L, result.getBuyPriceGp())));
        top.setForeground(GREEN);
        top.setFont(top.getFont().deriveFont(Font.BOLD, localSearchRow ? 13f : 12f));
        top.setToolTipText(localSearchRow ? "Local GE catalog match" : "Execution buy price");

        JLabel bottom = new JLabel(localSearchRow ? html(result.getSlug().isEmpty() ? ("item #" + result.getItemId()) : result.getSlug()) : "Sell " + formatGp(Math.max(0L, result.getSellPriceGp())));
        bottom.setForeground(localSearchRow ? TEXT : MUTED);
        bottom.setFont(bottom.getFont().deriveFont(Font.BOLD, localSearchRow ? 12f : 11f));
        bottom.setToolTipText(localSearchRow ? "Select this row or press Enter to add it" : result.getMarketUrl());

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));

        JButton watch = button("Add & Edit");
        watch.setPreferredSize(new Dimension(localSearchRow ? 172 : 112, localSearchRow ? 32 : 26));
        watch.setMinimumSize(new Dimension(localSearchRow ? 172 : 112, localSearchRow ? 32 : 26));
        watch.setMaximumSize(new Dimension(localSearchRow ? 172 : 112, localSearchRow ? 32 : 26));
        watch.addActionListener(e -> {
            plugin.addAutoFlipToBuyItem(result.getItemId(), result.getItemName());
            toBuySelectedItemId = result.getItemId();
            refreshToBuySearchWindowAfterMutation();
        });

        JButton open = button("Open");
        open.setPreferredSize(new Dimension(localSearchRow ? 100 : 60, localSearchRow ? 32 : 26));
        open.setMinimumSize(new Dimension(localSearchRow ? 100 : 60, localSearchRow ? 32 : 26));
        open.setMaximumSize(new Dimension(localSearchRow ? 100 : 60, localSearchRow ? 32 : 26));
        open.addActionListener(e -> plugin.openAutoFlipMarketForItem(result.getItemId(), result.getItemName()));

        actions.add(watch);
        actions.add(Box.createHorizontalStrut(6));
        actions.add(open);

        detailContent.add(top);
        detailContent.add(bottom);
        detailContent.add(actions);

        detailRow.add(iconLabel, BorderLayout.WEST);
        detailRow.add(detailContent, BorderLayout.CENTER);
        rowContent.add(titleRow);
        if (localSearchRow)
        {
            rowContent.add(space(4));
        }
        rowContent.add(detailRow);
        p.add(rowContent, BorderLayout.NORTH);

        p.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                setToBuySearchWindowSelectionByItemId(result.getItemId());
                if (e.getClickCount() >= 2)
                {
                    activateSelectedToBuySearchWindowResult();
                }
            }
        });
        return p;
    }

    private void openToBuySearchWindow()
    {
        if (toBuySearchWindowDialog != null && toBuySearchWindowDialog.isShowing())
        {
            toBuySearchWindowDialog.toFront();
            toBuySearchWindowDialog.requestFocus();
            return;
        }

        Window parent = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(parent, "AutoFlip GE Search", Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());
        dialog.setMinimumSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH, 760));
        dialog.setPreferredSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH, 800));
        dialog.getContentPane().setBackground(BG);
        JComponent content = buildToBuySearchWindowContent(dialog);
        dialog.add(content, BorderLayout.CENTER);
        dialog.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosed(WindowEvent e)
            {
                if (toBuySearchWindowDebounceTimer != null)
                {
                    toBuySearchWindowDebounceTimer.stop();
                }
                toBuySearchWindowDialog = null;
            }

            @Override
            public void windowClosing(WindowEvent e)
            {
                if (toBuySearchWindowDebounceTimer != null)
                {
                    toBuySearchWindowDebounceTimer.stop();
                }
                toBuySearchWindowDialog = null;
            }
        });
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        toBuySearchWindowDialog = dialog;
        dialog.setVisible(true);
        SwingUtilities.invokeLater(() -> toBuySearchWindowField.requestFocusInWindow());
    }

    private JPanel buildToBuySearchWindowContent(JDialog dialog)
    {
        JPanel root = new PopupBackgroundPanel(loadToBuyPopupBackgroundImage());
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.setLayout(new BorderLayout(0, 10));

        JPanel top = new PopupSurfacePanel(new Color(10, 10, 10, 225), 8);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));

        JPanel titleRow = new JPanel(new BorderLayout(8, 0));
        titleRow.setOpaque(false);
        JLabel title = new JLabel("AUTOFLIP GE ADD & EDIT");
        title.setForeground(TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JLabel subtitle = new JLabel("Type an item name to add it to To-Buy or edit its settings.");
        subtitle.setForeground(MUTED);
        subtitle.setFont(subtitle.getFont().deriveFont(Font.BOLD, 11f));
        titleRow.add(title, BorderLayout.WEST);
        toBuySearchWindowStatus.setForeground(MUTED);
        toBuySearchWindowStatus.setFont(toBuySearchWindowStatus.getFont().deriveFont(Font.BOLD, 11f));
        titleRow.add(toBuySearchWindowStatus, BorderLayout.EAST);
        top.add(titleRow);
        top.add(space(3));
        top.add(subtitle);

        JPanel searchRow = rowPanel();
        searchRow.setOpaque(false);
        searchRow.setPreferredSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH - 40, 30));
        searchRow.setMinimumSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH - 40, 30));
        searchRow.setMaximumSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH - 40, 30));

        JLabel searchLabel = new JLabel("Search");
        searchLabel.setForeground(MUTED);
        searchLabel.setFont(searchLabel.getFont().deriveFont(Font.BOLD, 11f));

        toBuySearchWindowField.setPreferredSize(new Dimension(420, 28));
        toBuySearchWindowField.setMinimumSize(new Dimension(420, 28));
        toBuySearchWindowField.setMaximumSize(new Dimension(620, 28));
        toBuySearchWindowField.setBackground(new Color(20, 20, 20));
        toBuySearchWindowField.setForeground(TEXT);
        toBuySearchWindowField.setCaretColor(GREEN);
        toBuySearchWindowField.setSelectionColor(new Color(55, 100, 48));
        toBuySearchWindowField.setSelectedTextColor(Color.WHITE);
        toBuySearchWindowField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(105, 96, 79)),
            BorderFactory.createEmptyBorder(3, 7, 3, 7)
        ));
        toBuySearchWindowField.setToolTipText("Type to search the full GE catalog");
        toBuySearchWindowField.addActionListener(e -> refreshToBuySearchWindowAsync(toBuySearchWindowField.getText()));
        toBuySearchWindowField.getDocument().addDocumentListener(new DocumentListener()
        {
            private void schedule()
            {
                if (toBuySearchWindowDebounceTimer == null)
                {
                    toBuySearchWindowDebounceTimer = new javax.swing.Timer(60, evt -> refreshToBuySearchWindowAsync(toBuySearchWindowField.getText()));
                    toBuySearchWindowDebounceTimer.setRepeats(false);
                }
                toBuySearchWindowDebounceTimer.restart();
            }

            @Override
            public void insertUpdate(DocumentEvent e)
            {
                schedule();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                schedule();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                schedule();
            }
        });

        JButton go = button("Search");
        go.setPreferredSize(new Dimension(126, 30));
        go.setMinimumSize(new Dimension(126, 30));
        go.setMaximumSize(new Dimension(126, 30));
        go.addActionListener(e -> refreshToBuySearchWindowAsync(toBuySearchWindowField.getText()));

        JButton clear = button("Clear");
        clear.setPreferredSize(new Dimension(126, 30));
        clear.setMinimumSize(new Dimension(126, 30));
        clear.setMaximumSize(new Dimension(126, 30));
            clear.addActionListener(e -> {
            toBuySearchWindowField.setText("");
            toBuySearchWindowResults = java.util.Collections.emptyList();
            toBuySearchWindowSelectedIndex = -1;
            toBuySearchWindowSelectedItemId = -1;
            refreshToBuySearchWindowViews();
            toBuySearchWindowStatus.setText("Search any GE item");
        });

        toBuySearchWindowField.addActionListener(e ->
        {
            refreshToBuySearchWindowAsync(toBuySearchWindowField.getText());
            activateTopToBuySearchWindowResult();
        });

        searchRow.add(searchLabel);
        searchRow.add(Box.createHorizontalStrut(8));
        searchRow.add(toBuySearchWindowField);
        searchRow.add(Box.createHorizontalStrut(6));
        searchRow.add(go);
        searchRow.add(Box.createHorizontalStrut(6));
        searchRow.add(clear);
        searchRow.add(Box.createHorizontalGlue());

        toBuySearchWindowResultsPanel.setOpaque(false);
        toBuySearchWindowResultsPanel.setLayout(new BoxLayout(toBuySearchWindowResultsPanel, BoxLayout.Y_AXIS));
        toBuySearchWindowResultsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        top.add(searchRow);
        root.add(top, BorderLayout.NORTH);

        JPanel leftColumn = new JPanel();
        leftColumn.setOpaque(false);
        leftColumn.setLayout(new BorderLayout(0, 8));
        leftColumn.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 2, new Color(92, 222, 72, 185)));

        JScrollPane leftScroll = new JScrollPane(toBuySearchWindowResultsPanel);
        leftScroll.setBorder(BorderFactory.createEmptyBorder());
        leftScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        leftScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        leftScroll.getVerticalScrollBar().setUnitIncrement(18);
        leftScroll.getVerticalScrollBar().setBlockIncrement(90);
        leftScroll.setPreferredSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 34, 430));
        leftScroll.setMaximumSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 34, 560));
        leftScroll.setOpaque(false);
        leftScroll.getViewport().setOpaque(false);
        leftColumn.add(leftScroll, BorderLayout.CENTER);

        JPanel leftFooter = new JPanel(new BorderLayout());
        leftFooter.setOpaque(false);
        JLabel hint = new JLabel("Results update as you type. Click Add & Edit to watch or edit the item.");
        hint.setForeground(MUTED);
        hint.setFont(hint.getFont().deriveFont(Font.BOLD, 11f));
        leftFooter.add(hint, BorderLayout.WEST);
        leftColumn.add(leftFooter, BorderLayout.SOUTH);

        JPanel rightColumn = new JPanel();
        rightColumn.setOpaque(false);
        rightColumn.setLayout(new BorderLayout(0, 8));
        rightColumn.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0, 0, 0, 110)));

        JPanel watchlistHeader = new JPanel(new BorderLayout());
        watchlistHeader.setOpaque(false);
        JLabel watchTitle = new JLabel("CURRENT TO-BUY");
        watchTitle.setForeground(TEXT);
        watchTitle.setFont(watchTitle.getFont().deriveFont(Font.BOLD, 15f));
        JLabel watchStatus = new JLabel("Live watchlist");
        watchStatus.setForeground(MUTED);
        watchStatus.setFont(watchStatus.getFont().deriveFont(Font.BOLD, 11f));
        watchlistHeader.add(watchTitle, BorderLayout.WEST);
        watchlistHeader.add(watchStatus, BorderLayout.EAST);
        rightColumn.add(watchlistHeader, BorderLayout.NORTH);

        toBuySearchWindowWatchlistPanel.setOpaque(false);
        toBuySearchWindowWatchlistPanel.setLayout(new BoxLayout(toBuySearchWindowWatchlistPanel, BoxLayout.Y_AXIS));
        toBuySearchWindowWatchlistPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JScrollPane watchlistScroll = new JScrollPane(toBuySearchWindowWatchlistPanel);
        watchlistScroll.setBorder(BorderFactory.createEmptyBorder());
        watchlistScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        watchlistScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        watchlistScroll.getVerticalScrollBar().setUnitIncrement(18);
        watchlistScroll.getVerticalScrollBar().setBlockIncrement(90);
        watchlistScroll.setPreferredSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 34, 430));
        watchlistScroll.setMaximumSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 34, 560));
        watchlistScroll.setOpaque(false);
        watchlistScroll.getViewport().setOpaque(false);
        rightColumn.add(watchlistScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftColumn, rightColumn);
        split.setOpaque(false);
        split.setBorder(BorderFactory.createEmptyBorder());
        split.setDividerSize(8);
        split.setResizeWeight(0.42);
        split.setDividerLocation(0.42);
        dialog.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentShown(ComponentEvent e)
            {
                SwingUtilities.invokeLater(() -> {
                    if (split.getWidth() > 0)
                    {
                        split.setDividerLocation(0.42);
                    }
                });
            }
        });
        root.add(split, BorderLayout.CENTER);

        installToBuySearchWindowKeyBindings(root, leftScroll);
        refreshToBuySearchWindowViews();
        return root;
    }

    private static BufferedImage loadToBuyPopupBackgroundImage()
    {
        try
        {
            URL resource = AutoFlipSidePanel.class.getResource(TO_BUY_POPUP_BACKGROUND_RESOURCE);
            if (resource != null)
            {
                return ImageIO.read(resource);
            }
        }
        catch (IOException ignored)
        {
        }

        return null;
    }

    private void refreshToBuySearchWindowViews()
    {
        refreshToBuySearchWindowResults();
        refreshToBuySearchWindowWatchlist();
    }

    private void refreshToBuySearchWindowAsync(String query)
    {
        final String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isEmpty())
        {
            toBuySearchWindowResults = java.util.Collections.emptyList();
            toBuySearchWindowStatus.setText("Search any GE item");
            refreshToBuySearchWindowResults();
            return;
        }

        toBuySearchWindowStatus.setText("Searching \"" + safeQuery + "\"...");
        try
        {
            List<AutoFlipPlugin.AutoFlipMarketSearchResult> results = plugin.searchAutoFlipMarketItemsLocally(safeQuery, 18);
            toBuySearchWindowResults = results == null ? java.util.Collections.emptyList() : results;
            toBuySearchWindowStatus.setText(toBuySearchWindowResults.isEmpty()
                ? "No matches"
                : toBuySearchWindowResults.size() + " results");
            refreshToBuySearchWindowResults();
        }
        catch (Exception error)
        {
            toBuySearchWindowResults = java.util.Collections.emptyList();
            toBuySearchWindowStatus.setText("Search failed");
            refreshToBuySearchWindowResults();
        }
    }

    private void refreshToBuySearchWindowResults()
    {
        toBuySearchWindowResultsPanel.removeAll();
        List<AutoFlipPlugin.AutoFlipMarketSearchResult> results = toBuySearchWindowResults == null
            ? java.util.Collections.emptyList()
            : toBuySearchWindowResults;

        if (results.isEmpty())
        {
            toBuySearchWindowSelectedIndex = -1;
            toBuySearchWindowSelectedItemId = -1;
        }
        else if (toBuySearchWindowSelectedIndex < 0 || toBuySearchWindowSelectedIndex >= results.size())
        {
            toBuySearchWindowSelectedIndex = 0;
            toBuySearchWindowSelectedItemId = results.get(0).getItemId();
        }
        else
        {
            AutoFlipPlugin.AutoFlipMarketSearchResult selectedResult = results.get(toBuySearchWindowSelectedIndex);
            if (selectedResult != null)
            {
                toBuySearchWindowSelectedItemId = selectedResult.getItemId();
            }
        }

        if (results.isEmpty())
        {
            toBuySearchWindowResultsPanel.add(buildToBuyPopupEmptyRow());
        }
        else
        {
            for (int i = 0; i < results.size(); i++)
            {
                AutoFlipPlugin.AutoFlipMarketSearchResult result = results.get(i);
                if (result != null)
                {
                    toBuySearchWindowResultsPanel.add(buildToBuySearchResultRow(result, TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 44));
                    toBuySearchWindowResultsPanel.add(space(6));
                }
            }
        }

        toBuySearchWindowResultsPanel.revalidate();
        toBuySearchWindowResultsPanel.repaint();
    }

    private void refreshToBuySearchWindowWatchlist()
    {
        toBuySearchWindowWatchlistPanel.removeAll();
        java.util.List<AutoFlipPlugin.AutoFlipToBuyItem> watchlist = plugin.getAutoFlipToBuySnapshot();
        if (watchlist == null || watchlist.isEmpty())
        {
            JLabel empty = new JLabel("No To-Buy items yet.");
            empty.setForeground(MUTED);
            empty.setFont(empty.getFont().deriveFont(Font.BOLD, 11f));
            empty.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            toBuySearchWindowWatchlistPanel.add(empty);
        }
        else
        {
            for (AutoFlipPlugin.AutoFlipToBuyItem item : watchlist)
            {
                if (item != null)
                {
                    toBuySearchWindowWatchlistPanel.add(buildToBuySearchWindowItemRow(item));
                    toBuySearchWindowWatchlistPanel.add(space(6));
                }
            }
        }

        toBuySearchWindowWatchlistPanel.revalidate();
        toBuySearchWindowWatchlistPanel.repaint();
    }

    private void refreshToBuySearchWindowAfterMutation()
    {
        refreshFromPlugin();
        if (toBuySearchWindowDialog != null && toBuySearchWindowDialog.isShowing())
        {
            refreshToBuySearchWindowViews();
        }
    }

    private JPanel buildToBuySearchWindowItemRow(AutoFlipPlugin.AutoFlipToBuyItem item)
    {
        JPanel p = popupCardPanel();
        p.setLayout(new BorderLayout(0, 0));

        boolean selected = toBuySearchWindowEditorItemId == item.getItemId();
        int rowHeight = selected ? 336 : 96;
        p.setPreferredSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 44, rowHeight));
        p.setMinimumSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 44, rowHeight));
        p.setMaximumSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 44, selected ? 348 : 100));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(item.isBuyReady() ? GREEN : BORDER),
            BorderFactory.createEmptyBorder(5, 7, 5, 7)
        ));

        JPanel rowContent = new JPanel();
        rowContent.setOpaque(false);
        rowContent.setLayout(new BoxLayout(rowContent, BoxLayout.Y_AXIS));
        rowContent.setPreferredSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 52, selected ? 322 : 86));
        rowContent.setMinimumSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 52, selected ? 322 : 86));
        rowContent.setMaximumSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 52, selected ? 332 : 90));

        JPanel titleRow = new JPanel(new BorderLayout(6, 0));
        titleRow.setOpaque(false);
        titleRow.setPreferredSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 52, 44));
        titleRow.setMaximumSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 52, 44));

        JLabel fullNameLabel = new JLabel(item.getItemName());
        fullNameLabel.setForeground(TEXT);
        fullNameLabel.setFont(fullNameLabel.getFont().deriveFont(Font.BOLD, 13f));
        titleRow.add(fullNameLabel, BorderLayout.CENTER);

        JLabel stateTop = new JLabel(html(item.getSignalLabel()));
        stateTop.setForeground(GREEN);
        stateTop.setFont(stateTop.getFont().deriveFont(Font.BOLD, 11f));
        stateTop.setHorizontalAlignment(JLabel.RIGHT);

        JLabel stateBottom = new JLabel("x" + gpFormat.format(item.getQuantity()));
        stateBottom.setForeground(MUTED);
        stateBottom.setFont(stateBottom.getFont().deriveFont(Font.BOLD, 10f));
        stateBottom.setHorizontalAlignment(JLabel.RIGHT);

        JPanel actionRow = new JPanel();
        actionRow.setOpaque(false);
        actionRow.setLayout(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actionRow.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JButton gear = new JButton("⚙");
        gear.setToolTipText("Edit this To-Buy item");
        gear.setPreferredSize(new Dimension(22, 22));
        gear.setMinimumSize(new Dimension(22, 22));
        gear.setMaximumSize(new Dimension(22, 22));
        gear.setMargin(new Insets(0, 0, 0, 0));
        gear.setOpaque(false);
        gear.setContentAreaFilled(false);
        gear.setBorderPainted(false);
        gear.setFocusPainted(false);
        gear.setForeground(GREEN);
        gear.setFont(gear.getFont().deriveFont(Font.BOLD, 16f));
        gear.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        gear.addActionListener(e -> {
            toBuySearchWindowEditorItemId = selected ? -1 : item.getItemId();
            refreshToBuySearchWindowViews();
        });

        JButton remove = toBuyRemoveButton();
        remove.setToolTipText("Remove from To-Buy");
        remove.addActionListener(e -> {
            plugin.removeAutoFlipToBuyItem(item.getItemId());
            if (toBuySearchWindowEditorItemId == item.getItemId())
            {
                toBuySearchWindowEditorItemId = -1;
            }
            refreshToBuySearchWindowAfterMutation();
        });

        actionRow.add(gear);
        actionRow.add(remove);

        JPanel actionColumn = new JPanel();
        actionColumn.setOpaque(false);
        actionColumn.setLayout(new BoxLayout(actionColumn, BoxLayout.Y_AXIS));
        actionColumn.setPreferredSize(new Dimension(104, 48));
        actionColumn.setMinimumSize(new Dimension(104, 48));
        actionColumn.setMaximumSize(new Dimension(104, 48));
        actionColumn.add(stateTop);
        actionColumn.add(stateBottom);
        actionColumn.add(actionRow);
        titleRow.add(actionColumn, BorderLayout.EAST);

        JPanel detailRow = new JPanel(new BorderLayout(6, 0));
        detailRow.setOpaque(false);
        detailRow.setPreferredSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 52, 58));
        detailRow.setMaximumSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 52, 60));

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(38, 42));
        iconLabel.setHorizontalAlignment(JLabel.CENTER);
        iconLabel.setVerticalAlignment(JLabel.TOP);
        java.awt.image.BufferedImage itemImage = plugin.getAutoFlipInventoryItemImage(item.getItemId());
        if (itemImage != null)
        {
            iconLabel.setIcon(new javax.swing.ImageIcon(itemImage));
        }

        JPanel detailContent = new JPanel();
        detailContent.setOpaque(false);
        detailContent.setLayout(new BoxLayout(detailContent, BoxLayout.Y_AXIS));
        detailContent.setPreferredSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 98, 58));
        detailContent.setMaximumSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 98, 60));
        detailContent.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel requirement = new JLabel(item.getRequirementLabel());
        requirement.setForeground(item.isBuyReady() ? GREEN : GOLD);
        requirement.setFont(requirement.getFont().deriveFont(Font.BOLD, 11f));

        JLabel prices = new JLabel("Now " + formatGp(item.getCurrentBuyPriceGp()) + " · Good " + formatGp(item.getEffectiveBuyThresholdGp()));
        prices.setForeground(MUTED);
        prices.setFont(prices.getFont().deriveFont(Font.BOLD, 10f));

        String totalCostLabel = item.getTargetBuyTotalCostLabel();
        JLabel totalCost = null;
        if (totalCostLabel != null && !totalCostLabel.isEmpty())
        {
            totalCost = new JLabel(totalCostLabel);
            totalCost.setForeground(GREEN);
            totalCost.setFont(totalCost.getFont().deriveFont(Font.BOLD, 10f));
        }

        detailContent.add(requirement);
        detailContent.add(prices);
        if (totalCost != null)
        {
            detailContent.add(totalCost);
        }

        detailRow.add(iconLabel, BorderLayout.WEST);
        detailRow.add(detailContent, BorderLayout.CENTER);
        rowContent.add(titleRow);
        rowContent.add(detailRow);

        if (selected)
        {
            rowContent.add(buildToBuyEditor(item, () -> {
                toBuySearchWindowEditorItemId = -1;
                refreshToBuySearchWindowAfterMutation();
            }, TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 52));
        }

        p.add(rowContent, BorderLayout.NORTH);
        return p;
    }

    private void installToBuySearchWindowKeyBindings(JComponent root, JScrollPane scroll)
    {
        InputMap inputMap = root.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actionMap = root.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "autoflip_to_buy_up");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "autoflip_to_buy_down");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "autoflip_to_buy_enter");
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "autoflip_to_buy_escape");

        actionMap.put("autoflip_to_buy_up", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                moveToBuySearchWindowSelection(-1);
            }
        });
        actionMap.put("autoflip_to_buy_down", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                moveToBuySearchWindowSelection(1);
            }
        });
        actionMap.put("autoflip_to_buy_enter", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                activateSelectedToBuySearchWindowResult();
            }
        });
        actionMap.put("autoflip_to_buy_escape", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                if (toBuySearchWindowDialog != null)
                {
                    toBuySearchWindowDialog.dispose();
                }
            }
        });

        root.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_UP)
                {
                    moveToBuySearchWindowSelection(-1);
                }
                else if (e.getKeyCode() == KeyEvent.VK_DOWN)
                {
                    moveToBuySearchWindowSelection(1);
                }
                else if (e.getKeyCode() == KeyEvent.VK_ENTER)
                {
                    activateSelectedToBuySearchWindowResult();
                }
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE && toBuySearchWindowDialog != null)
                {
                    toBuySearchWindowDialog.dispose();
                }
            }
        });
        scroll.getViewport().setFocusable(false);
    }

    private void setToBuySearchWindowSelectionByItemId(int itemId)
    {
        List<AutoFlipPlugin.AutoFlipMarketSearchResult> results = toBuySearchWindowResults == null
            ? java.util.Collections.emptyList()
            : toBuySearchWindowResults;
        int index = -1;
        for (int i = 0; i < results.size(); i++)
        {
            AutoFlipPlugin.AutoFlipMarketSearchResult result = results.get(i);
            if (result != null && result.getItemId() == itemId)
            {
                index = i;
                break;
            }
        }
        if (index >= 0)
        {
            toBuySearchWindowSelectedIndex = index;
            toBuySearchWindowSelectedItemId = itemId;
            refreshToBuySearchWindowResults();
        }
    }

    private void moveToBuySearchWindowSelection(int delta)
    {
        List<AutoFlipPlugin.AutoFlipMarketSearchResult> results = toBuySearchWindowResults == null
            ? java.util.Collections.emptyList()
            : toBuySearchWindowResults;
        if (results.isEmpty())
        {
            return;
        }
        if (toBuySearchWindowSelectedIndex < 0)
        {
            toBuySearchWindowSelectedIndex = 0;
        }
        else
        {
            toBuySearchWindowSelectedIndex = Math.max(0, Math.min(results.size() - 1, toBuySearchWindowSelectedIndex + delta));
        }
        AutoFlipPlugin.AutoFlipMarketSearchResult selectedResult = results.get(toBuySearchWindowSelectedIndex);
        toBuySearchWindowSelectedItemId = selectedResult == null ? -1 : selectedResult.getItemId();
        refreshToBuySearchWindowResults();
    }

    private void activateTopToBuySearchWindowResult()
    {
        List<AutoFlipPlugin.AutoFlipMarketSearchResult> results = toBuySearchWindowResults == null
            ? java.util.Collections.emptyList()
            : toBuySearchWindowResults;
        if (!results.isEmpty())
        {
            AutoFlipPlugin.AutoFlipMarketSearchResult result = results.get(0);
            if (result != null)
            {
                plugin.addAutoFlipToBuyItem(result.getItemId(), result.getItemName());
                toBuySelectedItemId = result.getItemId();
                refreshToBuySearchWindowAfterMutation();
            }
        }
    }

    private void activateSelectedToBuySearchWindowResult()
    {
        List<AutoFlipPlugin.AutoFlipMarketSearchResult> results = toBuySearchWindowResults == null
            ? java.util.Collections.emptyList()
            : toBuySearchWindowResults;
        if (results.isEmpty())
        {
            return;
        }
        if (toBuySearchWindowSelectedIndex < 0 || toBuySearchWindowSelectedIndex >= results.size())
        {
            toBuySearchWindowSelectedIndex = 0;
        }
        AutoFlipPlugin.AutoFlipMarketSearchResult result = results.get(toBuySearchWindowSelectedIndex);
        if (result != null)
        {
            toBuySearchWindowSelectedItemId = result.getItemId();
            plugin.addAutoFlipToBuyItem(result.getItemId(), result.getItemName());
            toBuySelectedItemId = result.getItemId();
            refreshToBuySearchWindowAfterMutation();
        }
    }

    private JPanel buildToBuyItemRow(AutoFlipPlugin.AutoFlipToBuyItem item)
    {
        JPanel p = cardPanel();
        p.setLayout(new BorderLayout(0, 0));
        p.setBackground(CARD);

        boolean selected = toBuySelectedItemId == item.getItemId();
        int rowHeight = selected ? 336 : 126;
        p.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH, rowHeight));
        p.setMinimumSize(new Dimension(INVENTORY_VIEW_WIDTH, rowHeight));
        p.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH, selected ? 352 : 142));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(item.isBuyReady() ? GREEN : BORDER),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        p.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        p.addMouseListener(new java.awt.event.MouseAdapter()
        {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e)
            {
                toBuySelectedItemId = selected ? -1 : item.getItemId();
                refreshFromPlugin();
            }
        });

        JPanel rowContent = new JPanel();
        rowContent.setOpaque(false);
        rowContent.setLayout(new BoxLayout(rowContent, BoxLayout.Y_AXIS));
        rowContent.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH - 8, selected ? 322 : 106));
        rowContent.setMinimumSize(new Dimension(INVENTORY_VIEW_WIDTH - 8, selected ? 322 : 106));
        rowContent.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH - 8, selected ? 332 : 116));

        JPanel titleRow = new JPanel(new BorderLayout(4, 0));
        titleRow.setOpaque(false);
        titleRow.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH - 8, 30));
        titleRow.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH - 8, 30));

        JLabel fullNameLabel = new JLabel(item.getItemName());
        fullNameLabel.setForeground(item.isBuyReady() ? GREEN : TEXT);
        fullNameLabel.setFont(fullNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleRow.add(fullNameLabel, BorderLayout.CENTER);

        JLabel stateLabel = new JLabel("<html><div style='text-align:right;color:#5cde48'><span style='font-size:11px'>" + html(item.getSignalLabel()) + "</span><br><span style='color:#aaa'>x" + gpFormat.format(item.getQuantity()) + "</span></div></html>");
        stateLabel.setHorizontalAlignment(JLabel.RIGHT);

        JPanel actionColumn = new JPanel();
        actionColumn.setOpaque(false);
        actionColumn.setLayout(new BoxLayout(actionColumn, BoxLayout.Y_AXIS));
        actionColumn.setAlignmentY(Component.TOP_ALIGNMENT);
        actionColumn.setPreferredSize(new Dimension(106, 48));
        actionColumn.setMinimumSize(new Dimension(106, 48));
        actionColumn.setMaximumSize(new Dimension(106, 48));

        JPanel actionRow = new JPanel();
        actionRow.setOpaque(false);
        actionRow.setLayout(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actionRow.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JButton edit = button("⚙");
        edit.setToolTipText("Edit To-Buy settings");
        edit.setPreferredSize(new Dimension(22, 22));
        edit.setMinimumSize(new Dimension(22, 22));
        edit.setMaximumSize(new Dimension(22, 22));
        edit.setMargin(new Insets(0, 0, 0, 0));
        edit.addActionListener(e -> {
            toBuySelectedItemId = item.getItemId();
            refreshToBuySearchWindowAfterMutation();
        });

        JButton remove = toBuyRemoveButton();
        remove.setToolTipText("Remove from To-Buy");
        remove.addActionListener(e -> {
            plugin.removeAutoFlipToBuyItem(item.getItemId());
            toBuySelectedItemId = -1;
            refreshToBuySearchWindowAfterMutation();
        });

        actionRow.add(edit);
        actionRow.add(remove);
        actionColumn.add(stateLabel);
        actionColumn.add(actionRow);
        titleRow.add(actionColumn, BorderLayout.EAST);

        JPanel detailRow = new JPanel(new BorderLayout(6, 0));
        detailRow.setOpaque(false);
        detailRow.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH - 8, 88));
        detailRow.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH - 8, 96));

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(38, 70));
        iconLabel.setHorizontalAlignment(JLabel.CENTER);
        iconLabel.setVerticalAlignment(JLabel.TOP);
        java.awt.image.BufferedImage itemImage = plugin.getAutoFlipInventoryItemImage(item.getItemId());
        if (itemImage != null)
        {
            iconLabel.setIcon(new javax.swing.ImageIcon(itemImage));
        }

        JPanel detailContent = new JPanel();
        detailContent.setOpaque(false);
        detailContent.setLayout(new BoxLayout(detailContent, BoxLayout.Y_AXIS));
        detailContent.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH - 54, 84));
        detailContent.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH - 54, 92));

        JLabel requirement = new JLabel(item.getRequirementLabel());
        requirement.setForeground(item.isBuyReady() ? GREEN : GOLD);
        requirement.setFont(requirement.getFont().deriveFont(Font.BOLD, 12f));
        requirement.setToolTipText(requirement.getText());

        JLabel prices = new JLabel("Now " + formatGp(item.getCurrentBuyPriceGp()) + " · Good " + formatGp(item.getEffectiveBuyThresholdGp()));
        prices.setForeground(TEXT);
        prices.setFont(prices.getFont().deriveFont(Font.BOLD, 11f));

        String totalCostLabel = item.getTargetBuyTotalCostLabel();
        JLabel totalCost = null;
        if (totalCostLabel != null && !totalCostLabel.isEmpty())
        {
            totalCost = new JLabel(totalCostLabel);
            totalCost.setForeground(GREEN);
            totalCost.setFont(totalCost.getFont().deriveFont(Font.BOLD, 10f));
        }

        JLabel source = new JLabel(item.getPriceReason().isEmpty() ? item.getPriceSource() : item.getPriceReason());
        source.setForeground(MUTED);
        source.setFont(source.getFont().deriveFont(Font.BOLD, 10f));

        detailContent.add(requirement);
        detailContent.add(prices);
        if (totalCost != null)
        {
            detailContent.add(totalCost);
        }
        detailContent.add(source);

        detailRow.add(iconLabel, BorderLayout.WEST);
        detailRow.add(detailContent, BorderLayout.CENTER);
        rowContent.add(titleRow);
        rowContent.add(detailRow);

        if (selected)
        {
            rowContent.add(buildToBuyEditor(item, () -> {
                toBuySelectedItemId = -1;
                refreshFromPlugin();
            }, INVENTORY_VIEW_WIDTH - 18));
        }

        p.add(rowContent, BorderLayout.NORTH);
        return p;
    }

    private JPanel buildToBuyEditor(AutoFlipPlugin.AutoFlipToBuyItem item, Runnable onClose)
    {
        return buildToBuyEditor(item, onClose, INVENTORY_VIEW_WIDTH - 18);
    }

    private JPanel buildToBuyEditor(AutoFlipPlugin.AutoFlipToBuyItem item, Runnable onClose, int editorWidth)
    {
        int usableWidth = Math.max(240, editorWidth);
        int rowWidth = Math.max(220, usableWidth - 2);
        Runnable closeAction = onClose == null ? () -> {} : onClose;
        plugin.logToBuyEditorTrace(
            "editor_build",
            item.getItemId(),
            "quantity=" + item.getQuantity()
                + " targetPriceGp=" + item.getTargetBuyPriceGp()
                + " buyWindowHours=" + item.getBuyWindowHours()
        );

        JPanel editor = new JPanel();
        editor.setOpaque(false);
        editor.setLayout(new BoxLayout(editor, BoxLayout.Y_AXIS));

        JPanel row1 = rowPanel();
        row1.setPreferredSize(new Dimension(rowWidth, 18));
        row1.setMaximumSize(new Dimension(rowWidth, 18));

        JLabel itemLabel = new JLabel(item.getItemName() + "  x" + gpFormat.format(item.getQuantity()));
        itemLabel.setForeground(TEXT);
        itemLabel.setFont(itemLabel.getFont().deriveFont(Font.BOLD, 11f));
        row1.add(itemLabel);

        JPanel row2 = rowPanel();
        row2.setPreferredSize(new Dimension(rowWidth, 24));
        row2.setMaximumSize(new Dimension(rowWidth, 24));

        JLabel typeLabel = new JLabel("Buy mode");
        typeLabel.setForeground(MUTED);
        typeLabel.setFont(typeLabel.getFont().deriveFont(10f));

        JComboBox<String> buyType = new JComboBox<>(new String[] {"Auto", "Specific price", "Best in window"});
        buyType.setMaximumSize(new Dimension(148, 23));
        buyType.setPreferredSize(new Dimension(148, 23));
        buyType.setFocusable(false);

        row2.add(typeLabel);
        row2.add(Box.createHorizontalStrut(6));
        row2.add(buyType);

        JPanel row3 = rowPanel();
        row3.setPreferredSize(new Dimension(rowWidth, 28));
        row3.setMaximumSize(new Dimension(rowWidth, 28));

        JPanel row4 = rowPanel();
        row4.setPreferredSize(new Dimension(rowWidth, 28));
        row4.setMaximumSize(new Dimension(rowWidth, 28));

        final JTextField[] priceFieldRef = new JTextField[1];
        final JTextField[] hoursFieldRef = new JTextField[1];
        final JTextField[] quantityFieldRef = new JTextField[1];
        final Runnable[] saveActionRef = new Runnable[1];

        Runnable rebuildValueRow = () -> {
            row3.removeAll();
            priceFieldRef[0] = null;
            hoursFieldRef[0] = null;

            String selectedType = String.valueOf(buyType.getSelectedItem());
            if ("Specific price".equals(selectedType))
            {
                JLabel priceLabel = new JLabel("Price (each)");
                priceLabel.setForeground(MUTED);
                priceLabel.setFont(priceLabel.getFont().deriveFont(10f));

                JTextField priceField = new JTextField();
                priceField.setText(getToBuyDraftValue(toBuyPriceDrafts, item.getItemId(),
                    item.getTargetBuyPriceGp() > 0L ? gpFormat.format(item.getTargetBuyPriceGp()) : ""));
                priceField.setMaximumSize(new Dimension(Math.min(180, Math.max(120, rowWidth - 142)), 23));
                priceField.setPreferredSize(new Dimension(Math.min(180, Math.max(120, rowWidth - 142)), 23));
                priceField.setToolTipText("Examples: 1234k, 1m, 1600m, 1.6b");
                priceFieldRef[0] = priceField;
                bindToBuyDraftField(priceField, item.getItemId(), toBuyPriceDrafts);
                priceField.addActionListener(evt -> {
                    if (saveActionRef[0] != null)
                    {
                        saveActionRef[0].run();
                    }
                });

                row3.add(priceLabel);
                row3.add(Box.createHorizontalStrut(6));
                row3.add(priceField);
            }
            else if ("Best in window".equals(selectedType))
            {
                JLabel hoursLabel = new JLabel("Hours");
                hoursLabel.setForeground(MUTED);
                hoursLabel.setFont(hoursLabel.getFont().deriveFont(10f));

                JTextField hoursField = new JTextField();
                hoursField.setText(getToBuyDraftValue(toBuyHoursDrafts, item.getItemId(),
                    item.getBuyWindowHours() > 0 ? String.valueOf(item.getBuyWindowHours()) : ""));
                hoursField.setMaximumSize(new Dimension(62, 23));
                hoursField.setPreferredSize(new Dimension(62, 23));
                hoursField.setToolTipText("Window hours such as 24, 72, 168");
                hoursFieldRef[0] = hoursField;
                bindToBuyDraftField(hoursField, item.getItemId(), toBuyHoursDrafts);
                hoursField.addActionListener(evt -> {
                    if (saveActionRef[0] != null)
                    {
                        saveActionRef[0].run();
                    }
                });

                row3.add(hoursLabel);
                row3.add(Box.createHorizontalStrut(6));
                row3.add(hoursField);
            }
            else
            {
                JLabel note = new JLabel("Uses auto good price");
                note.setForeground(GREEN);
                note.setFont(note.getFont().deriveFont(Font.BOLD, 10f));
                row3.add(note);
            }

            row3.add(Box.createHorizontalGlue());
            row3.revalidate();
            row3.repaint();
        };

        JLabel quantityLabel = new JLabel("Target Quantity");
        quantityLabel.setForeground(MUTED);
        quantityLabel.setFont(quantityLabel.getFont().deriveFont(10f));

        JTextField quantityField = new JTextField();
        quantityField.setText(getToBuyDraftValue(toBuyQuantityDrafts, item.getItemId(), String.valueOf(Math.max(1, item.getQuantity()))));
        quantityField.setMaximumSize(new Dimension(92, 23));
        quantityField.setPreferredSize(new Dimension(92, 23));
        quantityField.setToolTipText("Target quantity to save with this watch item");
        quantityField.setName("toBuyQuantityField");
        quantityFieldRef[0] = quantityField;
        bindToBuyDraftField(quantityField, item.getItemId(), toBuyQuantityDrafts);
        quantityField.addActionListener(evt -> {
            if (saveActionRef[0] != null)
            {
                saveActionRef[0].run();
            }
        });

        JPanel row5 = rowPanel();
        row5.setPreferredSize(new Dimension(rowWidth, 24));
        row5.setMaximumSize(new Dimension(rowWidth, 24));

        JButton autoflipQty = button("Autoflip Qty");
        autoflipQty.setPreferredSize(new Dimension(96, 23));
        autoflipQty.setMaximumSize(new Dimension(96, 23));
        autoflipQty.setToolTipText("Use the current AutoFlip quantity");
        autoflipQty.addActionListener(e -> {
            String targetQuantity = String.valueOf(Math.max(1, item.getQuantity()));
            plugin.logToBuyEditorTrace("autoflip_qty_click", item.getItemId(), "targetQuantity=" + targetQuantity);
            quantityField.setText(targetQuantity);
            quantityField.selectAll();
            quantityField.requestFocusInWindow();
        });

        row4.add(quantityLabel);
        row4.add(Box.createHorizontalStrut(6));
        row4.add(quantityField);
        row4.add(Box.createHorizontalGlue());

        row5.add(autoflipQty);
        row5.add(Box.createHorizontalGlue());

        buyType.addActionListener(e -> rebuildValueRow.run());
        if (item.getTargetBuyPriceGp() > 0L)
        {
            buyType.setSelectedItem("Specific price");
        }
        else if (item.getBuyWindowHours() > 0)
        {
            buyType.setSelectedItem("Best in window");
        }
        else
        {
            buyType.setSelectedItem("Auto");
        }
        rebuildValueRow.run();

        JPanel row6 = rowPanel();
        row6.setPreferredSize(new Dimension(rowWidth, 24));
        row6.setMaximumSize(new Dimension(rowWidth, 24));

        JButton save = button("Save");
        save.setPreferredSize(new Dimension(72, 23));
        save.setMaximumSize(new Dimension(72, 23));
        saveActionRef[0] = save::doClick;
        save.addActionListener(e -> {
            String selectedType = String.valueOf(buyType.getSelectedItem());
            int parsedQuantity = Math.max(1, item.getQuantity());
            String quantityText = quantityFieldRef[0] == null ? "" : quantityFieldRef[0].getText().trim();
            if (!quantityText.isEmpty())
            {
                try
                {
                    parsedQuantity = Math.max(1, Integer.parseInt(quantityText.replace(",", "").trim()));
                }
                catch (NumberFormatException ignored)
                {
                    parsedQuantity = Math.max(1, item.getQuantity());
                }
            }

            plugin.logToBuyEditorTrace(
                "save_clicked",
                item.getItemId(),
                "selectedType=" + selectedType
                    + " quantityText=" + quantityText
                    + " parsedQuantity=" + parsedQuantity
                    + " priceText=" + (priceFieldRef[0] == null ? "" : priceFieldRef[0].getText())
                    + " hoursText=" + (hoursFieldRef[0] == null ? "" : hoursFieldRef[0].getText())
            );

            boolean clearDrafts = false;
            if ("Specific price".equals(selectedType))
            {
                long parsed = parseSmartSellPriceGp(priceFieldRef[0] == null ? "" : priceFieldRef[0].getText());
                if (parsed > 0L)
                {
                    plugin.setAutoFlipToBuyTargetPrice(item.getItemId(), parsed);
                    plugin.setAutoFlipToBuyWindowHours(item.getItemId(), 0);
                    clearDrafts = true;
                }
            }
            else if ("Best in window".equals(selectedType))
            {
                int hours = 0;
                try
                {
                    hours = Integer.parseInt(hoursFieldRef[0] == null ? "" : hoursFieldRef[0].getText().trim());
                }
                catch (NumberFormatException ignored)
                {
                    hours = 0;
                }

                if (hours > 0)
                {
                    plugin.setAutoFlipToBuyWindowHours(item.getItemId(), hours);
                    plugin.setAutoFlipToBuyTargetPrice(item.getItemId(), 0L);
                    clearDrafts = true;
                }
            }
            else
            {
                plugin.clearAutoFlipToBuyCustomSettings(item.getItemId());
                clearDrafts = true;
            }

            plugin.setAutoFlipToBuyQuantity(item.getItemId(), parsedQuantity);
            if (clearDrafts)
            {
                clearToBuyDrafts(item.getItemId());
            }

            closeAction.run();
        });

        JButton cancel = button("Cancel");
        cancel.setPreferredSize(new Dimension(72, 23));
        cancel.setMaximumSize(new Dimension(72, 23));
        cancel.addActionListener(e -> {
            clearToBuyDrafts(item.getItemId());
            closeAction.run();
        });

        JButton remove = button("Remove");
        remove.setPreferredSize(new Dimension(72, 23));
        remove.setMaximumSize(new Dimension(72, 23));
        remove.addActionListener(e -> {
            clearToBuyDrafts(item.getItemId());
            plugin.removeAutoFlipToBuyItem(item.getItemId());
            closeAction.run();
        });

        row6.add(save);
        row6.add(Box.createHorizontalStrut(6));
        row6.add(cancel);
        row6.add(Box.createHorizontalStrut(6));
        row6.add(remove);

        editor.add(space(4));
        editor.add(row1);
        editor.add(space(3));
        editor.add(row2);
        editor.add(space(3));
        editor.add(row3);
        editor.add(space(3));
        editor.add(row4);
        editor.add(space(4));
        editor.add(row5);
        editor.add(space(3));
        editor.add(row6);
        return editor;
    }
    private JPanel buildHoldSellValueBlock(AutoFlipPlugin.AutoFlipInventoryItem item)
    {
        long sellValueEachGp = item.getAssessedUnitValueGp() > 0L ? item.getAssessedUnitValueGp() : item.getHeldCapitalGp();
        long sellValueStackGp = multiplySmartSellStackGp(sellValueEachGp, item.getQuantity());
        String sellValueTooltip = item.getQuantity() > 1
            ? "Sell Value Each: " + gpFormat.format(sellValueEachGp) + " gp"
            : "Sell Value: " + gpFormat.format(sellValueEachGp) + " gp";

        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.setPreferredSize(new Dimension(236, 72));
        block.setMaximumSize(new Dimension(236, 72));
        block.setMinimumSize(new Dimension(236, 72));

        JLabel top = new JLabel("Holding until Sell Value");
        top.setForeground(MUTED);
        top.setFont(top.getFont().deriveFont(Font.BOLD, 11f));
        top.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel stackLine = buildCashStackLine("", sellValueStackGp, false, sellValueTooltip);
        stackLine.setPreferredSize(new Dimension(218, 38));
        stackLine.setMaximumSize(new Dimension(218, 38));
        stackLine.setMinimumSize(new Dimension(218, 38));

        JLabel bottom = new JLabel("is achievable.");
        bottom.setForeground(MUTED);
        bottom.setFont(bottom.getFont().deriveFont(Font.BOLD, 11f));
        bottom.setAlignmentX(Component.LEFT_ALIGNMENT);

        block.add(top);
        block.add(stackLine);
        block.add(bottom);
        return block;
    }

    private JPanel buildSmartSellTimeGoalBlock(AutoFlipPlugin.AutoFlipInventoryItem item, String savedInstruction)
    {
        long goalEachGp = item.getAssessedUnitValueGp() > 0L ? item.getAssessedUnitValueGp() : item.getHeldCapitalGp();
        long goalStackGp = multiplySmartSellStackGp(goalEachGp, item.getQuantity());
        String goalTooltip = item.getQuantity() > 1
            ? "Goal Each: " + gpFormat.format(goalEachGp) + " gp"
            : "Goal: " + gpFormat.format(goalEachGp) + " gp";

        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.setPreferredSize(new Dimension(236, 72));
        block.setMaximumSize(new Dimension(236, 72));
        block.setMinimumSize(new Dimension(236, 72));

        JLabel timeLabel = new JLabel(savedInstruction == null ? "Remaining Time" : savedInstruction);
        timeLabel.setForeground(GREEN);
        timeLabel.setFont(timeLabel.getFont().deriveFont(Font.BOLD, 12f));
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        timeLabel.setToolTipText(timeLabel.getText());

        JPanel goalLine = buildCashStackLine("Goal:", goalStackGp, false, goalTooltip);
        goalLine.setPreferredSize(new Dimension(230, 38));
        goalLine.setMaximumSize(new Dimension(230, 38));
        goalLine.setMinimumSize(new Dimension(230, 38));

        block.add(timeLabel);
        block.add(Box.createVerticalStrut(2));
        block.add(goalLine);
        return block;
    }
    private JPanel buildSmartSellCashStackLine(String savedInstruction)
    {
        long amountGp = extractSmartSellTargetGp(savedInstruction);
        return buildCashStackLine("Target Sell:", amountGp, true, "Target Sell Each: " + gpFormat.format(amountGp) + " gp");
    }

    private long multiplySmartSellStackGp(long targetEachGp, int quantity)
    {
        long safeQuantity = Math.max(1L, (long)quantity);

        if (targetEachGp <= 0L)
        {
            return 0L;
        }

        if (targetEachGp > Long.MAX_VALUE / safeQuantity)
        {
            return Long.MAX_VALUE;
        }

        return targetEachGp * safeQuantity;
    }
    private long extractSmartSellTargetGp(String savedInstruction)
    {
        if (savedInstruction == null)
        {
            return 0L;
        }

        String raw = savedInstruction.replace("Target Sell:", "").replace("gp", "").trim();
        return parseSmartSellPriceGp(raw);
    }

    private String getToBuyDraftValue(java.util.Map<Integer, String> drafts, int itemId, String fallback)
    {
        if (!drafts.containsKey(itemId))
        {
            return fallback;
        }

        String draft = drafts.get(itemId);
        return draft == null ? "" : draft;
    }

    private void bindToBuyDraftField(JTextField field, int itemId, java.util.Map<Integer, String> drafts)
    {
        if (field == null)
        {
            return;
        }

        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
        {
            private void sync()
            {
                storeToBuyDraftValue(drafts, itemId, field.getText());
                plugin.logToBuyEditorTrace("draft_update", itemId, "text=" + field.getText());
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e)
            {
                sync();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e)
            {
                sync();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e)
            {
                sync();
            }
        });
    }

    private void storeToBuyDraftValue(java.util.Map<Integer, String> drafts, int itemId, String raw)
    {
        if (raw == null)
        {
            drafts.put(itemId, "");
            return;
        }

        drafts.put(itemId, raw);
    }

    private void clearToBuyDrafts(int itemId)
    {
        toBuyPriceDrafts.remove(itemId);
        toBuyQuantityDrafts.remove(itemId);
        toBuyHoursDrafts.remove(itemId);
    }

    private JPanel buildToBuyEmptyRow()
    {
        JPanel p = cardPanel();
        p.setLayout(new BorderLayout());
        p.setBackground(new Color(27, 25, 22));
        p.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH, 52));
        p.setMinimumSize(new Dimension(INVENTORY_VIEW_WIDTH, 52));
        p.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH, 60));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel("Search the GE catalog to add a watch item");
        label.setForeground(MUTED);
        p.add(label, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildToBuyPopupEmptyRow()
    {
        JPanel p = popupCardPanel();
        p.setLayout(new BorderLayout());
        p.setPreferredSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 44, 64));
        p.setMinimumSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 44, 64));
        p.setMaximumSize(new Dimension(TO_BUY_SEARCH_WINDOW_WIDTH / 2 - 44, 70));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel("Search the GE catalog to add a watch item");
        label.setForeground(MUTED);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        p.add(label, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildToBuyHelp()
    {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel top = new JLabel("To-Buy watches prices even when the item is not in your cache yet.");
        top.setForeground(TEXT);
        top.setFont(top.getFont().deriveFont(Font.BOLD, 11f));
        top.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel bottom = new JLabel("Custom rules light up in green; otherwise AutoFlip uses the best current buy signal.");
        bottom.setForeground(MUTED);
        bottom.setFont(bottom.getFont().deriveFont(Font.BOLD, 10f));
        bottom.setAlignmentX(Component.LEFT_ALIGNMENT);

        p.add(top);
        p.add(bottom);
        return p;
    }

    private JPanel buildToBuyNextAction()
    {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = sectionLabel("Next Best Action");
        toBuyNextAction.setForeground(GREEN);
        toBuyNextAction.setFont(toBuyNextAction.getFont().deriveFont(Font.BOLD, 11f));
        toBuyNextAction.setText("Watching for a good buy");

        p.add(title);
        p.add(toBuyNextAction);
        return p;
    }

    private JScrollPane findScrollPaneAncestor(Component component)
    {
        Component current = component;
        while (current != null)
        {
            if (current instanceof JScrollPane)
            {
                return (JScrollPane)current;
            }
            current = current.getParent();
        }
        return null;
    }

    private String formatSmartSellCashStackLabel(long amountGp)
    {
        if (amountGp <= 0L)
        {
            return "";
        }

        if (amountGp >= 10_000_000L)
        {
            return gpFormat.format(amountGp / 1_000_000L) + "m";
        }

        if (amountGp >= 100_000L)
        {
            return gpFormat.format(amountGp / 1_000L) + "k";
        }

        return gpFormat.format(amountGp);
    }
    private JPanel buildSmartSellEditor(AutoFlipPlugin.AutoFlipInventoryItem item)
    {
        JPanel editor = new JPanel();
        editor.setOpaque(false);
        editor.setLayout(new BoxLayout(editor, BoxLayout.Y_AXIS));

        JPanel row1 = rowPanel();
        row1.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH - 18, 18));
        row1.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH - 18, 18));

        JLabel itemLabel = new JLabel(item.getItemName() + "  x" + gpFormat.format(item.getQuantity()));
        itemLabel.setForeground(TEXT);
        itemLabel.setFont(itemLabel.getFont().deriveFont(Font.BOLD, 11f));
        row1.add(itemLabel);

        JPanel row2 = rowPanel();
        row2.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH - 18, 24));
        row2.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH - 18, 24));

        JLabel typeLabel = new JLabel("Sell type");
        typeLabel.setForeground(MUTED);
        typeLabel.setFont(typeLabel.getFont().deriveFont(10f));

        JComboBox<String> sellType = new JComboBox<>(new String[] {"Sell within time period", "Sell at Price"});
        sellType.setMaximumSize(new Dimension(148, 23));
        sellType.setPreferredSize(new Dimension(148, 23));
        sellType.setFocusable(false);

        row2.add(typeLabel);
        row2.add(Box.createHorizontalStrut(6));
        row2.add(sellType);

        JPanel row3 = rowPanel();
        row3.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH - 18, 24));
        row3.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH - 18, 24));

        final javax.swing.JTextField[] customDaysFieldRef = new javax.swing.JTextField[1];
        final javax.swing.JTextField[] priceFieldRef = new javax.swing.JTextField[1];

        Runnable rebuildValueRow = () -> {
            row3.removeAll();
            customDaysFieldRef[0] = null;
            priceFieldRef[0] = null;

            if ("Sell at Price".equals(String.valueOf(sellType.getSelectedItem())))
            {
                JLabel priceLabel = new JLabel(item.getQuantity() > 1 ? "Price Each" : "Price");
                priceLabel.setForeground(MUTED);
                priceLabel.setFont(priceLabel.getFont().deriveFont(10f));

                javax.swing.JTextField priceField = new javax.swing.JTextField();
                priceField.setText("");
                priceField.setMaximumSize(new Dimension(112, 23));
                priceField.setPreferredSize(new Dimension(112, 23));
                priceField.setToolTipText("Examples: 1234k, 1m, 1600m, 1.6b");
                priceField.setName("smartSellPriceField");
                priceFieldRef[0] = priceField;

                priceField.addActionListener(ev -> {
                    long parsed = parseSmartSellPriceGp(priceField.getText());
                    if (parsed > 0L)
                    {
                        priceField.setText(gpFormat.format(parsed));
                    }
                });

                row3.add(priceLabel);
                row3.add(Box.createHorizontalStrut(6));
                row3.add(priceField);
            }
            else
            {
                JLabel timeLabel = new JLabel("Time");
                timeLabel.setForeground(MUTED);
                timeLabel.setFont(timeLabel.getFont().deriveFont(10f));

                JComboBox<String> timeBox = new JComboBox<>(new String[] {"1 day", "2 days", "5 days", "7 days", "Custom days"});
                timeBox.setMaximumSize(new Dimension(92, 23));
                timeBox.setPreferredSize(new Dimension(92, 23));
                timeBox.setFocusable(false);
                timeBox.setName("smartSellTimeBox");

                javax.swing.JTextField customDays = new javax.swing.JTextField();
                customDays.setMaximumSize(new Dimension(44, 23));
                customDays.setPreferredSize(new Dimension(44, 23));
                customDays.setToolTipText("Number of days");
                customDays.setVisible(false);
                customDaysFieldRef[0] = customDays;

                timeBox.addActionListener(ev -> {
                    boolean custom = "Custom days".equals(String.valueOf(timeBox.getSelectedItem()));
                    customDays.setVisible(custom);
                    row3.revalidate();
                    row3.repaint();
                });

                customDays.addActionListener(ev -> saveSmartSellTimeInstruction(item, timeBox, customDays));

                row3.add(timeLabel);
                row3.add(Box.createHorizontalStrut(6));
                row3.add(timeBox);
                row3.add(Box.createHorizontalStrut(5));
                row3.add(customDays);
            }

            row3.revalidate();
            row3.repaint();
        };

        sellType.addActionListener(e -> rebuildValueRow.run());
        rebuildValueRow.run();

        JPanel row4 = rowPanel();
        row4.setPreferredSize(new Dimension(INVENTORY_VIEW_WIDTH - 18, 24));
        row4.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH - 18, 24));

        JButton save = button("Save");
        save.setPreferredSize(new Dimension(72, 23));
        save.setMaximumSize(new Dimension(72, 23));
        save.addActionListener(e -> {
            if ("Sell at Price".equals(String.valueOf(sellType.getSelectedItem())))
            {
                javax.swing.JTextField priceField = priceFieldRef[0];
                String raw = priceField == null ? "" : priceField.getText();
                long parsed = parseSmartSellPriceGp(raw);
                if (parsed > 0L)
                {
                    if (priceField != null)
                    {
                        priceField.setText(gpFormat.format(parsed));
                    }

                    plugin.markAutoFlipInventorySmartSellBaseline(item.getItemId());
                    smartSellInstructions.put(item.getItemId(), "Target Sell: " + gpFormat.format(parsed) + " gp");
                    persistSmartSellInstructions();
                }
                else
                {
                    smartSellInstructions.put(item.getItemId(), "Target Sell: invalid price");
                    persistSmartSellInstructions();
                }
            }
            else
            {
                JComboBox<?> timeBox = null;
                for (Component c : row3.getComponents())
                {
                    if (c instanceof JComboBox)
                    {
                        timeBox = (JComboBox<?>)c;
                    }
                }

                saveSmartSellTimeInstruction(item, timeBox, customDaysFieldRef[0]);
                return;
            }

            smartSellSelectedItemId = -1;
            smartSellMode = false;
            refreshFromPlugin();
            refreshSoon();
        });

        JButton cancel = button("Cancel");
        cancel.setPreferredSize(new Dimension(72, 23));
        cancel.setMaximumSize(new Dimension(72, 23));
        cancel.addActionListener(e -> {
            smartSellSelectedItemId = -1;
            refreshFromPlugin();
        });

        row4.add(save);
        row4.add(Box.createHorizontalStrut(6));
        row4.add(cancel);

        editor.add(space(4));
        editor.add(row1);
        editor.add(space(3));
        editor.add(row2);
        editor.add(space(3));
        editor.add(row3);
        editor.add(space(4));
        editor.add(row4);
        return editor;
    }

    private void saveSmartSellTimeInstruction(AutoFlipPlugin.AutoFlipInventoryItem item, JComboBox<?> timeBox, javax.swing.JTextField customDays)
    {
        String time = timeBox == null ? "1 day" : String.valueOf(timeBox.getSelectedItem());

        if ("Custom days".equals(time))
        {
            String daysText = customDays == null ? "" : customDays.getText().trim();
            if (daysText.isEmpty())
            {
                daysText = "custom";
            }

            time = daysText + " days";
        }

        plugin.markAutoFlipInventorySmartSellBaseline(item.getItemId());
        smartSellInstructions.put(item.getItemId(), "Remaining Time: " + time);
        persistSmartSellInstructions();
        smartSellSelectedItemId = -1;
        smartSellMode = false;
        refreshFromPlugin();
    }

    private long parseSmartSellPriceGp(String raw)
    {
        if (raw == null)
        {
            return 0L;
        }

        String s = raw.trim().toLowerCase(java.util.Locale.ROOT).replace(",", "");
        if (s.isEmpty())
        {
            return 0L;
        }

        double multiplier = 1.0;

        if (s.endsWith("k"))
        {
            multiplier = 1_000.0;
            s = s.substring(0, s.length() - 1);
        }
        else if (s.endsWith("m"))
        {
            multiplier = 1_000_000.0;
            s = s.substring(0, s.length() - 1);
        }
        else if (s.endsWith("b"))
        {
            multiplier = 1_000_000_000.0;
            s = s.substring(0, s.length() - 1);
        }

        try
        {
            double value = Double.parseDouble(s);
            return Math.max(0L, Math.round(value * multiplier));
        }
        catch (NumberFormatException ignored)
        {
            return 0L;
        }
    }
    private JPanel buildEmptyRow()
    {
        JPanel p = cardPanel();
        p.setLayout(new BorderLayout());
        p.setBackground(new Color(27, 25, 22));

        int rowWidth = INVENTORY_VIEW_WIDTH;
        p.setPreferredSize(new Dimension(rowWidth, 48));
        p.setMinimumSize(new Dimension(rowWidth, 48));
        p.setMaximumSize(new Dimension(INVENTORY_VIEW_WIDTH, 60));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel label = new JLabel("No held items yet");
        label.setForeground(MUTED);
        p.add(label, BorderLayout.CENTER);
        return p;
    }

    private JPanel rowPanel()
    {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBackground(BG);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JPanel cardPanel()
    {
        JPanel p = new JPanel();
        p.setBackground(CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        p.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 8, 120));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JPanel popupCardPanel()
    {
        JPanel p = new PopupSurfacePanel(new Color(22, 20, 18, 218), 8);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(103, 85, 58, 210)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JLabel sectionLabel(String s)
    {
        JLabel l = new JLabel(s);
        l.setForeground(GOLD);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 13f));
        return l;
    }

    private JButton button(String text)
    {
        JButton b = new JButton(text);
        b.setBackground(PURPLE);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(PluginPanel.PANEL_WIDTH - 32, 32));
        return b;
    }

    private JButton toBuyRemoveButton()
    {
        JButton button = new JButton("X");
        button.setPreferredSize(new Dimension(24, 22));
        button.setMinimumSize(new Dimension(24, 22));
        button.setMaximumSize(new Dimension(24, 22));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBackground(new Color(178, 42, 42));
        button.setForeground(Color.WHITE);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createLineBorder(new Color(235, 86, 86)));
        button.setBorderPainted(true);
        button.setFocusPainted(false);
        button.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        return button;
    }

    private Component space(int h)
    {
        return Box.createRigidArea(new Dimension(1, h));
    }

    private String formatGp(long gp)
    {
        if (gp >= 1000000L)
        {
            return String.format("%.2fM gp", gp / 1000000.0D);
        }
        if (gp >= 1000L)
        {
            return String.format("%.1fK gp", gp / 1000.0D);
        }
        return gpFormat.format(gp) + " gp";
    }

    private String html(String value)
    {
        if (value == null)
        {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void refreshSoon()
    {
        Thread worker = new Thread(() -> {
            try
            {
                Thread.sleep(900L);
            }
            catch (InterruptedException ignored)
            {
                Thread.currentThread().interrupt();
            }
            refreshFromPlugin();
        }, "autoflip-side-panel-refresh");

        worker.setDaemon(true);
        worker.start();
    }
}
