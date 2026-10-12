package org.example.signal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.state.SymbolState;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OfiSignal} with a 10 second window and a threshold of 50 shares. Every test
 * starts from a book of bid 278.55 x 100 and ask 278.59 x 100. Times are in seconds.
 */
class OfiSignalTest {

    private static final long SECOND = 1_000_000_000L;

    private final SymbolState state = new SymbolState("AAPL", Duration.ofSeconds(10));
    private final Signal signal = new OfiSignalDefinition()
            .create("AAPL", Map.of("windowSeconds", 10.0, "threshold", 50.0));

    OfiSignalTest() {
        apply(0, EventType.ADD, 1, BookSide.BID, 2785500, 100);
        apply(0, EventType.ADD, 2, BookSide.ASK, 2785900, 100);
    }

    @Test
    void noPressureMeansNoOpinion() {
        assertThat(signal.conviction()).isZero();
    }

    @Test
    void buyingPressureAboveTheThresholdBuys() {
        apply(1, EventType.ADD, 3, BookSide.BID, 2785500, 80);

        assertThat(signal.conviction()).isEqualTo(1);
    }

    @Test
    void sellingPressureAboveTheThresholdSells() {
        apply(1, EventType.ADD, 3, BookSide.ASK, 2785900, 80);

        assertThat(signal.conviction()).isEqualTo(-1);
    }

    @Test
    void pressureBelowTheThresholdIsIgnored() {
        apply(1, EventType.ADD, 3, BookSide.BID, 2785500, 30);

        assertThat(signal.conviction()).isZero();
    }

    @Test
    void pressureExactlyAtTheThresholdIsIgnored() {
        apply(1, EventType.ADD, 3, BookSide.BID, 2785500, 50);

        assertThat(signal.conviction()).isZero();
    }

    @Test
    void opinionFadesWhenThePressureLeavesTheWindow() {
        apply(1, EventType.ADD, 3, BookSide.BID, 2785500, 80);
        apply(12, EventType.ADD, 4, BookSide.BID, 2785400, 10);

        assertThat(signal.conviction()).isZero();
    }

    @Test
    void definitionDescribesItsSettings() {
        OfiSignalDefinition definition = new OfiSignalDefinition();

        assertThat(definition.id()).isEqualTo("ofi");
        assertThat(definition.parameters())
                .extracting(ParameterSpec::key)
                .containsExactly("windowSeconds", "threshold");
    }

    private void apply(
            long second, EventType type, long orderId, BookSide side, long price, long quantity) {
        MarketEvent event =
                new MarketEvent(second * SECOND, "AAPL", type, orderId, side, price, quantity);
        state.apply(event);
        signal.update(event, state);
    }
}
