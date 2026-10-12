package org.example.signal;

import java.time.Duration;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.flow.OrderFlowImbalance;
import org.example.marketdata.state.SymbolState;

/**
 * Buys when buyers got stronger at the best prices of the book, sells when sellers did.
 *
 * <p>It watches the {@link OrderFlowImbalance} of a time window. Above {@code +threshold} the
 * opinion is +1, below {@code -threshold} it is -1, in between it is 0, so small noise is ignored.
 */
public class OfiSignal implements Signal {

    private final OrderFlowImbalance ofi;
    private final long threshold;

    /**
     * Creates the signal with no history yet.
     *
     * @param symbol the symbol to watch, for example {@code AAPL}
     * @param window how far back the order flow imbalance counts changes, for example 10 seconds
     * @param threshold how large the imbalance must be to act, in shares; at least 0
     * @throws IllegalArgumentException if the window is not positive or the threshold is negative
     */
    public OfiSignal(String symbol, Duration window, long threshold) {
        if (threshold < 0) {
            throw new IllegalArgumentException("threshold must not be negative, was " + threshold);
        }
        this.ofi = new OrderFlowImbalance(symbol, window);
        this.threshold = threshold;
    }

    @Override
    public void update(MarketEvent event, SymbolState state) {
        ofi.apply(event, state.book());
    }

    @Override
    public double conviction() {
        long value = ofi.value();
        if (value > threshold) {
            return 1;
        } else if (value < -threshold) {
            return -1;
        }
        return 0;
    }
}
