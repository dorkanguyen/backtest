package org.example.marketdata.state;

import java.time.Duration;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.flow.TradeFlow;

/**
 * Everything we know about the market of one symbol: its order book and its recent trades.
 *
 * <p>Both parts are updated with the same events, so they always describe the same moment.
 */
public class SymbolState {

    private final String symbol;
    private final OrderBook book;
    private final TradeFlow trades;

    /**
     * Creates the empty state of one symbol.
     *
     * @param symbol the symbol, for example {@code AAPL}
     * @param tradeWindow how far back the trade flow counts trades, for example 10 seconds
     */
    public SymbolState(String symbol, Duration tradeWindow) {
        this.symbol = symbol;
        this.book = new OrderBook(symbol);
        this.trades = new TradeFlow(symbol, tradeWindow);
    }

    /**
     * Updates the order book and the trade flow with one event.
     *
     * @param event the next event of this symbol
     * @throws IllegalArgumentException if the event belongs to another symbol
     */
    public void apply(MarketEvent event) {
        book.apply(event);
        trades.apply(event);
    }

    /** Returns the symbol, for example {@code AAPL}. */
    public String symbol() {
        return symbol;
    }

    /** Returns the order book: the orders waiting to be traded now. */
    public OrderBook book() {
        return book;
    }

    /** Returns the trade flow: the trades of the last few seconds. */
    public TradeFlow trades() {
        return trades;
    }
}
