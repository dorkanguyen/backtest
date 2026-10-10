package org.example.marketdata.flow;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.PriceLevel;

/**
 * Tracks the recent trades of one symbol in a sliding time window.
 *
 * <p>Like the order book, it is updated event by event, so it works the same for replayed history
 * and for live data. The current time is the time of the last event. For every trade it records
 * who was the aggressor (the taker who accepted a waiting price): an execution of an ask order
 * was started by a buyer, an execution of a bid order by a seller. For trades with hidden orders
 * the aggressor is unknown, so they count only in the total volume.
 */
public class TradeFlow {

    private final String symbol;
    private final long windowNanos;
    private final Deque<Trade> trades = new ArrayDeque<>();
    private long buyVolume;
    private long sellVolume;
    private long totalVolume;
    private long totalNotional;
    private long lastPrice;
    private boolean hasLastPrice;

    /**
     * Creates an empty trade flow.
     *
     * @param symbol the symbol this trade flow belongs to, for example {@code AAPL}
     * @param window how far back trades are counted, for example 10 seconds
     * @throws IllegalArgumentException if the window is not positive
     */
    public TradeFlow(String symbol, Duration window) {
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Window must be positive: " + window);
        }
        this.symbol = symbol;
        this.windowNanos = window.toNanos();
    }

    /**
     * Updates the trade flow with one event. Events that are not trades only move the time
     * forward, so old trades leave the window.
     *
     * @param event the next event of this symbol
     * @throws IllegalArgumentException if the event belongs to another symbol
     */
    public void apply(MarketEvent event) {
        if (!event.symbol().equals(symbol)) {
            throw new IllegalArgumentException(
                    "Event of " + event.symbol() + " applied to trade flow of " + symbol);
        }
        if (event.type() == EventType.EXECUTE) {
            Aggressor aggressor = event.side() == BookSide.ASK ? Aggressor.BUYER : Aggressor.SELLER;
            addTrade(event, aggressor);
        } else if (event.type() == EventType.TRADE) {
            addTrade(event, Aggressor.UNKNOWN);
        }
        removeTradesBefore(event.timestamp() - windowNanos);
    }

    /**
     * Returns the price of the last trade, even if it is older than the window.
     *
     * @return price in 1/10000 dollars, or empty if there was no trade yet
     */
    public OptionalLong lastPrice() {
        return hasLastPrice ? OptionalLong.of(lastPrice) : OptionalLong.empty();
    }

    /**
     * Returns the number of shares bought by aggressive buyers in the window.
     *
     * @return shares bought by takers
     */
    public long buyVolume() {
        return buyVolume;
    }

    /**
     * Returns the number of shares sold by aggressive sellers in the window.
     *
     * @return shares sold by takers
     */
    public long sellVolume() {
        return sellVolume;
    }

    /**
     * Returns the number of shares traded in the window, including trades with hidden orders.
     *
     * @return all traded shares
     */
    public long totalVolume() {
        return totalVolume;
    }

    /**
     * Returns the number of trades in the window, including trades with hidden orders.
     *
     * @return number of trades
     */
    public int tradeCount() {
        return trades.size();
    }

    /**
     * Returns the volume weighted average price (VWAP) of the trades in the window: the average
     * price where every share counts once, so big trades count more than small ones. Trades with
     * hidden orders are included.
     *
     * @return the VWAP in dollars, or empty if there was no trade in the window
     */
    public OptionalDouble vwap() {
        if (totalVolume == 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(totalNotional / PriceLevel.UNITS_PER_DOLLAR / totalVolume);
    }

    /**
     * Returns the balance of aggressive buying and selling in the window: {@code +1} if only
     * buyers were aggressive, {@code -1} if only sellers, {@code 0} if balanced or no trades.
     *
     * @return (buy volume - sell volume) / (buy volume + sell volume)
     */
    public double imbalance() {
        long known = buyVolume + sellVolume;
        return known == 0 ? 0.0 : (double) (buyVolume - sellVolume) / known;
    }

    private void addTrade(MarketEvent event, Aggressor aggressor) {
        Trade trade = new Trade(event.timestamp(), aggressor, event.price(), event.quantity());
        trades.addLast(trade);
        count(trade, 1);
        lastPrice = event.price();
        hasLastPrice = true;
    }

    /** Removes the trades that happened at or before {@code limit}. */
    private void removeTradesBefore(long limit) {
        while (!trades.isEmpty() && trades.peekFirst().timestamp() <= limit) {
            count(trades.removeFirst(), -1);
        }
    }

    /** Adds ({@code sign = 1}) or subtracts ({@code sign = -1}) a trade from the totals. */
    private void count(Trade trade, int sign) {
        long quantity = sign * trade.quantity();
        totalVolume += quantity;
        totalNotional += quantity * trade.price();
        switch (trade.aggressor()) {
            case BUYER -> buyVolume += quantity;
            case SELLER -> sellVolume += quantity;
            default -> {
                // UNKNOWN: a trade with a hidden order counts only in the total volume.
            }
        }
    }

    /** Who started a trade by accepting a waiting price. */
    private enum Aggressor {
        BUYER,
        SELLER,
        UNKNOWN
    }

    /** One trade in the window. */
    private record Trade(long timestamp, Aggressor aggressor, long price, long quantity) {

    }
}
