package org.example.execution;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.book.PriceLevel;
import org.example.order.Order;
import org.example.order.OrderType;
import org.example.order.Side;

/**
 * Simulates the place of one of our limit orders in the queue of its price, using the
 * order-by-order history of the market.
 *
 * <p>When our order joins a price, every order already waiting there is ahead of us; orders that
 * arrive later are behind us. Executions, cancels and deletes of the orders ahead move us
 * forward. The fill rule is cautious: only executions of orders on our side of the book count,
 * and we trade only when
 * <ul>
 *   <li>nobody is left ahead of us and a later execution happens at our price, or</li>
 *   <li>an execution happens at a price better for us than our limit (the price went through
 *       our level), for example someone sells below our buy price.</li>
 * </ul>
 * Each such execution fills us with at most its own quantity. Trades with hidden orders are not
 * counted, because we do not know whose order they took.
 *
 * <p>Simplification: our order does not change the market. The other orders behave as in the
 * recorded history, as if we were not there; this is acceptable for small orders.
 */
public class QueueSimulator {

    private final Order order;
    private final BookSide bookSide;
    private final Map<Long, Long> ahead;
    private long sharesAhead;
    private long remaining;

    /**
     * Places our order at the back of the queue of its price.
     *
     * @param order our limit order
     * @param book the book of the order's symbol at the moment the order arrives
     * @throws IllegalArgumentException if the order is not a limit order or would trade at once,
     *         because a buy is at or above the best ask (or a sell at or below the best bid)
     */
    public QueueSimulator(Order order, OrderBook book) {
        if (order.type() != OrderType.LIMIT) {
            throw new IllegalArgumentException("Only a limit order can wait in a queue");
        }
        this.order = order;
        this.bookSide = order.side() == Side.BUY ? BookSide.BID : BookSide.ASK;
        Optional<PriceLevel> opposite = order.side() == Side.BUY ? book.bestAsk() : book.bestBid();
        if (opposite.isPresent() && isAtOrBetterThanOurPrice(opposite.get().price())) {
            throw new IllegalArgumentException(
                    "Limit price " + order.limitPrice() + " would trade at once");
        }
        this.ahead = new HashMap<>(book.ordersAt(bookSide, order.limitPrice()));
        this.sharesAhead = ahead.values().stream().mapToLong(Long::longValue).sum();
        this.remaining = order.quantity();
    }

    /**
     * Updates the queue with the next market event and tells how much of our order it filled.
     *
     * @param event the next event of the order's symbol
     * @return number of shares of our order filled by this event, 0 if none; they trade at the
     *         order's limit price
     * @throws IllegalArgumentException if the event belongs to another symbol
     */
    public long apply(MarketEvent event) {
        if (!event.symbol().equals(order.symbol())) {
            throw new IllegalArgumentException(
                    "Event of " + event.symbol() + " applied to order of " + order.symbol());
        }
        if (remaining == 0) {
            return 0;
        }
        Long aheadQuantity = ahead.get(event.orderId());
        if (aheadQuantity != null) {
            moveForward(event, aheadQuantity);
            return 0;
        }
        if (event.type() != EventType.EXECUTE || event.side() != bookSide) {
            return 0;
        }
        boolean atOurPrice = event.price() == order.limitPrice() && sharesAhead == 0;
        boolean throughOurPrice = event.price() != order.limitPrice()
                && isAtOrBetterThanOurPrice(event.price());
        if (!atOurPrice && !throughOurPrice) {
            return 0;
        }
        long filled = Math.min(event.quantity(), remaining);
        remaining -= filled;
        return filled;
    }

    /**
     * Returns our order.
     *
     * @return the limit order this queue belongs to
     */
    public Order order() {
        return order;
    }

    /**
     * Returns how many shares wait ahead of our order at its price.
     *
     * @return shares ahead
     */
    public long sharesAhead() {
        return sharesAhead;
    }

    /**
     * Returns how many shares of our order are not filled yet.
     *
     * @return unfilled shares
     */
    public long remaining() {
        return remaining;
    }

    /**
     * Tells whether our order is completely filled.
     *
     * @return true if nothing is left to fill
     */
    public boolean isFilled() {
        return remaining == 0;
    }

    /** An event of an order ahead of us shrinks or removes it. */
    private void moveForward(MarketEvent event, long aheadQuantity) {
        long removed = switch (event.type()) {
            case EXECUTE, CANCEL -> Math.min(event.quantity(), aheadQuantity);
            case DELETE -> aheadQuantity;
            default -> 0;
        };
        sharesAhead -= removed;
        if (aheadQuantity - removed > 0) {
            ahead.put(event.orderId(), aheadQuantity - removed);
        } else {
            ahead.remove(event.orderId());
        }
    }

    /**
     * Tells whether a price is at least as good as our limit price for us: for a buy not higher,
     * for a sell not lower.
     */
    private boolean isAtOrBetterThanOurPrice(long price) {
        return order.side() == Side.BUY
                ? price <= order.limitPrice()
                : price >= order.limitPrice();
    }
}
