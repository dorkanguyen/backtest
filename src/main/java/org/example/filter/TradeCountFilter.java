package org.example.filter;

import java.time.Duration;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.flow.TradeFlow;
import org.example.marketdata.state.SymbolState;

/**
 * Allows trading only when the market is busy enough: at least {@code minTrades} trades in the
 * window. In a quiet market the signals have little information to work with.
 */
public class TradeCountFilter implements TradingFilter {

    private final TradeFlow trades;
    private final int minTrades;

    /**
     * Creates the filter with no history yet.
     *
     * @param symbol the symbol to watch, for example {@code AAPL}
     * @param window how far back trades are counted, for example 10 seconds
     * @param minTrades the number of trades needed to allow trading; at least 0
     * @throws IllegalArgumentException if the window is not positive or minTrades is negative
     */
    public TradeCountFilter(String symbol, Duration window, int minTrades) {
        if (minTrades < 0) {
            throw new IllegalArgumentException("minTrades must not be negative, was " + minTrades);
        }
        this.trades = new TradeFlow(symbol, window);
        this.minTrades = minTrades;
    }

    @Override
    public void update(MarketEvent event, SymbolState state) {
        trades.apply(event);
    }

    @Override
    public boolean allowsTrading() {
        return trades.tradeCount() >= minTrades;
    }
}
