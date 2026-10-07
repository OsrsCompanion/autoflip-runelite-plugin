package gg.autoflip;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.ImageIcon;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class AutoFlipOverlay extends Overlay
{
    // AUTOFLIP_PATCH_SETUP_LOGO_TAGLINE_ASSETS_V1
    private java.awt.image.BufferedImage autoFlipSetupLogoAssetImage;
    private java.awt.image.BufferedImage autoFlipSetupTaglineAssetImage;
    private java.awt.image.BufferedImage autoFlipSetupQuantityHintPanelAssetImage;
    private java.awt.image.BufferedImage autoFlipSetupPriceInputFrameAssetImage;
    private java.awt.image.BufferedImage autoFlipSetupQuantityInputFrameAssetImage;
    // AUTOFLIP_PATCH_SETUP_UI_ASSETS_LOGO_RENDER_V1_FIELDS
    private transient java.awt.image.BufferedImage autoFlipSetupLogoWatermarkImage;
    private transient boolean autoFlipSetupLogoWatermarkLoadAttempted;
    private static final String LOGO_RESOURCE = "/gg/autoflip/AppIcon_GeButton24.png";
    private static final String MARKET_LINK_ICON_RESOURCE = "/gg/autoflip/AppIcon_Favicon4.png";

    private static final PlanCard[] PLACEHOLDER_PLAN = new PlanCard[]
    {
        new PlanCard(
            "Magic logs",
            "x400",
            "+1,725 gp",
            "690,000 gp",
            "5h max",
            "High volume stable spread",
            new Color(95, 63, 28, 230),
            new Color(214, 139, 60, 210)
        ),
        new PlanCard(
            "Dragon arrowtips",
            "x7,000",
            "+6,746 gp",
            "47,222,000 gp",
            "12h max",
            "High margin long flip",
            new Color(72, 74, 58, 230),
            new Color(128, 146, 94, 205)
        )
    };

    private final AutoFlipPlugin plugin;
    private final DevConfig config = new DevConfig();
    private final LiveOverlayAsset buyReadyAsset = new LiveOverlayAsset();
    private final LiveOverlayAsset sellReadyAsset = new LiveOverlayAsset();
    private final LiveOverlayAsset sellBankAsset = new LiveOverlayAsset();

    private Image logoImage;
    private Image headerBrandImage;
    private Image cardWebsiteButtonImage;
    private boolean logoLoadAttempted = false;

    @Inject
    public AutoFlipOverlay(AutoFlipPlugin plugin)
    {
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        config.reloadIfNeeded();

        boolean geOpen = plugin.isGeWindowOpenForOverlay();
        boolean workflowActive = plugin.isAutoFlipUiWorkflowActiveForOverlay();
        if (!geOpen && !workflowActive)
        {
            plugin.rememberAutoFlipButtonBounds(null);
            plugin.clearAutoFlipGeMarketLinkBounds();
            return null;
        }

        if (workflowActive)
        {
            // AUTOFLIP_TINKER_STATE_LABEL_ALWAYS_ON_V2
            drawAutoFlipStateDetectorLabel(graphics, null);
            drawReadyToSellInventoryNotifier(graphics);
            drawAutoFlipOrdinarySellPromptActions(graphics);
        }

        if (!geOpen)
        {
            plugin.rememberAutoFlipButtonBounds(null);
            plugin.clearAutoFlipGeMarketLinkBounds();
            return null;
        }

        Rectangle header = plugin.getGeHeaderBoundsForOverlay();
        if (header == null)
        {
            plugin.rememberAutoFlipButtonBounds(null);
            plugin.clearAutoFlipGeMarketLinkBounds();
            return null;
        }

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            config.textSharpEnabled() ? RenderingHints.VALUE_TEXT_ANTIALIAS_OFF : RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);

        boolean active = plugin.isAutoFlipOverlayActive();

        int buttonSize = config.buttonSize();
        int buttonX = header.x + header.width + config.buttonOffsetX();
        int buttonY = header.y + config.buttonOffsetY();

        Rectangle buttonBounds = new Rectangle(buttonX, buttonY, buttonSize, buttonSize);
        plugin.rememberAutoFlipButtonBounds(buttonBounds);

        drawButton(graphics, buttonX, buttonY, buttonSize, active);

        if (!active && !workflowActive)
        {
            plugin.clearAutoFlipGeMarketLinkBounds();
            return null;
        }

        // AUTOFLIP_PATCH_SETUP_UI_BUTTON_HISTORY_V1
        if (active)
        {
            int uiWidth = config.getInt("setup.ui.button.w", 30);
            int uiHeight = config.getInt("setup.ui.button.h", 20);
            int uiX = header.x + config.getInt("setup.ui.button.x", 82);
            int uiY = header.y + config.getInt("setup.ui.button.y", 5);
            boolean customSetupUiEnabled = plugin.isAutoFlipCustomSetupUiEnabledForOverlay();

            Color oldUiColor = graphics.getColor();
            Font oldUiFont = graphics.getFont();
            Stroke oldUiStroke = graphics.getStroke();
            try
            {
                graphics.setColor(customSetupUiEnabled ? new Color(176, 120, 24, 235) : new Color(34, 25, 14, 235));
                graphics.fillRoundRect(uiX, uiY, uiWidth, uiHeight, 6, 6);

                graphics.setStroke(new BasicStroke(customSetupUiEnabled ? 2.0f : 1.2f));
                graphics.setColor(customSetupUiEnabled ? new Color(244, 198, 89, 245) : new Color(128, 92, 42, 210));
                graphics.drawRoundRect(uiX, uiY, uiWidth, uiHeight, 6, 6);

                int fontSize = Math.max(9, config.getInt("setup.ui.button.font.size", 11));
                graphics.setFont(new Font("Arial", Font.BOLD, fontSize));
                FontMetrics fm = graphics.getFontMetrics();
                String uiText = "UI";
                int textX = uiX + ((uiWidth - fm.stringWidth(uiText)) / 2);
                int textY = uiY + ((uiHeight - fm.getHeight()) / 2) + fm.getAscent();
                graphics.setColor(customSetupUiEnabled ? new Color(20, 14, 8, 245) : new Color(230, 190, 95, 230));
                graphics.drawString(uiText, textX, textY);
            }
            finally
            {
                graphics.setStroke(oldUiStroke);
                graphics.setFont(oldUiFont);
                graphics.setColor(oldUiColor);
            }
        }


        String geTitle = plugin.getGeHeaderTextForOverlay();
        boolean guidedSetup = plugin.getAutoFlipSetupTargetBoardCard() != null;
        boolean ordinarySellPromptOpen = plugin.isAutoFlipOrdinarySellPricePromptOpenForOverlay();
        boolean ordinarySellMismatch = plugin.isAutoFlipOrdinarySellPriceMismatchForOverlay();
        boolean ordinaryBuyPromptOpen = plugin.isAutoFlipOrdinaryBuyPricePromptOpenForOverlay();
        boolean ordinaryBuyMismatch = plugin.isAutoFlipOrdinaryBuyPriceMismatchForOverlay();
        boolean ordinarySellPrice = ordinarySellPromptOpen || ordinarySellMismatch || ordinaryBuyPromptOpen || ordinaryBuyMismatch;
        if (AutoFlipOrdinarySellEligibility.shouldRenderSetupFlow(active, guidedSetup, ordinarySellPrice))
        {
            if (geTitle != null
                && geTitle.startsWith("Grand Exchange: Set up offer")
                && (guidedSetup || ordinarySellPrice))
            {
                // AUTOFLIP_GE_SETUP_MODE_PROBE_RENDER_V1
                // AUTOFLIP_PATCH_Q3_MINIMAL_SETUP_GATE_V1
                // AUTOFLIP_GUIDED_SETUP_FLOW_DRAW_CALL_V14
                drawAutoFlipGuidedSetupFlowOverlay(graphics, header);
                if (guidedSetup)
                {
                    // AUTOFLIP_PATCH_SETUP_UI_ASSETS_LOGO_RENDER_V1_CALL
                    drawAutoFlipSetupUiLogoAsset(graphics, header);
                }
            }
        }
        String autoFlipGeTitleForControls = plugin.getGeHeaderTextForOverlay();
        boolean autoFlipSetupWindowForControls = autoFlipGeTitleForControls != null && autoFlipGeTitleForControls.startsWith("Grand Exchange:");

        if (active && !autoFlipSetupWindowForControls)
        {
            // AUTOFLIP_HIDE_REFRESH_ON_GE_SETUP_V1
            int refreshWidth = 96;
            int refreshGap = 10;
            drawRefreshBoardButton(graphics, buttonX - refreshWidth - refreshGap, buttonY, refreshWidth, buttonSize);
        }

        if (active)
        {
            int menuX = header.x + header.width + config.menuOffsetX();
            int menuY = header.y + config.menuOffsetY();

            drawMenuShell(graphics, menuX, menuY);
            plugin.maybeRefreshAutoFlipBoardCache();
            if (!autoFlipSetupWindowForControls)
            {
                // AUTOFLIP_PATCH_N1_LINE_MAIN_GRID_ONLY_BOARD_SUMMARY_V1
                drawStaticGhostCards(graphics);
                // AUTOFLIP_PATCH_M_FINAL_TINY_SUMMARY_CALL_V1
                drawAutoFlipBoardSummaryStrip(graphics);
            }
                if (config.editModeEnabled())
                {
                    drawEditHandles(graphics);
                }
                if (config.explanationPanelEnabled())
                {
                    drawExplanationPanel(graphics, header);
                }
        }

        // AUTOFLIP_PATCH_AH2_DRAW_TOGGLE_AFTER_CARDS_V1
        // Draw the normal toggle again after menu/cards so active offer cards cannot cover the logo.
        drawButton(graphics, buttonX, buttonY, buttonSize, active);

        drawGeMarketItemLinks(graphics);
        drawAutoFlipSellGuidance(graphics);

        return null;
    }





    // AUTOFLIP_STATE_DETECTOR_LABEL_V5_DRAW_METHOD
    // AUTOFLIP_STATE_DETECTOR_LABEL_V5_DRAW_METHOD
    private void drawAutoFlipStateDetectorLabel(Graphics2D graphics, Rectangle header)
    {
        // AUTOFLIP_STATE_DETECTOR_LABEL_V7_DRAW_METHOD
        if (graphics == null || plugin == null)
        {
            return;
        }

        // AUTOFLIP_TINKER_ONLY_STATE_LABEL_GATE_V2
        String userDir = System.getProperty("user.dir", "");
        if (userDir == null || !userDir.replace('\\', '/').toLowerCase().contains("plugin-tinker"))
        {
            return;
        }

        Color oldColor = graphics.getColor();
        Font oldFont = graphics.getFont();

        try
        {
            String state = plugin.getAutoFlipStateDetectorLabelForOverlay();
            if (state == null || state.trim().isEmpty())
            {
                state = "unknown state";
            }

            String accountMode = plugin.isAutoFlipFreeToPlayAccount() ? "F2P" : "MEMBERS";
            String label = "AUTOFLIP STATE [" + accountMode + "]: " + state;
            int x = (header == null) ? 8 : Math.max(4, header.x);
            int y = (header == null) ? 22 : Math.max(14, header.y - 8);

            graphics.setFont(new Font("Arial", Font.BOLD, 13));
            FontMetrics fm = graphics.getFontMetrics();
            int labelW = fm.stringWidth(label);
            int boxW = Math.max(220, labelW + 10);
            int boxH = 18;

            graphics.setColor(new Color(0, 0, 0, 190));
            graphics.fillRoundRect(x - 5, y - 14, boxW, boxH, 6, 6);

            graphics.setColor(new Color(95, 255, 135, 245));
            graphics.drawString(label, x, y);
        }
        catch (Throwable ignored)
        {
            // Debug label must never break overlay rendering.
        }
        finally
        {
            graphics.setFont(oldFont);
            graphics.setColor(oldColor);
        }
    }

    private void drawGeSetupModeProbe(Graphics2D graphics, Rectangle header, String geTitle)
    {
        if (graphics == null || header == null)
        {
            return;
        }

        // AUTOFLIP_GE_SETUP_ITEM_TARGET_V1
        // AUTOFLIP_PATCH_J_SETUP_USES_REMEMBERED_NATIVE_SLOT_V1
        AutoFlipPlugin.AutoFlipBoardCard card = plugin.getAutoFlipSetupTargetBoardCard();
        boolean sellPlan = card != null && isAutoFlipSellPlan(card.getReason(), card.getItemName());

        Color primary = sellPlan ? new Color(80, 170, 255, 245) : new Color(80, 255, 120, 245);
        Color fill = sellPlan ? new Color(20, 52, 92, 88) : new Color(18, 92, 38, 88);

        Rectangle itemBox = new Rectangle(
            header.x + config.getInt("setup.item.icon.x", 56),
            header.y + config.getInt("setup.item.icon.y", 48),
            config.getInt("setup.item.icon.w", 44),
            config.getInt("setup.item.icon.h", 44)
        );

        Rectangle quickBox = new Rectangle(
            header.x + config.getInt("setup.quick.x", 295),
            header.y + config.getInt("setup.quick.y", 186),
            config.getInt("setup.quick.w", 62),
            config.getInt("setup.quick.h", 28)
        );

        // AUTOFLIP_PATCH_V_RESTORE_QUANTITY_SETUP_QUICK_BOX_V1
        Rectangle quantityQuickBox = new Rectangle(
            header.x + config.getInt("setup.quantity.quick.x", 178),
            header.y + config.getInt("setup.quantity.quick.y", 186),
            config.getInt("setup.quantity.quick.w", 62),
            config.getInt("setup.quantity.quick.h", 28)
        );

        Rectangle confirmBox = new Rectangle(
            header.x + config.getInt("setup.confirm.x", 172),
            header.y + config.getInt("setup.confirm.y", 251),
            config.getInt("setup.confirm.w", 164),
            config.getInt("setup.confirm.h", 40)
        );

        if (config.editModeEnabled())
        {
            // AUTOFLIP_SETUP_ITEM_TARGET_EDIT_DRAW_V1
            drawNativeHoleEditHandle(graphics, "ITEM", itemBox, primary);

            // AUTOFLIP_SETUP_QUICK_SET_EDIT_DRAW_V1
            drawNativeHoleEditHandle(graphics, "QUICK", quickBox, primary);

            // AUTOFLIP_SETUP_CONFIRM_EDIT_DRAW_V1
            drawNativeHoleEditHandle(graphics, "CONFIRM", confirmBox, primary);
        }

        Stroke oldStroke = graphics.getStroke();
        Font oldFont = graphics.getFont();
        Color oldColor = graphics.getColor();

        // AUTOFLIP_PATCH_V_RESTORE_QUANTITY_SETUP_QUICK_DRAW_V1
        graphics.setColor(new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 48));
        graphics.fillRoundRect(quantityQuickBox.x - 3, quantityQuickBox.y - 3, quantityQuickBox.width + 6, quantityQuickBox.height + 6, 8, 8);
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.setColor(primary);
        graphics.drawRoundRect(quantityQuickBox.x - 2, quantityQuickBox.y - 2, quantityQuickBox.width + 4, quantityQuickBox.height + 4, 8, 8);

        graphics.setColor(new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 48));
        graphics.fillRoundRect(itemBox.x - 7, itemBox.y - 7, itemBox.width + 14, itemBox.height + 14, 12, 12);

        graphics.setColor(fill);
        graphics.fillRoundRect(itemBox.x - 3, itemBox.y - 3, itemBox.width + 6, itemBox.height + 6, 10, 10);

        graphics.setStroke(new BasicStroke(2.4f));
        graphics.setColor(primary);
        graphics.drawRoundRect(itemBox.x - 4, itemBox.y - 4, itemBox.width + 8, itemBox.height + 8, 10, 10);

        graphics.setStroke(new BasicStroke(1.2f));
        graphics.setColor(new Color(255, 245, 190, 210));
        graphics.drawRoundRect(itemBox.x, itemBox.y, itemBox.width, itemBox.height, 8, 8);

        String label = "Select item";

        if (card != null)
        {
            if (card.getItemName() != null && !card.getItemName().isEmpty())
            {
                label = "Select: " + card.getItemName();
            }

            BufferedImage image = plugin.getAutoFlipInventoryItemImage(card.getItemId());
            if (image != null)
            {
                int drawX = itemBox.x + ((itemBox.width - image.getWidth()) / 2);
                int drawY = itemBox.y + ((itemBox.height - image.getHeight()) / 2);
                graphics.drawImage(image, drawX, drawY, null);
            }
        }

        graphics.setFont(oldFont.deriveFont(Font.BOLD, 11.0f));
        graphics.setColor(new Color(0, 0, 0, 150));
        graphics.drawString(label, itemBox.x + itemBox.width + 9, itemBox.y + 16 + 1);
        graphics.setColor(new Color(238, 229, 196, 255));
        graphics.drawString(label, itemBox.x + itemBox.width + 8, itemBox.y + 16);

        // AUTOFLIP_SETUP_QUICK_SET_BOX_V1
        graphics.setColor(new Color(0, 0, 0, 130));
        graphics.fillRoundRect(quickBox.x + 2, quickBox.y + 2, quickBox.width, quickBox.height, 7, 7);
        graphics.setColor(fill);
        graphics.fillRoundRect(quickBox.x, quickBox.y, quickBox.width, quickBox.height, 7, 7);
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.setColor(primary);
        graphics.drawRoundRect(quickBox.x, quickBox.y, quickBox.width, quickBox.height, 7, 7);

        graphics.setFont(oldFont.deriveFont(Font.BOLD, quickBox.height >= 32 ? 12.0f : 10.0f));
        FontMetrics quickMetrics = graphics.getFontMetrics();
        // AUTOFLIP_BLANK_QUICK_SET_TEXT_V1
                String quickLabel = "Qty " + Math.max(1, card.getQuantity());
                graphics.setColor(new Color(255, 245, 190, 255));
                graphics.drawString(
                        quickLabel,
            quickBox.x + ((quickBox.width - quickMetrics.stringWidth(quickLabel)) / 2),
            quickBox.y + ((quickBox.height - quickMetrics.getHeight()) / 2) + quickMetrics.getAscent()
        );

        // AUTOFLIP_SETUP_CONFIRM_BOX_V1
        graphics.setColor(new Color(0, 0, 0, 145));
        graphics.fillRoundRect(confirmBox.x + 3, confirmBox.y + 3, confirmBox.width, confirmBox.height, 8, 8);
        graphics.setColor(fill);
        graphics.fillRoundRect(confirmBox.x, confirmBox.y, confirmBox.width, confirmBox.height, 8, 8);
        graphics.setStroke(new BasicStroke(2.2f));
        graphics.setColor(primary);
        graphics.drawRoundRect(confirmBox.x, confirmBox.y, confirmBox.width, confirmBox.height, 8, 8);

        Image logo = getCardWebsiteButtonImage();
        int textShift = 0;
        if (logo != null)
        {
            int s = Math.min(24, Math.max(16, confirmBox.height - 10));
            graphics.drawImage(logo, confirmBox.x + 8, confirmBox.y + ((confirmBox.height - s) / 2), s, s, null);
            textShift = 12;
        }

        graphics.setFont(oldFont.deriveFont(Font.BOLD, confirmBox.height >= 36 ? 14.0f : 11.0f));
        FontMetrics confirmMetrics = graphics.getFontMetrics();
        String confirmLabel = sellPlan ? "Confirm Sell" : "Confirm Buy";
        graphics.setColor(new Color(255, 245, 190, 255));
        graphics.drawString(
            confirmLabel,
            confirmBox.x + ((confirmBox.width - confirmMetrics.stringWidth(confirmLabel)) / 2) + textShift,
            confirmBox.y + ((confirmBox.height - confirmMetrics.getHeight()) / 2) + confirmMetrics.getAscent()
        );

        graphics.setStroke(oldStroke);
        graphics.setFont(oldFont);
        graphics.setColor(oldColor);
    }
    private void drawReadyToSellInventoryNotifier(Graphics2D graphics)
    {
        java.util.List<AutoFlipPlugin.AutoFlipInventoryItem> readyItems = plugin.getAutoFlipReadyToSellInventorySnapshot();
        if (readyItems == null || readyItems.isEmpty())
        {
            return;
        }

        Rectangle clip = graphics.getClipBounds();
        int canvasWidth = clip == null ? 765 : Math.max(320, clip.width);
        int canvasHeight = clip == null ? 503 : Math.max(240, clip.height);

        int iconSize = 36;
        int gap = 5;
        int count = Math.min(5, readyItems.size());

        int x = Math.max(8, canvasWidth - 250);
        int y = Math.max(8, canvasHeight - 252);

        Color oldColor = graphics.getColor();
        Stroke oldStroke = graphics.getStroke();
        Font oldFont = graphics.getFont();

        graphics.setFont(new Font("Arial", Font.BOLD, 11));
        graphics.setColor(new Color(92, 222, 72, 235));
        graphics.drawString("Ready", x, y - 5);

        for (int i = 0; i < count; i++)
        {
            AutoFlipPlugin.AutoFlipInventoryItem item = readyItems.get(i);
            if (item == null)
            {
                continue;
            }

            int boxX = x + i * (iconSize + gap);
            int boxY = y;

            graphics.setColor(new Color(22, 120, 42, 118));
            graphics.fillRoundRect(boxX, boxY, iconSize, iconSize, 5, 5);

            graphics.setStroke(new BasicStroke(2f));
            graphics.setColor(new Color(92, 222, 72, 230));
            graphics.drawRoundRect(boxX, boxY, iconSize, iconSize, 5, 5);

            BufferedImage image = plugin.getAutoFlipInventoryItemImage(item.getItemId());
            if (image != null)
            {
                int drawX = boxX + (iconSize - image.getWidth()) / 2;
                int drawY = boxY + (iconSize - image.getHeight()) / 2;
                graphics.drawImage(image, drawX, drawY, null);
            }
        }

        graphics.setFont(oldFont);
        graphics.setColor(oldColor);
        graphics.setStroke(oldStroke);
    }

    private void drawAutoFlipSellGuidance(Graphics2D graphics)
    {
        java.util.List<AutoFlipPlugin.AutoFlipSellGuidance> guidanceList = plugin.getAutoFlipSellGuidanceListForOverlay();
        if (guidanceList == null || guidanceList.isEmpty())
        {
            return;
        }

        for (AutoFlipPlugin.AutoFlipSellGuidance guidance : guidanceList)
        {
            if (guidance == null)
            {
                continue;
            }

            java.util.List<Rectangle> itemBounds = guidance.isPresentInInventory()
                ? plugin.getAutoFlipInventoryItemBoundsForOverlay(guidance.getItemId())
                : java.util.Collections.emptyList();

            if (itemBounds != null && !itemBounds.isEmpty())
            {
                drawAutoFlipInventorySellHighlights(graphics, guidance, itemBounds);
            }

            if (plugin.isGeWindowOpenForOverlay())
            {
                // Widget bounds only control whether an on-screen inventory icon can be outlined.
                // Inventory ownership comes exclusively from the GE-session cache so switching
                // to Equipment/another side tab cannot turn a cached item into "ITEM IS IN BANK".
                drawAutoFlipSellGuidanceCard(
                    graphics,
                    guidance,
                    resolveAutoFlipSellCardInventoryPresence(
                        guidance.isPresentInInventory(),
                        itemBounds != null && !itemBounds.isEmpty()
                    )
                );
            }
        }
    }

    static boolean resolveAutoFlipSellCardInventoryPresence(boolean sessionCacheContainsItem, boolean visibleWidgetFound)
    {
        return sessionCacheContainsItem;
    }

    private void drawAutoFlipOrdinarySellPromptActions(Graphics2D graphics)
    {
        if (graphics == null || plugin == null
            || (!plugin.isAutoFlipOrdinarySellPricePromptOpenForOverlay()
                && !plugin.isAutoFlipOrdinaryBuyPricePromptOpenForOverlay()
                && !plugin.isAutoFlipTargetBuyPricePromptOpenForOverlay()
                && plugin.getAutoFlipQuantityChatboxButtonBounds() == null))
        {
            return;
        }

        Rectangle recommendedBounds = plugin.getAutoFlipOrdinarySellRecommendedButtonBounds();
        Rectangle lastSoldBounds = plugin.getAutoFlipOrdinarySellLastSoldButtonBounds();
        Rectangle targetPriceBounds = plugin.getAutoFlipOrdinaryBuyTargetPriceButtonBounds();
        Rectangle quantityBounds = plugin.getAutoFlipQuantityChatboxButtonBounds();
        String recommendedLabel = plugin.getAutoFlipOrdinarySellRecommendedButtonLabel();
        String lastSoldLabel = plugin.getAutoFlipOrdinarySellLastSoldButtonLabel();
        String targetPriceLabel = plugin.getAutoFlipOrdinaryBuyTargetPriceButtonLabel();
        String quantityLabel = plugin.getAutoFlipQuantityChatboxButtonLabel();

        if (recommendedBounds != null && recommendedBounds.width > 0 && recommendedBounds.height > 0)
        {
            drawPromptChip(
                graphics,
                recommendedBounds,
                recommendedLabel == null || recommendedLabel.trim().isEmpty() ? "Autoflip recommended" : recommendedLabel,
                new Color(88, 255, 130, 245),
                new Color(14, 82, 30, 200),
                true
            );
        }

        if (lastSoldBounds != null && lastSoldBounds.width > 0 && lastSoldBounds.height > 0)
        {
            drawPromptChip(
                graphics,
                lastSoldBounds,
                lastSoldLabel == null || lastSoldLabel.trim().isEmpty() ? "Last sold" : lastSoldLabel,
                new Color(255, 214, 110, 240),
                new Color(88, 56, 14, 200),
                false
            );
        }

        if (targetPriceBounds != null && targetPriceBounds.width > 0 && targetPriceBounds.height > 0)
        {
            drawPromptChip(
                graphics,
                targetPriceBounds,
                targetPriceLabel == null || targetPriceLabel.trim().isEmpty() ? "Your Target Price" : targetPriceLabel,
                new Color(130, 210, 255, 245),
                new Color(22, 58, 92, 210),
                true
            );
        }

        if (quantityBounds != null && quantityBounds.width > 0 && quantityBounds.height > 0)
        {
            drawPromptChip(
                graphics,
                quantityBounds,
                quantityLabel == null || quantityLabel.trim().isEmpty() ? "Autoflip Qty" : quantityLabel,
                new Color(115, 255, 182, 245),
                new Color(14, 82, 30, 200),
                true
            );
        }
    }

    private void drawPromptChip(Graphics2D graphics, Rectangle bounds, String label, Color accent, Color fill, boolean strong)
    {
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0 || label == null)
        {
            return;
        }

        Font oldFont = graphics.getFont();
        Stroke oldStroke = graphics.getStroke();
        Color oldColor = graphics.getColor();
        try
        {
            graphics.setColor(new Color(31, 22, 9, 240));
            graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 3, 3);

            graphics.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), strong ? 155 : 140));
            graphics.fillRoundRect(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2, 3, 3);

            graphics.setStroke(new BasicStroke(strong ? 1.9f : 1.5f));
            graphics.setColor(accent);
            graphics.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 3, 3);
            graphics.setColor(new Color(255, 255, 255, strong ? 28 : 18));
            graphics.drawLine(bounds.x + 1, bounds.y + 1, bounds.x + bounds.width - 2, bounds.y + 1);
            graphics.setColor(new Color(0, 0, 0, 75));
            graphics.drawLine(bounds.x + 1, bounds.y + bounds.height - 2, bounds.x + bounds.width - 2, bounds.y + bounds.height - 2);

            graphics.setFont(oldFont.deriveFont(Font.BOLD, Math.max(9.0f, Math.min(10.0f, bounds.height - 8.5f))));
            FontMetrics fm = graphics.getFontMetrics();
            int textX = bounds.x + Math.max(7, (bounds.width - fm.stringWidth(label)) / 2);
            int textY = bounds.y + ((bounds.height - fm.getHeight()) / 2) + fm.getAscent();

            graphics.setColor(new Color(0, 0, 0, 140));
            graphics.drawString(label, textX + 1, textY + 1);
            graphics.setColor(strong ? new Color(220, 255, 220, 255) : new Color(247, 224, 162, 255));
            graphics.drawString(label, textX, textY);
        }
        finally
        {
            graphics.setStroke(oldStroke);
            graphics.setFont(oldFont);
            graphics.setColor(oldColor);
        }
    }

    private void drawAutoFlipInventorySellHighlights(Graphics2D graphics, AutoFlipPlugin.AutoFlipSellGuidance guidance, java.util.List<Rectangle> itemBounds)
    {
        Color oldColor = graphics.getColor();
        Stroke oldStroke = graphics.getStroke();
        Font oldFont = graphics.getFont();
        Composite oldComposite = graphics.getComposite();

        Rectangle first = null;
        for (Rectangle bounds : itemBounds)
        {
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
            {
                continue;
            }

            if (first == null)
            {
                first = bounds;
            }

            graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.24f));
            graphics.setColor(new Color(28, 235, 86));
            graphics.fillRoundRect(bounds.x - 3, bounds.y - 3, bounds.width + 6, bounds.height + 6, 7, 7);

            graphics.setComposite(oldComposite);
            graphics.setStroke(new BasicStroke(3.0f));
            graphics.setColor(new Color(74, 255, 116, 245));
            graphics.drawRoundRect(bounds.x - 4, bounds.y - 4, bounds.width + 8, bounds.height + 8, 8, 8);
        }

        graphics.setComposite(oldComposite);
        graphics.setFont(oldFont);
        graphics.setStroke(oldStroke);
        graphics.setColor(oldColor);
    }

    private void drawAutoFlipSellGuidanceCard(
        Graphics2D graphics,
        AutoFlipPlugin.AutoFlipSellGuidance guidance,
        boolean presentInInventory)
    {
        if (!config.sellGuidanceEnabled())
        {
            return;
        }

        Rectangle slotBounds = plugin.getGeSlotBoundsForOverlay(guidance.getSlotIndex());
        if (slotBounds == null)
        {
            return;
        }

        int cardX = slotBounds.x + config.cardOffsetX();
        int cardY = slotBounds.y + config.cardOffsetY();
        int cardWidth = config.cardWidth();
        int cardHeight = config.cardHeight();
        Rectangle configuredPanel = guidancePanelBounds(
            cardX,
            cardY,
            cardWidth,
            cardHeight,
            config.sellGuidancePanelX(),
            config.sellGuidancePanelY(),
            config.sellGuidancePanelWidth(cardWidth),
            config.sellGuidancePanelHeight()
        );
        Rectangle nativeButtonHole = getAutoFlipNativeButtonHole(cardX, cardY, cardWidth, cardHeight, true);
        boolean bankState = !presentInInventory;
        Rectangle panel = bankState
            ? configuredPanel
            : AutoFlipCardLayout.compactTimingPanel(
                cardX,
                cardY,
                cardWidth,
                cardHeight,
                nativeButtonHole,
                true
            );

        Color oldColor = graphics.getColor();
        Stroke oldStroke = graphics.getStroke();
        Font oldFont = graphics.getFont();
        Composite oldComposite = graphics.getComposite();
        java.awt.Shape oldClip = graphics.getClip();
        Object oldTextAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);

        if (!bankState)
        {
            java.awt.geom.Area readyClip = new java.awt.geom.Area(
                oldClip == null ? new Rectangle(0, 0, 10000, 10000) : oldClip
            );
            readyClip.subtract(new java.awt.geom.Area(nativeButtonHole));
            graphics.setClip(readyClip);
        }

        BufferedImage asset = (bankState ? sellBankAsset : sellReadyAsset).load(
            bankState ? config.sellGuidanceBankAssetPath() : config.sellGuidanceReadyAssetPath()
        );
        drawAutoFlipGuidancePanelBackground(
            graphics,
            panel,
            asset,
            config.sellGuidanceAssetAlpha(),
            bankState ? config.sellGuidanceBankFill() : config.sellGuidanceReadyFill(),
            bankState ? config.sellGuidanceBankBorder() : config.sellGuidanceReadyBorder(),
            config.sellGuidanceBorderWidth()
        );

        graphics.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            config.sellGuidanceTextSharpEnabled()
                ? RenderingHints.VALUE_TEXT_ANTIALIAS_OFF
                : RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        int textX = panel.x + config.sellGuidanceTextX();
        int maxTextWidth = Math.max(12, panel.width - config.sellGuidanceTextX() - 4);
        String title = bankState ? config.sellGuidanceBankTitle() : config.sellGuidanceReadyTitle();
        String status = bankState ? config.sellGuidanceBankStatus() : config.sellGuidanceReadyStatus();

        graphics.setFont(autoFlipCardFont(oldFont, Font.BOLD, config.sellGuidanceTitleFont()));
        drawAutoFlipShadowedFittedText(
            graphics,
            title,
            textX,
            panel.y + config.sellGuidanceTitleY(),
            maxTextWidth,
            config.sellGuidanceTitleColor(),
            config.sellGuidanceShadowEnabled(),
            config.sellGuidanceShadowAlpha()
        );

        if (bankState)
        {
            graphics.setFont(autoFlipCardFont(oldFont, Font.BOLD, config.sellGuidanceStatusFont()));
            drawAutoFlipShadowedFittedText(
                graphics,
                status,
                textX,
                panel.y + config.sellGuidanceItemY(),
                maxTextWidth,
                config.sellGuidanceBankStatusColor(),
                config.sellGuidanceShadowEnabled(),
                config.sellGuidanceShadowAlpha()
            );
        }

        if (bankState && config.sellGuidanceBankBlocksSlot())
        {
            plugin.rememberAutoFlipCardBlockBounds(new Rectangle(panel));
        }

        restoreAutoFlipGuidanceGraphics(
            graphics,
            oldClip,
            oldComposite,
            oldTextAntialiasing,
            oldFont,
            oldStroke,
            oldColor
        );
    }

    private Rectangle guidancePanelBounds(
        int cardX,
        int cardY,
        int cardWidth,
        int cardHeight,
        int offsetX,
        int offsetY,
        int requestedWidth,
        int requestedHeight)
    {
        int safeX = Math.max(0, Math.min(Math.max(0, cardWidth - 1), offsetX));
        int safeY = Math.max(0, Math.min(Math.max(0, cardHeight - 1), offsetY));
        int availableWidth = Math.max(1, cardWidth - safeX);
        int availableHeight = Math.max(1, cardHeight - safeY);
        int width = Math.max(1, Math.min(availableWidth, requestedWidth));
        int height = Math.max(1, Math.min(availableHeight, requestedHeight));
        return new Rectangle(cardX + safeX, cardY + safeY, width, height);
    }

    private void drawAutoFlipGuidancePanelBackground(
        Graphics2D graphics,
        Rectangle panel,
        BufferedImage asset,
        int assetAlpha,
        Color fill,
        Color border,
        float borderWidth)
    {
        Composite oldComposite = graphics.getComposite();
        if (asset != null)
        {
            graphics.setComposite(AlphaComposite.getInstance(
                AlphaComposite.SRC_OVER,
                Math.max(0, Math.min(255, assetAlpha)) / 255.0f
            ));
            graphics.drawImage(asset, panel.x, panel.y, panel.width, panel.height, null);
            graphics.setComposite(oldComposite);
            return;
        }

        graphics.setColor(fill);
        graphics.fillRoundRect(panel.x, panel.y, panel.width, panel.height, 5, 5);
        graphics.setStroke(new BasicStroke(Math.max(1.0f, borderWidth)));
        graphics.setColor(border);
        graphics.drawRoundRect(panel.x, panel.y, panel.width, panel.height, 5, 5);
    }

    private void restoreAutoFlipGuidanceGraphics(
        Graphics2D graphics,
        java.awt.Shape oldClip,
        Composite oldComposite,
        Object oldTextAntialiasing,
        Font oldFont,
        Stroke oldStroke,
        Color oldColor)
    {
        graphics.setClip(oldClip);
        graphics.setComposite(oldComposite);
        if (oldTextAntialiasing != null)
        {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, oldTextAntialiasing);
        }
        graphics.setFont(oldFont);
        graphics.setStroke(oldStroke);
        graphics.setColor(oldColor);
    }

    private void drawAutoFlipShadowedFittedText(
        Graphics2D graphics,
        String rawText,
        int x,
        int baselineY,
        int maxWidth,
        Color color,
        boolean shadowEnabled,
        int shadowAlpha)
    {
        String text = rawText == null ? "" : rawText.trim();
        FontMetrics metrics = graphics.getFontMetrics();
        if (metrics.stringWidth(text) > maxWidth)
        {
            String suffix = "...";
            int suffixWidth = metrics.stringWidth(suffix);
            while (!text.isEmpty() && metrics.stringWidth(text) + suffixWidth > maxWidth)
            {
                text = text.substring(0, text.length() - 1);
            }
            text = text.trim() + suffix;
        }

        if (shadowEnabled)
        {
            graphics.setColor(new Color(0, 0, 0, Math.max(0, Math.min(255, shadowAlpha))));
            graphics.drawString(text, x + 1, baselineY + 1);
        }
        graphics.setColor(color);
        graphics.drawString(text, x, baselineY);
    }
    private void drawStaticGhostCards(Graphics2D graphics)
    {
        plugin.clearAutoFlipCardActionBounds();
        plugin.clearAutoFlipCardBlockBounds();
        drawAutoFlipUnavailableMembershipSlots(graphics);
        java.util.List<AutoFlipPlugin.AutoFlipBoardCard> dynamicCards = plugin.getAutoFlipBoardCardsSnapshot();

        if (dynamicCards != null && !dynamicCards.isEmpty())
        {
            int rendered = 0;

            for (AutoFlipPlugin.AutoFlipBoardCard card : dynamicCards)
            {
                if (card == null)
                {
                    continue;
                }

                boolean itemAllowedForAccount = plugin.isAutoFlipItemAllowedForCurrentAccount(card.getItemId());
                if (!plugin.isAutoFlipGeSlotUsableForCurrentAccount(card.getSlotIndex())
                    || !plugin.isAutoFlipGeSlotAvailableForRecommendation(card.getSlotIndex())
                    || !AutoFlipPlugin.isAutoFlipPriorityCardAllowedForCurrentAccount(
                        card.getRiskLabel(),
                        itemAllowedForAccount))
                {
                    if (plugin.isAutoFlipVerboseRuntimeLoggingEnabled())
                    {
                        System.out.println(
                            "AUTOFLIP_BOARD_RENDER_SKIP"
                                + " reason=slot_unavailable"
                                + " slot=" + card.getSlotIndex()
                                + " item_id=" + card.getItemId()
                                + " rendered_index=" + rendered
                        );
                    }
                    continue;
                }

                Rectangle slotBounds = plugin.getGeSlotBoundsForOverlay(card.getSlotIndex());

                if (slotBounds == null)
                {
                    if (plugin.isAutoFlipVerboseRuntimeLoggingEnabled())
                    {
                        System.out.println(
                            "AUTOFLIP_BOARD_RENDER_SKIP"
                                + " reason=missing_slot_bounds"
                                + " slot=" + card.getSlotIndex()
                                + " item_id=" + card.getItemId()
                                + " rendered_index=" + rendered
                        );
                    }
                    continue;
                }

                String quantity = "x" + String.format(java.util.Locale.US, "%,d", Math.max(0, card.getQuantity()));
                String eachProfit = "+" + String.format(java.util.Locale.US, "%,d gp", card.getProfitEachGp());
                String totalProfit = String.format(java.util.Locale.US, "%,d gp", card.getTotalProfitGp());
                String timeHint = card.getMaxHoldTimeLabel() == null || card.getMaxHoldTimeLabel().isEmpty()
                    ? card.getRiskLabel()
                    : card.getMaxHoldTimeLabel();

                int cardX = slotBounds.x + config.cardOffsetX();
                int cardY = slotBounds.y + config.cardOffsetY();

                int cardWidth = config.cardWidth();
                int cardHeight = config.cardHeight();
                boolean sellPlan = isAutoFlipSellPlan(card.getReason(), card.getItemName());
                Rectangle nativeButtonHole = getAutoFlipNativeButtonHole(cardX, cardY, cardWidth, cardHeight, sellPlan);

                // AUTOFLIP_PATCH_H_TRUE_PANEL_CUTOUT_V1
                // Do not draw a peer-through box. Instead, do not paint the ghost panel
                // inside the native Buy/Sell button hole at all.
                java.awt.Shape oldClip = graphics.getClip();
                java.awt.geom.Area cutoutClip = new java.awt.geom.Area(
                    oldClip == null
                        ? new Rectangle(0, 0, 10000, 10000)
                        : oldClip
                );
                cutoutClip.subtract(new java.awt.geom.Area(new Rectangle(
                    nativeButtonHole.x - 1,
                    nativeButtonHole.y - 1,
                    nativeButtonHole.width + 2,
                    nativeButtonHole.height + 2
                )));

                graphics.setClip(cutoutClip);
                drawGhostCard(
                    graphics,
                    cardX,
                    cardY,
                    new PlanCard(
                        card.getItemName(),
                        quantity,
                        eachProfit,
                        totalProfit,
                        timeHint == null ? "" : timeHint,
                        card.getReason() == null ? "" : card.getReason(),
                        new Color(95, 63, 28, 230),
                        new Color(214, 139, 60, 210)
                    ),
                    card.getItemId()
                );
                graphics.setClip(oldClip);

                Rectangle recommendationMarketBounds = null;
                if (AutoFlipGeMarketLink.shouldShowRecommendationLink(plugin.isAutoFlipOverlayActive(), card.getItemId()))
                {
                    recommendationMarketBounds = new Rectangle(cardX + cardWidth - 21, cardY + 42, 16, 16);
                    drawCardWebsiteIcon(
                        graphics,
                        recommendationMarketBounds.x,
                        recommendationMarketBounds.y,
                        recommendationMarketBounds.width
                    );
                }

                if (!sellPlan)
                {
                    drawAutoFlipBuyGuidanceCard(
                        graphics,
                        card,
                        cardX,
                        cardY,
                        cardWidth,
                        cardHeight,
                        nativeButtonHole
                    );
                }

                // AUTOFLIP_CARD_SURFACE_BLOCKS_NATIVE_GE_DYNAMIC_V1
                // The AutoFlip card is the input-blocking surface. Only the tuned native Buy/Sell hole remains pass-through.
                Rectangle cardSurfaceBounds = new Rectangle(cardX, cardY, cardWidth, cardHeight);
                rememberAutoFlipSlotMaskBlockBounds(cardSurfaceBounds, nativeButtonHole);
                plugin.rememberAutoFlipNativeButtonHoleBounds(card.getSlotIndex(), card.getItemId(), nativeButtonHole);
                plugin.rememberAutoFlipCardActionBounds(
                    card.getSlotIndex(),
                    card.getItemId(),
                    plugin.getAutoFlipMarketItemUrl(card.getItemId(), card.getItemName()),
                    new Rectangle(cardX + cardWidth - 32, cardY + 23, 28, 16),
                    recommendationMarketBounds
                );

                rendered++;
            }

            if (rendered > 0)
            {
                return;
            }
        }

        // AUTOFLIP_NO_LEGACY_DUMMY_BOARD_PLACEHOLDERS_V1
        // If no real local/Release cards are loaded, render no fake board cards.
        // This prevents old test items from flashing before the real payload-backed board exists.
    }

    private void drawAutoFlipBuyGuidanceCard(
        Graphics2D graphics,
        AutoFlipPlugin.AutoFlipBoardCard card,
        int cardX,
        int cardY,
        int cardWidth,
        int cardHeight,
        Rectangle nativeButtonHole)
    {
        if (!config.buyGuidanceEnabled())
        {
            return;
        }

        Rectangle panel = AutoFlipCardLayout.compactTimingPanel(
            cardX,
            cardY,
            cardWidth,
            cardHeight,
            nativeButtonHole,
            false
        );

        Color oldColor = graphics.getColor();
        Stroke oldStroke = graphics.getStroke();
        Font oldFont = graphics.getFont();
        Composite oldComposite = graphics.getComposite();
        java.awt.Shape oldClip = graphics.getClip();
        Object oldTextAntialiasing = graphics.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);

        java.awt.geom.Area readyClip = new java.awt.geom.Area(
            oldClip == null ? new Rectangle(0, 0, 10000, 10000) : oldClip
        );
        readyClip.subtract(new java.awt.geom.Area(nativeButtonHole));
        graphics.setClip(readyClip);

        BufferedImage asset = buyReadyAsset.load(config.buyGuidanceAssetPath());
        drawAutoFlipGuidancePanelBackground(
            graphics,
            panel,
            asset,
            config.buyGuidanceAssetAlpha(),
            config.buyGuidanceFill(),
            config.buyGuidanceBorder(),
            config.buyGuidanceBorderWidth()
        );

        graphics.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            config.buyGuidanceTextSharpEnabled()
                ? RenderingHints.VALUE_TEXT_ANTIALIAS_OFF
                : RenderingHints.VALUE_TEXT_ANTIALIAS_ON
        );

        int textX = panel.x + config.buyGuidanceTextX();
        int maxTextWidth = Math.max(12, panel.width - config.buyGuidanceTextX() - 4);
        graphics.setFont(autoFlipCardFont(oldFont, Font.BOLD, config.buyGuidanceTitleFont()));
        String guidanceTitle = "TO_BUY".equalsIgnoreCase(card.getRiskLabel()) ? "BUY" : "Next Flip";
        drawAutoFlipShadowedFittedText(
            graphics,
            guidanceTitle,
            textX,
            panel.y + config.buyGuidanceTitleY(),
            maxTextWidth,
            config.buyGuidanceTitleColor(),
            config.buyGuidanceShadowEnabled(),
            config.buyGuidanceShadowAlpha()
        );

        restoreAutoFlipGuidanceGraphics(
            graphics,
            oldClip,
            oldComposite,
            oldTextAntialiasing,
            oldFont,
            oldStroke,
            oldColor
        );
    }

    private void drawAutoFlipUnavailableMembershipSlots(Graphics2D graphics)
    {
        if (graphics == null || plugin == null || !plugin.isAutoFlipFreeToPlayAccount())
        {
            return;
        }

        int firstUnavailableSlot = plugin.getAutoFlipUsableGeSlotCountForCurrentAccount();
        for (int slot = firstUnavailableSlot; slot < 8; slot++)
        {
            Rectangle slotBounds = plugin.getGeSlotBoundsForOverlay(slot);
            if (slotBounds == null)
            {
                continue;
            }

            int cardX = slotBounds.x + config.cardOffsetX();
            int cardY = slotBounds.y + config.cardOffsetY();
            int cardWidth = config.cardWidth();
            int cardHeight = config.cardHeight();
            Rectangle cardSurfaceBounds = new Rectangle(cardX, cardY, cardWidth, cardHeight);

            plugin.rememberAutoFlipCardBlockBounds(cardSurfaceBounds);
            drawAutoFlipUnavailableMembershipSlot(graphics, cardSurfaceBounds, slot);
        }
    }

    private void drawAutoFlipOccupiedOfferSlots(Graphics2D graphics, java.util.List<AutoFlipPlugin.AutoFlipBoardCard> dynamicCards)
    {
        if (graphics == null || plugin == null)
        {
            return;
        }

        java.util.Set<Integer> cardSlots = new java.util.HashSet<>();
        if (dynamicCards != null)
        {
            for (AutoFlipPlugin.AutoFlipBoardCard card : dynamicCards)
            {
                if (card != null)
                {
                    cardSlots.add(card.getSlotIndex());
                }
            }
        }

        int usableSlots = plugin.getAutoFlipUsableGeSlotCountForCurrentAccount();
        for (int slot = 0; slot < usableSlots; slot++)
        {
            if (cardSlots.contains(slot) || plugin.isAutoFlipGeSlotAvailableForRecommendation(slot))
            {
                continue;
            }

            Rectangle slotBounds = plugin.getGeSlotBoundsForOverlay(slot);
            if (slotBounds == null)
            {
                continue;
            }

            int cardX = slotBounds.x + config.cardOffsetX();
            int cardY = slotBounds.y + config.cardOffsetY();
            Rectangle cardSurfaceBounds = new Rectangle(cardX, cardY, config.cardWidth(), config.cardHeight());

            plugin.rememberAutoFlipCardBlockBounds(cardSurfaceBounds);
            drawAutoFlipReservedOfferSlot(graphics, cardSurfaceBounds, slot);
        }
    }

    private void drawAutoFlipReservedOfferSlot(Graphics2D graphics, Rectangle bounds, int slot)
    {
        Color oldColor = graphics.getColor();
        Font oldFont = graphics.getFont();
        Stroke oldStroke = graphics.getStroke();

        graphics.setColor(new Color(20, 14, 8, 210));
        graphics.fillRoundRect(bounds.x + 2, bounds.y + 3, bounds.width, bounds.height, 8, 8);

        graphics.setColor(new Color(70, 48, 30, 238));
        graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

        graphics.setStroke(new BasicStroke(1.3f));
        graphics.setColor(new Color(221, 148, 48, 215));
        graphics.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

        int itemId = plugin.getGeSlotItemIdForOverlay(slot);
        int quantity = plugin.getGeSlotQuantityForOverlay(slot);
        int iconSize = Math.max(24, Math.min(34, bounds.width / 3));
        int iconX = bounds.x + ((bounds.width - iconSize) / 2);
        int iconY = bounds.y + 7;
        BufferedImage itemImage = itemId > 0 ? plugin.getAutoFlipInventoryItemImage(itemId) : null;

        if (itemImage != null)
        {
            int drawW = itemImage.getWidth();
            int drawH = itemImage.getHeight();
            if (drawW > iconSize || drawH > iconSize)
            {
                double scale = Math.min((double) iconSize / Math.max(1, drawW), (double) iconSize / Math.max(1, drawH));
                drawW = Math.max(1, (int) Math.round(drawW * scale));
                drawH = Math.max(1, (int) Math.round(drawH * scale));
            }
            graphics.drawImage(
                itemImage,
                iconX + ((iconSize - drawW) / 2),
                iconY + ((iconSize - drawH) / 2),
                drawW,
                drawH,
                null
            );
        }

        if (quantity > 0)
        {
            graphics.setFont(autoFlipCardFont(oldFont, Font.BOLD, Math.max(9, config.cardQuantityFont())));
            graphics.setColor(new Color(246, 188, 80, 245));
            drawCenteredString(
                graphics,
                "x" + String.format(java.util.Locale.US, "%,d", quantity),
                bounds.x + 3,
                iconY + iconSize + 10,
                bounds.width - 6
            );
        }

        graphics.setFont(autoFlipCardFont(oldFont, Font.BOLD, Math.max(10, config.cardNameFont())));
        graphics.setColor(new Color(255, 220, 146, 245));
        drawCenteredString(graphics, "Active offer", bounds.x + 3, bounds.y + bounds.height - 32, bounds.width - 6);

        graphics.setFont(autoFlipCardFont(oldFont, Font.PLAIN, Math.max(9, config.cardQuantityFont())));
        graphics.setColor(new Color(213, 188, 135, 235));
        drawCenteredString(graphics, "Clear to reuse slot", bounds.x + 3, bounds.y + bounds.height - 15, bounds.width - 6);

        graphics.setStroke(oldStroke);
        graphics.setFont(oldFont);
        graphics.setColor(oldColor);
    }

    private void drawAutoFlipUnavailableMembershipSlot(Graphics2D graphics, Rectangle bounds, int slot)
    {
        Color oldColor = graphics.getColor();
        Font oldFont = graphics.getFont();
        Stroke oldStroke = graphics.getStroke();

        graphics.setColor(new Color(18, 17, 15, 205));
        graphics.fillRoundRect(bounds.x + 2, bounds.y + 3, bounds.width, bounds.height, 8, 8);

        graphics.setColor(new Color(55, 50, 42, 238));
        graphics.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

        graphics.setStroke(new BasicStroke(1.2f));
        graphics.setColor(new Color(120, 107, 80, 190));
        graphics.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 8, 8);

        int cx = bounds.x + (bounds.width / 2);
        int cy = bounds.y + (bounds.height / 2) - 8;
        int r = Math.max(13, Math.min(18, bounds.width / 5));

        graphics.setStroke(new BasicStroke(2.4f));
        graphics.setColor(new Color(172, 153, 105, 220));
        graphics.drawOval(cx - r, cy - r, r * 2, r * 2);
        graphics.drawLine(cx - r + 4, cy + r - 4, cx + r - 4, cy - r + 4);

        graphics.setFont(autoFlipCardFont(oldFont, Font.BOLD, Math.max(10, config.cardNameFont())));
        graphics.setColor(new Color(226, 206, 151, 235));
        drawCenteredString(graphics, "Members slot", bounds.x + 3, bounds.y + bounds.height - 30, bounds.width - 6);

        graphics.setFont(autoFlipCardFont(oldFont, Font.PLAIN, Math.max(9, config.cardQuantityFont())));
        graphics.setColor(new Color(176, 161, 124, 230));
        drawCenteredString(graphics, "F2P uses slots 1-3", bounds.x + 3, bounds.y + bounds.height - 14, bounds.width - 6);

        graphics.setStroke(oldStroke);
        graphics.setFont(oldFont);
        graphics.setColor(oldColor);
    }

    private void drawAutoFlipBoardSummaryStrip(Graphics2D graphics)
    {
        java.util.List<AutoFlipPlugin.AutoFlipBoardCard> cards = plugin.getAutoFlipBoardCardsSnapshot();
        if (graphics == null || cards == null || cards.isEmpty())
        {
            return;
        }

        long budgetPlanned = plugin.getAutoFlipBoardBudgetPlannedGp();
        long totalProfit = plugin.getAutoFlipBoardTotalExpectedProfitGp();

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        for (int slot = 0; slot < 8; slot++)
        {
            Rectangle slotBounds = plugin.getGeSlotBoundsForOverlay(slot);
            if (slotBounds == null)
            {
                continue;
            }

            int cardX = slotBounds.x + config.cardOffsetX();
            int cardY = slotBounds.y + config.cardOffsetY();
            int cardW = config.cardWidth();
            int cardH = config.cardHeight();

            minX = Math.min(minX, cardX);
            maxX = Math.max(maxX, cardX + cardW);
            maxY = Math.max(maxY, cardY + cardH);
        }

        Rectangle clip = graphics.getClipBounds();

        if (minX == Integer.MAX_VALUE || maxX == Integer.MIN_VALUE || maxY == Integer.MIN_VALUE)
        {
            int canvasWidth = clip == null ? 765 : Math.max(320, clip.width);
            minX = 12;
            maxX = Math.min(canvasWidth - 12, 520);
            maxY = 238;
        }

        int panelX = minX;
        int panelW = Math.max(260, maxX - minX);
        int panelH = 34;
        int panelY = maxY + 8;

        if (clip != null && panelY + panelH > clip.height - 8)
        {
            panelY = Math.max(8, maxY - panelH - 8);
        }

        Color oldColor = graphics.getColor();
        Font oldFont = graphics.getFont();
        Stroke oldStroke = graphics.getStroke();

        graphics.setColor(new Color(0, 0, 0, 172));
        graphics.fillRoundRect(panelX + 2, panelY + 2, panelW, panelH, 9, 9);

        graphics.setColor(new Color(32, 18, 8, 232));
        graphics.fillRoundRect(panelX, panelY, panelW, panelH, 9, 9);

        graphics.setStroke(new BasicStroke(1.4f));
        graphics.setColor(new Color(197, 138, 29, 225));
        graphics.drawRoundRect(panelX, panelY, panelW, panelH, 9, 9);

        int midX = panelX + (panelW / 2);
        graphics.setColor(new Color(197, 138, 29, 150));
        graphics.drawLine(midX, panelY + 6, midX, panelY + panelH - 6);

        // AUTOFLIP_PATCH_M_WRITEONLY_DRAW_BOARD_SUMMARY_V1
        long budgetLimit = plugin.getAutoFlipOverlayBudgetGp();

        graphics.setFont(oldFont.deriveFont(Font.PLAIN, 9.0f));
        graphics.setColor(new Color(238, 229, 196, 235));
        graphics.drawString("Budget deployed / Budget", panelX + 10, panelY + 12);
        graphics.drawString("Expected profit", midX + 10, panelY + 12);

        graphics.setFont(oldFont.deriveFont(Font.BOLD, 13.0f));
        graphics.setColor(new Color(255, 216, 42, 255));
        graphics.drawString(formatAutoFlipSummaryGp(budgetPlanned) + " / " + formatAutoFlipSummaryGp(budgetLimit), panelX + 10, panelY + 28);

        graphics.setColor(new Color(105, 255, 154, 255));
        graphics.drawString(formatAutoFlipSummaryGp(totalProfit), midX + 10, panelY + 28);

        graphics.setStroke(oldStroke);
        graphics.setFont(oldFont);
        graphics.setColor(oldColor);
    }

    private String formatAutoFlipSummaryGp(long value)
    {
        return String.format(java.util.Locale.US, "%,d gp", Math.max(0L, value));
    }
    private void drawExplanationPanel(Graphics2D graphics, Rectangle headerBounds)
    {
        int minX = headerBounds.x;
        int minY = headerBounds.y;
        int maxX = headerBounds.x + headerBounds.width;
        int maxY = headerBounds.y + headerBounds.height;

        for (int slot = 0; slot < 8; slot++)
        {
            Rectangle slotBounds = plugin.getGeSlotBoundsForOverlay(slot);
            if (slotBounds != null)
            {
                minX = Math.min(minX, slotBounds.x);
                minY = Math.min(minY, slotBounds.y);
                maxX = Math.max(maxX, slotBounds.x + slotBounds.width);
                maxY = Math.max(maxY, slotBounds.y + slotBounds.height);
            }
        }

        int geWidth = Math.max(1, maxX - minX);
        int panelWidth = config.explanationPanelWidth() > 0 ? config.explanationPanelWidth() : geWidth;
        int panelHeight = config.explanationPanelHeight();
        int x = minX + config.explanationPanelOffsetX();
        int y = maxY + config.explanationPanelOffsetY();

        Color shadow = new Color(0, 0, 0, 165);
        Color panel = new Color(35, 26, 18, config.explanationPanelAlpha());
        Color top = new Color(58, 42, 25, Math.min(255, config.explanationPanelAlpha() + 18));
        Color border = new Color(202, 158, 76, config.explanationPanelBorderAlpha());

        graphics.setColor(shadow);
        graphics.fillRoundRect(x + 3, y + 4, panelWidth, panelHeight, 10, 10);

        graphics.setColor(panel);
        graphics.fillRoundRect(x, y, panelWidth, panelHeight, 10, 10);

        graphics.setColor(top);
        graphics.fillRoundRect(x, y, panelWidth, 24, 10, 10);
        graphics.fillRect(x, y + 12, panelWidth, 12);

        graphics.setColor(border);
        graphics.drawRoundRect(x, y, panelWidth, panelHeight, 10, 10);

        Font oldFont = graphics.getFont();

        graphics.setColor(new Color(255, 226, 150));
        graphics.setFont(oldFont.deriveFont(Font.BOLD, config.explanationTitleFont()));
        graphics.drawString("AutoFlip Trade Details", x + 12, y + 17);

        int lineY = y + 39;
        graphics.setFont(oldFont.deriveFont(Font.PLAIN, config.explanationBodyFont()));

        for (int i = 0; i < PLACEHOLDER_PLAN.length; i++)
        {
            PlanCard card = PLACEHOLDER_PLAN[i];

            graphics.setColor(new Color(139, 255, 159));
            graphics.drawString((i + 1) + ". " + card.itemName + " " + card.quantity + " - " + card.shortReason, x + 12, lineY);
            lineY += config.explanationLineGap();

            graphics.setColor(new Color(235, 213, 170));
            graphics.drawString("   Profit/item " + card.profitEach + " | Total " + card.totalProfit + " | " + card.timeHint, x + 12, lineY);
            lineY += config.explanationLineGap() + 3;
        }

        graphics.setFont(oldFont);
    }
    private void drawEditHandles(Graphics2D graphics)
    {
        Stroke oldStroke = graphics.getStroke();
        Font oldFont = graphics.getFont();

        graphics.setStroke(new BasicStroke(1.0f));
        graphics.setFont(oldFont.deriveFont(Font.BOLD, config.editHandleFont()));

        int cardsDrawn = 0;

        for (int slot = 0; slot < 8 && cardsDrawn < PLACEHOLDER_PLAN.length; slot++)
        {
            Rectangle slotBounds = plugin.getGeSlotBoundsForOverlay(slot);
            if (slotBounds == null)
            {
                continue;
            }

            int x = slotBounds.x + config.cardOffsetX();
            int y = slotBounds.y + config.cardOffsetY();
            int cardWidth = config.cardWidth();

            int iconX = x + config.cardIconX();
            int iconY = y + AutoFlipCardLayout.raisedY(config.cardIconY());
            int iconSize = config.cardIconSize();

            int iconBoxX = iconX + config.cardIconBoxOffsetX();
            int iconBoxY = iconY + config.cardIconBoxOffsetY();
            int iconGlowX = iconX - 6 + config.cardIconGlowOffsetX();
            int iconGlowY = iconY - 5 + config.cardIconGlowOffsetY();

            drawEditHandle(graphics, "ICON_BOX", iconBoxX, iconBoxY, iconSize, iconSize);
            drawEditHandle(graphics, "ICON_GLOW", iconGlowX, iconGlowY, iconSize + 12, iconSize + 12);

            int quantityHandleWidth = config.editQuantityHandleWidth();
            int quantityHandleX = x + config.cardQuantityX() + ((cardWidth - quantityHandleWidth) / 2);
            drawEditHandle(graphics, "", quantityHandleX, y + AutoFlipCardLayout.raisedY(52) - config.editTextBoxBaselinePadY(), quantityHandleWidth, config.editTextBoxHeight());

            drawEditHandle(graphics, "", x + config.cardProfitLabelX(), y + config.cardProfitLabelY() - config.editTextBoxBaselinePadY(), config.editEachLabelBoxWidth(), config.editTextBoxHeight());
            drawEditHandle(graphics, "", x + config.cardProfitValueX(), y + config.cardProfitValueY() - config.editTextBoxBaselinePadY(), config.editEachValueBoxWidth(), config.editTextBoxHeight());

            drawEditHandle(graphics, "", x + config.cardTotalLabelX(), y + config.cardTotalLabelY() - config.editTextBoxBaselinePadY(), config.editTotalLabelBoxWidth(), config.editTextBoxHeight());
            drawEditHandle(graphics, "", x + config.cardTotalValueX(), y + config.cardTotalValueY() - config.editTextBoxBaselinePadY(), config.editTotalValueBoxWidth(), config.editTextBoxHeight());

            // AUTOFLIP_TUNABLE_BUY_SELL_HOLE_EDIT_HANDLES_V1
            Rectangle buyHole = new Rectangle(
                x + config.getInt("native.buy.hole.x", 2),
                y + config.getInt("native.buy.hole.y", 61),
                config.getInt("native.buy.hole.w", 44),
                config.getInt("native.buy.hole.h", 44)
            );
            Rectangle sellHole = new Rectangle(
                x + config.getInt("native.sell.hole.x", 68),
                y + config.getInt("native.sell.hole.y", 61),
                config.getInt("native.sell.hole.w", 44),
                config.getInt("native.sell.hole.h", 44)
            );

            drawNativeHoleEditHandle(graphics, "BUY", buyHole, new Color(80, 255, 120, 230));
            drawNativeHoleEditHandle(graphics, "SELL", sellHole, new Color(80, 170, 255, 230));

            cardsDrawn++;
        }

        graphics.setFont(oldFont);
        graphics.setStroke(oldStroke);
    }

    private void drawNativeHoleEditHandle(Graphics2D graphics, String label, Rectangle r, Color color)
    {
        if (graphics == null || r == null)
        {
            return;
        }

        Stroke oldStroke = graphics.getStroke();
        Font oldFont = graphics.getFont();
        Color oldColor = graphics.getColor();

        try
        {
            // AUTOFLIP_NATIVE_HOLE_EDIT_HANDLE_HELPER_V1
            // AUTOFLIP_PATCH_G_TRANSPARENT_NATIVE_BOXES_V1
            // Outline only: do not paint a filled green/blue rectangle over the native GE button.
            graphics.setStroke(new BasicStroke(2.0f));
            graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 210));
            graphics.drawRoundRect(r.x, r.y, r.width, r.height, 8, 8);

            graphics.setFont(oldFont.deriveFont(Font.BOLD, 9.0f));
            graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 220));
            graphics.drawString(label, r.x + 3, r.y - 3);
        }
        finally
        {
            graphics.setFont(oldFont);
            graphics.setStroke(oldStroke);
            graphics.setColor(oldColor);
        }
    }
    private void drawEditHandle(Graphics2D graphics, String label, int x, int y, int width, int height)
    {
        int alpha = config.editHandleAlpha();
        Color fill = new Color(40, 180, 255, Math.max(0, Math.min(90, alpha / 3)));
        Color border = new Color(80, 220, 255, alpha);
        Color text = new Color(225, 252, 255, alpha);

        graphics.setColor(fill);
        graphics.fillRect(x, y, width, height);

        graphics.setColor(border);
        graphics.drawRect(x, y, width, height);

        if (config.editHandleLabelsEnabled() && label != null && !label.isEmpty())
        {
            graphics.setColor(text);
            graphics.drawString(label, x + 2, y - 3);
        }
    }
    private boolean isAutoFlipSellPlan(String reason, String itemName)
    {
        // AUTOFLIP_STRICT_SELL_PLAN_CLASSIFICATION_V1
        // Recommendation cards often mention "sell" in normal flip explanations.
        // Default to BUY unless the backend/reason gives a clear sell directive.
        String text = (reason == null ? "" : reason).trim().toLowerCase(java.util.Locale.ROOT);

        return text.startsWith("sell ")
            || text.startsWith("sell:")
            || text.startsWith("action: sell")
            || text.startsWith("ready to sell")
            || text.startsWith("liquidate")
            || text.startsWith("collect")
            || text.startsWith("exit");
    }

    private Rectangle getAutoFlipNativeButtonHole(int cardX, int cardY, int cardWidth, int cardHeight, boolean sellPlan)
    {
        // AUTOFLIP_TUNABLE_NATIVE_BUTTON_HOLE_V1
        // Tuned live from _runtime/overlay_dev_config.properties.
        String prefix = sellPlan ? "native.sell.hole." : "native.buy.hole.";

        int fallbackX = sellPlan ? Math.max(0, cardWidth - 46) : 2;
        int x = config.getInt(prefix + "x", fallbackX);
        int y = config.getInt(prefix + "y", Math.max(0, cardHeight - 49));
        int w = config.getInt(prefix + "w", 44);
        int h = config.getInt(prefix + "h", 44);

        w = Math.max(8, Math.min(120, w));
        h = Math.max(8, Math.min(120, h));

        return new Rectangle(cardX + x, cardY + y, w, h);
    }

    private void rememberAutoFlipSlotMaskBlockBounds(Rectangle outer, Rectangle hole)
    {
        if (outer == null)
        {
            return;
        }

        if (hole == null || !outer.intersects(hole))
        {
            plugin.rememberAutoFlipCardBlockBounds(new Rectangle(outer));
            return;
        }

        Rectangle h = hole.intersection(outer);

        int outerRight = outer.x + outer.width;
        int outerBottom = outer.y + outer.height;
        int holeRight = h.x + h.width;
        int holeBottom = h.y + h.height;

        rememberBlockRect(outer.x, outer.y, outer.width, h.y - outer.y);
        rememberBlockRect(outer.x, holeBottom, outer.width, outerBottom - holeBottom);
        rememberBlockRect(outer.x, h.y, h.x - outer.x, h.height);
        rememberBlockRect(holeRight, h.y, outerRight - holeRight, h.height);
    }

    private void rememberBlockRect(int x, int y, int width, int height)
    {
        if (width <= 0 || height <= 0)
        {
            return;
        }

        plugin.rememberAutoFlipCardBlockBounds(new Rectangle(x, y, width, height));
    }

    private void clearAutoFlipNativeButtonHole(Graphics2D graphics, Rectangle hole, boolean sellPlan)
    {
        // AUTOFLIP_PATCH_H_CLEAR_WINDOW_DISABLED_V1
        // Disabled: transparent cutout is now created by clipping before drawGhostCard().
        // Drawing or clearing after the card paint causes black/colored artifacts on RuneLite's frame graphics.
        return;
    }
    private void drawGhostCard(Graphics2D graphics, int x, int y, PlanCard cardData)
    {
        drawGhostCard(graphics, x, y, cardData, 0);
    }

    private void drawGhostCard(Graphics2D graphics, int x, int y, PlanCard cardData, int itemId)
    {
        // AUTOFLIP_PATCH_I_REAL_ITEM_ICONS_BY_ID_V1
        int cardWidth = config.cardWidth();
        int cardHeight = config.cardHeight();

        // AUTOFLIP_PATCH_K_GE_BROWN_GHOST_PANEL_V1
        // AUTOFLIP_PATCH_L_GE_SLOT_MATCH_COLOR_V1
        // Keep the ghost card opaque/click-blocking, but visually match the native GE empty-slot panel.
        Color shadow = new Color(66, 61, 50, 20);
        // AUTOFLIP_OPAQUE_SLOT_MASK_V1
        // Make the custom slot mask strong enough to hide the native Buy/Sell button except through the clear window.
        Color card = new Color(76, 70, 58, Math.max(245, config.cardAlpha()));
        Color top = new Color(76, 70, 58, Math.max(235, config.cardTopAlpha()));
        Color border = new Color(111, 101, 78, 185);
        Color inner = new Color(96, 88, 68, 95);
        Color green = new Color(124, 244, 145, 250);
        Color gold = new Color(232, 177, 73, 245);
        Color text = new Color(232, 219, 177, 250);
        Color muted = new Color(164, 143, 103, 235);

        graphics.setColor(shadow);
        graphics.fillRoundRect(x + 3, y + 4, cardWidth, cardHeight, 8, 8);

        graphics.setColor(card);
        graphics.fillRoundRect(x, y, cardWidth, cardHeight, 8, 8);

        graphics.setColor(top);
        graphics.fillRoundRect(x + 2, y + 2, cardWidth - 4, 19, 7, 7);

        graphics.setStroke(new BasicStroke(1.1f));
        graphics.setColor(border);
        graphics.drawRoundRect(x, y, cardWidth, cardHeight, 8, 8);

        graphics.setColor(inner);
        graphics.drawRoundRect(x + 2, y + 2, cardWidth - 4, cardHeight - 4, 6, 6);

        Font oldFont = graphics.getFont();

        graphics.setFont(autoFlipCardFont(oldFont, Font.BOLD, config.cardNameFont()));
        graphics.setColor(green);
        drawCenteredString(graphics, cardData.itemName, x + 3, y + 15, cardWidth - 6);
        drawCardSkipIcon(graphics, x + cardWidth - 32, y + 23, 28, 16);

        int iconX = x + config.cardIconX();
        int iconY = y + AutoFlipCardLayout.raisedY(config.cardIconY());
        int iconSize = config.cardIconSize();

        // AUTOFLIP_PATCH_J_REMOVE_ICON_GLOW_V1
        // Removed orange/colored circle behind the item icon. Keep only the icon box.

        graphics.setColor(new Color(67, 61, 49, 190));
        graphics.fillRoundRect(iconX - 1, iconY - 1, iconSize + 2, iconSize + 2, 6, 6);

        BufferedImage itemImage = itemId > 0 ? plugin.getAutoFlipInventoryItemImage(itemId) : null;
        if (itemImage != null)
        {
            int drawW = itemImage.getWidth();
            int drawH = itemImage.getHeight();

            if (drawW > iconSize || drawH > iconSize)
            {
                double scale = Math.min((double) iconSize / Math.max(1, drawW), (double) iconSize / Math.max(1, drawH));
                drawW = Math.max(1, (int) Math.round(drawW * scale));
                drawH = Math.max(1, (int) Math.round(drawH * scale));
            }

            int drawX = iconX + ((iconSize - drawW) / 2);
            int drawY = iconY + ((iconSize - drawH) / 2);
            graphics.drawImage(itemImage, drawX, drawY, drawW, drawH, null);
        }
        // No fake/dummy item art here. If RuneLite has not resolved the item ID yet,
        // leave the icon window empty instead of drawing placeholder logs/arrows/bags.


        graphics.setFont(autoFlipCardFont(oldFont, Font.BOLD, config.cardQuantityFont()));
        graphics.setColor(text);
        graphics.drawString(cardData.quantity, x + 55, y + AutoFlipCardLayout.raisedY(52));

        graphics.setColor(new Color(112, 87, 43, 120));
        graphics.drawLine(x + 8, y + 66, x + cardWidth - 8, y + 66);

        graphics.setFont(autoFlipCardFont(oldFont, Font.PLAIN, config.cardTotalLabelFont()));
        graphics.setColor(muted);
        graphics.drawString("Total", x + 10, y + 83);

        graphics.setFont(autoFlipCardFont(oldFont, Font.BOLD, config.cardTotalValueFont()));
        graphics.setColor(green);
        drawRightString(graphics, cardData.totalProfit, x + cardWidth - 8, y + 83);

        graphics.setColor(green);
        int dotY = y + cardHeight - 11;
        int dotStartX = x + cardWidth - 50;
        for (int i = 0; i < 5; i++)
        {
            graphics.fillOval(dotStartX + (i * 9), dotY, 6, 6);
        }

        graphics.setFont(oldFont);
    }


    private void drawCardSkipIcon(Graphics2D graphics, int x, int y, int width, int height)
    {
        Font oldFont = graphics.getFont();

        Color shadow = new Color(0, 0, 0, 130);
        Color fill = new Color(146, 32, 32, 245);
        Color top = new Color(223, 83, 83, 245);
        Color border = new Color(255, 151, 151, 255);
        Color text = new Color(255, 255, 255, 255);

        graphics.setColor(shadow);
        graphics.fillRoundRect(x + 2, y + 2, width, height, 5, 5);

        graphics.setColor(fill);
        graphics.fillRoundRect(x, y, width, height, 5, 5);

        graphics.setColor(top);
        graphics.drawLine(x + 2, y + 2, x + width - 3, y + 2);

        graphics.setColor(border);
        graphics.drawRoundRect(x, y, width, height, 5, 5);

        graphics.setFont(oldFont.deriveFont(Font.BOLD, Math.max(9.0f, Math.min(width, height) - 6.0f)));
        FontMetrics metrics = graphics.getFontMetrics();
        String label = "SKIP";
        int textX = x + ((width - metrics.stringWidth(label)) / 2);
        int textY = y + ((height - metrics.getHeight()) / 2) + metrics.getAscent();

        graphics.setColor(text);
        graphics.drawString(label, textX, textY);

        graphics.setFont(oldFont);
    }

    private void drawCardWebsiteIcon(Graphics2D graphics, int x, int y, int size)
    {
        Image image = getCardWebsiteButtonImage();

        if (image != null)
        {
            graphics.drawImage(image, x, y, size, size, null);
        }
    }

    private void drawGeMarketItemLinks(Graphics2D graphics)
    {
        plugin.clearAutoFlipGeMarketLinkBounds();
        if (!AutoFlipGeMarketLink.shouldShowOccupiedOfferLink(plugin.isAutoFlipOverlayActive()))
        {
            return;
        }

        for (int slot = 0; slot < 8; slot++)
        {
            int itemId = plugin.getGeSlotItemIdForOverlay(slot);
            if (itemId <= 0)
            {
                continue;
            }

            Rectangle bounds = AutoFlipGeMarketLink.iconBounds(
                plugin.getGeSlotBoundsForOverlay(slot),
                config.cardOffsetX(),
                config.cardOffsetY(),
                config.cardWidth()
            );
            if (bounds == null)
            {
                continue;
            }

            String itemName = plugin.getAutoFlipItemNameForDebug(itemId);
            drawCardWebsiteIcon(graphics, bounds.x, bounds.y, bounds.width);
            plugin.rememberAutoFlipGeMarketLinkBounds(slot, itemId, itemName, bounds);
        }
    }

    private Image getCardWebsiteButtonImage()
    {
        if (cardWebsiteButtonImage != null)
        {
            return cardWebsiteButtonImage;
        }

        try
        {
            try (InputStream stream = AutoFlipOverlay.class.getResourceAsStream(MARKET_LINK_ICON_RESOURCE))
            {
                if (stream != null)
                {
                    BufferedImage bundledImage = ImageIO.read(stream);
                    if (bundledImage != null)
                    {
                        BufferedImage cropped = cropTransparentPadding(bundledImage);
                        cardWebsiteButtonImage = cropped == null ? bundledImage : cropped;
                        return cardWebsiteButtonImage;
                    }
                }
            }

            // Local development fallback for older unpacked workspaces.
            Path iconPath = Paths.get("C:\\osrs-flip-assistant\\ProjectPhotos\\Logos\\AppIcon_Favicon4.png");
            if (!Files.exists(iconPath))
            {
                return null;
            }

            BufferedImage image = ImageIO.read(iconPath.toFile());
            if (image == null)
            {
                return null;
            }
            BufferedImage cropped = cropTransparentPadding(image);
            cardWebsiteButtonImage = cropped == null ? image : cropped;
            return cardWebsiteButtonImage;
        }
        catch (Exception ignored)
        {
            return null;
        }
    }
    private void drawRightString(Graphics2D graphics, String value, int rightX, int baselineY)
    {
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(value, rightX - metrics.stringWidth(value), baselineY);
    }

    private void drawPlaceholderItemIcon(Graphics2D graphics, String itemName, int x, int y, int size)
    {
        if (itemName.toLowerCase(java.util.Locale.ROOT).contains("log"))
        {
            drawLogIcon(graphics, x, y, size);
            return;
        }

        if (itemName.toLowerCase(java.util.Locale.ROOT).contains("tip"))
        {
            drawArrowtipIcon(graphics, x, y, size);
            return;
        }

        graphics.setColor(new Color(94, 72, 38, 235));
        graphics.fillRoundRect(x, y, size, size, 6, 6);
        graphics.setColor(new Color(232, 209, 142, 220));
        graphics.drawRoundRect(x, y, size, size, 6, 6);
    }

    private void drawLogIcon(Graphics2D graphics, int x, int y, int size)
    {
        Color bark = new Color(162, 145, 91, 235);
        Color end = new Color(215, 211, 152, 235);
        Color ring = new Color(92, 75, 43, 220);
        Color band = new Color(134, 220, 245, 235);

        int h = Math.max(7, size / 4);
        int w = Math.max(20, size + 11);
        int startX = x - 3;

        for (int i = 0; i < 3; i++)
        {
            int yy = y + 4 + (i * Math.max(6, h - 1));
            graphics.setColor(bark);
            graphics.fillRoundRect(startX + (i % 2) * 2, yy, w, h, 5, 5);
            graphics.setColor(end);
            graphics.fillOval(startX + (i % 2) * 2 - 2, yy, h + 1, h);
            graphics.setColor(ring);
            graphics.drawOval(startX + (i % 2) * 2 - 1, yy + 1, h - 1, Math.max(1, h - 2));
            graphics.setColor(band);
            graphics.fillRoundRect(startX + w - 8 + (i % 2) * 2, yy + 1, 4, Math.max(2, h - 2), 3, 3);
        }
    }

    private void drawArrowtipIcon(Graphics2D graphics, int x, int y, int size)
    {
        Color body = new Color(147, 151, 118, 235);
        Color edge = new Color(222, 221, 171, 225);
        Color dark = new Color(64, 62, 45, 220);

        for (int i = 0; i < 4; i++)
        {
            int xx = x + 1 + ((i % 2) * 12);
            int yy = y + 2 + ((i / 2) * 10);
            Polygon p = new Polygon();
            p.addPoint(xx, yy + 5);
            p.addPoint(xx + 16, yy);
            p.addPoint(xx + 12, yy + 8);
            p.addPoint(xx + 3, yy + 10);
            graphics.setColor(body);
            graphics.fillPolygon(p);
            graphics.setColor(edge);
            graphics.drawLine(xx + 2, yy + 5, xx + 15, yy + 1);
            graphics.setColor(dark);
            graphics.drawPolygon(p);
        }
    }
    private Font autoFlipCardFont(Font fallback, int style, float size)
    {
        Font base = fallback;

        if (config.cardRuneScapeFontEnabled())
        {
            String methodName;
            if (style == Font.BOLD)
            {
                methodName = "getRunescapeBoldFont";
            }
            else if (size <= 8.5f)
            {
                methodName = "getRunescapeSmallFont";
            }
            else
            {
                methodName = "getRunescapeFont";
            }

            base = loadRuneLiteFont(methodName, fallback);
        }

        return base.deriveFont(style, size * config.cardFontScale());
    }

    private Font loadRuneLiteFont(String methodName, Font fallback)
    {
        try
        {
            Class<?> fontManager = Class.forName("net.runelite.client.ui.FontManager");
            Object result = fontManager.getMethod(methodName).invoke(null);
            if (result instanceof Font)
            {
                return (Font) result;
            }
        }
        catch (Exception ignored)
        {
            return fallback;
        }

        return fallback;
    }
    // AUTOFLIP_GUIDED_SETUP_FLOW_DRAW_METHOD_V14
    // AUTOFLIP_PATCH_SETUP_PERSISTENT_HOLES_VISUAL_V2
    private void drawAutoFlipGuidedSetupFlowOverlay(Graphics2D graphics, Rectangle header)
    {
        if (graphics == null || plugin == null)
        {
            return;
        }

        boolean ordinarySellPromptOpen = plugin.isAutoFlipOrdinarySellPricePromptOpenForOverlay();
        boolean ordinarySellMismatch = plugin.isAutoFlipOrdinarySellPriceMismatchForOverlay();
        boolean ordinaryBuyPromptOpen = plugin.isAutoFlipOrdinaryBuyPricePromptOpenForOverlay();
        boolean ordinaryBuyMismatch = plugin.isAutoFlipOrdinaryBuyPriceMismatchForOverlay();
        boolean ordinarySellPrice = ordinarySellPromptOpen || ordinarySellMismatch || ordinaryBuyPromptOpen || ordinaryBuyMismatch;
        String stage = (ordinarySellPromptOpen || ordinaryBuyPromptOpen)
            ? "price_enter"
            : (ordinarySellMismatch || ordinaryBuyMismatch)
                ? "price_click"
                : plugin.getAutoFlipGuidedSetupStage();
        if (stage == null || stage.isEmpty())
        {
            return;
        }

        Rectangle clip = graphics.getClipBounds();
        int canvasHeight = clip == null ? 503 : clip.height;

        Rectangle itemIconHole = new Rectangle(
            header.x + config.getInt("setup.item.icon.x", 56),
            header.y + config.getInt("setup.item.icon.y", 48),
            config.getInt("setup.item.icon.w", 42),
            config.getInt("setup.item.icon.h", 40)
        );

        Rectangle itemResultHole = new Rectangle(
            config.getInt("setup.item.result.x", 4),
            config.getInt("setup.item.result.y", Math.max(330, canvasHeight - 128)),
            config.getInt("setup.item.result.w", 245),
            config.getInt("setup.item.result.h", 52)
        );
        // AUTOFLIP_PATCH_SETUP_ITEM_SEARCH_HIGHLIGHT_ROUTING_V1_CHATBOX_RECT
        Rectangle itemSearchChatboxHole = new Rectangle(
            config.getInt("setup.item.search.chatbox.x", 84),
            config.getInt("setup.item.search.chatbox.y", Math.max(330, canvasHeight - 170)),
            config.getInt("setup.item.search.chatbox.w", 410),
            config.getInt("setup.item.search.chatbox.h", 26)
        );
        // AUTOFLIP_PATCH_DYNAMIC_SEARCH_CHATBOX_ANCHOR_V1
        Rectangle liveItemSearchChatboxHole = plugin.getAutoFlipGuidedSetupSearchInputBounds();
        if (liveItemSearchChatboxHole != null && liveItemSearchChatboxHole.width > 0 && liveItemSearchChatboxHole.height > 0)
        {
            itemSearchChatboxHole = liveItemSearchChatboxHole;
        }

        Rectangle liveItemResultHole = plugin.getAutoFlipGuidedSetupSearchResultBounds();
        if (liveItemResultHole != null && liveItemResultHole.width > 0 && liveItemResultHole.height > 0)
        {
            itemResultHole = liveItemResultHole;
        }

        Rectangle quantityHole = new Rectangle(
            header.x + config.getInt("setup.quantity.quick.x", 178),
            header.y + config.getInt("setup.quantity.quick.y", 186),
            config.getInt("setup.quantity.quick.w", 62),
            config.getInt("setup.quantity.quick.h", 28)
        );

        Rectangle priceHole = new Rectangle(
            header.x + config.getInt("setup.quick.x", 295),
            header.y + config.getInt("setup.quick.y", 186),
            config.getInt("setup.quick.w", 62),
            config.getInt("setup.quick.h", 28)
        );

        Rectangle confirmHole = new Rectangle(
            header.x + config.getInt("setup.confirm.x", 172),
            header.y + config.getInt("setup.confirm.y", 251),
            config.getInt("setup.confirm.w", 152),
            config.getInt("setup.confirm.h", 39)
        );

        Rectangle backArrowHole = new Rectangle(
            header.x + config.getInt("setup.back.arrow.x", 17),
            header.y + config.getInt("setup.back.arrow.y", 248),
            config.getInt("setup.back.arrow.w", 43),
            config.getInt("setup.back.arrow.h", 42)
        );
        Polygon backArrowHolePolygon = buildAutoFlipSetupBackArrowHole(backArrowHole); // AUTOFLIP_PATCH_SETUP_BACK_ARROW_POLYGON_HOLE_V1

        Rectangle itemDescriptionHole = new Rectangle(
            header.x + config.getInt("setup.item.description.x", 160),
            header.y + config.getInt("setup.item.description.y", 32),
            config.getInt("setup.item.description.w", 318),
            config.getInt("setup.item.description.h", 92)
        );

        Rectangle quantityValueHole = new Rectangle(
            header.x + config.getInt("setup.quantity.value.x", 34),
            header.y + config.getInt("setup.quantity.value.y", 150),
            config.getInt("setup.quantity.value.w", 188),
            config.getInt("setup.quantity.value.h", 27)
        );

        Rectangle priceValueHole = new Rectangle(
            header.x + config.getInt("setup.price.value.x", 248),
            header.y + config.getInt("setup.price.value.y", 150),
            config.getInt("setup.price.value.w", 210),
            config.getInt("setup.price.value.h", 27)
        );

        Rectangle finalPriceHole = new Rectangle(
            header.x + config.getInt("setup.final.price.x", 34),
            header.y + config.getInt("setup.final.price.y", 207),
            config.getInt("setup.final.price.w", 424),
            config.getInt("setup.final.price.h", 29)
        );

        Rectangle setupCover = new Rectangle(
            header.x + config.getInt("setup.cover.x", -5),
            header.y + config.getInt("setup.cover.y", 27),
            config.getInt("setup.cover.w", 484),
            config.getInt("setup.cover.h", 270)
        );
        Rectangle activeHole = null;
        String activeLabel = "";
        boolean solidActive = false;
        boolean labelVisible = true;
        if ("item_enter".equals(stage))
        {
            activeHole = itemSearchChatboxHole;
            activeLabel = "Searching";
            labelVisible = false;
            solidActive = false;
        }
        else if ("item_pick".equals(stage))
        {
            activeHole = itemResultHole;
            activeLabel = "";
            labelVisible = false;
        }
        else if ("quantity_click".equals(stage))
        {
            activeHole = quantityHole;
            activeLabel = "Qty";
        }
        else if ("quantity_enter".equals(stage))
        {
            activeHole = quantityHole;
            activeLabel = "Press Enter";
            solidActive = true;
        }
        else if ("price_click".equals(stage))
        {
            activeHole = priceHole;
            activeLabel = "Price";
        }
        else if ("price_enter".equals(stage))
        {
            activeHole = priceHole;
            activeLabel = "Press Enter";
            solidActive = true;
        }
        else if ("confirm_click".equals(stage))
        {
            activeHole = confirmHole;
            activeLabel = "Confirm";
        }

        Font oldFont = graphics.getFont();
        Color oldColor = graphics.getColor();
        Stroke oldStroke = graphics.getStroke();

        try
        {
            // AUTOFLIP_PATCH_SETUP_CUSTOM_UI_GATE_ONLY_V1
            // This gates only the custom GE setup overhaul visuals. The active green
            // guided workflow highlight stays outside this gate below.
            boolean customSetupUiEnabled = !ordinarySellPrice && plugin.isAutoFlipCustomSetupUiEnabledForOverlay();
            if (customSetupUiEnabled)
            {
                // AUTOFLIP_PATCH_SETUP_FULL_COVER_VISUAL_V1
                            // Full setup-page visual cover. Holes are subtracted so the native
                            // GE controls/value fields remain visible underneath.
                            drawAutoFlipSetupFullCover(
                                graphics,
                                setupCover,
                                itemIconHole,
                                itemResultHole,
                                itemDescriptionHole,
                                quantityValueHole,
                                quantityHole,
                                priceValueHole,
                                priceHole,
                                finalPriceHole,
                                confirmHole,
                                backArrowHolePolygon
                            );
                
                            // These holes must always be visible. The active workflow step is
                            // highlighted separately, but non-current holes remain present.
                            drawAutoFlipSetupPassiveHole(graphics, itemIconHole);
                            drawAutoFlipSetupPassiveHole(graphics, itemResultHole);
                            drawAutoFlipSetupPassiveHole(graphics, itemDescriptionHole);
                            drawAutoFlipSetupPassiveHole(graphics, quantityValueHole);
                            drawAutoFlipSetupPassiveHole(graphics, quantityHole);
                            drawAutoFlipSetupPassiveHole(graphics, priceValueHole);
                            drawAutoFlipSetupPassiveHole(graphics, priceHole);
                            drawAutoFlipSetupPassiveHole(graphics, finalPriceHole);
                            drawAutoFlipSetupPassiveHole(graphics, confirmHole);
                            drawAutoFlipSetupPassivePolygonHole(graphics, backArrowHolePolygon); // AUTOFLIP_PATCH_SETUP_BACK_ARROW_POLYGON_HOLE_V1
            }

            if (activeHole != null)
            {
                drawAutoFlipSetupActiveHole(graphics, activeHole, activeLabel, solidActive, labelVisible, oldFont);
            }

            if (customSetupUiEnabled)
            {
                // AUTOFLIP_PATCH_SETUP_CONFIRM_ITEM_FRAMES_V1_INLINE
                try
                {
                    if (config.getInt("setup.asset.buy.item.frame.enabled", 1) != 0)
                    {
                        try (java.io.InputStream autoFlipBuyItemFrameIn = AutoFlipOverlay.class.getResourceAsStream("/autoflip/setup_ui/pick_item_tile_bg.png"))
                        {
                            if (autoFlipBuyItemFrameIn != null)
                            {
                                java.awt.image.BufferedImage autoFlipBuyItemFrameImage = javax.imageio.ImageIO.read(autoFlipBuyItemFrameIn);
                                Rectangle autoFlipBuyItemFrameBox = new Rectangle(
                                    header.x + config.getInt("setup.asset.buy.item.frame.x", 54),
                                    header.y + config.getInt("setup.asset.buy.item.frame.y", 61),
                                    config.getInt("setup.asset.buy.item.frame.w", 48),
                                    config.getInt("setup.asset.buy.item.frame.h", 46)
                                );
                                drawAutoFlipSetupImageAsset(graphics, autoFlipBuyItemFrameImage, autoFlipBuyItemFrameBox,
                                    config.getInt("setup.asset.buy.item.frame.alpha", 55), true);
                            }
                        }
                    }

                    if (config.getInt("setup.asset.confirm.button.frame.enabled", 1) != 0)
                    {
                        try (java.io.InputStream autoFlipConfirmFrameIn = AutoFlipOverlay.class.getResourceAsStream("/autoflip/setup_ui/review_message_bar_bg.png"))
                        {
                            if (autoFlipConfirmFrameIn != null)
                            {
                                java.awt.image.BufferedImage autoFlipConfirmFrameImage = javax.imageio.ImageIO.read(autoFlipConfirmFrameIn);
                                Rectangle autoFlipConfirmFrameBox = new Rectangle(
                                    header.x + config.getInt("setup.asset.confirm.button.frame.x", 161),
                                    header.y + config.getInt("setup.asset.confirm.button.frame.y", 244),
                                    config.getInt("setup.asset.confirm.button.frame.w", 152),
                                    config.getInt("setup.asset.confirm.button.frame.h", 39)
                                );
                                drawAutoFlipSetupImageAsset(graphics, autoFlipConfirmFrameImage, autoFlipConfirmFrameBox,
                                    config.getInt("setup.asset.confirm.button.frame.alpha", 72), true);
                            }
                        }
                    }
                }
                catch (Exception ignored)
                {
                    // Render-only confirm/item frame safety.
                }
                // AUTOFLIP_PATCH_SETUP_QUANTITY_INPUT_FRAME_ASSET_V1_CALL
                drawAutoFlipSetupQuantityInputFrameAsset(graphics, header);
                // AUTOFLIP_PATCH_SETUP_PRICE_INPUT_FRAME_ASSET_V1_CALL
                drawAutoFlipSetupPriceInputFrameAsset(graphics, header);
                // AUTOFLIP_PATCH_SETUP_GOLD_INFO_FRAMES_V4_INLINE
                try
                {
                    if (config.getInt("setup.asset.item.description.frame.enabled", 1) != 0)
                    {
                        try (java.io.InputStream autoFlipItemDescFrameIn = AutoFlipOverlay.class.getResourceAsStream("/autoflip/setup_ui/review_message_bar_bg.png"))
                        {
                            if (autoFlipItemDescFrameIn != null)
                            {
                                java.awt.image.BufferedImage autoFlipItemDescFrameImage = javax.imageio.ImageIO.read(autoFlipItemDescFrameIn);
                                Rectangle autoFlipItemDescFrameBox = new Rectangle(
                                    header.x + config.getInt("setup.asset.item.description.frame.x", 160),
                                    header.y + config.getInt("setup.asset.item.description.frame.y", 33),
                                    config.getInt("setup.asset.item.description.frame.w", 310),
                                    config.getInt("setup.asset.item.description.frame.h", 92)
                                );
                                drawAutoFlipSetupImageAsset(graphics, autoFlipItemDescFrameImage, autoFlipItemDescFrameBox,
                                    config.getInt("setup.asset.item.description.frame.alpha", 88), true);
                            }
                        }
                    }

                    if (config.getInt("setup.asset.final.price.frame.enabled", 1) != 0)
                    {
                        try (java.io.InputStream autoFlipFinalPriceFrameIn = AutoFlipOverlay.class.getResourceAsStream("/autoflip/setup_ui/final_price_bar_bg.png"))
                        {
                            if (autoFlipFinalPriceFrameIn != null)
                            {
                                java.awt.image.BufferedImage autoFlipFinalPriceFrameImage = javax.imageio.ImageIO.read(autoFlipFinalPriceFrameIn);
                                Rectangle autoFlipFinalPriceFrameBox = new Rectangle(
                                    header.x + config.getInt("setup.asset.final.price.frame.x", 34),
                                    header.y + config.getInt("setup.asset.final.price.frame.y", 205),
                                    config.getInt("setup.asset.final.price.frame.w", 409),
                                    config.getInt("setup.asset.final.price.frame.h", 23)
                                );
                                drawAutoFlipSetupImageAsset(graphics, autoFlipFinalPriceFrameImage, autoFlipFinalPriceFrameBox,
                                    config.getInt("setup.asset.final.price.frame.alpha", 94), true);
                            }
                        }
                    }
                }
                catch (Exception ignored)
                {
                    // Render-only asset safety.
                }
                // AUTOFLIP_PATCH_SETUP_QUANTITY_HINT_PANEL_ASSET_V3_CALL
                drawAutoFlipSetupQuantityHintPanelAsset(graphics, header);
                // AUTOFLIP_PATCH_SETUP_PRICE_HINT_PANEL_TEXT_V1_CALL
                drawAutoFlipSetupPriceHintPanelAsset(graphics, header);
                // AUTOFLIP_PATCH_SETUP_GOLD_RUNE_CONFIRM_BUY_ITEM_V1_INLINE
                try
                {
                    if (config.getInt("setup.asset.buy.item.gold.edge.enabled", 1) != 0)
                    {
                        try (java.io.InputStream autoFlipBuyItemGoldIn = AutoFlipOverlay.class.getResourceAsStream("/autoflip/setup_ui/buy_item_gold_edge_frame.png"))
                        {
                            if (autoFlipBuyItemGoldIn != null)
                            {
                                java.awt.image.BufferedImage autoFlipBuyItemGoldImage = javax.imageio.ImageIO.read(autoFlipBuyItemGoldIn);
                                if (autoFlipBuyItemGoldImage != null)
                                {
                                    Rectangle autoFlipBuyItemGoldBox = new Rectangle(
                                        header.x + config.getInt("setup.asset.buy.item.gold.edge.x", 54),
                                        header.y + config.getInt("setup.asset.buy.item.gold.edge.y", 61),
                                        config.getInt("setup.asset.buy.item.gold.edge.w", 48),
                                        config.getInt("setup.asset.buy.item.gold.edge.h", 46)
                                    );
                                    drawAutoFlipSetupImageAsset(graphics, autoFlipBuyItemGoldImage, autoFlipBuyItemGoldBox,
                                        config.getInt("setup.asset.buy.item.gold.edge.alpha", 88), true);
                                }
                            }
                        }
                    }

                    if (config.getInt("setup.asset.confirm.gold.rune.enabled", 1) != 0)
                    {
                        try (java.io.InputStream autoFlipConfirmGoldIn = AutoFlipOverlay.class.getResourceAsStream("/autoflip/setup_ui/confirm_gold_rune_button.png"))
                        {
                            if (autoFlipConfirmGoldIn != null)
                            {
                                java.awt.image.BufferedImage autoFlipConfirmGoldImage = javax.imageio.ImageIO.read(autoFlipConfirmGoldIn);
                                if (autoFlipConfirmGoldImage != null)
                                {
                                    Rectangle autoFlipConfirmGoldBox = new Rectangle(
                                        header.x + config.getInt("setup.asset.confirm.gold.rune.x", 161),
                                        header.y + config.getInt("setup.asset.confirm.gold.rune.y", 244),
                                        config.getInt("setup.asset.confirm.gold.rune.w", 152),
                                        config.getInt("setup.asset.confirm.gold.rune.h", 39)
                                    );
                                    drawAutoFlipSetupImageAsset(graphics, autoFlipConfirmGoldImage, autoFlipConfirmGoldBox,
                                        config.getInt("setup.asset.confirm.gold.rune.alpha", 88), true);
                                }
                            }
                        }
                    }

                    if (config.getInt("setup.confirm.custom.text.enabled", 1) != 0)
                    {
                        Font autoFlipConfirmOldFont = graphics.getFont();
                        Color autoFlipConfirmOldColor = graphics.getColor();
                        try
                        {
                            String autoFlipConfirmText = "";
                            Rectangle autoFlipConfirmTextBox = new Rectangle(
                                header.x + config.getInt("setup.confirm.x", 172),
                                header.y + config.getInt("setup.confirm.y", 251),
                                config.getInt("setup.confirm.w", 164),
                                config.getInt("setup.confirm.h", 40)
                            );
                            try
                            {
                                java.util.Properties autoFlipConfirmProps = new java.util.Properties();
                                java.nio.file.Path autoFlipConfirmPath = java.nio.file.Paths.get("_runtime", "overlay_dev_config.properties");
                                if (!java.nio.file.Files.exists(autoFlipConfirmPath))
                                {
                                    autoFlipConfirmPath = java.nio.file.Paths.get("C:\\osrs-flip-assistant\\Plugin-Tinker\\_runtime\\overlay_dev_config.properties");
                                }
                                if (java.nio.file.Files.exists(autoFlipConfirmPath))
                                {
                                    try (java.io.Reader autoFlipConfirmReader = java.nio.file.Files.newBufferedReader(autoFlipConfirmPath, java.nio.charset.StandardCharsets.UTF_8))
                                    {
                                        autoFlipConfirmProps.load(autoFlipConfirmReader);
                                    }
                                    String configuredConfirmText = autoFlipConfirmProps.getProperty("setup.confirm.custom.text");
                                    if (configuredConfirmText != null && !configuredConfirmText.trim().isEmpty())
                                    {
                                        autoFlipConfirmText = configuredConfirmText.trim();
                                    }
                                }
                            }
                            catch (Exception ignored)
                            {
                            }

                            Font autoFlipConfirmBaseFont = oldFont == null ? autoFlipConfirmOldFont : oldFont;
                            graphics.setFont(autoFlipConfirmBaseFont.deriveFont(Font.BOLD, (float) config.getInt("setup.confirm.custom.text.font.size", 17)));
                            graphics.setColor(new Color(18, 12, 6, 230));
                            java.awt.FontMetrics autoFlipConfirmMetrics = graphics.getFontMetrics();
                            int autoFlipConfirmTextWidth = autoFlipConfirmMetrics.stringWidth(autoFlipConfirmText);
                            int autoFlipConfirmTextX = autoFlipConfirmTextBox.x + Math.max(0, (autoFlipConfirmTextBox.width - autoFlipConfirmTextWidth) / 2);
                            int autoFlipConfirmTextY = autoFlipConfirmTextBox.y + Math.max(0, (autoFlipConfirmTextBox.height - autoFlipConfirmMetrics.getHeight()) / 2) + autoFlipConfirmMetrics.getAscent();
                            graphics.drawString(autoFlipConfirmText, autoFlipConfirmTextX, autoFlipConfirmTextY);
                        }
                        finally
                        {
                            graphics.setFont(autoFlipConfirmOldFont);
                            graphics.setColor(autoFlipConfirmOldColor);
                        }
                    }
                }
                catch (Exception ignored)
                {
                    // Render-only asset safety.
                }
                // AUTOFLIP_PATCH_SETUP_GOLD_RUNE_CONFIRM_BUY_ITEM_V1_END
                // AUTOFLIP_PATCH_SETUP_REVIEW_MESSAGE_TEXT_V3_INLINE
                if (config.getInt("setup.review.message.text.enabled", 1) != 0)
                {
                    Font autoFlipReviewOldFont = graphics.getFont();
                    Color autoFlipReviewOldColor = graphics.getColor();
                    try
                    {
                        String autoFlipReviewMessageText = "Review offer before confirming";
                        try
                        {
                            java.util.Properties autoFlipReviewProps = new java.util.Properties();
                            java.nio.file.Path autoFlipReviewPath = java.nio.file.Paths.get("_runtime", "overlay_dev_config.properties");
                            if (!java.nio.file.Files.exists(autoFlipReviewPath))
                            {
                                autoFlipReviewPath = java.nio.file.Paths.get("C:\\osrs-flip-assistant\\Plugin-Tinker\\_runtime\\overlay_dev_config.properties");
                            }
                            if (java.nio.file.Files.exists(autoFlipReviewPath))
                            {
                                try (java.io.Reader autoFlipReviewReader = java.nio.file.Files.newBufferedReader(autoFlipReviewPath, java.nio.charset.StandardCharsets.UTF_8))
                                {
                                    autoFlipReviewProps.load(autoFlipReviewReader);
                                }
                                String configuredReviewMessage = autoFlipReviewProps.getProperty("setup.review.message.text");
                                if (configuredReviewMessage != null && !configuredReviewMessage.trim().isEmpty())
                                {
                                    autoFlipReviewMessageText = configuredReviewMessage.trim();
                                }
                            }
                        }
                        catch (Exception ignored)
                        {
                        }

                        Font autoFlipReviewBaseFont = oldFont == null ? autoFlipReviewOldFont : oldFont;
                        graphics.setFont(autoFlipReviewBaseFont.deriveFont(Font.BOLD, (float) config.getInt("setup.review.message.font.size", 12)));
                        graphics.setColor(new Color(
                            config.getInt("setup.review.message.color.r", 235),
                            config.getInt("setup.review.message.color.g", 213),
                            config.getInt("setup.review.message.color.b", 142),
                            config.getInt("setup.review.message.color.a", 245)
                        ));
                        graphics.drawString(autoFlipReviewMessageText,
                            header.x + config.getInt("setup.review.message.x", 70),
                            header.y + config.getInt("setup.review.message.y", 224)
                        );
                    }
                    finally
                    {
                        graphics.setFont(autoFlipReviewOldFont);
                        graphics.setColor(autoFlipReviewOldColor);
                    }
                }
                drawAutoFlipSetupReplacementLabels(graphics, header, oldFont); // AUTOFLIP_PATCH_SETUP_COVER_BG_LABELS_V1
            }
        }
        catch (Throwable ignored)
        {
            // Render-only safety.
        }
        finally
        {
            graphics.setFont(oldFont);
            graphics.setColor(oldColor);
            graphics.setStroke(oldStroke);
        }
    }

    // AUTOFLIP_PATCH_SETUP_COVER_BG_LABELS_V1
    private void drawAutoFlipSetupReplacementLabels(Graphics2D graphics, Rectangle header, Font baseFont)
    {
        if (graphics == null || header == null)
        {
            return;
        }

        Font oldFont = graphics.getFont();
        Color oldColor = graphics.getColor();

        try
        {
            Font drawFont = baseFont == null ? oldFont : baseFont;
            graphics.setFont(drawFont.deriveFont(Font.BOLD, (float) config.getInt("setup.label.font.size", 12)));
            graphics.setColor(new Color(255, 172, 36, 245));

            graphics.drawString("Buy offer",
                header.x + config.getInt("setup.label.buy.offer.x", 52),
                header.y + config.getInt("setup.label.buy.offer.y", 53)
            );
            graphics.drawString("Quantity:",
                header.x + config.getInt("setup.label.quantity.x", 92),
                header.y + config.getInt("setup.label.quantity.y", 143)
            );
            graphics.drawString("Price per item:",
                header.x + config.getInt("setup.label.price.x", 306),
                header.y + config.getInt("setup.label.price.y", 143)
            );

            // AUTOFLIP_PATCH_SETUP_QUANTITY_HINT_TEXT_VISIBLE_V1
            if (config.getInt("setup.hint.quantity.enabled", 1) != 0)
            {
                graphics.setFont(drawFont.deriveFont(Font.BOLD, (float) config.getInt("setup.hint.font.size", 9)));
                graphics.setColor(new Color(
                    config.getInt("setup.hint.color.r", 235),
                    config.getInt("setup.hint.color.g", 213),
                    config.getInt("setup.hint.color.b", 142),
                    config.getInt("setup.hint.color.a", 245)
                ));
                                // AUTOFLIP_PATCH_SETUP_QUANTITY_HINT_TEXT_CONFIG_V2
                String quantityHintText = "AutoFlip enters exact quantity";
                try
                {
                    java.lang.reflect.Method method = config.getClass().getMethod("getString", String.class, String.class);
                    Object value = method.invoke(config, "setup.hint.quantity.text", quantityHintText);
                    if (value instanceof String && !((String) value).trim().isEmpty())
                    {
                        quantityHintText = ((String) value).trim();
                    }
                }
                catch (Exception ignored)
                {
                    try
                    {
                        java.nio.file.Path path = java.nio.file.Paths.get("C:\\osrs-flip-assistant\\Plugin-Tinker\\_runtime\\overlay_dev_config.properties");
                        java.util.Properties props = new java.util.Properties();
                        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(path, java.nio.charset.StandardCharsets.UTF_8))
                        {
                            props.load(reader);
                        }
                        String value = props.getProperty("setup.hint.quantity.text");
                        if (value != null && !value.trim().isEmpty())
                        {
                            quantityHintText = value.trim();
                        }
                    }
                    catch (Exception ignoredFile)
                    {
                        // keep fallback
                    }
                }

                graphics.drawString(quantityHintText,
                    header.x + config.getInt("setup.hint.quantity.x", 42),
                    header.y + config.getInt("setup.hint.quantity.y", 193)
                );
            }
 
            // AUTOFLIP_PATCH_SETUP_PRICE_HINT_TEXT_VISIBLE_V1
            if (config.getInt("setup.hint.price.enabled", 1) != 0)
            {
                graphics.setFont(drawFont.deriveFont(Font.BOLD, (float) config.getInt("setup.hint.font.size", 12)));
                graphics.setColor(new Color(
                    config.getInt("setup.hint.color.r", 235),
                    config.getInt("setup.hint.color.g", 213),
                    config.getInt("setup.hint.color.b", 142),
                    config.getInt("setup.hint.color.a", 245)
                ));
                String priceHintText = "AutoFlip enters optimized price";
                try
                {
                    java.nio.file.Path path = java.nio.file.Paths.get("C:\\osrs-flip-assistant\\Plugin-Tinker\\_runtime\\overlay_dev_config.properties");
                    if (java.nio.file.Files.exists(path))
                    {
                        java.util.Properties props = new java.util.Properties();
                        try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(path, java.nio.charset.StandardCharsets.UTF_8))
                        {
                            props.load(reader);
                        }
                        String configuredText = props.getProperty("setup.hint.price.text");
                        if (configuredText != null && !configuredText.trim().isEmpty())
                        {
                            priceHintText = configuredText.trim();
                        }
                    }
                }
                catch (Exception ignored)
                {
                }
                graphics.drawString(priceHintText,
                    header.x + config.getInt("setup.hint.price.x", 250),
                    header.y + config.getInt("setup.hint.price.y", 192)
                );
            }
        }
        finally
        {
            graphics.setFont(oldFont);
            graphics.setColor(oldColor);
        }
    }
    // AUTOFLIP_PATCH_SETUP_FULL_COVER_VISUAL_V1
    private void drawAutoFlipSetupFullCover(Graphics2D graphics, Rectangle cover, java.awt.Shape... holes)
    {
        if (graphics == null || cover == null || cover.width <= 0 || cover.height <= 0)
        {
            return;
        }

        Color oldColor = graphics.getColor();
        Stroke oldStroke = graphics.getStroke();

        try
        {
            java.awt.geom.Area coveredArea = new java.awt.geom.Area(cover);
            if (holes != null)
            {
                for (java.awt.Shape hole : holes)
                {
                    if (hole == null)
                    {
                        continue;
                    }

                    Rectangle bounds = hole.getBounds();
                    if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
                    {
                        continue;
                    }

                    java.awt.geom.Area holeArea = new java.awt.geom.Area(hole);
                    if (hole instanceof Rectangle)
                    {
                        Rectangle paddedHole = new Rectangle(
                            bounds.x - 1,
                            bounds.y - 1,
                            bounds.width + 2,
                            bounds.height + 2
                        );
                        holeArea = new java.awt.geom.Area(paddedHole);
                    }
                    coveredArea.subtract(holeArea);
                }
            }

                        // AUTOFLIP_PATCH_SETUP_COVER_BG_LABELS_V1
            graphics.setColor(new Color(
                config.getInt("setup.cover.bg.r", 43),
                config.getInt("setup.cover.bg.g", 35),
                config.getInt("setup.cover.bg.b", 24),
                config.getInt("setup.cover.bg.a", 252)
            ));
            graphics.fill(coveredArea);

            if (config.getInt("setup.cover.border.enabled", 0) > 0)
            {
                graphics.setStroke(new BasicStroke(1.0f));
                graphics.setColor(new Color(95, 76, 46, 190));
                graphics.drawRect(cover.x, cover.y, cover.width, cover.height);

                graphics.setColor(new Color(18, 14, 10, 70));
                graphics.drawLine(cover.x + 8, cover.y + 8, cover.x + cover.width - 8, cover.y + 8);
            }
        }
        finally
        {
            graphics.setColor(oldColor);
            graphics.setStroke(oldStroke);
        }
    }
    // AUTOFLIP_PATCH_SETUP_UI_ASSETS_LOGO_RENDER_V1_METHOD
    // AUTOFLIP_PATCH_SETUP_UI_ASSETS_LOGO_RENDER_V1_METHOD

    // AUTOFLIP_PATCH_SETUP_LOGO_TAGLINE_ASSETS_V1
        // AUTOFLIP_PATCH_SETUP_PRICE_HINT_PANEL_TEXT_V1_METHOD
    private void drawAutoFlipSetupPriceHintPanelAsset(Graphics2D graphics, Rectangle header)
    {
        if (graphics == null || header == null)
        {
            return;
        }
        if (config.getInt("setup.custom.ui.enabled", 1) == 0 || config.getInt("setup.asset.price.hint.bg.enabled", 1) == 0)
        {
            return;
        }

        try (java.io.InputStream in = AutoFlipOverlay.class.getResourceAsStream("/autoflip/setup_ui/hint_panel_price_bg.png"))
        {
            if (in == null)
            {
                return;
            }
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(in);
            if (image == null)
            {
                return;
            }
            Rectangle box = new Rectangle(
                header.x + config.getInt("setup.asset.price.hint.bg.x", 246),
                header.y + config.getInt("setup.asset.price.hint.bg.y", 175),
                config.getInt("setup.asset.price.hint.bg.w", 180),
                config.getInt("setup.asset.price.hint.bg.h", 27)
            );
            drawAutoFlipSetupImageAsset(graphics, image, box, config.getInt("setup.asset.price.hint.bg.alpha", 96), true);
        }
        catch (Exception ignored)
        {
        }
    }
// AUTOFLIP_PATCH_SETUP_QUANTITY_HINT_PANEL_ASSET_V3_METHOD
    private java.awt.image.BufferedImage getAutoFlipSetupQuantityHintPanelImage()
    {
        if (autoFlipSetupQuantityHintPanelAssetImage != null)
        {
            return autoFlipSetupQuantityHintPanelAssetImage;
        }

        try (java.io.InputStream in = AutoFlipOverlay.class.getResourceAsStream("/autoflip/setup_ui/hint_panel_quantity_bg.png"))
        {
            if (in == null)
            {
                return null;
            }
            autoFlipSetupQuantityHintPanelAssetImage = javax.imageio.ImageIO.read(in);
            return autoFlipSetupQuantityHintPanelAssetImage;
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    // AUTOFLIP_PATCH_SETUP_QUANTITY_HINT_PANEL_ASSET_V3_METHOD
    private void drawAutoFlipSetupQuantityHintPanelAsset(Graphics2D graphics, Rectangle header)
    {
        if (graphics == null || header == null)
        {
            return;
        }
        if (config.getInt("setup.custom.ui.enabled", 1) == 0)
        {
            return;
        }
        if (config.getInt("setup.asset.quantity.hint.bg.enabled", 1) == 0)
        {
            return;
        }

        java.awt.image.BufferedImage image = getAutoFlipSetupQuantityHintPanelImage();
        if (image == null)
        {
            return;
        }

        Rectangle box = new Rectangle(
            header.x + config.getInt("setup.asset.quantity.hint.bg.x", 34),
            header.y + config.getInt("setup.asset.quantity.hint.bg.y", 190),
            config.getInt("setup.asset.quantity.hint.bg.w", 188),
            config.getInt("setup.asset.quantity.hint.bg.h", 32)
        );
        drawAutoFlipSetupImageAsset(graphics, image, box, config.getInt("setup.asset.quantity.hint.bg.alpha", 96), true);
    }
    // AUTOFLIP_PATCH_SETUP_PRICE_INPUT_FRAME_ASSET_V1_METHOD
    private java.awt.image.BufferedImage getAutoFlipSetupPriceInputFrameImage()
    {
        if (autoFlipSetupPriceInputFrameAssetImage != null)
        {
            return autoFlipSetupPriceInputFrameAssetImage;
        }

        try (java.io.InputStream in = AutoFlipOverlay.class.getResourceAsStream("/autoflip/setup_ui/input_frame_price.png"))
        {
            if (in == null)
            {
                return null;
            }
            autoFlipSetupPriceInputFrameAssetImage = javax.imageio.ImageIO.read(in);
            return autoFlipSetupPriceInputFrameAssetImage;
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    // AUTOFLIP_PATCH_SETUP_PRICE_INPUT_FRAME_ASSET_V1_METHOD
    private void drawAutoFlipSetupPriceInputFrameAsset(Graphics2D graphics, Rectangle header)
    {
        if (graphics == null || header == null)
        {
            return;
        }
        if (config.getInt("setup.custom.ui.enabled", 1) == 0)
        {
            return;
        }
        if (config.getInt("setup.asset.price.input.frame.enabled", 1) == 0)
        {
            return;
        }

        java.awt.image.BufferedImage image = getAutoFlipSetupPriceInputFrameImage();
        if (image == null)
        {
            return;
        }

        Rectangle box = new Rectangle(
            header.x + config.getInt("setup.asset.price.input.frame.x", 246),
            header.y + config.getInt("setup.asset.price.input.frame.y", 147),
            config.getInt("setup.asset.price.input.frame.w", 180),
            config.getInt("setup.asset.price.input.frame.h", 29)
        );
        drawAutoFlipSetupImageAsset(graphics, image, box, config.getInt("setup.asset.price.input.frame.alpha", 96), true);
    }
    // AUTOFLIP_PATCH_SETUP_QUANTITY_INPUT_FRAME_ASSET_V1_METHOD
    private java.awt.image.BufferedImage getAutoFlipSetupQuantityInputFrameImage()
    {
        if (autoFlipSetupQuantityInputFrameAssetImage != null)
        {
            return autoFlipSetupQuantityInputFrameAssetImage;
        }

        try (java.io.InputStream in = AutoFlipOverlay.class.getResourceAsStream("/autoflip/setup_ui/input_frame_quantity.png"))
        {
            if (in == null)
            {
                return null;
            }
            autoFlipSetupQuantityInputFrameAssetImage = javax.imageio.ImageIO.read(in);
            return autoFlipSetupQuantityInputFrameAssetImage;
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    // AUTOFLIP_PATCH_SETUP_QUANTITY_INPUT_FRAME_ASSET_V1_METHOD
    private void drawAutoFlipSetupQuantityInputFrameAsset(Graphics2D graphics, Rectangle header)
    {
        if (graphics == null || header == null)
        {
            return;
        }
        if (config.getInt("setup.custom.ui.enabled", 1) == 0)
        {
            return;
        }
        if (config.getInt("setup.asset.quantity.input.frame.enabled", 1) == 0)
        {
            return;
        }

        java.awt.image.BufferedImage image = getAutoFlipSetupQuantityInputFrameImage();
        if (image == null)
        {
            return;
        }

        Rectangle box = new Rectangle(
            header.x + config.getInt("setup.asset.quantity.input.frame.x", 31),
            header.y + config.getInt("setup.asset.quantity.input.frame.y", 146),
            config.getInt("setup.asset.quantity.input.frame.w", 194),
            config.getInt("setup.asset.quantity.input.frame.h", 35)
        );
        drawAutoFlipSetupImageAsset(graphics, image, box, config.getInt("setup.asset.quantity.input.frame.alpha", 96), true);
    }
    private java.awt.image.BufferedImage getAutoFlipSetupLogoWatermarkImage()
    {
        if (autoFlipSetupLogoAssetImage != null)
        {
            return autoFlipSetupLogoAssetImage;
        }

        try (java.io.InputStream in = AutoFlipOverlay.class.getResourceAsStream("/autoflip/setup_ui/AppIcon_Favicon5_clean.png"))
        {
            if (in == null)
            {
                return null;
            }
            autoFlipSetupLogoAssetImage = javax.imageio.ImageIO.read(in);
            return autoFlipSetupLogoAssetImage;
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    // AUTOFLIP_PATCH_SETUP_LOGO_TAGLINE_ASSETS_V1
    private java.awt.image.BufferedImage getAutoFlipSetupTaglineImage()
    {
        if (autoFlipSetupTaglineAssetImage != null)
        {
            return autoFlipSetupTaglineAssetImage;
        }

        try (java.io.InputStream in = AutoFlipOverlay.class.getResourceAsStream("/autoflip/setup_ui/flip_smarter_green_tagline_glow.png"))
        {
            if (in == null)
            {
                return null;
            }
            autoFlipSetupTaglineAssetImage = javax.imageio.ImageIO.read(in);
            return autoFlipSetupTaglineAssetImage;
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    // AUTOFLIP_PATCH_SETUP_LOGO_TAGLINE_ASSETS_V1
    private void drawAutoFlipSetupUiLogoAsset(Graphics2D graphics, Rectangle header)
    {
        if (graphics == null || header == null)
        {
            return;
        }
        if (config.getInt("setup.custom.ui.enabled", 1) == 0)
        {
            return;
        }

        if (config.getInt("setup.asset.logo.enabled", 1) != 0)
        {
            java.awt.image.BufferedImage logo = getAutoFlipSetupLogoWatermarkImage();
            if (logo != null)
            {
                Rectangle logoBox = new Rectangle(
                    header.x + config.getInt("setup.asset.logo.x", 335),
                    header.y + config.getInt("setup.asset.logo.y", 228),
                    config.getInt("setup.asset.logo.w", 126),
                    config.getInt("setup.asset.logo.h", 82)
                );
                drawAutoFlipSetupImageAsset(graphics, logo, logoBox, config.getInt("setup.asset.logo.alpha", 92), true);
            }
        }

        if (config.getInt("setup.asset.tagline.enabled", 1) != 0)
        {
            java.awt.image.BufferedImage tagline = getAutoFlipSetupTaglineImage();
            if (tagline != null)
            {
                Rectangle taglineBox = new Rectangle(
                    header.x + config.getInt("setup.asset.tagline.x", 355),
                    header.y + config.getInt("setup.asset.tagline.y", 298),
                    config.getInt("setup.asset.tagline.w", 92),
                    config.getInt("setup.asset.tagline.h", 12)
                );
                drawAutoFlipSetupImageAsset(graphics, tagline, taglineBox, config.getInt("setup.asset.tagline.alpha", 96), true);
            }
        }
    }

    // AUTOFLIP_PATCH_SETUP_LOGO_TAGLINE_ASSETS_V1
    private void drawAutoFlipSetupImageAsset(Graphics2D graphics, java.awt.image.BufferedImage image, Rectangle dest, int alphaPct, boolean cropTransparentBounds)
    {
        if (graphics == null || image == null || dest == null || dest.width <= 0 || dest.height <= 0)
        {
            return;
        }

        java.awt.Composite oldComposite = graphics.getComposite();
        Object oldInterpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
        try
        {
            int clampedAlpha = Math.max(0, Math.min(100, alphaPct));
            if (clampedAlpha <= 0)
            {
                return;
            }

            graphics.setComposite(java.awt.AlphaComposite.getInstance(java.awt.AlphaComposite.SRC_OVER, clampedAlpha / 100.0f));
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            Rectangle src = cropTransparentBounds ? findAutoFlipNonTransparentBounds(image) : new Rectangle(0, 0, image.getWidth(), image.getHeight());
            if (src == null || src.width <= 0 || src.height <= 0)
            {
                return;
            }

            graphics.drawImage(
                image,
                dest.x,
                dest.y,
                dest.x + dest.width,
                dest.y + dest.height,
                src.x,
                src.y,
                src.x + src.width,
                src.y + src.height,
                null
            );
        }
        finally
        {
            graphics.setComposite(oldComposite);
            if (oldInterpolation != null)
            {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, oldInterpolation);
            }
        }
    }

    // AUTOFLIP_PATCH_SETUP_LOGO_TAGLINE_ASSETS_V1
    private Rectangle findAutoFlipNonTransparentBounds(java.awt.image.BufferedImage image)
    {
        if (image == null)
        {
            return null;
        }

        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                int alpha = (image.getRGB(x, y) >>> 24) & 0xff;
                if (alpha > 8)
                {
                    if (x < minX) { minX = x; }
                    if (y < minY) { minY = y; }
                    if (x > maxX) { maxX = x; }
                    if (y > maxY) { maxY = y; }
                }
            }
        }

        if (maxX < minX || maxY < minY)
        {
            return new Rectangle(0, 0, image.getWidth(), image.getHeight());
        }
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
    // AUTOFLIP_PATCH_SETUP_BACK_ARROW_POLYGON_HOLE_V1
    private Polygon buildAutoFlipSetupBackArrowHole(Rectangle box)
    {
        Polygon arrow = new Polygon();
        if (box == null || box.width <= 0 || box.height <= 0)
        {
            return arrow;
        }

        int x = box.x;
        int y = box.y;
        int w = box.width;
        int h = box.height;
        int midY = y + (h / 2);

        arrow.addPoint(x, midY);
        arrow.addPoint(x + Math.max(8, w / 3), y + Math.max(2, h / 8));
        arrow.addPoint(x + Math.max(8, w / 3), y + Math.max(9, h / 3));
        arrow.addPoint(x + w, y + Math.max(9, h / 3));
        arrow.addPoint(x + w, y + h - Math.max(9, h / 3));
        arrow.addPoint(x + Math.max(8, w / 3), y + h - Math.max(9, h / 3));
        arrow.addPoint(x + Math.max(8, w / 3), y + h - Math.max(2, h / 8));

        return arrow;
    }

    // AUTOFLIP_PATCH_SETUP_UI_ASSETS_LOGO_RENDER_V1_METHOD
    // AUTOFLIP_PATCH_SETUP_UI_ASSETS_LOGO_RENDER_V1_METHOD
    // AUTOFLIP_PATCH_SETUP_BACK_ARROW_POLYGON_HOLE_V1
    private void drawAutoFlipSetupPassivePolygonHole(Graphics2D graphics, Polygon polygon)
    {
        if (graphics == null || polygon == null || polygon.npoints <= 0)
        {
            return;
        }

        Stroke oldStroke = graphics.getStroke();
        Color oldColor = graphics.getColor();

        try
        {
            graphics.setStroke(new BasicStroke(1.5f));
            graphics.setColor(new Color(235, 180, 75, 82));
            graphics.drawPolygon(polygon);

            graphics.setColor(new Color(35, 22, 10, 28));
            graphics.fillPolygon(polygon);
        }
        finally
        {
            graphics.setStroke(oldStroke);
            graphics.setColor(oldColor);
        }
    }
    // AUTOFLIP_PATCH_SETUP_PERSISTENT_HOLES_VISUAL_V2
    private void drawAutoFlipSetupPassiveHole(Graphics2D graphics, Rectangle box)
    {
        if (graphics == null || box == null || box.width <= 0 || box.height <= 0)
        {
            return;
        }

        Stroke oldStroke = graphics.getStroke();
        Color oldColor = graphics.getColor();

        try
        {
            graphics.setStroke(new BasicStroke(1.5f));
            graphics.setColor(new Color(235, 180, 75, 82));
            graphics.drawRoundRect(box.x, box.y, box.width, box.height, 9, 9);

            graphics.setColor(new Color(35, 22, 10, 28));
            graphics.fillRoundRect(box.x, box.y, box.width, box.height, 9, 9);
        }
        finally
        {
            graphics.setStroke(oldStroke);
            graphics.setColor(oldColor);
        }
    }

    // AUTOFLIP_PATCH_SETUP_PERSISTENT_HOLES_VISUAL_V2
    private void drawAutoFlipSetupActiveHole(Graphics2D graphics, Rectangle box, String label, boolean solid, boolean labelVisible, Font baseFont)
    {
        if (graphics == null || box == null || box.width <= 0 || box.height <= 0)
        {
            return;
        }

        Stroke oldStroke = graphics.getStroke();
        Color oldColor = graphics.getColor();
        Font oldFont = graphics.getFont();

        try
        {
            graphics.setStroke(new BasicStroke(3.0f));
            graphics.setColor(solid ? new Color(0, 180, 80, 225) : new Color(0, 180, 80, 76));
            graphics.fillRoundRect(box.x, box.y, box.width, box.height, 10, 10);

            graphics.setColor(new Color(80, 255, 140, 245));
            graphics.drawRoundRect(box.x, box.y, box.width, box.height, 10, 10);

            if (labelVisible && label != null && !label.isEmpty())
            {
                Font drawFont = baseFont == null ? oldFont : baseFont;
                float labelFontSize = (float) Math.max(6, config.getInt("setup.active.label.font.size", 12));
                graphics.setFont(drawFont.deriveFont(Font.BOLD, labelFontSize));
                graphics.setColor(Color.WHITE);
                FontMetrics metrics = graphics.getFontMetrics();
                int textX = box.x + Math.max(0, (box.width - metrics.stringWidth(label)) / 2);
                int textY = box.y + ((box.height - metrics.getHeight()) / 2) + metrics.getAscent();
                graphics.drawString(label, textX, textY);
            }
        }
        finally
        {
            graphics.setStroke(oldStroke);
            graphics.setColor(oldColor);
            graphics.setFont(oldFont);
        }
    }

    private void drawCenteredString(Graphics2D graphics, String value, int x, int baselineY, int width)
    {
        Font original = graphics.getFont();
        FontMetrics metrics = graphics.getFontMetrics();

        if (metrics.stringWidth(value) > width && original.getSize2D() > 7.0f)
        {
            float size = original.getSize2D();
            while (size > 7.0f && metrics.stringWidth(value) > width)
            {
                size -= 0.5f;
                graphics.setFont(original.deriveFont(size));
                metrics = graphics.getFontMetrics();
            }
        }

        int textX = x + ((width - metrics.stringWidth(value)) / 2);
        graphics.drawString(value, textX, baselineY);
        graphics.setFont(original);
    }
    private void drawButton(Graphics2D graphics, int x, int y, int buttonSize, boolean active)
    {
        Color shadow = config.buttonShadowColor();
        Color outer = config.buttonOuterColor();
        Color inner = config.buttonInnerColor();
        Color border = active ? config.buttonActiveBorderColor() : config.buttonBorderColor();
        Color bevelLight = config.buttonBevelLightColor();
        Color bevelDark = config.buttonBevelDarkColor();

        if ((!active && config.buttonGlowEnabled()) || (active && config.buttonActiveGlowEnabled()))
        {
            Color glow = active ? config.buttonActiveGlowColor() : config.buttonGlowColor();
            graphics.setColor(glow);
            graphics.fillRoundRect(x - 2, y - 2, buttonSize + 4, buttonSize + 4, 7, 7);
        }

        graphics.setColor(shadow);
        graphics.fillRoundRect(x + 2, y + 2, buttonSize, buttonSize, 4, 4);

        graphics.setColor(outer);
        graphics.fillRoundRect(x, y, buttonSize, buttonSize, 4, 4);

        graphics.setColor(bevelLight);
        graphics.drawLine(x + 1, y + 1, x + buttonSize - 3, y + 1);
        graphics.drawLine(x + 1, y + 1, x + 1, y + buttonSize - 3);

        graphics.setColor(bevelDark);
        graphics.drawLine(x + 2, y + buttonSize - 2, x + buttonSize - 2, y + buttonSize - 2);
        graphics.drawLine(x + buttonSize - 2, y + 2, x + buttonSize - 2, y + buttonSize - 2);

        graphics.setColor(inner);
        graphics.fillRect(x + 3, y + 3, buttonSize - 6, buttonSize - 6);

        Image logo = getLogoImage();
        int logoPadding = config.buttonLogoPadding();
        if (logo != null)
        {
            int logoBaseSize = Math.max(1, buttonSize - (logoPadding * 2));
            int logoDrawWidth = Math.max(1, Math.round(logoBaseSize * config.buttonLogoScaleX()));
            int logoDrawHeight = Math.max(1, Math.round(logoBaseSize * config.buttonLogoScaleY()));
            int logoDrawX = x + logoPadding + ((logoBaseSize - logoDrawWidth) / 2) + config.buttonLogoOffsetX();
            int logoDrawY = y + logoPadding + ((logoBaseSize - logoDrawHeight) / 2) + config.buttonLogoOffsetY();

            graphics.drawImage(
                logo,
                logoDrawX,
                logoDrawY,
                logoDrawWidth,
                logoDrawHeight,
                null
            );
        }

        // AUTOFLIP_PATCH_AI2_REMOVE_LOGO_FALLBACK_V1
        // Fallback A removed. Toggle uses the normal PNG logo path only.

        graphics.setStroke(new BasicStroke(1.2f));
        graphics.setColor(border);
        graphics.drawRect(x, y, buttonSize, buttonSize);

        if (active && config.buttonActiveInnerOutlineEnabled())
        {
            graphics.setColor(config.buttonActiveBorderColor());
            graphics.drawRect(x + 3, y + 3, buttonSize - 6, buttonSize - 6);
        }    }

    private void drawRefreshBoardButton(Graphics2D graphics, int x, int y, int width, int height)
    {
        Font oldFont = graphics.getFont();

        Color fill = new Color(42, 122, 68, 245);
        Color top = new Color(67, 201, 104, 245);
        Color border = new Color(77, 224, 119, 255);
        Color text = new Color(12, 22, 14, 255);

        graphics.setColor(new Color(0, 0, 0, 115));
        graphics.fillRoundRect(x + 3, y + 4, width, height, 8, 8);

        graphics.setColor(fill);
        graphics.fillRoundRect(x, y, width, height, 8, 8);

        graphics.setColor(top);
        graphics.fillRoundRect(x, y, width, Math.max(10, height / 2), 8, 8);

        graphics.setColor(border);
        graphics.drawRoundRect(x, y, width, height, 8, 8);

        graphics.setFont(oldFont.deriveFont(Font.BOLD, 10.8f));
        graphics.setColor(text);
        drawCenteredString(graphics, "Refresh Board", x, y + (height / 2) + 4, width);

        graphics.setFont(oldFont);
    }

    private void drawOptimizeButton(Graphics2D graphics, int x, int y, int width)
    {
        int height = 46;
        Font oldFont = graphics.getFont();

        Color fill = new Color(91, 58, 21, 245);
        Color top = new Color(127, 86, 33, 245);
        Color border = new Color(232, 174, 70, 255);
        Color text = new Color(255, 238, 186, 255);

        graphics.setColor(new Color(0, 0, 0, 115));
        graphics.fillRoundRect(x + 3, y + 4, width, height, 8, 8);

        graphics.setColor(fill);
        graphics.fillRoundRect(x, y, width, height, 8, 8);

        graphics.setColor(top);
        graphics.fillRoundRect(x, y, width, Math.max(12, height / 2), 8, 8);

        graphics.setColor(border);
        graphics.drawRoundRect(x, y, width, height, 8, 8);

        graphics.setFont(oldFont.deriveFont(Font.BOLD, 13.5f));
        graphics.setColor(text);
        drawCenteredString(graphics, "Optimize Board", x, y + 22, width);

        graphics.setFont(oldFont.deriveFont(Font.BOLD, 10.4f));
        graphics.setColor(new Color(245, 245, 235, 255));
        drawCenteredString(graphics, "Calculate with current settings", x, y + 35, width);

        graphics.setFont(oldFont);
    }
    private void drawMenuShell(Graphics2D graphics, int x, int y)
    {
        int menuWidth = config.menuWidth();
        int menuHeight = config.menuHeight();

        Color panel = new Color(18, 13, 8, 238);
        Color panelTop = new Color(28, 19, 10, 225);
        Color border = new Color(185, 126, 29, 235);
        Color gold = new Color(226, 170, 52, 255);
        Color text = new Color(245, 245, 235, 255);
        Color muted = new Color(232, 232, 220, 250);
        Color green = new Color(123, 239, 139, 255);
        Color field = new Color(27, 22, 16, 240);

        Font oldFont = graphics.getFont();

        graphics.setColor(new Color(0, 0, 0, 150));
        graphics.fillRoundRect(x + 5, y + 6, menuWidth, menuHeight, 10, 10);

        graphics.setColor(panel);
        graphics.fillRoundRect(x, y, menuWidth, menuHeight, 10, 10);

        graphics.setColor(panelTop);
        graphics.fillRoundRect(x + 4, y + 4, menuWidth - 8, 58, 8, 8);

        graphics.setStroke(new BasicStroke(1.5f));
        graphics.setColor(border);
        graphics.drawRoundRect(x, y, menuWidth, menuHeight, 10, 10);

        drawHeaderBrandImage(graphics, x + 12, y + 7, menuWidth - 24, 49);

        graphics.setFont(oldFont.deriveFont(Font.BOLD, 10.8f));
        graphics.setColor(muted);
        graphics.drawString("Smart plan for your time & budget.", x + 20, y + 84);

        int rowY = y + 112;
        int fieldWidth = menuWidth - 40;

        int hoursFieldY = rowY;
        drawField(graphics, x + 20, rowY, fieldWidth, "Hours away (Auto rounded)", plugin.getAutoFlipHoursLabel(), field, green, gold, true);
        rowY += 62;

        drawField(graphics, x + 20, rowY, fieldWidth, "Budget (gp)", plugin.getAutoFlipBudgetLabel(), field, green, gold, false);
        rowY += 62;

        drawCheckbox(graphics, x + 22, rowY - 7, plugin.isAutoFlipUseCurrentCashStack(), "Use current cash stack", text, green);
        graphics.setFont(oldFont.deriveFont(Font.BOLD, 10.8f));
        graphics.setColor(muted);
        graphics.drawString(plugin.getAutoFlipCashBalanceLabel(), x + 57, rowY + 27);
        rowY += 50;

        graphics.setFont(oldFont.deriveFont(Font.BOLD, 12.2f));
        graphics.setColor(text);
        graphics.drawString("Strategy", x + 20, rowY);

        graphics.setColor(new Color(245, 245, 235, 255));
        graphics.drawOval(x + menuWidth - 43, rowY - 14, 17, 17);
        graphics.drawString("i", x + menuWidth - 37, rowY - 1);

        rowY += 17;
        int riskFieldY = rowY;
        drawRiskDropdown(graphics, x + 20, riskFieldY, fieldWidth, plugin.getAutoFlipRiskMode(), false, plugin.getAutoFlipHoveredRiskIndex());

        int optimizeY = riskFieldY + 54;
        drawOptimizeButton(graphics, x + 20, optimizeY, fieldWidth);

        graphics.setFont(oldFont.deriveFont(Font.BOLD, 10.4f));
        graphics.setColor(muted);
        graphics.fillOval(x + 20, y + menuHeight - 25, 8, 8);
        graphics.drawString("Recommendations update in real-time.", x + 36, y + menuHeight - 17);

        if (plugin.isAutoFlipHoursDropdownOpen())
        {
            drawHoursDropdown(graphics, x + 20, hoursFieldY + 33, fieldWidth, plugin.getAutoFlipHoursAway(), plugin.getAutoFlipHoveredHoursIndex());
        }

        if (plugin.isAutoFlipRiskDropdownOpen())
        {
            drawRiskDropdown(graphics, x + 20, riskFieldY, fieldWidth, plugin.getAutoFlipRiskMode(), true, plugin.getAutoFlipHoveredRiskIndex());
        }

        graphics.setFont(oldFont);
    }

    private void drawHoursDropdown(Graphics2D graphics, int x, int y, int width, int selectedHours, int hoveredIndex)
    {
        int[] options = new int[] {4, 8, 12, 16, 20, 24, 48};
        int optionHeight = 31;
        int height = optionHeight * 8;

        Color field = new Color(42, 35, 30, 250);
        Color selected = new Color(120, 120, 120, 235);
        Color hover = new Color(82, 74, 58, 245);
        Color border = new Color(159, 113, 39, 245);
        Color green = new Color(123, 239, 139, 255);
        Color text = new Color(245, 235, 210, 255);
        Color muted = new Color(224, 211, 174, 245);

        graphics.setColor(new Color(0, 0, 0, 145));
        graphics.fillRect(x + 3, y + 4, width, height);

        graphics.setColor(field);
        graphics.fillRect(x, y, width, height);

        graphics.setColor(border);
        graphics.drawRect(x, y, width, height);

        Font oldFont = graphics.getFont();
        graphics.setFont(oldFont.deriveFont(Font.BOLD, 13.0f));

        for (int i = 0; i < 8; i++)
        {
            boolean isCustom = i == 7;
            boolean active = !isCustom && options[i] == selectedHours;
            boolean hovered = i == hoveredIndex;
            int optionY = y + (i * optionHeight);

            if (hovered)
            {
                graphics.setColor(hover);
                graphics.fillRect(x + 1, optionY + 1, width - 2, optionHeight - 2);
            }

            if (active)
            {
                graphics.setColor(selected);
                graphics.fillRect(x + 1, optionY + 1, width - 2, optionHeight - 2);
            }

            graphics.setColor(active ? green : (isCustom ? muted : text));
            String label = isCustom ? "Custom..." : options[i] + " hours";
            graphics.drawString(label, x + 16, optionY + 21);
        }

        graphics.setFont(oldFont);
    }
    private void drawField(Graphics2D graphics, int x, int y, int width, String label, String value, Color field, Color text, Color accent, boolean drawChevron)
    {
        Font oldFont = graphics.getFont();

        Color labelColor = new Color(245, 245, 235, 255);
        Color valueColor = new Color(129, 255, 139, 255);
        Color border = new Color(187, 128, 32, 245);
        Color bg = new Color(18, 13, 8, 236);

        graphics.setFont(oldFont.deriveFont(Font.BOLD, 11.8f));
        graphics.setColor(labelColor);
        graphics.drawString(label, x, y - 7);

        graphics.setColor(bg);
        graphics.fillRoundRect(x, y, width, 30, 6, 6);

        graphics.setColor(border);
        graphics.drawRoundRect(x, y, width, 30, 6, 6);

        graphics.setFont(oldFont.deriveFont(Font.BOLD, 13.4f));
        graphics.setColor(valueColor);
        graphics.drawString(value, x + 12, y + 20);

        if (drawChevron)
        {
            drawChevronDown(graphics, x + width - 29, y + 11, accent);
        }

        graphics.setFont(oldFont);
    }

    private void drawCheckbox(Graphics2D graphics, int x, int y, boolean checked, String label, Color text, Color accent)
    {
        Font oldFont = graphics.getFont();

        graphics.setColor(new Color(24, 35, 19, 230));
        graphics.fillRoundRect(x, y, 15, 15, 3, 3);

        graphics.setColor(new Color(159, 113, 39, 235));
        graphics.drawRoundRect(x, y, 15, 15, 3, 3);

        if (checked)
        {
            Stroke oldStroke = graphics.getStroke();
            graphics.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(accent);
            graphics.drawLine(x + 3, y + 8, x + 6, y + 12);
            graphics.drawLine(x + 6, y + 12, x + 13, y + 3);
            graphics.setStroke(oldStroke);
        }

        graphics.setFont(oldFont.deriveFont(Font.PLAIN, 11.0f));
        graphics.setColor(text);
        graphics.drawString(label, x + 23, y + 12);

        graphics.setFont(oldFont);
    }

    private void drawHeaderBrandImage(Graphics2D graphics, int x, int y, int maxWidth, int maxHeight)
    {
        Image image = loadHeaderBrandImage();

        if (image == null)
        {
            drawAssistantLogo(graphics, x + 4, y + 4, 32);

            Font oldFont = graphics.getFont();
            graphics.setFont(oldFont.deriveFont(Font.BOLD, 14.5f));
            graphics.setColor(new Color(247, 218, 111, 255));
            graphics.drawString("AutoFlip Assistant", x + 46, y + 27);
            graphics.setFont(oldFont);
            return;
        }

        int imageWidth = image.getWidth(null);
        int imageHeight = image.getHeight(null);

        if (imageWidth <= 0 || imageHeight <= 0)
        {
            return;
        }

        double scale = Math.min((double) maxWidth / (double) imageWidth, (double) maxHeight / (double) imageHeight);
        int drawWidth = Math.max(1, (int) Math.round(imageWidth * scale));
        int drawHeight = Math.max(1, (int) Math.round(imageHeight * scale));

        int drawX = x;
        int drawY = y + Math.max(0, (maxHeight - drawHeight) / 2);

        graphics.drawImage(image, drawX, drawY, drawWidth, drawHeight, null);
    }

    private Image loadHeaderBrandImage()
    {
        if (headerBrandImage != null)
        {
            return headerBrandImage;
        }

        try
        {
            Path path = Paths.get("C:\\osrs-flip-assistant\\ProjectPhotos\\Logos\\autoflip-gg-custom-transparent.png");
            if (!Files.exists(path))
            {
                return null;
            }

            URL url = path.toUri().toURL();

            BufferedImage image = ImageIO.read(url);
            if (image != null)
            {
                BufferedImage cropped = cropTransparentPadding(image);
                headerBrandImage = cropped == null ? image : cropped;
                return headerBrandImage;
            }

            ImageIcon icon = new ImageIcon(url);
            if (icon.getIconWidth() > 0 && icon.getIconHeight() > 0)
            {
                headerBrandImage = icon.getImage();
                return headerBrandImage;
            }
        }
        catch (Exception ignored)
        {
            return null;
        }

        return null;
    }
    private void drawAssistantLogo(Graphics2D graphics, int x, int y, int size)
    {
        Stroke oldStroke = graphics.getStroke();
        graphics.setStroke(new BasicStroke(Math.max(2.0f, size / 10.0f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(new Color(225, 225, 205, 255));
        graphics.drawLine(x, y + size, x + size / 3, y + 2);
        graphics.drawLine(x + size / 3, y + 2, x + size / 2, y + size - 4);
        graphics.setColor(new Color(95, 222, 75, 255));
        graphics.drawLine(x + size / 2, y + size - 4, x + size, y + 5);
        graphics.drawLine(x + size - 12, y + 5, x + size, y + 5);
        graphics.drawLine(x + size, y + 5, x + size, y + 17);
        graphics.setStroke(oldStroke);
    }

    private void drawCoinStack(Graphics2D graphics, int x, int y)
    {
        Color coin = new Color(222, 166, 17, 255);
        Color edge = new Color(133, 91, 7, 255);
        for (int i = 0; i < 4; i++)
        {
            graphics.setColor(coin);
            graphics.fillOval(x + (i % 2) * 4, y - (i * 4), 18, 9);
            graphics.setColor(edge);
            graphics.drawOval(x + (i % 2) * 4, y - (i * 4), 18, 9);
        }
    }

    private void drawFolderIcon(Graphics2D graphics, int x, int y)
    {
        graphics.setColor(new Color(208, 176, 111, 245));
        graphics.drawRoundRect(x, y + 4, 22, 16, 3, 3);
        graphics.drawLine(x + 2, y + 4, x + 7, y);
        graphics.drawLine(x + 7, y, x + 13, y + 4);
    }

    private void drawRiskDropdown(Graphics2D graphics, int x, int y, int width, String selectedRisk, boolean open, int hoveredIndex)
    {
        Color field = new Color(35, 27, 17, 238);
        Color selected = new Color(82, 64, 26, 230);
        Color hover = new Color(82, 74, 58, 245);
        Color border = new Color(159, 113, 39, 235);
        Color text = new Color(245, 245, 235, 255);
        Color green = new Color(123, 239, 139, 255);
        Color muted = new Color(232, 232, 220, 250);
        Color tooltipBg = new Color(20, 18, 14, 248);

        String strategy = normalizeStrategyForOverlay(selectedRisk);
        int headerHeight = 40;
        int optionHeight = 31;
        int height = open ? headerHeight + (optionHeight * 3) : headerHeight;

        graphics.setColor(field);
        graphics.fillRoundRect(x, y, width, height, 8, 8);
        graphics.setColor(border);
        graphics.drawRoundRect(x, y, width, height, 8, 8);

        Font oldFont = graphics.getFont();

        String label = strategyLabel(strategy);

        graphics.setFont(oldFont.deriveFont(Font.BOLD, 12.5f));
        graphics.setColor(green);

        if ("optimize".equals(strategy))
        {
            drawTinyShieldIcon(graphics, x + 22, y + 13, green);
        }
        else if ("exploratory".equals(strategy))
        {
            drawTinyRiskIcon(graphics, x + 22, y + 12, green);
        }
        else
        {
            drawTinyScaleIcon(graphics, x + 21, y + 12, green);
        }

        graphics.drawString(label, x + 45, y + 26);
        drawChevronDown(graphics, x + width - 29, y + 16, text);

        if (open)
        {
            String[] values = new String[] {"optimize", "adaptive", "exploratory"};
            String[] labels = new String[] {"Optimize", "Adaptive (Recommended)", "Exploratory"};
            String[] descriptions = new String[] {
                "Pure Optimization",
                "AutoFlip finds your favorite types of flips faster without sacrificing profit",
                "A mix of Optimizer and Adaptive"
            };

            for (int i = 0; i < labels.length; i++)
            {
                int optionY = y + headerHeight + (i * optionHeight);
                boolean isHovered = i == hoveredIndex;
                boolean isActive = values[i].equals(strategy);

                if (isHovered)
                {
                    graphics.setColor(hover);
                    graphics.fillRect(x + 8, optionY + 2, width - 16, optionHeight - 4);
                }

                if (isActive)
                {
                    graphics.setColor(selected);
                    graphics.fillRect(x + 8, optionY + 2, width - 16, optionHeight - 4);
                }

                Color rowColor = isActive ? green : muted;
                graphics.setColor(rowColor);
                graphics.setFont(oldFont.deriveFont(isActive ? Font.BOLD : Font.PLAIN, 12.0f));

                if ("optimize".equals(values[i]))
                {
                    drawTinyShieldIcon(graphics, x + 22, optionY + 7, rowColor);
                }
                else if ("adaptive".equals(values[i]))
                {
                    drawTinyScaleIcon(graphics, x + 21, optionY + 7, rowColor);
                }
                else
                {
                    drawTinyRiskIcon(graphics, x + 22, optionY + 7, rowColor);
                }

                graphics.drawString(labels[i], x + 45, optionY + 22);
            }

            if (hoveredIndex >= 0 && hoveredIndex < descriptions.length)
            {
                int tooltipX = x + 10;
                int tooltipY = y + headerHeight + (hoveredIndex * optionHeight) + optionHeight - 1;
                int tooltipWidth = width - 20;
                int tooltipHeight = hoveredIndex == 1 ? 48 : 30;

                if (tooltipY + tooltipHeight > y + height + 52)
                {
                    tooltipY = y + height - tooltipHeight - 3;
                }

                graphics.setColor(new Color(0, 0, 0, 150));
                graphics.fillRoundRect(tooltipX + 3, tooltipY + 3, tooltipWidth, tooltipHeight, 8, 8);

                graphics.setColor(tooltipBg);
                graphics.fillRoundRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 8, 8);

                graphics.setColor(new Color(123, 239, 139, 220));
                graphics.drawRoundRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 8, 8);

                graphics.setFont(oldFont.deriveFont(Font.BOLD, 9.8f));
                graphics.setColor(text);
                drawWrappedStrategyTooltip(graphics, descriptions[hoveredIndex], tooltipX + 8, tooltipY + 14, tooltipWidth - 16, 12);
            }
        }

        graphics.setFont(oldFont);
    }

    private String normalizeStrategyForOverlay(String raw)
    {
        if (raw == null)
        {
            return "adaptive";
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

        return "adaptive";
    }

    private String strategyLabel(String strategy)
    {
        if ("optimize".equals(strategy))
        {
            return "Optimize";
        }

        if ("exploratory".equals(strategy))
        {
            return "Exploratory";
        }

        return "Adaptive (Recommended)";
    }

    private void drawWrappedStrategyTooltip(Graphics2D graphics, String text, int x, int y, int width, int lineHeight)
    {
        if (text == null || text.isEmpty())
        {
            return;
        }

        FontMetrics metrics = graphics.getFontMetrics();
        String[] words = text.split(" ");
        String line = "";
        int lineY = y;

        for (String word : words)
        {
            String next = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && metrics.stringWidth(next) > width)
            {
                graphics.drawString(line, x, lineY);
                line = word;
                lineY += lineHeight;
            }
            else
            {
                line = next;
            }
        }

        if (!line.isEmpty())
        {
            graphics.drawString(line, x, lineY);
        }
    }

    private void drawTinyRiskIcon(Graphics2D graphics, int x, int y, Color color)
    {
        Stroke oldStroke = graphics.getStroke();
        graphics.setStroke(new BasicStroke(1.3f));
        graphics.setColor(color);

        Polygon p = new Polygon();
        p.addPoint(x + 7, y);
        p.addPoint(x + 15, y + 15);
        p.addPoint(x - 1, y + 15);
        graphics.drawPolygon(p);

        graphics.drawLine(x + 7, y + 5, x + 7, y + 10);
        graphics.fillOval(x + 6, y + 12, 2, 2);

        graphics.setStroke(oldStroke);
    }
    private void drawChevronDown(Graphics2D graphics, int x, int y, Color color)
    {
        Stroke oldStroke = graphics.getStroke();
        graphics.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(color);
        graphics.drawLine(x, y, x + 5, y + 5);
        graphics.drawLine(x + 5, y + 5, x + 10, y);
        graphics.setStroke(oldStroke);
    }

    private void drawTinyScaleIcon(Graphics2D graphics, int x, int y, Color color)
    {
        Stroke oldStroke = graphics.getStroke();
        graphics.setStroke(new BasicStroke(1.3f));
        graphics.setColor(color);
        graphics.drawLine(x + 7, y, x + 7, y + 13);
        graphics.drawLine(x + 2, y + 4, x + 12, y + 4);
        graphics.drawLine(x + 3, y + 4, x, y + 11);
        graphics.drawLine(x + 11, y + 4, x + 14, y + 11);
        graphics.drawLine(x - 2, y + 11, x + 2, y + 11);
        graphics.drawLine(x + 12, y + 11, x + 16, y + 11);
        graphics.drawLine(x + 4, y + 15, x + 10, y + 15);
        graphics.setStroke(oldStroke);
    }

    private void drawTinyShieldIcon(Graphics2D graphics, int x, int y, Color color)
    {
        Polygon p = new Polygon();
        p.addPoint(x + 7, y);
        p.addPoint(x + 14, y + 4);
        p.addPoint(x + 12, y + 13);
        p.addPoint(x + 7, y + 17);
        p.addPoint(x + 2, y + 13);
        p.addPoint(x, y + 4);

        graphics.setColor(color);
        graphics.drawPolygon(p);
    }
    private void drawSmallButton(Graphics2D graphics, int x, int y, int width, String label, boolean primary)
    {
        int height = primary ? 48 : 46;
        Color fill = primary ? new Color(44, 103, 52, 242) : new Color(36, 28, 19, 238);
        Color top = primary ? new Color(71, 139, 74, 238) : new Color(57, 43, 25, 235);
        Color border = primary ? new Color(106, 208, 108, 245) : new Color(151, 106, 42, 238);
        Color text = new Color(238, 229, 196, 255);

        graphics.setColor(fill);
        graphics.fillRoundRect(x, y, width, height, 8, 8);

        graphics.setColor(top);
        graphics.drawLine(x + 3, y + 3, x + width - 4, y + 3);

        graphics.setColor(border);
        graphics.drawRoundRect(x, y, width, height, 8, 8);

        Font oldFont = graphics.getFont();
        graphics.setFont(oldFont.deriveFont(primary ? Font.BOLD : Font.PLAIN, 16.0f));
        FontMetrics metrics = graphics.getFontMetrics();
        int textX = x + ((width - metrics.stringWidth(label)) / 2);
        int textY = y + ((height - metrics.getHeight()) / 2) + metrics.getAscent();

        graphics.setColor(text);
        graphics.drawString(label, textX, textY);
        graphics.setFont(oldFont);
    }
    private static final class PlanCard
    {
        private final String itemName;
        private final String quantity;
        private final String profitEach;
        private final String totalProfit;
        private final String timeHint;
        private final String shortReason;
        private final Color iconFill;
        private final Color iconGlow;

        private PlanCard(
            String itemName,
            String quantity,
            String profitEach,
            String totalProfit,
            String timeHint,
            String shortReason,
            Color iconFill,
            Color iconGlow
        )
        {
            this.itemName = itemName;
            this.quantity = quantity;
            this.profitEach = profitEach;
            this.totalProfit = totalProfit;
            this.timeHint = timeHint;
            this.shortReason = shortReason;
            this.iconFill = iconFill;
            this.iconGlow = iconGlow;
        }
    }

    private static final class LiveOverlayAsset
    {
        private String loadedPath = "";
        private long loadedModifiedMs = -1L;
        private BufferedImage image;

        private BufferedImage load(String rawPath)
        {
            String value = rawPath == null ? "" : rawPath.trim();
            if (value.isEmpty())
            {
                loadedPath = "";
                loadedModifiedMs = -1L;
                image = null;
                return null;
            }

            try
            {
                Path path = Paths.get(value);
                if (!path.isAbsolute())
                {
                    path = Paths.get("C:\\osrs-flip-assistant\\Plugin-Tinker").resolve(path).normalize();
                }

                if (!Files.isRegularFile(path))
                {
                    image = null;
                    return null;
                }

                String absolutePath = path.toAbsolutePath().normalize().toString();
                long modifiedMs = Files.getLastModifiedTime(path).toMillis();
                if (!absolutePath.equals(loadedPath) || modifiedMs != loadedModifiedMs)
                {
                    image = ImageIO.read(path.toFile());
                    loadedPath = absolutePath;
                    loadedModifiedMs = modifiedMs;
                }
                return image;
            }
            catch (Exception ignored)
            {
                return null;
            }
        }
    }

    private static final class DevConfig
    {
        private final Properties props = new Properties();
        private long lastAttemptMs = 0L;
        private long lastModifiedMs = -1L;

        private void reloadIfNeeded()
        {
            long now = System.currentTimeMillis();
            if (now - lastAttemptMs < 750L)
            {
                return;
            }

            lastAttemptMs = now;

            Path path = Paths.get("_runtime", "overlay_dev_config.properties");
            if (!Files.exists(path))
            {
                path = Paths.get(
                    "C:\\osrs-flip-assistant\\Plugin-Tinker\\_runtime\\overlay_dev_config.properties"
                );
            }

            try
            {
                if (!Files.exists(path))
                {
                    return;
                }

                long modified = Files.getLastModifiedTime(path).toMillis();
                if (modified == lastModifiedMs)
                {
                    return;
                }

                Properties loaded = new Properties();
                try (InputStream input = Files.newInputStream(path))
                {
                    loaded.load(input);
                }

                props.clear();
                props.putAll(loaded);
                lastModifiedMs = modified;
            }
            catch (Exception ignored)
            {
                // Keep previous good settings.
            }
        }

        private int getInt(String key, int fallback)
        {
            try
            {
                return Integer.parseInt(props.getProperty(key, Integer.toString(fallback)).trim());
            }
            catch (Exception ignored)
            {
                return fallback;
            }
        }

        private float getFloat(String key, float fallback)
        {
            try
            {
                return Float.parseFloat(props.getProperty(key, Float.toString(fallback)).trim());
            }
            catch (Exception ignored)
            {
                return fallback;
            }
        }

        private boolean getBool(String key, boolean fallback)
        {
            String value = props.getProperty(key);
            if (value == null)
            {
                return fallback;
            }

            return "true".equalsIgnoreCase(value.trim())
                || "yes".equalsIgnoreCase(value.trim())
                || "1".equals(value.trim());
        }

        private String getString(String key, String fallback)
        {
            String value = props.getProperty(key);
            return value == null ? fallback : value.trim();
        }

        private Color getRgbColor(String key, Color fallback)
        {
            try
            {
                String raw = props.getProperty(key);
                if (raw == null)
                {
                    return fallback;
                }

                String[] parts = raw.trim().split(",");
                if (parts.length != 3)
                {
                    return fallback;
                }

                int r = Math.max(0, Math.min(255, Integer.parseInt(parts[0].trim())));
                int g = Math.max(0, Math.min(255, Integer.parseInt(parts[1].trim())));
                int b = Math.max(0, Math.min(255, Integer.parseInt(parts[2].trim())));
                return new Color(r, g, b, fallback.getAlpha());
            }
            catch (Exception ignored)
            {
                return fallback;
            }
        }

        private Color withAlpha(Color color, int alpha)
        {
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), clampAlpha(alpha));
        }
        private int clampAlpha(int value)
        {
            return Math.max(0, Math.min(255, value));
        }

        private boolean buttonGlowEnabled() { return getBool("button.glow.enabled", false); }
        private boolean buttonActiveGlowEnabled() { return getBool("button.active.glow.enabled", false); }
        private boolean buttonActiveInnerOutlineEnabled() { return getBool("button.active.inner.outline.enabled", true); }

        private Color buttonOuterColor() { return getRgbColor("button.outer.rgb", new Color(35, 28, 21, 240)); }
        private Color buttonInnerColor() { return getRgbColor("button.inner.rgb", new Color(73, 54, 31, 235)); }
        private Color buttonBorderColor() { return getRgbColor("button.border.rgb", new Color(91, 69, 38, 250)); }
        private Color buttonActiveBorderColor() { return getRgbColor("button.active.border.rgb", new Color(201, 159, 74, 255)); }
        private Color buttonBevelLightColor() { return getRgbColor("button.bevel.light.rgb", new Color(128, 96, 52, 255)); }
        private Color buttonBevelDarkColor() { return getRgbColor("button.bevel.dark.rgb", new Color(12, 10, 8, 255)); }

        private Color buttonShadowColor()
        {
            return withAlpha(
                getRgbColor("button.shadow.rgb", new Color(0, 0, 0, 180)),
                getInt("button.shadow.alpha", 180)
            );
        }

        private Color buttonGlowColor()
        {
            return withAlpha(
                getRgbColor("button.glow.rgb", new Color(63, 198, 116, 0)),
                getInt("button.glow.alpha", 0)
            );
        }

        private Color buttonActiveGlowColor()
        {
            return withAlpha(
                getRgbColor("button.active.glow.rgb", new Color(94, 255, 145, 0)),
                getInt("button.active.glow.alpha", 0)
            );
        }
        private int buttonSize() { return getInt("button.size", 30); }
        private int buttonOffsetX() { return getInt("button.offset.x", -65); }
        private int buttonOffsetY() { return getInt("button.offset.y", -2); }
        private int buttonLogoPadding() { return getInt("button.logo.padding", 4); }
        private float buttonLogoScaleX() { return getFloat("button.logo.scale.x", 1.00f); }
        private float buttonLogoScaleY() { return getFloat("button.logo.scale.y", 1.00f); }
        private int buttonLogoOffsetX() { return getInt("button.logo.offset.x", 0); }
        private int buttonLogoOffsetY() { return getInt("button.logo.offset.y", 0); }

        private int menuOffsetX() { return getInt("menu.offset.x", 8); }
        private int menuOffsetY() { return getInt("menu.offset.y", 28); }
        private int menuWidth() { return getInt("menu.width", 178); }
        private int menuHeight() { return getInt("menu.height", 236); }

        private int cardOffsetX() { return getInt("card.offset.x", 0); }
        private int cardOffsetY() { return getInt("card.offset.y", 0); }
        private int cardWidth() { return getInt("card.width", 112); }
        private int cardHeight() { return getInt("card.height", 128); }
        private int cardHeaderHeight() { return getInt("card.header.height", 20); }
        private int cardIconX() { return getInt("card.icon.x", 35); }
        private int cardIconY() { return getInt("card.icon.y", 24); }
        private int cardIconSize() { return getInt("card.icon.size", 30); }


        private int cardIconBoxOffsetX() { return getInt("card.icon.box.offset.x", 0); }
        private int cardIconBoxOffsetY() { return getInt("card.icon.box.offset.y", 0); }
        private int cardIconGlowOffsetX() { return getInt("card.icon.glow.offset.x", 0); }
        private int cardIconGlowOffsetY() { return getInt("card.icon.glow.offset.y", 0); }
        private int cardNameY() { return getInt("card.name.y", 68); }
        private int cardQuantityY() { return getInt("card.quantity.y", 81); }

        private int cardQuantityX() { return getInt("card.quantity.x", 0); }
        private int cardProfitLabelX() { return getInt("card.profit.label.x", 8); }
        private int cardProfitValueX() { return getInt("card.profit.value.x", 8); }
        private int cardTotalLabelX() { return getInt("card.total.label.x", 8); }
        private int cardTotalValueX() { return getInt("card.total.value.x", 8); }
        private int cardProfitLabelY() { return getInt("card.profit.label.y", 95); }
        private int cardProfitValueY() { return getInt("card.profit.value.y", 107); }
        private int cardTotalLabelY() { return getInt("card.total.label.y", 119); }
        private int cardTotalValueY() { return getInt("card.total.value.y", 131); }
        private int cardReasonY() { return getInt("card.reason.y", 145); }
        private boolean textSharpEnabled() { return getBool("text.sharp.enabled", true); }

        private boolean editModeEnabled() { return getBool("edit.mode.enabled", false); }
        private boolean editHandleLabelsEnabled() { return getBool("edit.handles.labels.enabled", true); }
        private int editHandleAlpha() { return clampAlpha(getInt("edit.handles.alpha", 220)); }
        private float editHandleFont() { return getFloat("edit.handles.font", 8.0f); }
        private int editQuantityHandleWidth() { return getInt("edit.quantity.handle.width", 70); }
        private int editTextBoxHeight() { return getInt("edit.text.box.height", 15); }
        private int editTextBoxBaselinePadY() { return getInt("edit.text.box.baseline.pad.y", 11); }
        private int editEachLabelBoxWidth() { return getInt("edit.each.label.box.width", 34); }
        private int editEachValueBoxWidth() { return getInt("edit.each.value.box.width", 78); }
        private int editTotalLabelBoxWidth() { return getInt("edit.total.label.box.width", 36); }
        private int editTotalValueBoxWidth() { return getInt("edit.total.value.box.width", 92); }
        private boolean explanationPanelEnabled() { return getBool("explanation.panel.enabled", true); }
        private int explanationPanelOffsetX() { return getInt("explanation.panel.offset.x", 0); }
        private int explanationPanelOffsetY() { return getInt("explanation.panel.offset.y", 8); }
        private int explanationPanelWidth() { return getInt("explanation.panel.width", 0); }
        private int explanationPanelHeight() { return getInt("explanation.panel.height", 92); }
        private int explanationPanelAlpha() { return clampAlpha(getInt("explanation.panel.alpha", 226)); }
        private int explanationPanelBorderAlpha() { return clampAlpha(getInt("explanation.panel.border.alpha", 230)); }
        private int explanationLineGap() { return getInt("explanation.line.gap", 16); }
        private float explanationTitleFont() { return getFloat("explanation.font.title", 12.0f); }
        private float explanationBodyFont() { return getFloat("explanation.font.body", 10.0f); }

        private boolean cardReasonEnabled() { return getBool("card.reason.enabled", true); }
private float cardFontScale() { return getFloat("card.font.scale", 1.00f); }
private float cardNameFont() { return getFloat("card.font.name", 10.0f); }
        private float cardQuantityFont() { return getFloat("card.font.quantity", 9.5f); }
        private float cardLabelFont() { return getFloat("card.font.label", 8.5f); }

        private float cardEachLabelFont() { return getFloat("card.font.each.label", cardLabelFont()); }
        private float cardEachValueFont() { return getFloat("card.font.each.value", cardValueFont()); }
        private float cardTotalLabelFont() { return getFloat("card.font.total.label", cardLabelFont()); }
        private float cardTotalValueFont() { return getFloat("card.font.total.value", cardValueFont()); }
        private float cardValueFont() { return getFloat("card.font.value", 9.0f); }
        private float cardReasonFont() { return getFloat("card.reason.font", 8.0f); }
        private float cardTimeFont() { return getFloat("card.font.time", 8.0f); }
        private boolean cardRuneScapeFontEnabled() { return getBool("card.font.runescape.enabled", true); }

        private int cardAlpha() { return clampAlpha(getInt("card.alpha", 218)); }
        private int cardTopAlpha() { return clampAlpha(getInt("card.top.alpha", 228)); }
        private int cardBorderAlpha() { return clampAlpha(getInt("card.border.alpha", 225)); }

        private boolean buyGuidanceEnabled() { return getBool("buy.guidance.enabled", true); }
        private int buyGuidancePanelX() { return getInt("buy.guidance.panel.x", 2); }
        private int buyGuidancePanelY() { return getInt("buy.guidance.panel.y", 67); }
        private int buyGuidancePanelWidth(int cardWidth)
        {
            int configured = getInt("buy.guidance.panel.width", 0);
            return configured <= 0 ? Math.max(1, cardWidth - buyGuidancePanelX() - 2) : configured;
        }
        private int buyGuidancePanelHeight() { return getInt("buy.guidance.panel.height", 41); }
        private int buyGuidanceTextX() { return getInt("buy.guidance.text.x", 5); }
        private int buyGuidanceTitleY() { return getInt("buy.guidance.title.y", 11); }
        private int buyGuidanceItemY() { return getInt("buy.guidance.item.y", 24); }
        private int buyGuidanceStatusY() { return getInt("buy.guidance.status.y", 37); }
        private float buyGuidanceTitleFont() { return getFloat("buy.guidance.font.title", 8.5f); }
        private float buyGuidanceItemFont() { return getFloat("buy.guidance.font.item", 8.5f); }
        private float buyGuidanceStatusFont() { return getFloat("buy.guidance.font.status", 8.0f); }
        private boolean buyGuidanceTextSharpEnabled() { return getBool("buy.guidance.text.sharp.enabled", true); }
        private boolean buyGuidanceShadowEnabled() { return getBool("buy.guidance.shadow.enabled", true); }
        private int buyGuidanceShadowAlpha() { return clampAlpha(getInt("buy.guidance.shadow.alpha", 220)); }
        private int buyGuidanceAssetAlpha() { return clampAlpha(getInt("buy.guidance.asset.alpha", 255)); }
        private float buyGuidanceBorderWidth() { return getFloat("buy.guidance.border.width", 1.5f); }
        private String buyGuidanceAssetPath() { return getString("buy.guidance.asset.path", ""); }
        private String buyGuidanceTitle() { return getString("buy.guidance.title", "TIME TO BUY"); }
        private String buyGuidanceStatus() { return getString("buy.guidance.status", "CLICK BUY"); }
        private Color buyGuidanceFill()
        {
            return withAlpha(
                getRgbColor("buy.guidance.fill.rgb", new Color(10, 35, 22, 235)),
                getInt("buy.guidance.fill.alpha", 235)
            );
        }
        private Color buyGuidanceBorder()
        {
            return withAlpha(
                getRgbColor("buy.guidance.border.rgb", new Color(78, 255, 130, 245)),
                getInt("buy.guidance.border.alpha", 245)
            );
        }
        private Color buyGuidanceTitleColor()
        {
            return withAlpha(
                getRgbColor("buy.guidance.title.rgb", new Color(92, 255, 126, 255)),
                getInt("buy.guidance.title.alpha", 255)
            );
        }
        private Color buyGuidanceItemColor()
        {
            return withAlpha(
                getRgbColor("buy.guidance.item.rgb", new Color(255, 235, 180, 255)),
                getInt("buy.guidance.item.alpha", 255)
            );
        }
        private Color buyGuidanceStatusColor()
        {
            return withAlpha(
                getRgbColor("buy.guidance.status.rgb", new Color(135, 255, 164, 255)),
                getInt("buy.guidance.status.alpha", 255)
            );
        }

        private boolean sellGuidanceEnabled() { return getBool("sell.guidance.enabled", true); }
        private int sellGuidancePanelX() { return getInt("sell.guidance.panel.x", 2); }
        private int sellGuidancePanelY() { return getInt("sell.guidance.panel.y", 67); }
        private int sellGuidancePanelWidth(int cardWidth)
        {
            int configured = getInt("sell.guidance.panel.width", 0);
            return configured <= 0 ? Math.max(1, cardWidth - sellGuidancePanelX() - 2) : configured;
        }
        private int sellGuidancePanelHeight() { return getInt("sell.guidance.panel.height", 41); }
        private int sellGuidanceTextX() { return getInt("sell.guidance.text.x", 5); }
        private int sellGuidanceTitleY() { return getInt("sell.guidance.title.y", 11); }
        private int sellGuidanceItemY() { return getInt("sell.guidance.item.y", 24); }
        private int sellGuidanceStatusY() { return getInt("sell.guidance.status.y", 37); }
        private float sellGuidanceTitleFont() { return getFloat("sell.guidance.font.title", 8.5f); }
        private float sellGuidanceItemFont() { return getFloat("sell.guidance.font.item", 8.5f); }
        private float sellGuidanceStatusFont() { return getFloat("sell.guidance.font.status", 8.0f); }
        private boolean sellGuidanceTextSharpEnabled() { return getBool("sell.guidance.text.sharp.enabled", true); }
        private boolean sellGuidanceShadowEnabled() { return getBool("sell.guidance.shadow.enabled", true); }
        private int sellGuidanceShadowAlpha() { return clampAlpha(getInt("sell.guidance.shadow.alpha", 220)); }
        private int sellGuidanceAssetAlpha() { return clampAlpha(getInt("sell.guidance.asset.alpha", 255)); }
        private float sellGuidanceBorderWidth() { return getFloat("sell.guidance.border.width", 1.5f); }
        private String sellGuidanceReadyAssetPath() { return getString("sell.guidance.ready.asset.path", ""); }
        private String sellGuidanceBankAssetPath() { return getString("sell.guidance.bank.asset.path", ""); }
        private String sellGuidanceReadyTitle() { return getString("sell.guidance.ready.title", "TIME TO SELL"); }
        private String sellGuidanceReadyStatus() { return getString("sell.guidance.ready.status", "CLICK SELL"); }
        private String sellGuidanceBankTitle() { return getString("sell.guidance.bank.title", "ITEM IS IN BANK"); }
        private String sellGuidanceBankStatus() { return getString("sell.guidance.bank.status", "GET IT AND COME BACK"); }
        private boolean sellGuidanceBankBlocksSlot() { return getBool("sell.guidance.bank.blocks.slot", true); }
        private Color sellGuidanceReadyFill()
        {
            return withAlpha(
                getRgbColor("sell.guidance.ready.fill.rgb", new Color(15, 45, 20, 235)),
                getInt("sell.guidance.ready.fill.alpha", 235)
            );
        }
        private Color sellGuidanceReadyBorder()
        {
            return withAlpha(
                getRgbColor("sell.guidance.ready.border.rgb", new Color(76, 255, 118, 245)),
                getInt("sell.guidance.ready.border.alpha", 245)
            );
        }
        private Color sellGuidanceBankFill()
        {
            return withAlpha(
                getRgbColor("sell.guidance.bank.fill.rgb", new Color(28, 15, 6, 245)),
                getInt("sell.guidance.bank.fill.alpha", 245)
            );
        }
        private Color sellGuidanceBankBorder()
        {
            return withAlpha(
                getRgbColor("sell.guidance.bank.border.rgb", new Color(82, 255, 120, 245)),
                getInt("sell.guidance.bank.border.alpha", 245)
            );
        }
        private Color sellGuidanceTitleColor()
        {
            return withAlpha(
                getRgbColor("sell.guidance.title.rgb", new Color(92, 255, 126, 255)),
                getInt("sell.guidance.title.alpha", 255)
            );
        }
        private Color sellGuidanceItemColor()
        {
            return withAlpha(
                getRgbColor("sell.guidance.item.rgb", new Color(255, 235, 180, 255)),
                getInt("sell.guidance.item.alpha", 255)
            );
        }
        private Color sellGuidanceReadyStatusColor()
        {
            return withAlpha(
                getRgbColor("sell.guidance.ready.status.rgb", new Color(135, 255, 164, 255)),
                getInt("sell.guidance.ready.status.alpha", 255)
            );
        }
        private Color sellGuidanceBankStatusColor()
        {
            return withAlpha(
                getRgbColor("sell.guidance.bank.status.rgb", new Color(255, 190, 75, 255)),
                getInt("sell.guidance.bank.status.alpha", 255)
            );
        }
    }

    private Image getLogoImage()
    {
        if (logoLoadAttempted)
        {
            return logoImage;
        }

        logoLoadAttempted = true;

        try
        {
            URL url = AutoFlipOverlay.class.getResource(LOGO_RESOURCE);
            if (url == null)
            {
                return null;
            }

            BufferedImage image = ImageIO.read(url);
            if (image != null)
            {
                BufferedImage cropped = cropTransparentPadding(image);
                logoImage = cropped == null ? image : cropped;
                return logoImage;
            }

            ImageIcon icon = new ImageIcon(url);
            if (icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0)
            {
                return null;
            }

            logoImage = icon.getImage();
            return logoImage;
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private BufferedImage cropTransparentPadding(BufferedImage image)
    {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < image.getHeight(); y++)
        {
            for (int x = 0; x < image.getWidth(); x++)
            {
                int alpha = (image.getRGB(x, y) >>> 24) & 0xff;
                if (alpha > 12)
                {
                    if (x < minX) { minX = x; }
                    if (y < minY) { minY = y; }
                    if (x > maxX) { maxX = x; }
                    if (y > maxY) { maxY = y; }
                }
            }
        }

        if (maxX < minX || maxY < minY)
        {
            return null;
        }

        return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

}
