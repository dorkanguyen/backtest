package org.example.marketdata.book;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import org.example.marketdata.BookSide;
import org.example.marketdata.MarketEvent;

/**
 * The order book of one symbol, rebuilt from market-by-order events.
 *
 * <p>Events are applied one by one in time order, so the same book works for replayed history
 * and for live data. The book is kept by order id, because an execution can happen at a price
 * that differs from the price of the order.
 */
public class OrderBook {

    private final String symbol;
    private final Map<Long, RestingOrder> orders = new HashMap<>();
    private final NavigableMap<Long, Long> bids = new TreeMap<>(Collections.reverseOrder());
    private final NavigableMap<Long, Long> asks = new TreeMap<>();

    /**
     * Creates an empty book.
     *
     * @param symbol the symbol this book belongs to, for example {@code AAPL}
     */
    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    /**
     * Updates the book with one event.
     *
     * @param event the next event of this symbol
     * @throws IllegalArgumentException if the event belongs to another symbol
     */
    public void apply(MarketEvent event) {
        if (!event.symbol().equals(symbol)) {
            throw new IllegalArgumentException(
                    "Event of " + event.symbol() + " applied to book of " + symbol);
        }
        switch (event.type()) {
            case ADD -> add(event.orderId(), event.side(), event.price(), event.quantity());
            case EXECUTE, CANCEL -> reduce(event.orderId(), event.quantity());
            case DELETE -> reduce(event.orderId(), Long.MAX_VALUE);
            default -> {
                // TRADE: a trade with a hidden order does not change the visible book.
            }
        }
    }

    /**
     * Returns the highest price someone wants to buy at.
     *
     * @return the best bid level, or empty if nobody wants to buy
     */
    public Optional<PriceLevel> bestBid() {
        return first(bids);
    }

    /**
     * Returns the lowest price someone wants to sell at.
     *
     * @return the best ask level, or empty if nobody wants to sell
     */
    public Optional<PriceLevel> bestAsk() {
        return first(asks);
    }

    /**
     * Returns the best price levels of the buy side, best (highest) price first.
     *
     * @param depth maximum number of levels, for example 10
     * @return at most {@code depth} levels
     */
    public List<PriceLevel> bids(int depth) {
        return top(bids, depth);
    }

    /**
     * Returns the best price levels of the sell side, best (lowest) price first.
     *
     * @param depth maximum number of levels, for example 10
     * @return at most {@code depth} levels
     */
    public List<PriceLevel> asks(int depth) {
        return top(asks, depth);
    }

    private void add(long orderId, BookSide side, long price, long quantity) {
        orders.put(orderId, new RestingOrder(side, price, quantity));
        levels(side).merge(price, quantity, Long::sum);
    }

    /** Removes {@code amount} shares from an order (or the whole order if it has fewer). */
    private void reduce(long orderId, long amount) {
        RestingOrder order = orders.get(orderId);
        if (order == null) {
            return;
        }
        long removed = Math.min(amount, order.quantity());
        long remaining = order.quantity() - removed;
        if (remaining > 0) {
            orders.put(orderId, new RestingOrder(order.side(), order.price(), remaining));
        } else {
            orders.remove(orderId);
        }
        NavigableMap<Long, Long> levels = levels(order.side());
        long left = levels.get(order.price()) - removed;
        if (left > 0) {
            levels.put(order.price(), left);
        } else {
            levels.remove(order.price());
        }
    }

    private NavigableMap<Long, Long> levels(BookSide side) {
        return side == BookSide.BID ? bids : asks;
    }

    private static Optional<PriceLevel> first(NavigableMap<Long, Long> levels) {
        Map.Entry<Long, Long> entry = levels.firstEntry();
        return entry == null
                ? Optional.empty()
                : Optional.of(new PriceLevel(entry.getKey(), entry.getValue()));
    }

    private static List<PriceLevel> top(NavigableMap<Long, Long> levels, int depth) {
        List<PriceLevel> result = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : levels.entrySet()) {
            if (result.size() == depth) {
                break;
            }
            result.add(new PriceLevel(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    /** An order waiting in the book. */
    private record RestingOrder(BookSide side, long price, long quantity) {

    }
}
