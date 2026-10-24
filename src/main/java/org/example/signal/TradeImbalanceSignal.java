package org.example.signal;

import java.time.Duration;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.flow.TradeFlow;
import org.example.marketdata.state.SymbolState;

/**
 * Follows the pressure of the recent trades: buys when hurried buyers traded more shares than
 * hurried sellers, sells in the opposite case.
 *
 * <p>The opinion is {@code (buy - sell) / (buy + sell)} of the aggressive volume in the window,
 * so it is already from -1 to +1. Hidden trades do not count, because their side is unknown.
 * With fewer than {@code minShares} known-side shares in the window there is no opinion, so a
 * single small trade on a quiet market cannot give a full opinion. The signal has its own
 * {@link TradeFlow}, so its window does not depend on the rest of the system.
 */
public class TradeImbalanceSignal implements Signal {

    private final TradeFlow trades;
    private final long minShares;
    private double conviction;

    /**
     * Creates the signal with no history yet.
     *
     * @param symbol the symbol to watch, for example {@code AAPL}
     * @param window how far back trades count, for example 10 seconds
     * @param minShares the fewest known-side shares in the window that give an opinion; 0 or
     *         more
     * @throws IllegalArgumentException if the window is not positive or minShares is negative
     */
    public TradeImbalanceSignal(String symbol, Duration window, long minShares) {
        if (minShares < 0) {
            throw new IllegalArgumentException("minShares must not be negative, was " + minShares);
        }
        this.trades = new TradeFlow(symbol, window);
        this.minShares = minShares;
    }

    @Override
    public void update(MarketEvent event, SymbolState state) {
        trades.apply(event);
        long knownSide = trades.buyVolume() + trades.sellVolume();
        conviction = knownSide < minShares ? 0 : trades.imbalance();
    }

    @Override
    public double conviction() {
        return conviction;
    }
}
