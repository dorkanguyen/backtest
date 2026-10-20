package org.example.execution;

/**
 * Whether a fill added liquidity to the market or took it away; exchanges charge the two
 * differently.
 */
public enum Liquidity {

    /** Our order waited in the book and someone traded with it (limit order); earns a rebate. */
    MAKER,

    /** Our order traded at once with an order waiting in the book (market order); pays a fee. */
    TAKER
}
