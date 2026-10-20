package org.example.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.OrderBook;
import org.example.order.Order;
import org.example.order.Side;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link QueueSimulator}, with the apple market example: we want to buy 2 apples at
 * 9.00 (90000) and Anna (3) and Béla (2) are already waiting at that price.
 */
class QueueSimulatorTest {

    private static final long ANNA = 1;
    private static final long BELA = 2;
    private static final long CILI = 3;
    private static final long DANI = 4;
    private static final long SELLER = 10;
    private static final long PRICE = 90000;

    private final OrderBook book = new OrderBook("AAPL");

    QueueSimulatorTest() {
        book.apply(event(EventType.ADD, ANNA, BookSide.BID, PRICE, 3));
        book.apply(event(EventType.ADD, BELA, BookSide.BID, PRICE, 2));
        book.apply(event(EventType.ADD, SELLER, BookSide.ASK, 100000, 5));
    }

    @Test
    void joinsBehindTheOrdersAlreadyWaiting() {
        QueueSimulator queue = buyTwo();

        assertThat(queue.sharesAhead()).isEqualTo(5);
        assertThat(queue.remaining()).isEqualTo(2);
        assertThat(queue.isFilled()).isFalse();
    }

    @Test
    void executionOfAnOrderAheadMovesUsForward() {
        QueueSimulator queue = buyTwo();

        assertThat(queue.apply(event(EventType.EXECUTE, ANNA, BookSide.BID, PRICE, 2))).isZero();

        assertThat(queue.sharesAhead()).isEqualTo(3);
    }

    @Test
    void cancelAndDeleteOfOrdersAheadMoveUsForward() {
        QueueSimulator queue = buyTwo();

        queue.apply(event(EventType.CANCEL, ANNA, BookSide.BID, PRICE, 1));
        assertThat(queue.sharesAhead()).isEqualTo(4);

        queue.apply(event(EventType.DELETE, BELA, BookSide.BID, PRICE, 2));
        assertThat(queue.sharesAhead()).isEqualTo(2);
    }

    @Test
    void ordersBehindUsDoNotMatter() {
        QueueSimulator queue = buyTwo();

        queue.apply(event(EventType.ADD, CILI, BookSide.BID, PRICE, 4));
        queue.apply(event(EventType.DELETE, CILI, BookSide.BID, PRICE, 4));

        assertThat(queue.sharesAhead()).isEqualTo(5);
        assertThat(queue.remaining()).isEqualTo(2);
    }

    @Test
    void fillsOnlyWhenNobodyIsAheadAndAnotherSellerComes() {
        QueueSimulator queue = buyTwo();
        queue.apply(event(EventType.ADD, CILI, BookSide.BID, PRICE, 4));

        queue.apply(event(EventType.EXECUTE, ANNA, BookSide.BID, PRICE, 3));
        // Béla's order is the last one ahead: it trades with him, not yet with us (cautious rule).
        assertThat(queue.apply(event(EventType.EXECUTE, BELA, BookSide.BID, PRICE, 2))).isZero();
        assertThat(queue.sharesAhead()).isZero();

        // The next seller would have traded with us first.
        long filled = queue.apply(event(EventType.EXECUTE, CILI, BookSide.BID, PRICE, 1));
        assertThat(filled).isEqualTo(1);
        assertThat(queue.remaining()).isEqualTo(1);

        filled = queue.apply(event(EventType.EXECUTE, CILI, BookSide.BID, PRICE, 3));
        assertThat(filled).isEqualTo(1);
        assertThat(queue.isFilled()).isTrue();
        assertThat(queue.apply(event(EventType.EXECUTE, CILI, BookSide.BID, PRICE, 1))).isZero();
    }

    @Test
    void executionBehindUsWhileOthersAreAheadDoesNotFill() {
        QueueSimulator queue = buyTwo();
        queue.apply(event(EventType.ADD, CILI, BookSide.BID, PRICE, 4));

        assertThat(queue.apply(event(EventType.EXECUTE, CILI, BookSide.BID, PRICE, 1))).isZero();
    }

    @Test
    void sellingBelowOurBuyPriceFillsUsEvenWithOrdersAhead() {
        QueueSimulator queue = buyTwo();
        queue.apply(event(EventType.ADD, DANI, BookSide.BID, 89000, 10));

        // Someone sold at 8.90: on the way down the price went through our 9.00.
        long filled = queue.apply(event(EventType.EXECUTE, DANI, BookSide.BID, 89000, 5));
        assertThat(filled).isEqualTo(2);
        assertThat(queue.isFilled()).isTrue();
    }

    @Test
    void executionOnTheOtherSideOfTheBookDoesNotFill() {
        QueueSimulator queue = buyTwo();

        assertThat(queue.apply(event(EventType.EXECUTE, SELLER, BookSide.ASK, 100000, 5))).isZero();
    }

    @Test
    void hiddenTradeDoesNotFill() {
        QueueSimulator queue = buyTwo();

        assertThat(queue.apply(event(EventType.TRADE, 0, BookSide.BID, 89000, 100))).isZero();
    }

    @Test
    void sellOrderWorksTheSameWayOnTheAskSide() {
        QueueSimulator queue = new QueueSimulator(
                Order.limit(1, "AAPL", Side.SELL, 2, 100000), book);
        assertThat(queue.sharesAhead()).isEqualTo(5);

        queue.apply(event(EventType.ADD, 11, BookSide.ASK, 101000, 3));
        // Someone bought at 10.10, above our 10.00 sell price.
        assertThat(queue.apply(event(EventType.EXECUTE, 11, BookSide.ASK, 101000, 3))).isEqualTo(2);
    }

    @Test
    void limitOrderThatWouldTradeAtOnceIsRejected() {
        Order buyAtTheAsk = Order.limit(1, "AAPL", Side.BUY, 2, 100000);

        assertThatThrownBy(() -> new QueueSimulator(buyAtTheAsk, book))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void marketOrderIsRejected() {
        Order market = Order.market(1, "AAPL", Side.BUY, 2);

        assertThatThrownBy(() -> new QueueSimulator(market, book))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventOfAnotherSymbolIsRejected() {
        QueueSimulator queue = buyTwo();
        MarketEvent msft = new MarketEvent(0, "MSFT", EventType.ADD, 1, BookSide.BID, 1, 1);

        assertThatThrownBy(() -> queue.apply(msft)).isInstanceOf(IllegalArgumentException.class);
    }

    private QueueSimulator buyTwo() {
        return new QueueSimulator(Order.limit(1, "AAPL", Side.BUY, 2, PRICE), book);
    }

    private static MarketEvent event(
            EventType type, long orderId, BookSide side, long price, long quantity) {
        return new MarketEvent(0, "AAPL", type, orderId, side, price, quantity);
    }
}
