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
 * Tests for the impact price signal. The mid price is 278.57 and half the spread is 0.02.
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
class ImpactPriceSignalTest {

    private final SymbolState state = new SymbolState("AAPL", Duration.ofSeconds(10));

    @Test
    void quantityOfTheBestLevelsHasNoOpinion() {
        // 100 shares: buy at 278.59, sell at 278.55, the middle is the mid
        Signal signal = create(100);
        fillBook(signal);

        assertThat(signal.conviction()).isCloseTo(0, within(1e-9));
    }

    @Test
    void thinBidSideSells() {
        // 400 shares: buy average 278.5925, sell average 278.54, middle 278.56625
        // (278.56625 - 278.57) / 0.02 = -0.1875
        Signal signal = create(400);
        fillBook(signal);

        assertThat(signal.conviction()).isCloseTo(-0.1875, within(1e-9));
    }

    @Test
    void notEnoughSharesInTheBookHasNoOpinion() {
        // only 600 shares are asked
        Signal signal = create(601);
        fillBook(signal);

        assertThat(signal.conviction()).isZero();
    }

    private Signal create(double quantity) {
        return new ImpactPriceSignalDefinition().create("AAPL", Map.of("quantity", quantity));
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
