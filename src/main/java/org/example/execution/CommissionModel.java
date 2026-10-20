package org.example.execution;

import java.math.BigDecimal;

/**
 * Calculates the commission (broker and exchange fees) for a fill.
 *
 * <p>Each implementation models one fee scheme, for example a fee per share. Exchanges charge
 * orders that take liquidity and may pay a rebate to orders that add it, so the fee depends on
 * the {@link Liquidity} of the fill.
 */
public interface CommissionModel {

    /**
     * Returns the commission for trading {@code quantity} shares at {@code price}.
     *
     * @param quantity number of shares, always positive
     * @param price price per share
     * @param liquidity whether the fill added (maker) or took (taker) liquidity
     * @return the commission; negative only if a rebate is larger than all fees
     */
    BigDecimal calculate(int quantity, BigDecimal price, Liquidity liquidity);
}
