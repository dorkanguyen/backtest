package org.example.marketdata.book;

/**
 * One price level of the order book: all orders waiting at the same price on one side.
 *
 * @param price price in 1/10000 dollars, for example 170.0100 is {@code 1700100}
 * @param quantity total number of shares waiting at this price
 */
public record PriceLevel(long price, long quantity) {

    /** Number of price units in one dollar. */
    public static final double UNITS_PER_DOLLAR = 10_000.0;

    /**
     * Returns the price in dollars, for example {@code 170.01}.
     *
     * @return the price in dollars
     */
    public double priceInDollars() {
        return price / UNITS_PER_DOLLAR;
    }
}
