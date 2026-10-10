package org.example.fairprice;

import java.util.List;
import java.util.OptionalDouble;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.book.PriceLevel;

/**
 * Like {@link MultiLevelPrice}, but levels further from the best price count less.
 *
 * <p>The quantity of the first level counts fully, the second level is multiplied by
 * {@code decay}, the third by {@code decay * decay}, and so on. Orders far from the best price
 * are often canceled before they trade, so they should not count as much as the best level. With
 * {@code decay = 1} this is the same as {@link MultiLevelPrice}.
 */
public class DecayingLevelPrice implements FairPriceModel {

    private final int levels;
    private final double decay;

    /**
     * Creates the model.
     *
     * @param levels number of price levels to use on each side, at least 1
     * @param decay how much each deeper level counts compared to the previous one, for example
     *         {@code 0.5}; more than 0 and at most 1
     * @throws IllegalArgumentException if a parameter is out of range
     */
    public DecayingLevelPrice(int levels, double decay) {
        if (levels < 1) {
            throw new IllegalArgumentException("levels must be at least 1, was " + levels);
        }
        if (!(decay > 0 && decay <= 1)) {
            throw new IllegalArgumentException("decay must be in (0, 1], was " + decay);
        }
        this.levels = levels;
        this.decay = decay;
    }

    @Override
    public OptionalDouble fairPrice(OrderBook book) {
        List<PriceLevel> bids = book.bids(levels);
        List<PriceLevel> asks = book.asks(levels);
        if (bids.isEmpty() || asks.isEmpty()) {
            return OptionalDouble.empty();
        }
        double bidWeight = weightedQuantity(bids);
        double askWeight = weightedQuantity(asks);
        double bid = bids.get(0).priceInDollars();
        double ask = asks.get(0).priceInDollars();
        return OptionalDouble.of((bid * askWeight + ask * bidWeight) / (bidWeight + askWeight));
    }

    private double weightedQuantity(List<PriceLevel> side) {
        double total = 0;
        double weight = 1;
        for (PriceLevel level : side) {
            total += weight * level.quantity();
            weight *= decay;
        }
        return total;
    }
}
