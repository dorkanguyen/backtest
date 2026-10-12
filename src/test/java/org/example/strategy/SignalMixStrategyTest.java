package org.example.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.example.filter.TradingFilter;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.state.SymbolState;
import org.example.signal.Signal;
import org.junit.jupiter.api.Test;

class SignalMixStrategyTest {

    @Test
    void weightedAverageTimesMaxPosition() {
        // 0.5 * 60% + 0.3 * 20% + 0.2 * -50% = 26%
        SignalMixStrategy strategy = new SignalMixStrategy(
                List.of(weighted(0.6, 50), weighted(0.2, 30), weighted(-0.5, 20)),
                List.of(), 100);

        assertThat(strategy.conviction()).isCloseTo(0.26, within(1e-9));
        assertThat(strategy.targetPosition(0)).isEqualTo(26);
    }

    @Test
    void onlyTheRatioOfTheWeightsMatters() {
        // 2 : 1 : 1 is 50% : 25% : 25%
        SignalMixStrategy strategy = new SignalMixStrategy(
                List.of(weighted(1, 2), weighted(-1, 1), weighted(0, 1)), List.of(), 100);

        assertThat(strategy.targetPosition(0)).isEqualTo(25);
    }

    @Test
    void zeroWeightIsIgnored() {
        SignalMixStrategy strategy = new SignalMixStrategy(
                List.of(weighted(1, 1), weighted(-1, 0)), List.of(), 100);

        assertThat(strategy.targetPosition(0)).isEqualTo(100);
    }

    @Test
    void sellingOpinionGivesShortPosition() {
        SignalMixStrategy strategy =
                new SignalMixStrategy(List.of(weighted(-0.333, 1)), List.of(), 100);

        assertThat(strategy.targetPosition(0)).isEqualTo(-33);
    }

    @Test
    void blockingFilterKeepsTheCurrentPosition() {
        SignalMixStrategy strategy = new SignalMixStrategy(
                List.of(weighted(1, 1)), List.of(new FixedFilter(true), new FixedFilter(false)),
                100);

        assertThat(strategy.targetPosition(42)).isEqualTo(42);
    }

    @Test
    void allowingFiltersLetTheSignalsDecide() {
        SignalMixStrategy strategy = new SignalMixStrategy(
                List.of(weighted(1, 1)), List.of(new FixedFilter(true)), 100);

        assertThat(strategy.targetPosition(42)).isEqualTo(100);
    }

    @Test
    void allWeightsZeroIsRejected() {
        assertThatThrownBy(() -> new SignalMixStrategy(
                List.of(weighted(1, 0), weighted(-1, 0)), List.of(), 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noSignalIsRejected() {
        assertThatThrownBy(() -> new SignalMixStrategy(List.of(), List.of(), 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeWeightIsRejected() {
        assertThatThrownBy(() -> weighted(1, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    private static WeightedSignal weighted(double conviction, double weight) {
        return new WeightedSignal(new FixedSignal(conviction), weight);
    }

    /** A signal that always has the same opinion. */
    private record FixedSignal(double conviction) implements Signal {

        @Override
        public void update(MarketEvent event, SymbolState state) {
        }
    }

    /** A filter that always gives the same answer. */
    private record FixedFilter(boolean allowsTrading) implements TradingFilter {

        @Override
        public void update(MarketEvent event, SymbolState state) {
        }
    }
}
