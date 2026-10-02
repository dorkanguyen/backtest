package org.example.order;

/**
 * A request to buy or sell, created from the strategy's target position.
 *
 * @param symbol instrument to trade
 * @param side buy or sell
 * @param quantity number of shares, always positive
 */
public record Order(String symbol, Side side, int quantity) {
}
