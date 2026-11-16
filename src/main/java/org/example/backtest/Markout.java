package org.example.backtest;

import org.example.execution.Liquidity;

/**
 * How the price moved after our fills: the markout. It shows whether our fills came at good or
 * bad moments.
 *
 * <p>For every fill the mid price some time later is compared with the fill price, from our side:
 * after a buy a higher mid is good, after a sell a lower mid is good. A maker buy at the bid
 * starts with half the spread in its favour; if its markout is still negative after a second,
 * the price tends to fall right after our bids are filled (adverse selection).
 *
 * @param horizonMillis how long after the fill the mid is taken, in milliseconds
 * @param liquidity which fills are counted: the ones that waited in the book or the ones that
 *         took liquidity
 * @param fills how many fills were counted; fills whose horizon ends after the period, and the
 *         closing order at the end, are not counted
 * @param shares how many shares these fills had
 * @param averageCents the average markout per share in cents, weighted by shares; positive is
 *         good for us; 0 if no fill was counted
 */
public record Markout(
        long horizonMillis,
        Liquidity liquidity,
        int fills,
        long shares,
        double averageCents) {

}
