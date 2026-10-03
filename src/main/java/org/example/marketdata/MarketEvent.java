package org.example.marketdata;

/**
 * One change in the order book of a symbol (market-by-order event).
 *
 * <p>All values are whole numbers so that no precision is lost and the data can be stored in any
 * format later.
 *
 * @param timestamp event time in nanoseconds since 1970-01-01 UTC
 * @param symbol instrument, for example {@code AAPL}
 * @param type what happened
 * @param orderId id of the order the event belongs to
 * @param side bid or ask side of the book
 * @param price price in 1/10000 dollars, for example 170.0100 is {@code 1700100}
 * @param quantity number of shares
 */
public record MarketEvent(
        long timestamp,
        String symbol,
        EventType type,
        long orderId,
        BookSide side,
        long price,
        long quantity
) {

}
