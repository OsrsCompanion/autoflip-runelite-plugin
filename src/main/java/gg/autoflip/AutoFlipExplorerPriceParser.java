package gg.autoflip;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AutoFlipExplorerPriceParser
{
    static final class PricePoint
    {
        final long suggestedBuyPrice;
        final long suggestedSellPrice;
        final long latestBuyPrice;
        final long latestSellPrice;
        final long buyTimeSeconds;
        final long sellTimeSeconds;
        final String itemName;

        PricePoint(long suggestedBuyPrice, long suggestedSellPrice, long latestBuyPrice,
            long latestSellPrice, long buyTimeSeconds, long sellTimeSeconds)
        {
            this(suggestedBuyPrice, suggestedSellPrice, latestBuyPrice, latestSellPrice,
                buyTimeSeconds, sellTimeSeconds, "");
        }

        PricePoint(long suggestedBuyPrice, long suggestedSellPrice, long latestBuyPrice,
            long latestSellPrice, long buyTimeSeconds, long sellTimeSeconds, String itemName)
        {
            this.suggestedBuyPrice = suggestedBuyPrice;
            this.suggestedSellPrice = suggestedSellPrice;
            this.latestBuyPrice = latestBuyPrice;
            this.latestSellPrice = latestSellPrice;
            this.buyTimeSeconds = buyTimeSeconds;
            this.sellTimeSeconds = sellTimeSeconds;
            this.itemName = itemName == null ? "" : itemName.trim();
        }
    }
    private AutoFlipExplorerPriceParser()
    {
    }

    static long parseSuggestedSellPrice(String json)
    {
        long executionSellPrice = readPositiveLong(json, "execution_sell_price");
        if (executionSellPrice > 0L)
        {
            return executionSellPrice;
        }

        return readPositiveLong(json, "sell_price");
    }

    static Map<Integer, Long> parseSuggestedSellPrices(String json)
    {
        Map<Integer, Long> prices = new LinkedHashMap<>();
        if (json == null || json.isEmpty())
        {
            return prices;
        }

        Matcher objects = Pattern.compile("\\{[^{}]*\"item_id\"\\s*:\\s*\\d+[^{}]*}").matcher(json);
        while (objects.find())
        {
            String object = objects.group();
            long itemIdValue = readPositiveLong(object, "item_id");
            long sellPrice = parseSuggestedSellPrice(object);
            if (itemIdValue > 0L && itemIdValue <= Integer.MAX_VALUE && sellPrice > 0L)
            {
                prices.put((int) itemIdValue, sellPrice);
            }
        }
        return prices;
    }

    static long parseSuggestedBuyPrice(String json)
    {
        long executionBuyPrice = readPositiveLong(json, "execution_buy_price");
        return executionBuyPrice > 0L ? executionBuyPrice : readPositiveLong(json, "buy_price");
    }

    static Map<Integer, PricePoint> parsePricePoints(String json)
    {
        Map<Integer, PricePoint> points = new LinkedHashMap<>();
        if (json == null || json.isEmpty())
        {
            return points;
        }

        Matcher objects = Pattern.compile("\\{[^{}]*\"item_id\"\\s*:\\s*\\d+[^{}]*}").matcher(json);
        while (objects.find())
        {
            String object = objects.group();
            long itemIdValue = readPositiveLong(object, "item_id");
            if (itemIdValue <= 0L || itemIdValue > Integer.MAX_VALUE)
            {
                continue;
            }
            long rawBuy = readPositiveLong(object, "raw_buy_price");
            long rawSell = readPositiveLong(object, "raw_sell_price");
            if (rawBuy <= 0L) rawBuy = readPositiveLong(object, "buy_price");
            if (rawSell <= 0L) rawSell = readPositiveLong(object, "sell_price");
            points.put((int) itemIdValue, new PricePoint(
                parseSuggestedBuyPrice(object), parseSuggestedSellPrice(object), rawBuy, rawSell,
                readPositiveLong(object, "buy_time"), readPositiveLong(object, "sell_time"),
                firstNonEmpty(readString(object, "item_name"), readString(object, "name"))
            ));
        }
        return points;
    }

    private static String readString(String json, String key)
    {
        if (json == null || json.isEmpty()) return "";
        Matcher matcher = Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        ).matcher(json);
        if (!matcher.find()) return "";
        return matcher.group(1)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .trim();
    }

    private static String firstNonEmpty(String first, String second)
    {
        return first != null && !first.trim().isEmpty() ? first.trim() : (second == null ? "" : second.trim());
    }

    private static long readPositiveLong(String json, String key)
    {
        if (json == null || json.isEmpty())
        {
            return 0L;
        }

        Matcher matcher = Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)"
        ).matcher(json);

        if (!matcher.find())
        {
            return 0L;
        }

        try
        {
            return Math.max(0L, Long.parseLong(matcher.group(1)));
        }
        catch (NumberFormatException ignored)
        {
            return 0L;
        }
    }
}
