package org.example.execution;

import java.math.BigDecimal;

/**
 * Calculates the commission (broker fee) for a fill.
 *
 * <p>Each implementation models one fee scheme, for example a fee per share.
 */
public interface CommissionModel {

    /**
     * Returns the commission for trading {@code quantity} shares at {@code price}.
     *
     * @param quantity number of shares, always positive
     * @param price price per share
     * @return the commission, never negative
     */
    BigDecimal calculate(int quantity, BigDecimal price);
}
