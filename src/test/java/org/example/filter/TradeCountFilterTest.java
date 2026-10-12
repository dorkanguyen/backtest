package org.example.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.state.SymbolState;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TradeCountFilter} with a 10 second window and at least 3 trades. Every test
 * starts from a book with an ask of 278.59 x 100. Times are in seconds.
 */
class TradeCountFilterTest {

    private static final long SECOND = 1_000_000_000L;

    private final SymbolState state = new SymbolState("AAPL", Duration.ofSeconds(10));
    private final TradingFilter filter = new TradeCountFilterDefinition()
            .create("AAPL", Map.of("windowSeconds", 10.0, "minTrades", 3.0));

    TradeCountFilterTest() {
        apply(0, EventType.ADD, BookSide.ASK, 100);
    }

    @Test
    void quietMarketStopsTrading() {
        trade(1);
        trade(2);

        assertThat(filter.allowsTrading()).isFalse();
    }

    @Test
    void busyMarketAllowsTrading() {
        trade(1);
        trade(2);
        trade(3);

        assertThat(filter.allowsTrading()).isTrue();
    }

    @Test
    void marketGetsQuietWhenTradesLeaveTheWindow() {
        trade(1);
        trade(2);
        trade(3);
        apply(12, EventType.ADD, BookSide.BID, 10);

        assertThat(filter.allowsTrading()).isFalse();
    }

    @Test
    void zeroMinimumAlwaysAllowsTrading() {
        TradingFilter noFilter = new TradeCountFilterDefinition()
                .create("AAPL", Map.of("minTrades", 0.0));

        assertThat(noFilter.allowsTrading()).isTrue();
    }

    private void trade(long second) {
        apply(second, EventType.EXECUTE, BookSide.ASK, 1);
    }

    private void apply(long second, EventType type, BookSide side, long quantity) {
        long orderId = side == BookSide.ASK ? 1 : 2;
        MarketEvent event =
                new MarketEvent(second * SECOND, "AAPL", type, orderId, side, 2785900, quantity);
        state.apply(event);
        filter.update(event, state);
    }
}
