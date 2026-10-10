package org.example.fairprice;

import java.util.List;
import java.util.OptionalDouble;
import java.util.function.IntFunction;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.book.PriceLevel;

/**
 * Fair price as the middle of the average buying and the average selling price of a given
 * quantity.
 *
 * <p>Buying {@code quantity} shares at once takes the asks level by level, so the average price
 * gets worse when the best levels are small. The same is done for selling on the bid side. A thin
 * side of the book moves its average price further away, so the fair price moves towards the
 * stronger side. For a quantity that fits in the best levels this is the same as the mid price.
 */
public class ImpactMidPrice implements FairPriceModel {

    /** Number of levels read from the book first; doubled while it is not enough. */
    private static final int FIRST_DEPTH = 8;

    private final long quantity;

    /**
     * Creates the model.
     *
     * @param quantity the number of shares to buy and to sell, at least 1
     * @throws IllegalArgumentException if {@code quantity} is less than 1
     */
    public ImpactMidPrice(long quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1, was " + quantity);
        }
        this.quantity = quantity;
    }

    /**
     * Calculates the fair price.
     *
     * @param book the current order book
     * @return the fair price in dollars, or empty if one side of the book has fewer shares than
     *         {@code quantity}
     */
    @Override
    public OptionalDouble fairPrice(OrderBook book) {
        OptionalDouble buy = averagePrice(book::asks);
        OptionalDouble sell = averagePrice(book::bids);
        if (buy.isEmpty() || sell.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of((buy.getAsDouble() + sell.getAsDouble()) / 2);
    }

    /**
     * Returns the average price of taking {@code quantity} shares from one side of the book.
     *
     * @param side returns the best levels of the side for a given depth
     */
    private OptionalDouble averagePrice(IntFunction<List<PriceLevel>> side) {
        int depth = FIRST_DEPTH;
        while (true) {
            List<PriceLevel> levels = side.apply(depth);
            long remaining = quantity;
            long cost = 0;
            for (PriceLevel level : levels) {
                long taken = Math.min(remaining, level.quantity());
                cost += taken * level.price();
                remaining -= taken;
                if (remaining == 0) {
                    return OptionalDouble.of(cost / PriceLevel.UNITS_PER_DOLLAR / quantity);
                }
            }
            if (levels.size() < depth) {
                return OptionalDouble.empty();
            }
            depth *= 2;
        }
    }
}
