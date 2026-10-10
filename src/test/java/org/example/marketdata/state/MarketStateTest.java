package org.example.marketdata.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Set;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;
import org.junit.jupiter.api.Test;

/** Tests for {@link MarketState} following AAPL and NVDA. Times are in seconds for readability. */
class MarketStateTest {

    private static final long SECOND = 1_000_000_000L;

    private final MarketState market =
            new MarketState(Set.of("NVDA", "AAPL"), Duration.ofSeconds(10));

    @Test
    void newMarketStateIsEmpty() {
        assertThat(market.currentTime()).isEmpty();
        assertThat(market.symbol("AAPL").book().bestBid()).isEmpty();
        assertThat(market.symbol("AAPL").trades().totalVolume()).isZero();
    }

    @Test
    void symbolsAreInAlphabeticalOrder() {
        assertThat(market.symbols()).containsExactly("AAPL", "NVDA");
    }

    @Test
    void eventGoesOnlyToItsOwnSymbol() {
        market.apply(event(1, "NVDA", EventType.ADD, 1, BookSide.BID, 1800000, 100));

        assertThat(market.symbol("NVDA").book().bestBid()).isPresent();
        assertThat(market.symbol("AAPL").book().bestBid()).isEmpty();
    }

    @Test
    void eventUpdatesBothBookAndTrades() {
        market.apply(event(1, "AAPL", EventType.ADD, 1, BookSide.ASK, 2785900, 100));
        market.apply(event(2, "AAPL", EventType.EXECUTE, 1, BookSide.ASK, 2785900, 30));

        SymbolState aapl = market.symbol("AAPL");
        assertThat(aapl.book().bestAsk().orElseThrow().quantity()).isEqualTo(70);
        assertThat(aapl.trades().buyVolume()).isEqualTo(30);
    }

    @Test
    void clockIsTheTimeOfTheLastEvent() {
        market.apply(event(1, "AAPL", EventType.ADD, 1, BookSide.BID, 2785500, 100));
        market.apply(event(3, "NVDA", EventType.ADD, 2, BookSide.BID, 1800000, 100));

        assertThat(market.currentTime()).hasValue(3 * SECOND);
    }

    @Test
    void eventsWithTheSameTimeAreAllowed() {
        market.apply(event(1, "AAPL", EventType.ADD, 1, BookSide.BID, 2785500, 100));
        market.apply(event(1, "NVDA", EventType.ADD, 2, BookSide.BID, 1800000, 100));

        assertThat(market.currentTime()).hasValue(SECOND);
    }

    @Test
    void olderEventIsRejected() {
        market.apply(event(5, "AAPL", EventType.ADD, 1, BookSide.BID, 2785500, 100));
        MarketEvent older = event(4, "NVDA", EventType.ADD, 2, BookSide.BID, 1800000, 100);

        assertThatThrownBy(() -> market.apply(older))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownSymbolIsRejected() {
        MarketEvent msft = event(1, "MSFT", EventType.ADD, 1, BookSide.BID, 4800000, 100);

        assertThatThrownBy(() -> market.apply(msft))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> market.symbol("MSFT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MarketEvent event(long seconds, String symbol, EventType type, long orderId,
            BookSide side, long price, long quantity) {
        return new MarketEvent(seconds * SECOND, symbol, type, orderId, side, price, quantity);
    }
}
