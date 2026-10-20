package org.example.execution;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.OrderBook;
import org.example.order.Order;
import org.example.order.OrderType;

/**
 * Plays the market for our orders in a backtest: carries out the instructions of the execution
 * algorithm and reports the fills.
 *
 * <p>A market order is filled at once from the book by a {@link BookExecutionEngine} (taker).
 * A limit order waits in the queue of its price, simulated by a {@link QueueSimulator}, and is
 * filled at its limit price by later events (maker). A cancel removes a waiting order.
 *
 * <p>One simulator belongs to one backtest run, because it remembers the waiting orders.
 */
public class ExecutionSimulator {

    private final CommissionModel commissionModel;
    private final BookExecutionEngine marketOrders;
    /** Waiting limit orders by id, in the order they were placed. */
    private final Map<Long, QueueSimulator> waiting = new LinkedHashMap<>();

    /**
     * Creates the simulator.
     *
     * @param commissionModel calculates the commission of every fill
     */
    public ExecutionSimulator(CommissionModel commissionModel) {
        this.commissionModel = commissionModel;
        this.marketOrders = new BookExecutionEngine(commissionModel);
    }

    /**
     * Carries out one instruction.
     *
     * @param instruction what to do
     * @param book the current order book of the symbol
     * @param time the current time, used as the time of a market order's fill
     * @return the fill of a market order, or nothing for a limit order or a cancel
     * @throws IllegalStateException if a placed order has the id of a waiting order
     * @throws IllegalArgumentException if a limit order would trade at once, see
     *         {@link QueueSimulator}
     */
    public List<Fill> carryOut(Instruction instruction, OrderBook book, LocalDateTime time) {
        if (instruction instanceof Instruction.Place place) {
            return place(place.order(), book, time);
        }
        Instruction.Cancel cancel = (Instruction.Cancel) instruction;
        waiting.remove(cancel.orderId());
        return List.of();
    }

    /**
     * Moves the waiting orders forward in their queues with the next market event.
     *
     * @param event the next event of the symbol
     * @param time the time of the event, used as the time of the fills
     * @return the fills of waiting orders caused by this event, often none
     */
    public List<Fill> onEvent(MarketEvent event, LocalDateTime time) {
        if (waiting.isEmpty()) {
            return List.of();
        }
        List<Fill> fills = new ArrayList<>();
        Iterator<QueueSimulator> queues = waiting.values().iterator();
        while (queues.hasNext()) {
            QueueSimulator queue = queues.next();
            long filled = queue.apply(event);
            if (filled > 0) {
                fills.add(makerFill(queue.order(), (int) filled, time));
            }
            if (queue.isFilled()) {
                queues.remove();
            }
        }
        return fills;
    }

    /** Cancels every waiting order, for example before the position is closed at the end. */
    public void cancelAll() {
        waiting.clear();
    }

    /**
     * Returns how many limit orders are waiting in the book.
     *
     * @return number of waiting orders
     */
    public int waitingOrders() {
        return waiting.size();
    }

    private List<Fill> place(Order order, OrderBook book, LocalDateTime time) {
        if (waiting.containsKey(order.id())) {
            throw new IllegalStateException("Order id " + order.id() + " is already waiting");
        }
        if (order.type() == OrderType.MARKET) {
            return marketOrders.execute(order, book, time).map(List::of).orElse(List.of());
        }
        waiting.put(order.id(), new QueueSimulator(order, book));
        return List.of();
    }

    private Fill makerFill(Order order, int quantity, LocalDateTime time) {
        // the limit price is in 1/10000 dollars: 4 decimal places
        BigDecimal price = BigDecimal.valueOf(order.limitPrice(), 4);
        BigDecimal commission = commissionModel.calculate(quantity, price, Liquidity.MAKER);
        return new Fill(order.id(), order.symbol(), order.side(), quantity, price, commission,
                Liquidity.MAKER, time);
    }
}
