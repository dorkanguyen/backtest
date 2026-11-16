package org.example.execution;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.example.fairprice.ImpactMidPrice;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.book.PriceLevel;
import org.example.order.Order;
import org.example.order.Side;

/**
 * Trades the difference to the target position with one limit order at the best price of its
 * side: a buy joins the best bid, a sell joins the best ask. It never pays the spread, but its
 * order may never be filled.
 *
 * <p>Rules, checked after every event:
 * <ul>
 *   <li>No order waiting and shares are needed: place one at the best price.</li>
 *   <li>The waiting order is still good: leave it alone, so it keeps its place in the queue.</li>
 *   <li>Others now offer a better price (for a buy: the best bid is above ours): cancel and join
 *       the new best price.</li>
 *   <li>The target changed by at least {@code tolerance} shares, or the other side is needed:
 *       cancel and place a new order.</li>
 * </ul>
 * If the best price moves away to a worse price (for a buy: the best bid falls below ours), the
 * order stays: often the orders ahead of it were just filled, so in reality it would now be the
 * best bid itself. As in {@link ThrottledExecution}, a difference smaller than the tolerance is not
 * traded unless the target is 0.
 *
 * <p>Optional guard against adverse selection: a resting order is mostly filled just before the
 * price moves against it. So the impact price of {@code guardShares} shares is compared with the
 * mid price. If it leans against the order by more than {@code guardCents} (for a buy: the fair
 * price is below the mid), the order is cancelled, or not placed, until the book looks better.
 * The cancelled order loses its place in the queue. With {@code guardShares} 0 there is no guard.
 */
public class JoinBestQuote implements ExecutionAlgo {

    private final int tolerance;
    /** Fair price of the guard, or null if there is no guard. */
    private final ImpactMidPrice guard;
    private final double guardCents;
    private long nextOrderId = 1;
    /** Our waiting order, or null if none. */
    private Working working;

    /**
     * Creates the algorithm.
     *
     * @param tolerance the smallest change in shares that is worth a new order; at least 1
     * @throws IllegalArgumentException if the tolerance is below 1
     */
    public JoinBestQuote(int tolerance) {
        this(tolerance, 0, 0);
    }

    /**
     * Creates the algorithm with a guard against adverse selection.
     *
     * @param tolerance the smallest change in shares that is worth a new order; at least 1
     * @param guardShares the quantity of the impact price that the guard compares with the mid
     *         price; 0 turns the guard off
     * @param guardCents how many cents the impact price may lean against our order before it is
     *         cancelled; not negative
     * @throws IllegalArgumentException if the tolerance is below 1, or a guard setting is negative
     */
    public JoinBestQuote(int tolerance, int guardShares, double guardCents) {
        if (tolerance < 1) {
            throw new IllegalArgumentException("tolerance must be at least 1, was " + tolerance);
        }
        if (guardShares < 0 || guardCents < 0) {
            throw new IllegalArgumentException("guard settings must not be negative, were "
                    + guardShares + " shares and " + guardCents + " cents");
        }
        this.tolerance = tolerance;
        this.guard = guardShares == 0 ? null : new ImpactMidPrice(guardShares);
        this.guardCents = guardCents;
    }

    @Override
    public List<Instruction> decide(long timestamp, String symbol, int currentPosition,
            int targetPosition, OrderBook book) {
        int needed = targetPosition - currentPosition;
        List<Instruction> instructions = new ArrayList<>();
        if (working != null) {
            if (isStillGood(needed, targetPosition, book)
                    && !leansAgainst(working.side(), book)) {
                return instructions;
            }
            instructions.add(new Instruction.Cancel(working.id()));
            working = null;
        }
        if (needed == 0 || (Math.abs(needed) < tolerance && targetPosition != 0)) {
            return instructions;
        }
        Side side = needed > 0 ? Side.BUY : Side.SELL;
        Optional<PriceLevel> best = best(side, book);
        if (best.isEmpty() || leansAgainst(side, book)) {
            return instructions;
        }
        Order order =
                Order.limit(nextOrderId++, symbol, side, Math.abs(needed), best.get().price());
        working = new Working(order.id(), side, order.limitPrice(), order.quantity());
        instructions.add(new Instruction.Place(order));
        return instructions;
    }

    @Override
    public void onFill(Fill fill) {
        if (working == null || fill.orderId() != working.id()) {
            return;
        }
        int remaining = working.remaining() - fill.quantity();
        working = remaining > 0 ? working.withRemaining(remaining) : null;
    }

    private boolean isStillGood(int needed, int targetPosition, OrderBook book) {
        if (needed == 0 || (needed > 0) != (working.side() == Side.BUY)) {
            return false;
        }
        int change = Math.abs(Math.abs(needed) - working.remaining());
        boolean sizeIsGood = change == 0 || (change < tolerance && targetPosition != 0);
        Optional<PriceLevel> best = best(working.side(), book);
        boolean nobodyOffersMore = best.isEmpty() || !isBetter(best.get().price());
        return sizeIsGood && nobodyOffersMore;
    }

    /**
     * Tells whether the guard expects the price to move against an order of this side. Without
     * a guard, or if the impact price or the mid price is unknown, the answer is no.
     */
    private boolean leansAgainst(Side side, OrderBook book) {
        if (guard == null) {
            return false;
        }
        OptionalDouble fair = guard.fairPrice(book);
        Optional<PriceLevel> bid = book.bestBid();
        Optional<PriceLevel> ask = book.bestAsk();
        if (fair.isEmpty() || bid.isEmpty() || ask.isEmpty()) {
            return false;
        }
        double mid = (bid.get().priceInDollars() + ask.get().priceInDollars()) / 2;
        double leanCents = (fair.getAsDouble() - mid) * 100;
        return side == Side.BUY ? leanCents < -guardCents : leanCents > guardCents;
    }

    /** Tells whether a price of our side of the book is better than ours for the other side. */
    private boolean isBetter(long price) {
        return working.side() == Side.BUY ? price > working.price() : price < working.price();
    }

    private static Optional<PriceLevel> best(Side side, OrderBook book) {
        return side == Side.BUY ? book.bestBid() : book.bestAsk();
    }

    /** The waiting order: what we placed and how many shares are still not filled. */
    private record Working(long id, Side side, long price, int remaining) {

        Working withRemaining(int newRemaining) {
            return new Working(id, side, price, newRemaining);
        }
    }
}
