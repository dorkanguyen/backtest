package org.example.execution;

import java.math.BigDecimal;

/**
 * Commission made of a broker fee with a minimum, plus exchange and clearing fees per share.
 *
 * <p>This is how brokers with tiered pricing (for example Interactive Brokers "Tiered") charge:
 * the broker fee has a minimum per fill, while the exchange and clearing fees are passed on per
 * share. Example with 0.0035 per share and 0.35 minimum, 0.003 exchange and 0.0002 clearing fee:
 * 100 shares cost 0.35 + 0.30 + 0.02 = 0.67.
 *
 * <p>With zero exchange and clearing fees this is the same as {@link PerShareCommission}.
 *
 * <p>Not modelled, because they are tiny or do not apply yet: the cap of the broker fee at 1% of
 * the trade value, the SEC fee on sales (zero per million dollars in December 2025), the FINRA
 * trading activity fee on sales (a small fraction of a cent per share) and exchange rebates for
 * limit orders that add liquidity.
 */
public class TieredCommission implements CommissionModel {

    private final BigDecimal brokerPerShare;
    private final BigDecimal brokerMinimum;
    private final BigDecimal exchangePerShare;
    private final BigDecimal clearingPerShare;

    /**
     * Creates a tiered commission model.
     *
     * @param brokerPerShare broker fee per share, for example 0.0035
     * @param brokerMinimum least broker fee of one fill, for example 0.35
     * @param exchangePerShare exchange fee per share, for example 0.003
     * @param clearingPerShare clearing fee per share, for example 0.0002
     * @throws IllegalArgumentException if a fee is negative
     */
    public TieredCommission(BigDecimal brokerPerShare, BigDecimal brokerMinimum,
            BigDecimal exchangePerShare, BigDecimal clearingPerShare) {
        this.brokerPerShare = notNegative(brokerPerShare, "brokerPerShare");
        this.brokerMinimum = notNegative(brokerMinimum, "brokerMinimum");
        this.exchangePerShare = notNegative(exchangePerShare, "exchangePerShare");
        this.clearingPerShare = notNegative(clearingPerShare, "clearingPerShare");
    }

    @Override
    public BigDecimal calculate(int quantity, BigDecimal price) {
        BigDecimal shares = BigDecimal.valueOf(quantity);

        BigDecimal brokerFee = brokerPerShare.multiply(shares);
        if (brokerFee.compareTo(brokerMinimum) < 0) {
            brokerFee = brokerMinimum;
        }

        BigDecimal passedOnFees = exchangePerShare.add(clearingPerShare).multiply(shares);
        return brokerFee.add(passedOnFees);
    }

    private static BigDecimal notNegative(BigDecimal fee, String name) {
        if (fee == null || fee.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return fee;
    }
}
