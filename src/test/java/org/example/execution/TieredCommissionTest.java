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
    void withoutPassedOnFeesItEqualsPerShareCommission() {
        TieredCommission fixed = new TieredCommission(new BigDecimal("0.005"),
                new BigDecimal("1.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        PerShareCommission perShare =
                new PerShareCommission(new BigDecimal("0.005"), new BigDecimal("1.00"));

        for (int quantity : new int[] {1, 100, 200, 1000}) {
            for (Liquidity liquidity : Liquidity.values()) {
                assertThat(fixed.calculate(quantity, PRICE, liquidity))
                        .isEqualByComparingTo(perShare.calculate(quantity, PRICE, liquidity));
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
