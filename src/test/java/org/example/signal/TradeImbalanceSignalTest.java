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
 * Tests for {@link TradeImbalanceSignal} with a 10 second window. Every test starts from a book
 * of bid 278.55 x 1000 and ask 278.59 x 1000. A trade on the ask was a hurried buyer, a trade on
 * the bid a hurried seller. Times are in seconds.
 */
class TradeImbalanceSignalTest {

    private static final long SECOND = 1_000_000_000L;

    private final SymbolState state = new SymbolState("AAPL", Duration.ofSeconds(10));

    @Test
    void noTradeMeansNoOpinion() {
        Signal signal = create(100);
        startBook(signal);

        assertThat(signal.conviction()).isZero();
    }

    @Test
    void moreHurriedBuyersBuy() {
        // buyers 300, sellers 100: (300 - 100) / 400 = +0.5
        Signal signal = create(100);
        startBook(signal);
        apply(signal, 1, EventType.EXECUTE, 2, BookSide.ASK, 2785900, 300);
        apply(signal, 2, EventType.EXECUTE, 1, BookSide.BID, 2785500, 100);

        assertThat(signal.conviction()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void moreHurriedSellersSell() {
        // buyers 100, sellers 300: -0.5
        Signal signal = create(100);
        startBook(signal);
        apply(signal, 1, EventType.EXECUTE, 2, BookSide.ASK, 2785900, 100);
        apply(signal, 2, EventType.EXECUTE, 1, BookSide.BID, 2785500, 300);

        assertThat(signal.conviction()).isCloseTo(-0.5, within(1e-9));
    }

    @Test
    void tooFewSharesGiveNoOpinion() {
        // only 1 share was bought: below the 100 share minimum
        Signal signal = create(100);
        startBook(signal);
        apply(signal, 1, EventType.EXECUTE, 2, BookSide.ASK, 2785900, 1);

        assertThat(signal.conviction()).isZero();
    }

    @Test
    void zeroMinimumLetsOneShareGiveFullOpinion() {
        Signal signal = create(0);
        startBook(signal);
        apply(signal, 1, EventType.EXECUTE, 2, BookSide.ASK, 2785900, 1);

        assertThat(signal.conviction()).isEqualTo(1);
    }

    @Test
    void hiddenTradesDoNotCount() {
        // a hidden trade of 500 shares has no known side, so only the 100 bought shares count
        Signal signal = create(100);
        startBook(signal);
        apply(signal, 1, EventType.TRADE, 0, BookSide.BID, 2785700, 500);
        apply(signal, 2, EventType.EXECUTE, 2, BookSide.ASK, 2785900, 100);

        assertThat(signal.conviction()).isEqualTo(1);
    }

    @Test
    void opinionFadesWhenTheTradesLeaveTheWindow() {
        Signal signal = create(100);
        startBook(signal);
        apply(signal, 1, EventType.EXECUTE, 2, BookSide.ASK, 2785900, 300);
        apply(signal, 12, EventType.ADD, 3, BookSide.BID, 2785400, 10);

        assertThat(signal.conviction()).isZero();
    }

    private Signal create(double minShares) {
        return new TradeImbalanceSignalDefinition().create(
                "AAPL", Map.of("windowSeconds", 10.0, "minShares", minShares));
    }

    private void startBook(Signal signal) {
        apply(signal, 0, EventType.ADD, 1, BookSide.BID, 2785500, 1000);
        apply(signal, 0, EventType.ADD, 2, BookSide.ASK, 2785900, 1000);
    }

    private void apply(Signal signal, long second, EventType type, long orderId, BookSide side,
            long price, long quantity) {
        MarketEvent event =
                new MarketEvent(second * SECOND, "AAPL", type, orderId, side, price, quantity);
        state.apply(event);
        signal.update(event, state);
    }
}
