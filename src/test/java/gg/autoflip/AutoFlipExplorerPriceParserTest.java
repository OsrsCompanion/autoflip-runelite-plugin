package gg.autoflip;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AutoFlipExplorerPriceParserTest
{
    @Test
    public void prefersExecutionSellPrice()
    {
        assertEquals(
            107L,
            AutoFlipExplorerPriceParser.parseSuggestedSellPrice(
                "{\"item\":{\"execution_sell_price\":107,\"sell_price\":111}}"
            )
        );
    }

    @Test
    public void fallsBackToSellPrice()
    {
        assertEquals(
            111L,
            AutoFlipExplorerPriceParser.parseSuggestedSellPrice(
                "{\"item\":{\"execution_sell_price\":0,\"sell_price\":111}}"
            )
        );
    }

    @Test
    public void rejectsMissingOrInvalidPrices()
    {
        assertEquals(0L, AutoFlipExplorerPriceParser.parseSuggestedSellPrice("{}"));
        assertEquals(
            0L,
            AutoFlipExplorerPriceParser.parseSuggestedSellPrice(
                "{\"item\":{\"execution_sell_price\":-1,\"sell_price\":0}}"
            )
        );
    }

    @Test
    public void parsesBulkItemsByItemId()
    {
        Map<Integer, Long> prices = AutoFlipExplorerPriceParser.parseSuggestedSellPrices(
            "{\"items\":["
                + "{\"item_id\":564,\"execution_sell_price\":124,\"sell_price\":123},"
                + "{\"item_id\":890,\"execution_sell_price\":0,\"sell_price\":33}"
                + "]}"
        );

        assertEquals(Long.valueOf(124L), prices.get(564));
        assertEquals(Long.valueOf(33L), prices.get(890));
        assertEquals(2, prices.size());
    }

    @Test
    public void parsesBuyRecommendationAndLatestTradeMetadata()
    {
        Map<Integer, AutoFlipExplorerPriceParser.PricePoint> points = AutoFlipExplorerPriceParser.parsePricePoints(
            "{\"items\":[{\"item_id\":562,\"execution_buy_price\":104,\"buy_price\":106,"
                + "\"raw_buy_price\":105,\"buy_time\":1786400000,\"execution_sell_price\":108,"
                + "\"raw_sell_price\":109,\"sell_time\":1786400010}]}"
        );
        AutoFlipExplorerPriceParser.PricePoint point = points.get(562);
        assertEquals(104L, point.suggestedBuyPrice);
        assertEquals(105L, point.latestBuyPrice);
        assertEquals(1786400000L, point.buyTimeSeconds);
        assertEquals(108L, point.suggestedSellPrice);
    }

    @Test
    public void buyRecommendationFallsBackToBuyPrice()
    {
        assertEquals(106L, AutoFlipExplorerPriceParser.parseSuggestedBuyPrice(
            "{\"execution_buy_price\":0,\"buy_price\":106}"
        ));
    }

    @Test
    public void retainsApiItemNameForCacheDiagnostics()
    {
        Map<Integer, AutoFlipExplorerPriceParser.PricePoint> points = AutoFlipExplorerPriceParser.parsePricePoints(
            "{\"items\":[{\"item_id\":25775,\"item_name\":\"Emerald bracelet\",\"buy_price\":1597,\"sell_price\":1703}]}"
        );

        assertEquals("Emerald bracelet", points.get(25775).itemName);
    }
}
