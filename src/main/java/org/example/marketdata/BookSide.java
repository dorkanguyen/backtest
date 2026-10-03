package org.example.marketdata;

/** Side of the order book: buyers (bid) or sellers (ask). */
public enum BookSide {
    /** Buy orders: someone wants to buy at this price. */
    BID,
    /** Sell orders: someone wants to sell at this price. */
    ASK
}
