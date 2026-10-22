package org.example.execution;

import java.math.BigDecimal;

/**
 * Commission made of a broker fee with a minimum, plus exchange and clearing fees per share.
 *
 * <p>This is how brokers with tiered pricing (for example Interactive Brokers "Tiered") charge:
 * the broker fee has a minimum per order, while the exchange and clearing fees are passed on per
 * share. The exchange charges a fee to takers and pays a rebate to makers. Example with 0.0035
 * per share and 0.35 minimum, 0.003 taker fee, 0.002 maker rebate and 0.0002 clearing fee:
 * 100 shares cost 0.35 + 0.30 + 0.02 = 0.67 as a taker and 0.35 - 0.20 + 0.02 = 0.17 as a maker.
 *
 * <p>When an order is filled in parts, only the first fill pays the broker minimum; later fills
 * pay how much the broker fee of the whole order grows. With 0.0035 per share and 0.35 minimum,
 * fills of 30, 30 and 40 shares pay a broker fee of 0.35, 0 and 0: in total 0.35, the same as
 * one fill of 100 shares.
 *
 * <p>With zero exchange and clearing fees this is the same as {@link PerShareCommission}.
 *
 * <p>Not modelled, because they are tiny or do not apply yet: the cap of the broker fee at 1% of
 * the trade value, the SEC fee on sales (zero per million dollars in December 2025), the FINRA
 * trading activity fee on sales (a small fraction of a cent per share) and the volume tiers of
 * the exchange fees.
 */
public class TieredCommission implements CommissionModel {

    private final BigDecimal brokerPerShare;
    private final BigDecimal brokerMinimum;
    private final BigDecimal exchangePerShare;
    private final BigDecimal clearingPerShare;
    private final BigDecimal makerRebatePerShare;

    /**
     * Creates a tiered commission model.
     *
     * @param brokerPerShare broker fee per share, for example 0.0035
     * @param brokerMinimum least broker fee of one order, for example 0.35
     * @param exchangePerShare exchange fee per share paid by takers, for example 0.003
     * @param clearingPerShare clearing fee per share, for example 0.0002
     * @param makerRebatePerShare exchange rebate per share paid to makers, for example 0.002
     * @throws IllegalArgumentException if a fee or the rebate is negative
     */
    public TieredCommission(BigDecimal brokerPerShare, BigDecimal brokerMinimum,
            BigDecimal exchangePerShare, BigDecimal clearingPerShare,
            BigDecimal makerRebatePerShare) {
        this.brokerPerShare = notNegative(brokerPerShare, "brokerPerShare");
        this.brokerMinimum = notNegative(brokerMinimum, "brokerMinimum");
        this.exchangePerShare = notNegative(exchangePerShare, "exchangePerShare");
        this.clearingPerShare = notNegative(clearingPerShare, "clearingPerShare");
        this.makerRebatePerShare = notNegative(makerRebatePerShare, "makerRebatePerShare");
    }

    @Override
    public BigDecimal calculate(int quantity, BigDecimal price, Liquidity liquidity,
            int alreadyFilled) {
        BigDecimal shares = BigDecimal.valueOf(quantity);

        // the minimum applies to the whole order: charge how much the order's broker fee grows
        BigDecimal brokerFee = brokerFeeOfOrder(alreadyFilled + quantity)
                .subtract(brokerFeeOfOrder(alreadyFilled));

        BigDecimal exchangeFee =
                liquidity == Liquidity.TAKER ? exchangePerShare : makerRebatePerShare.negate();
        BigDecimal passedOnFees = exchangeFee.add(clearingPerShare).multiply(shares);
        return brokerFee.add(passedOnFees);
    }

    private BigDecimal brokerFeeOfOrder(int filledShares) {
        if (filledShares == 0) {
            return BigDecimal.ZERO;
        }
        return brokerPerShare.multiply(BigDecimal.valueOf(filledShares)).max(brokerMinimum);
    }

    private static BigDecimal notNegative(BigDecimal fee, String name) {
        if (fee == null || fee.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return fee;
    }
}
