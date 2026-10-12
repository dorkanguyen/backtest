package org.example.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.Map;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.state.SymbolState;
import org.junit.jupiter.api.Test;

/**
 * Tests for the decaying levels signal. The mid price is 278.57 and half the spread is 0.02.
 *
 * <pre>
 * ask 278.61 x 100
 * ask 278.60 x 200
 * ask 278.59 x 300
 * bid 278.55 x 100
 * bid 278.54 x 200
 * bid 278.53 x 900
 * </pre>
 */
class DecayingLevelSignalTest {

    private final SymbolState state = new SymbolState("AAPL", Duration.ofSeconds(10));

    @Test
    void onlyTheBestLevelSells() {
        // like the microprice: 278.56, halfway from the mid down to the bid
        Signal signal = create(1, 0.5);
        fillBook(signal);

        assertThat(signal.conviction()).isCloseTo(-0.5, within(1e-9));
    }

    @Test
    void allLevelsEqualBuys() {
        // 1200 shares bid against 600 asked: fair 278.5767, a third of the way up to the ask
        Signal signal = create(3, 1);
        fillBook(signal);

        assertThat(signal.conviction()).isCloseTo(1.0 / 3, within(1e-9));
    }

    @Test
    void halfDecayIsBalanced() {
        // 425 weighted shares on both sides: fair = mid
        Signal signal = create(3, 0.5);
        fillBook(signal);

        assertThat(signal.conviction()).isCloseTo(0, within(1e-9));
    }

    @Test
    void oneSidedBookHasNoOpinion() {
        Signal signal = create(3, 0.5);
        apply(signal, 1, BookSide.BID, 2785500, 100);

        assertThat(signal.conviction()).isZero();
    }

    private Signal create(double levels, double decay) {
        return new DecayingLevelSignalDefinition()
                .create("AAPL", Map.of("levels", levels, "decay", decay));
    }

    private void fillBook(Signal signal) {
        apply(signal, 1, BookSide.ASK, 2786100, 100);
        apply(signal, 2, BookSide.ASK, 2786000, 200);
        apply(signal, 3, BookSide.ASK, 2785900, 300);
        apply(signal, 4, BookSide.BID, 2785500, 100);
        apply(signal, 5, BookSide.BID, 2785400, 200);
        apply(signal, 6, BookSide.BID, 2785300, 900);
    }

    private void apply(Signal signal, long orderId, BookSide side, long price, long quantity) {
        MarketEvent event =
                new MarketEvent(0, "AAPL", EventType.ADD, orderId, side, price, quantity);
        state.apply(event);
        signal.update(event, state);
    }
}
