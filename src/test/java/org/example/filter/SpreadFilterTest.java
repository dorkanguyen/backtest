package org.example.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.state.SymbolState;
import org.junit.jupiter.api.Test;

/** Tests for {@link SpreadFilter} with a maximum spread of 2 cents. */
class SpreadFilterTest {

    private final SymbolState state = new SymbolState("AAPL", Duration.ofSeconds(10));
    private final TradingFilter filter =
            new SpreadFilterDefinition().create("AAPL", Map.of("maxSpreadCents", 2.0));

    @Test
    void narrowSpreadAllowsTrading() {
        // 278.55 / 278.56: 1 cent
        apply(1, BookSide.BID, 2785500);
        apply(2, BookSide.ASK, 2785600);

        assertThat(filter.allowsTrading()).isTrue();
    }

    @Test
    void spreadExactlyAtTheLimitAllowsTrading() {
        apply(1, BookSide.BID, 2785500);
        apply(2, BookSide.ASK, 2785700);

        assertThat(filter.allowsTrading()).isTrue();
    }

    @Test
    void wideSpreadStopsTrading() {
        // 278.55 / 278.59: 4 cents
        apply(1, BookSide.BID, 2785500);
        apply(2, BookSide.ASK, 2785900);

        assertThat(filter.allowsTrading()).isFalse();
    }

    @Test
    void oneSidedBookStopsTrading() {
        apply(1, BookSide.BID, 2785500);

        assertThat(filter.allowsTrading()).isFalse();
    }

    private void apply(long orderId, BookSide side, long price) {
        MarketEvent event = new MarketEvent(0, "AAPL", EventType.ADD, orderId, side, price, 100);
        state.apply(event);
        filter.update(event, state);
    }
}
