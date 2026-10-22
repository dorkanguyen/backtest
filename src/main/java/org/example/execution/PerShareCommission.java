package org.example.execution;

import java.math.BigDecimal;

/**
 * Commission charged per share, with a minimum per order.
 *
 * <p>Example: with 0.005 per share and a minimum of 1.00, 100 shares cost 1.00
 * and 1000 shares cost 5.00. Makers and takers pay the same. When an order is filled in parts,
 * only the first fill pays the minimum; later fills pay how much the fee of the whole order grows.
 */
public class PerShareCommission implements CommissionModel {

    private final BigDecimal perShare;
    private final BigDecimal minimum;

    /**
     * Creates a per-share commission model.
     *
     * @param perShare fee per share
     * @param minimum minimum fee per order
     */
    public PerShareCommission(BigDecimal perShare, BigDecimal minimum) {
        this.perShare = perShare;
        this.minimum = minimum;
    }

    @Override
    public BigDecimal calculate(int quantity, BigDecimal price, Liquidity liquidity,
            int alreadyFilled) {
        return feeOfOrder(alreadyFilled + quantity).subtract(feeOfOrder(alreadyFilled));
    }

    private BigDecimal feeOfOrder(int filledShares) {
        if (filledShares == 0) {
            return BigDecimal.ZERO;
        }
        return perShare.multiply(BigDecimal.valueOf(filledShares)).max(minimum);
    }
}
