package org.example.marketdata.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.OrderBook;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OrderFlowImbalance} with a 10 second window. Every test starts from a book of
 * bid 278.55 x 100 and ask 278.59 x 100. Times are in seconds for readability.
 */
class OrderFlowImbalanceTest {

    private static final long SECOND = 1_000_000_000L;

    private final OrderBook book = new OrderBook("AAPL");
    private final OrderFlowImbalance ofi = new OrderFlowImbalance("AAPL", Duration.ofSeconds(10));

    OrderFlowImbalanceTest() {
        apply(0, EventType.ADD, 1, BookSide.BID, 2785500, 100);
        apply(0, EventType.ADD, 2, BookSide.ASK, 2785900, 100);
    }

    @Test
    void buildingTheFirstQuotesCountsNothing() {
        assertThat(ofi.value()).isZero();
    }

    @Test
    void moreSharesOnTheBestBidIsBuyingPressure() {
        apply(1, EventType.ADD, 3, BookSide.BID, 2785500, 50);

        assertThat(ofi.value()).isEqualTo(50);
    }

    @Test
    void higherBestBidIsBuyingPressure() {
        apply(1, EventType.ADD, 3, BookSide.BID, 2785600, 30);

        assertThat(ofi.value()).isEqualTo(30);
    }

    @Test
    void moreSharesOnTheBestAskIsSellingPressure() {
        apply(1, EventType.ADD, 3, BookSide.ASK, 2785900, 50);

        assertThat(ofi.value()).isEqualTo(-50);
    }

    @Test
    void lowerBestAskIsSellingPressure() {
        apply(1, EventType.ADD, 3, BookSide.ASK, 2785800, 30);

        assertThat(ofi.value()).isEqualTo(-30);
    }

    @Test
    void sharesTakenFromTheBestAskIsBuyingPressure() {
        // a buyer takes 40 of the 100 shares on the ask: less selling pressure
        apply(1, EventType.EXECUTE, 2, BookSide.ASK, 2785900, 40);

        assertThat(ofi.value()).isEqualTo(40);
    }

    @Test
    void bidPriceFallingRemovesTheOldBid() {
        apply(1, EventType.ADD, 3, BookSide.BID, 2785400, 70);
        apply(2, EventType.DELETE, 1, BookSide.BID, 2785500, 100);

        // the old best bid of 100 shares is gone
        assertThat(ofi.value()).isEqualTo(-100);
    }

    @Test
    void oldChangesLeaveTheWindow() {
        apply(1, EventType.ADD, 3, BookSide.BID, 2785500, 50);
        apply(5, EventType.ADD, 4, BookSide.ASK, 2785900, 20);
        assertThat(ofi.value()).isEqualTo(30);

        apply(11, EventType.ADD, 5, BookSide.BID, 2780000, 10);
        assertThat(ofi.value()).isEqualTo(-20);

        apply(15, EventType.ADD, 6, BookSide.BID, 2780000, 10);
        assertThat(ofi.value()).isZero();
    }

    @Test
    void windowMustBePositive() {
        assertThatThrownBy(() -> new OrderFlowImbalance("AAPL", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventOfAnotherSymbolIsRejected() {
        MarketEvent msft = new MarketEvent(1, "MSFT", EventType.TRADE, 0, BookSide.BID, 1, 1);

        assertThatThrownBy(() -> ofi.apply(msft, book))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void apply(
            long seconds, EventType type, long orderId, BookSide side, long price, long quantity) {
        MarketEvent event =
                new MarketEvent(seconds * SECOND, "AAPL", type, orderId, side, price, quantity);
        book.apply(event);
        ofi.apply(event, book);
    }
}
