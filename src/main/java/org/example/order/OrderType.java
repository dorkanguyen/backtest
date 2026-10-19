package org.example.order;

/** How an order is executed. */
public enum OrderType {

    /** Trades at once against the waiting offers of the book, at whatever price they have. */
    MARKET,

    /**
     * Waits in the book at a chosen price until someone trades with it; never trades at a worse
     * price.
     */
    LIMIT
}
