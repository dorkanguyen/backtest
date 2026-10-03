package org.example.marketdata;

/** Kind of change in the order book. */
public enum EventType {
    /** A new order is added to the book. */
    ADD,
    /** Part or all of a resting order is executed (a trade happens). */
    EXECUTE,
    /** Part of a resting order is canceled; the rest stays in the book. */
    CANCEL,
    /** A resting order is fully removed from the book. */
    DELETE,
    /** A trade with an order that was not visible in the book. */
    TRADE
}
