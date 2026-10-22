package org.example.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TieredCommissionTest {

    private static final BigDecimal PRICE = new BigDecimal("278.59");

    private final TieredCommission tiered = new TieredCommission(new BigDecimal("0.0035"),
            new BigDecimal("0.35"), new BigDecimal("0.003"), new BigDecimal("0.0002"),
            new BigDecimal("0.002"));

    @Test
    void addsBrokerExchangeAndClearingFees() {
        // 0.35 + 0.30 + 0.02
        assertThat(tiered.calculate(100, PRICE, Liquidity.TAKER)).isEqualByComparingTo("0.67");
    }

    @Test
    void makerGetsTheRebateInsteadOfPayingTheExchangeFee() {
        // 0.35 - 0.20 + 0.02
        assertThat(tiered.calculate(100, PRICE, Liquidity.MAKER)).isEqualByComparingTo("0.17");
    }

    @Test
    void largeRebateMakesTheCommissionNegative() {
        TieredCommission generous = new TieredCommission(new BigDecimal("0.001"), BigDecimal.ZERO,
                new BigDecimal("0.003"), BigDecimal.ZERO, new BigDecimal("0.003"));

        // 0.10 - 0.30
        assertThat(generous.calculate(100, PRICE, Liquidity.MAKER)).isEqualByComparingTo("-0.20");
    }

    @Test
    void brokerFeeAboveTheMinimumIsChargedPerShare() {
        // 3.50 + 3.00 + 0.20
        assertThat(tiered.calculate(1000, PRICE, Liquidity.TAKER)).isEqualByComparingTo("6.70");
    }

    @Test
    void minimumAppliesOnlyToTheBrokerFee() {
        // 0.35 (minimum, not 0.035) + 0.03 + 0.002
        assertThat(tiered.calculate(10, PRICE, Liquidity.TAKER)).isEqualByComparingTo("0.382");
    }

    @Test
    void brokerMinimumIsChargedOncePerOrder() {
        // 1 cent per share, at least 35 cents per order, no other fees
        TieredCommission broker = new TieredCommission(new BigDecimal("0.01"),
                new BigDecimal("0.35"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        // 30 + 30 + 40 shares: 0.35, then 0.60 - 0.35, then 1.00 - 0.60
        assertThat(broker.calculate(30, PRICE, Liquidity.MAKER, 0)).isEqualByComparingTo("0.35");
        assertThat(broker.calculate(30, PRICE, Liquidity.MAKER, 30)).isEqualByComparingTo("0.25");
        assertThat(broker.calculate(40, PRICE, Liquidity.MAKER, 60)).isEqualByComparingTo("0.40");

        // 10 + 90 shares: 0.35, then 1.00 - 0.35
        assertThat(broker.calculate(10, PRICE, Liquidity.MAKER, 0)).isEqualByComparingTo("0.35");
        assertThat(broker.calculate(90, PRICE, Liquidity.MAKER, 10)).isEqualByComparingTo("0.65");
    }

    @Test
    void laterFillsBelowTheMinimumPayOnlyThePassedOnFees() {
        // 10 + 20 shares, still below the minimum: the broker fee is paid by the first fill only
        assertThat(tiered.calculate(10, PRICE, Liquidity.TAKER, 0)).isEqualByComparingTo("0.382");
        // 0 + 20 x (0.003 + 0.0002)
        assertThat(tiered.calculate(20, PRICE, Liquidity.TAKER, 10))
                .isEqualByComparingTo("0.064");
    }

    @Test
    void partsOfAnOrderCostTheSameAsOneFill() {
        BigDecimal inParts = tiered.calculate(30, PRICE, Liquidity.MAKER, 0)
                .add(tiered.calculate(50, PRICE, Liquidity.MAKER, 30))
                .add(tiered.calculate(120, PRICE, Liquidity.MAKER, 80));

        assertThat(inParts).isEqualByComparingTo(tiered.calculate(200, PRICE, Liquidity.MAKER));
    }

    @Test
    void withoutPassedOnFeesItEqualsPerShareCommission() {
        TieredCommission fixed = new TieredCommission(new BigDecimal("0.005"),
                new BigDecimal("1.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        PerShareCommission perShare =
                new PerShareCommission(new BigDecimal("0.005"), new BigDecimal("1.00"));

        for (int quantity : new int[] {1, 100, 200, 1000}) {
            for (Liquidity liquidity : Liquidity.values()) {
                assertThat(fixed.calculate(quantity, PRICE, liquidity))
                        .isEqualByComparingTo(perShare.calculate(quantity, PRICE, liquidity));
                assertThat(fixed.calculate(quantity, PRICE, liquidity, 150))
                        .isEqualByComparingTo(perShare.calculate(quantity, PRICE, liquidity, 150));
            }
        }
    }

    @Test
    void rejectsNegativeFees() {
        assertThatThrownBy(() -> new TieredCommission(new BigDecimal("0.0035"),
                new BigDecimal("0.35"), new BigDecimal("-0.002"), BigDecimal.ZERO,
                BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exchangePerShare");
        assertThatThrownBy(() -> new TieredCommission(new BigDecimal("0.0035"),
                new BigDecimal("0.35"), new BigDecimal("0.003"), BigDecimal.ZERO,
                new BigDecimal("-0.002")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("makerRebatePerShare");
    }
}
