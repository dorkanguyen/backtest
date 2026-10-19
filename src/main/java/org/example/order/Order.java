package org.example.order;

/**
 * A request to buy or sell, created from the strategy's target position.
 *
 * <p>A market order trades at once at the best prices of the book. A limit order waits in the
 * book at its limit price, so it needs an id: later it can be cancelled by that id. Create orders
 * with {@link #market} or {@link #limit}.
 *
 * @param id identifies the order; chosen by whoever creates it and unique among the orders of
 *         one backtest run
 * @param symbol instrument to trade
 * @param side buy or sell
 * @param type market or limit
 * @param quantity number of shares, always positive
 * @param limitPrice for a limit order the worst accepted price in 1/10000 dollars, like the
 *         prices of the book (278.59 dollars = 2785900); 0 for a market order
 */
public record Order(long id, String symbol, Side side, OrderType type, int quantity,
        long limitPrice) {

    /**
     * Checks the order.
     *
     * @throws IllegalArgumentException if the quantity is not positive, a limit order has no
     *         positive limit price, or a market order has one
     */
    public Order {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive, was " + quantity);
        }
        if (type == OrderType.LIMIT && limitPrice <= 0) {
            throw new IllegalArgumentException(
                    "A limit order needs a positive limit price, was " + limitPrice);
        }
        if (type == OrderType.MARKET && limitPrice != 0) {
            throw new IllegalArgumentException(
                    "A market order has no limit price, was " + limitPrice);
        }
    }

    /**
     * Creates a market order.
     *
     * @param id identifies the order
     * @param symbol instrument to trade
     * @param side buy or sell
     * @param quantity number of shares, always positive
     * @return the order
     */
    public static Order market(long id, String symbol, Side side, int quantity) {
        return new Order(id, symbol, side, OrderType.MARKET, quantity, 0);
    }

    /**
     * Creates a limit order.
     *
     * @param id identifies the order
     * @param symbol instrument to trade
     * @param side buy or sell
     * @param quantity number of shares, always positive
     * @param limitPrice the worst accepted price in 1/10000 dollars
     * @return the order
     */
    public static Order limit(long id, String symbol, Side side, int quantity, long limitPrice) {
        return new Order(id, symbol, side, OrderType.LIMIT, quantity, limitPrice);
    }
}
