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
 * Tests for {@link VwapSignal} with a 60 second window. Every test starts from a book of bid
 * 278.55 x 100 and ask 278.59 x 100, so the mid price is 278.57. Times are in seconds.
 */
class VwapSignalTest {

    private static final long SECOND = 1_000_000_000L;

    private final SymbolState state = new SymbolState("AAPL", Duration.ofSeconds(10));

    @Test
    void noTradeMeansNoOpinion() {
        Signal signal = create(5);
        startBook(signal);

        assertThat(signal.conviction()).isZero();
    }

    @Test
    void priceBelowTheVwapBuys() {
        // VWAP 278.59, mid 278.57: 2 cents below, with 5 cents for the full opinion
        Signal signal = create(5);
        startBook(signal);
        apply(signal, 1, EventType.EXECUTE, 2, BookSide.ASK, 2785900, 10);

        assertThat(signal.conviction()).isCloseTo(0.4, within(1e-9));
    }

    @Test
    void priceAboveTheVwapSells() {
        // VWAP 278.55, mid 278.57: 2 cents above
        Signal signal = create(5);
        startBook(signal);
        apply(signal, 1, EventType.EXECUTE, 1, BookSide.BID, 2785500, 10);

        assertThat(signal.conviction()).isCloseTo(-0.4, within(1e-9));
    }

    @Test
    void opinionStopsAtTheFullDistance() {
        // 2 cents below, but 1 cent is already the full opinion
        Signal signal = create(1);
        startBook(signal);
        apply(signal, 1, EventType.EXECUTE, 2, BookSide.ASK, 2785900, 10);

        assertThat(signal.conviction()).isEqualTo(1);
    }

    @Test
    void opinionFadesWhenTheTradesLeaveTheWindow() {
        Signal signal = create(5);
        startBook(signal);
        apply(signal, 1, EventType.EXECUTE, 2, BookSide.ASK, 2785900, 10);
        apply(signal, 62, EventType.ADD, 3, BookSide.BID, 2785400, 10);

        assertThat(signal.conviction()).isZero();
    }

    private Signal create(double fullDistanceCents) {
        return new VwapSignalDefinition().create(
                "AAPL", Map.of("windowSeconds", 60.0, "fullDistanceCents", fullDistanceCents));
    }

    private void startBook(Signal signal) {
        apply(signal, 0, EventType.ADD, 1, BookSide.BID, 2785500, 100);
        apply(signal, 0, EventType.ADD, 2, BookSide.ASK, 2785900, 100);
    }

    private void apply(Signal signal, long second, EventType type, long orderId, BookSide side,
            long price, long quantity) {
        MarketEvent event =
                new MarketEvent(second * SECOND, "AAPL", type, orderId, side, price, quantity);
        state.apply(event);
        signal.update(event, state);
    }
}
