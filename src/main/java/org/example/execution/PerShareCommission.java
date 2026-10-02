package org.example.execution;

import java.math.BigDecimal;

public class PerShareCommission implements CommissionModel {

    private final BigDecimal perShare;
    private final BigDecimal minimum;

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