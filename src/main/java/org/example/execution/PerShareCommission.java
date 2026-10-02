package org.example.execution;

import java.math.BigDecimal;

/**
 * Commission charged per share, with a minimum per fill.
 *
 * <p>Example: with 0.005 per share and a minimum of 1.00, 100 shares cost 1.00
 * and 1000 shares cost 5.00.
 */
public class PerShareCommission implements CommissionModel {

    private final BigDecimal perShare;
    private final BigDecimal minimum;

    /**
     * Creates a per-share commission model.
     *
     * @param perShare fee per share
     * @param minimum minimum fee per fill
     */
    public PerShareCommission(BigDecimal perShare, BigDecimal minimum) {
        this.perShare = perShare;
        this.minimum = minimum;
    }

    @Override
    public BigDecimal calculate(int quantity, BigDecimal price) {
        BigDecimal commission = perShare.multiply(BigDecimal.valueOf(quantity));

        if (commission.compareTo(minimum) < 0) {
            commission = minimum;
        }

        return commission;
    }
}
