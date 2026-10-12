package org.example.signal;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.book.PriceLevel;
import org.example.marketdata.flow.TradeFlow;
import org.example.marketdata.state.SymbolState;

/**
 * Expects the price to come back to the VWAP of the recent trades: sells when the mid price is
 * above it, buys when it is below.
 *
 * <p>The opinion grows with the distance and reaches -1 or +1 at {@code fullDistance} dollars.
 * Without a trade in the window or without a bid and an ask, there is no opinion. The signal has
 * its own {@link TradeFlow}, so its window does not depend on the rest of the system.
 */
public class VwapSignal implements Signal {

    private final TradeFlow trades;
    private final double fullDistance;
    private double conviction;

    /**
     * Creates the signal with no history yet.
     *
     * @param symbol the symbol to watch, for example {@code AAPL}
     * @param window how far back trades count in the VWAP, for example 60 seconds
     * @param fullDistance the distance from the VWAP in dollars where the opinion is strongest,
     *         for example {@code 0.05}; more than 0
     * @throws IllegalArgumentException if the window or the distance is not positive
     */
    public VwapSignal(String symbol, Duration window, double fullDistance) {
        if (!(fullDistance > 0)) {
            throw new IllegalArgumentException(
                    "fullDistance must be positive, was " + fullDistance);
        }
        this.trades = new TradeFlow(symbol, window);
        this.fullDistance = fullDistance;
    }

    @Override
    public void update(MarketEvent event, SymbolState state) {
        trades.apply(event);
        conviction = calculate(state.book());
    }

    @Override
    public double conviction() {
        return conviction;
    }

    private double calculate(OrderBook book) {
        Optional<PriceLevel> bid = book.bestBid();
        Optional<PriceLevel> ask = book.bestAsk();
        OptionalDouble vwap = trades.vwap();
        if (bid.isEmpty() || ask.isEmpty() || vwap.isEmpty()) {
            return 0;
        }
        double mid = (bid.get().priceInDollars() + ask.get().priceInDollars()) / 2;
        double opinion = (vwap.getAsDouble() - mid) / fullDistance;
        return Math.max(-1, Math.min(1, opinion));
    }
}
