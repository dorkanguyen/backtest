package org.example.execution;

import java.math.BigDecimal;

/**
 * Calculates the commission (broker and exchange fees) for a fill.
 *
 * <p>Each implementation models one fee scheme, for example a fee per share. Exchanges charge
 * orders that take liquidity and may pay a rebate to orders that add it, so the fee depends on
 * the {@link Liquidity} of the fill.
 *
 * <p>A limit order is often filled in several parts. Brokers charge their minimum fee once per
 * order, not once per fill, so the model is told how many shares of the same order were filled
 * before. The commissions of all fills of an order then add up to the commission of the order.
 */
public interface CommissionModel {

    /**
     * Returns the commission for one fill of an order.
     *
     * @param quantity number of shares in this fill, always positive
     * @param price price per share
     * @param liquidity whether the fill added (maker) or took (taker) liquidity
     * @param alreadyFilled shares of the same order filled before this fill, 0 for the first
     * @return the commission; negative only if a rebate is larger than all fees
     */
    BigDecimal calculate(int quantity, BigDecimal price, Liquidity liquidity, int alreadyFilled);

    /**
     * Returns the commission for the first (or only) fill of an order.
     *
     * @param quantity number of shares, always positive
     * @param price price per share
     * @param liquidity whether the fill added (maker) or took (taker) liquidity
     * @return the commission; negative only if a rebate is larger than all fees
     */
    default BigDecimal calculate(int quantity, BigDecimal price, Liquidity liquidity) {
        return calculate(quantity, price, liquidity, 0);
    }
}
