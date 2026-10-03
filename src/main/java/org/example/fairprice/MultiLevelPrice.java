package org.example.fairprice;

import java.util.List;
import java.util.OptionalDouble;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.book.PriceLevel;

/**
 * Fair price as the best bid and ask weighted by the quantity waiting on the opposite side,
 * summed over the best few levels of the book.
 *
 * <p>{@code fair = (bid * askQuantity + ask * bidQuantity) / (bidQuantity + askQuantity)}, where
 * the quantities are summed over {@code levels} price levels on each side. More levels use more
 * information, but orders far from the best price are often canceled before they trade.
 */
public class MultiLevelPrice implements FairPriceModel {

    private final int levels;

    /**
     * Creates the model.
     *
     * @param levels number of price levels to use on each side, at least 1
     * @throws IllegalArgumentException if {@code levels} is less than 1
     */
    public MultiLevelPrice(int levels) {
        if (levels < 1) {
            throw new IllegalArgumentException("levels must be at least 1, was " + levels);
        }
        this.levels = levels;
    }

    @Override
    public OptionalDouble fairPrice(OrderBook book) {
        List<PriceLevel> bids = book.bids(levels);
        List<PriceLevel> asks = book.asks(levels);
        if (bids.isEmpty() || asks.isEmpty()) {
            return OptionalDouble.empty();
        }
        double bidQuantity = totalQuantity(bids);
        double askQuantity = totalQuantity(asks);
        double bid = bids.get(0).priceInDollars();
        double ask = asks.get(0).priceInDollars();
        return OptionalDouble.of(
                (bid * askQuantity + ask * bidQuantity) / (bidQuantity + askQuantity));
    }

    private static long totalQuantity(List<PriceLevel> levels) {
        long total = 0;
        for (PriceLevel level : levels) {
            total += level.quantity();
        }
        return total;
    }
}
