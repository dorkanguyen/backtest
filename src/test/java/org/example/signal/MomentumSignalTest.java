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
 * Tests for {@link MomentumSignal} with a 60 second window. Every test starts at second 0 from a
 * book of bid 10.00 and ask 10.02, so the mid price is 10.01. Times are in seconds.
 */
class MomentumSignalTest {

    private static final long SECOND = 1_000_000_000L;

    private final SymbolState state = new SymbolState("AAPL", Duration.ofSeconds(10));

    @Test
    void riseBuys() {
        // mid 10.01 -> 10.04 (ask 10.08): 3 cents up, with 5 cents for the full opinion
        Signal signal = create(5);
        startBook(signal);
        moveAskTo(signal, 30, 100800);
        quietEvent(signal, 61);

        assertThat(signal.conviction()).isCloseTo(0.6, within(1e-9));
    }

    @Test
    void fallSells() {
        // mid 10.01 -> 9.98 (bid 9.94): 3 cents down
        Signal signal = create(5);
        startBook(signal);
        apply(signal, 30, EventType.DELETE, 1, BookSide.BID, 100000, 100);
        apply(signal, 30, EventType.ADD, 4, BookSide.BID, 99400, 100);
        quietEvent(signal, 61);

        assertThat(signal.conviction()).isCloseTo(-0.6, within(1e-9));
    }

    @Test
    void opinionStopsAtTheFullMove() {
        // 3 cents up, but 1 cent is already the full opinion
        Signal signal = create(1);
        startBook(signal);
        moveAskTo(signal, 30, 100800);
        quietEvent(signal, 61);

        assertThat(signal.conviction()).isEqualTo(1);
    }

    @Test
    void noOpinionBeforeWholeWindowIsKnown() {
        // at second 30 only 30 seconds of history are known
        Signal signal = create(5);
        startBook(signal);
        moveAskTo(signal, 30, 100800);

        assertThat(signal.conviction()).isZero();
    }

    @Test
    void oldMoveLeavesTheWindow() {
        // at second 95 the window starts at 35, after the move at 30: no move since then
        Signal signal = create(5);
        startBook(signal);
        moveAskTo(signal, 30, 100800);
        quietEvent(signal, 95);

        assertThat(signal.conviction()).isZero();
    }

    @Test
    void oneSidedBookGivesNoOpinion() {
        Signal signal = create(5);
        apply(signal, 0, EventType.ADD, 1, BookSide.BID, 100000, 100);
        apply(signal, 61, EventType.ADD, 5, BookSide.BID, 99000, 100);

        assertThat(signal.conviction()).isZero();
    }

    private Signal create(double fullMoveCents) {
        return new MomentumSignalDefinition().create(
                "AAPL", Map.of("windowSeconds", 60.0, "fullMoveCents", fullMoveCents));
    }

    private void startBook(Signal signal) {
        apply(signal, 0, EventType.ADD, 1, BookSide.BID, 100000, 100);
        apply(signal, 0, EventType.ADD, 2, BookSide.ASK, 100200, 100);
    }

    private void moveAskTo(Signal signal, long second, long price) {
        apply(signal, second, EventType.DELETE, 2, BookSide.ASK, 100200, 100);
        apply(signal, second, EventType.ADD, 3, BookSide.ASK, price, 100);
    }

    /** An order far below the best bid: it does not change the mid price. */
    private void quietEvent(Signal signal, long second) {
        apply(signal, second, EventType.ADD, 9, BookSide.BID, 99000, 100);
    }

    private void apply(Signal signal, long second, EventType type, long orderId, BookSide side,
            long price, long quantity) {
        MarketEvent event =
                new MarketEvent(second * SECOND, "AAPL", type, orderId, side, price, quantity);
        state.apply(event);
        signal.update(event, state);
    }
}
