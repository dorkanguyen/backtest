package org.example.execution;

import org.example.order.Order;

/**
 * What an execution algorithm asks the market to do: place a new order or cancel a waiting one.
 *
 * <p>The interface is {@code sealed}: these two records are the only kinds of instruction, so
 * code that handles instructions can be sure it knows every case.
 */
public sealed interface Instruction permits Instruction.Place, Instruction.Cancel {

    /**
     * Sends a new order. A market order trades at once; a limit order waits in the book.
     *
     * @param order the order; its id must not be used by another order of the same run
     */
    record Place(Order order) implements Instruction {

    }

    /**
     * Cancels a waiting limit order. Cancelling an order that is already filled or unknown does
     * nothing, like a cancel that arrives too late at a real exchange.
     *
     * @param orderId id of the order to cancel
     */
    record Cancel(long orderId) implements Instruction {

    }
}
