package gg.autoflip;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AutoFlipPriorityAndTargetBuyPolicyTest
{
    @Test
    public void sellThenToBuyThenRankedPriorityIsStable()
    {
        assertTrue(AutoFlipPlugin.resolveAutoFlipBoardPriority("SELL")
            < AutoFlipPlugin.resolveAutoFlipBoardPriority("TO_BUY"));
        assertTrue(AutoFlipPlugin.resolveAutoFlipBoardPriority("TO_BUY")
            < AutoFlipPlugin.resolveAutoFlipBoardPriority("BUY"));
    }

    @Test
    public void customTargetOwnsBuyAutofill()
    {
        assertEquals(1_000_000L, AutoFlipPlugin.resolveAutoFlipPreferredBuyAutofillGp(1_600L, 1_000_000L));
        assertEquals(1_600L, AutoFlipPlugin.resolveAutoFlipPreferredBuyAutofillGp(1_600L, 0L));
    }

    @Test
    public void geSessionCacheDoesNotDependOnHiddenTabTickSnapshots()
    {
        assertTrue(AutoFlipPlugin.isAutoFlipGrandExchangeInterfaceGroup(465));
        assertFalse(AutoFlipPlugin.isAutoFlipGrandExchangeInterfaceGroup(149));
        assertTrue(AutoFlipPlugin.shouldCaptureAutoFlipGeSessionInventory(true, true, true));
        assertFalse(AutoFlipPlugin.shouldCaptureAutoFlipGeSessionInventory(true, false, true));
        assertFalse(AutoFlipPlugin.shouldCaptureAutoFlipGeSessionInventory(false, true, true));
    }

    @Test
    public void hiddenInventoryWidgetCannotOverrideSessionCachePresence()
    {
        assertTrue(AutoFlipOverlay.resolveAutoFlipSellCardInventoryPresence(true, false));
        assertFalse(AutoFlipOverlay.resolveAutoFlipSellCardInventoryPresence(false, true));
    }

    @Test
    public void priorityCardCannotReserveInvisibleAccountIneligibleSlot()
    {
        assertTrue(AutoFlipPlugin.shouldReserveAutoFlipPriorityItem(true, false, false));
        assertFalse(AutoFlipPlugin.shouldReserveAutoFlipPriorityItem(false, false, false));
        assertFalse(AutoFlipPlugin.shouldReserveAutoFlipPriorityItem(true, true, false));
        assertFalse(AutoFlipPlugin.shouldReserveAutoFlipPriorityItem(true, false, true));
    }

    @Test
    public void explicitToBuyWatchOverridesOrdinaryRankedMembershipFilter()
    {
        assertTrue(AutoFlipPlugin.isAutoFlipPriorityCardAllowedForCurrentAccount("TO_BUY", false));
        assertTrue(AutoFlipPlugin.isAutoFlipPriorityCardAllowedForCurrentAccount("TO_BUY", true));
        assertFalse(AutoFlipPlugin.isAutoFlipPriorityCardAllowedForCurrentAccount("BUY", false));
        assertTrue(AutoFlipPlugin.isAutoFlipPriorityCardAllowedForCurrentAccount("BUY", true));
    }

    @Test
    public void budgetAllocationKeepsRankedUniverseOrderIntact() throws Exception
    {
        Class<?> rowClass = Class.forName("gg.autoflip.AutoFlipPlugin$AutoFlipPayloadRow");
        Class<?> contextClass = Class.forName("gg.autoflip.AutoFlipPlugin$AutoFlipRecommendationContext");

        java.lang.reflect.Constructor<?> ctor = rowClass.getDeclaredConstructor(
            int.class,
            String.class,
            long.class,
            long.class,
            long.class,
            int.class,
            int.class,
            double.class,
            String.class,
            String.class,
            String.class,
            String.class,
            contextClass
        );
        ctor.setAccessible(true);

        Object rubyNecklace = ctor.newInstance(1660, "Ruby necklace", 100L, 150L, 50L, 1, 1, 0.10D, "medium", "test", "", "", null);
        Object natureRune = ctor.newInstance(561, "Nature rune", 80L, 100L, 20L, 1, 1, 0.90D, "medium", "test", "", "", null);
        Object ruby = ctor.newInstance(1619, "Ruby", 70L, 90L, 20L, 1, 1, 0.99D, "medium", "test", "", "", null);

        java.lang.reflect.Method method = AutoFlipPlugin.class.getDeclaredMethod(
            "orderAutoFlipPayloadRowsForBudgetAllocation",
            java.util.List.class
        );
        method.setAccessible(true);

        List<?> out = (List<?>) method.invoke(new AutoFlipPlugin(), Arrays.asList(rubyNecklace, natureRune, ruby));

        assertEquals(Arrays.asList(rubyNecklace, natureRune, ruby), out);
    }

    @Test
    public void budgetAllocationScalesQuantitiesProportionallyAndUsesTheBudget() throws Exception
    {
        Class<?> rowClass = Class.forName("gg.autoflip.AutoFlipPlugin$AutoFlipPayloadRow");
        Class<?> contextClass = Class.forName("gg.autoflip.AutoFlipPlugin$AutoFlipRecommendationContext");

        java.lang.reflect.Constructor<?> ctor = rowClass.getDeclaredConstructor(
            int.class,
            String.class,
            long.class,
            long.class,
            long.class,
            int.class,
            int.class,
            double.class,
            String.class,
            String.class,
            String.class,
            String.class,
            contextClass
        );
        ctor.setAccessible(true);

        Object highConfidence = ctor.newInstance(1001, "High confidence", 10L, 14L, 4L, 100, 10_000, 0.95D, "high", "test", "", "", null);
        Object mediumConfidence = ctor.newInstance(1002, "Medium confidence", 10L, 13L, 3L, 50, 10_000, 0.50D, "medium", "test", "", "", null);
        Object lowConfidence = ctor.newInstance(1003, "Low confidence", 10L, 12L, 2L, 25, 10_000, 0.25D, "low", "test", "", "", null);

        java.lang.reflect.Method method = AutoFlipPlugin.class.getDeclaredMethod(
            "localPayloadScaleRowsToBudget",
            java.util.List.class,
            long.class
        );
        method.setAccessible(true);

        List<?> out = (List<?>) method.invoke(new AutoFlipPlugin(), Arrays.asList(highConfidence, mediumConfidence, lowConfidence), 5_000L);

        assertEquals(3, out.size());

        int highQty = payloadRowQuantity(out.get(0));
        int mediumQty = payloadRowQuantity(out.get(1));
        int lowQty = payloadRowQuantity(out.get(2));
        long spent = payloadRowCapital(out.get(0)) + payloadRowCapital(out.get(1)) + payloadRowCapital(out.get(2));

        assertTrue(spent <= 5_000L);
        assertTrue(spent >= 4_900L);
        assertTrue(highQty >= mediumQty);
        assertTrue(mediumQty >= lowQty);
    }

    @Test
    public void budgetAllocationSeedsWarmBufferForImmediateRefreshReuse() throws Exception
    {
        Class<?> rowClass = Class.forName("gg.autoflip.AutoFlipPlugin$AutoFlipPayloadRow");
        Class<?> contextClass = Class.forName("gg.autoflip.AutoFlipPlugin$AutoFlipRecommendationContext");
        Class<?> pluginClass = AutoFlipPlugin.class;

        java.lang.reflect.Constructor<?> ctor = rowClass.getDeclaredConstructor(
            int.class,
            String.class,
            long.class,
            long.class,
            long.class,
            int.class,
            int.class,
            double.class,
            String.class,
            String.class,
            String.class,
            String.class,
            contextClass
        );
        ctor.setAccessible(true);

        Object first = ctor.newInstance(2001, "Warm first", 10L, 14L, 4L, 80, 10_000, 0.90D, "high", "test", "", "", null);
        Object second = ctor.newInstance(2002, "Warm second", 10L, 12L, 2L, 40, 10_000, 0.40D, "medium", "test", "", "", null);

        java.lang.reflect.Method method = pluginClass.getDeclaredMethod(
            "localPayloadScaleRowsToBudget",
            java.util.List.class,
            long.class
        );
        method.setAccessible(true);

        AutoFlipPlugin plugin = new AutoFlipPlugin();
        List<?> out = (List<?>) method.invoke(plugin, Arrays.asList(first, second), 5_000L);

        java.lang.reflect.Field warmBufferField = pluginClass.getDeclaredField("autoFlipBudgetWarmBuffer");
        warmBufferField.setAccessible(true);
        Object warmBuffer = warmBufferField.get(plugin);

        assertEquals(2, out.size());
        assertNotNull(warmBuffer);
    }

    @Test
    public void refreshReplacementDoesNotClearSkippedItemsWhenNoCandidateExists() throws Exception
    {
        Class<?> rowClass = Class.forName("gg.autoflip.AutoFlipPlugin$AutoFlipPayloadRow");
        Class<?> contextClass = Class.forName("gg.autoflip.AutoFlipPlugin$AutoFlipRecommendationContext");
        Class<?> boardCardClass = Class.forName("gg.autoflip.AutoFlipPlugin$AutoFlipBoardCard");

        java.lang.reflect.Constructor<?> rowCtor = rowClass.getDeclaredConstructor(
            int.class,
            String.class,
            long.class,
            long.class,
            long.class,
            int.class,
            int.class,
            double.class,
            String.class,
            String.class,
            String.class,
            String.class,
            contextClass
        );
        rowCtor.setAccessible(true);

        java.lang.reflect.Constructor<?> boardCtor = boardCardClass.getDeclaredConstructor(
            int.class,
            int.class,
            String.class,
            int.class,
            int.class,
            long.class,
            long.class,
            long.class,
            long.class,
            String.class,
            String.class,
            String.class,
            String.class,
            double.class,
            contextClass
        );
        boardCtor.setAccessible(true);

        Object skippedCandidate = rowCtor.newInstance(2002, "Skipped candidate", 10L, 14L, 4L, 1, 1, 0.10D, "low", "test", "", "", null);
        Object oldCard = boardCtor.newInstance(0, 2001, "Current slot", 1, 1, 10L, 14L, 4L, 4L, "12 hours", "low", "test", "", 0.50D, null);

        java.lang.reflect.Method rememberSkipped = AutoFlipPlugin.class.getDeclaredMethod("rememberAutoFlipSkippedItem", int.class);
        rememberSkipped.setAccessible(true);

        java.lang.reflect.Method getSkippedSnapshot = AutoFlipPlugin.class.getDeclaredMethod("getAutoFlipSkippedItemIdsSnapshot");
        getSkippedSnapshot.setAccessible(true);

        java.lang.reflect.Method method = AutoFlipPlugin.class.getDeclaredMethod(
            "pickAutoFlipRefreshReplacementRow",
            java.util.List.class,
            boardCardClass,
            int.class,
            int.class,
            java.util.Set.class,
            java.util.Set.class,
            boolean.class
        );
        method.setAccessible(true);

        AutoFlipPlugin plugin = new AutoFlipPlugin();
        rememberSkipped.invoke(plugin, 2002);

        Object result = method.invoke(
            plugin,
            Arrays.asList(skippedCandidate),
            oldCard,
            0,
            0,
            java.util.Collections.emptySet(),
            getSkippedSnapshot.invoke(plugin),
            true
        );

        assertNull(result);
        assertTrue(((java.util.Set<?>) getSkippedSnapshot.invoke(plugin)).contains(2002));
    }

    @Test
    public void boardRescalePreservesSlotsWhileRebalancingQuantities() throws Exception
    {
        Class<?> contextClass = Class.forName("gg.autoflip.AutoFlipPlugin$AutoFlipRecommendationContext");
        Class<?> boardCardClass = Class.forName("gg.autoflip.AutoFlipPlugin$AutoFlipBoardCard");

        java.lang.reflect.Constructor<?> boardCtor = boardCardClass.getDeclaredConstructor(
            int.class,
            int.class,
            String.class,
            int.class,
            int.class,
            long.class,
            long.class,
            long.class,
            long.class,
            String.class,
            String.class,
            String.class,
            String.class,
            double.class,
            contextClass
        );
        boardCtor.setAccessible(true);

        Object first = boardCtor.newInstance(0, 5001, "First", 5, 5, 10L, 14L, 4L, 20L, "12 hours", "medium", "test", "", 0.50D, null);
        Object second = boardCtor.newInstance(1, 5002, "Second", 3, 3, 20L, 28L, 8L, 24L, "12 hours", "medium", "test", "", 0.60D, null);
        Object third = boardCtor.newInstance(2, 5003, "Third", 1, 1, 50L, 70L, 20L, 20L, "12 hours", "medium", "test", "", 0.70D, null);

        java.lang.reflect.Method method = AutoFlipPlugin.class.getDeclaredMethod(
            "localPayloadRescaleBoardCards",
            java.util.List.class,
            long.class
        );
        method.setAccessible(true);

        List<?> out = (List<?>) method.invoke(new AutoFlipPlugin(), Arrays.asList(first, second, third), 10_000L);

        assertEquals(8, out.size());
        assertEquals(5001, boardCardItemId(out.get(0)));
        assertEquals(5002, boardCardItemId(out.get(1)));
        assertEquals(5003, boardCardItemId(out.get(2)));
        assertTrue(boardCardQuantity(out.get(0)) >= 5);
        assertTrue(boardCardQuantity(out.get(1)) >= 3);
        assertTrue(boardCardQuantity(out.get(2)) >= 1);
    }

    @Test
    public void boardRescaleCanGrowBackTowardBudgetWhenMaxQuantityIsPreserved() throws Exception
    {
        Class<?> boardCardClass = Class.forName("gg.autoflip.AutoFlipPlugin$AutoFlipBoardCard");

        java.lang.reflect.Constructor<?> boardCtor = boardCardClass.getDeclaredConstructor(
            int.class,
            int.class,
            String.class,
            int.class,
            int.class,
            long.class,
            long.class,
            long.class,
            long.class,
            String.class,
            String.class,
            String.class,
            String.class,
            double.class,
            Class.forName("gg.autoflip.AutoFlipPlugin$AutoFlipRecommendationContext")
        );
        boardCtor.setAccessible(true);

        Object first = boardCtor.newInstance(0, 6001, "Grow first", 1, 100, 10L, 14L, 4L, 4L, "12 hours", "medium", "test", "", 0.50D, null);

        java.lang.reflect.Method method = AutoFlipPlugin.class.getDeclaredMethod(
            "localPayloadRescaleBoardCards",
            java.util.List.class,
            long.class
        );
        method.setAccessible(true);

        List<?> out = (List<?>) method.invoke(new AutoFlipPlugin(), Arrays.asList(first), 1_000L);

        assertEquals(8, out.size());
        assertEquals(6001, boardCardItemId(out.get(0)));
        assertTrue(boardCardQuantity(out.get(0)) > 1);
        assertTrue(boardCardCapital(out.get(0)) >= 900L);
    }

    @Test
    public void localPayloadRowCapsQuantityToBuyLimitWindowForTwelveHourBoards() throws Exception
    {
        Class<?> contextClass = Class.forName("gg.autoflip.AutoFlipPlugin$AutoFlipRecommendationContext");
        java.lang.reflect.Method method = AutoFlipPlugin.class.getDeclaredMethod(
            "localPayloadRowFromJson",
            String.class,
            int.class,
            int.class,
            int.class,
            contextClass
        );
        method.setAccessible(true);

        String obj = "{"
            + "\"item_id\":2353,"
            + "\"item_name\":\"Steel bar\","
            + "\"buy_price_gp\":10,"
            + "\"sell_price_gp\":14,"
            + "\"suggested_quantity\":3205,"
            + "\"buy_limit_4h\":1000,"
            + "\"time_adjusted_buy_limit_quantity\":3205,"
            + "\"realistic_quantity_cap\":0,"
            + "\"fill_probability\":0.95,"
            + "\"confidence_band\":\"high\""
            + "}";

        Object row = method.invoke(new AutoFlipPlugin(), obj, 0, 3205, 12, null);

        assertEquals(3000, payloadRowQuantity(row));
        assertEquals(3000, payloadRowMaxQuantity(row));
    }

    @Test
    public void localPayloadRowExpandsMaxQuantityFromExecutionAdjustedDeployableGp() throws Exception
    {
        Class<?> contextClass = Class.forName("gg.autoflip.AutoFlipPlugin$AutoFlipRecommendationContext");
        java.lang.reflect.Method method = AutoFlipPlugin.class.getDeclaredMethod(
            "localPayloadRowFromJson",
            String.class,
            int.class,
            int.class,
            int.class,
            contextClass
        );
        method.setAccessible(true);

        String obj = "{"
            + "\"item_id\":20146,"
            + "\"item_name\":\"Gilded dragonhide set\","
            + "\"buy_price_gp\":5000,"
            + "\"sell_price_gp\":5500,"
            + "\"suggested_quantity\":5,"
            + "\"buy_limit_4h\":10,"
            + "\"time_adjusted_buy_limit_quantity\":30,"
            + "\"realistic_quantity_cap\":6,"
            + "\"scoring_metadata\":{"
            + "\"raw_max_deployable_gp\":30000,"
            + "\"execution_adjusted_max_deployable_gp\":150000"
            + "},"
            + "\"fill_probability\":0.75,"
            + "\"confidence_band\":\"medium\""
            + "}";

        Object row = method.invoke(new AutoFlipPlugin(), obj, 0, 5, 12, null);

        assertEquals(30, payloadRowMaxQuantity(row));
        assertEquals(5, payloadRowQuantity(row));
    }

    private static int payloadRowQuantity(Object row) throws Exception
    {
        java.lang.reflect.Field field = row.getClass().getDeclaredField("quantity");
        field.setAccessible(true);
        return field.getInt(row);
    }

    private static long payloadRowCapital(Object row) throws Exception
    {
        java.lang.reflect.Field buyPriceField = row.getClass().getDeclaredField("buyPriceGp");
        buyPriceField.setAccessible(true);
        long buyPriceGp = buyPriceField.getLong(row);
        return buyPriceGp * (long) payloadRowQuantity(row);
    }

    private static int payloadRowMaxQuantity(Object row) throws Exception
    {
        java.lang.reflect.Field field = row.getClass().getDeclaredField("maxQuantity");
        field.setAccessible(true);
        return field.getInt(row);
    }

    private static int boardCardItemId(Object card) throws Exception
    {
        java.lang.reflect.Field field = card.getClass().getDeclaredField("itemId");
        field.setAccessible(true);
        return field.getInt(card);
    }

    private static int boardCardQuantity(Object card) throws Exception
    {
        java.lang.reflect.Field field = card.getClass().getDeclaredField("quantity");
        field.setAccessible(true);
        return field.getInt(card);
    }

    private static long boardCardCapital(Object card) throws Exception
    {
        java.lang.reflect.Field qtyField = card.getClass().getDeclaredField("quantity");
        qtyField.setAccessible(true);
        int quantity = qtyField.getInt(card);

        java.lang.reflect.Field buyPriceField = card.getClass().getDeclaredField("buyPriceGp");
        buyPriceField.setAccessible(true);
        long buyPriceGp = buyPriceField.getLong(card);
        return buyPriceGp * (long) quantity;
    }

}
