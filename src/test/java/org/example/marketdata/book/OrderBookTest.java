package org.example.marketdata.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;
import org.junit.jupiter.api.Test;

/** Tests for {@link OrderBook}. Prices are in 1/10000 dollars: 278.55 is 2785500. */
class OrderBookTest {

    private final OrderBook book = new OrderBook("AAPL");

    @Test
    void newBookIsEmpty() {
        assertThat(book.bestBid()).isEmpty();
        assertThat(book.bestAsk()).isEmpty();
    }

    @Test
    void ordersAtTheSamePriceAddUp() {
        book.apply(event(EventType.ADD, 1, BookSide.BID, 2785500, 100));
        book.apply(event(EventType.ADD, 2, BookSide.BID, 2785500, 50));

        assertThat(book.bestBid()).contains(new PriceLevel(2785500, 150));
    }

    @Test
    void deletingAllOrdersEmptiesTheLevel() {
        book.apply(event(EventType.ADD, 1, BookSide.BID, 2785500, 100));
        book.apply(event(EventType.ADD, 2, BookSide.BID, 2785500, 50));

        book.apply(event(EventType.DELETE, 1, BookSide.BID, 2785500, 100));
        assertThat(book.bestBid()).contains(new PriceLevel(2785500, 50));

        book.apply(event(EventType.DELETE, 2, BookSide.BID, 2785500, 50));
        assertThat(book.bestBid()).isEmpty();
    }

    @Test
    void bestBidIsTheHighestAndBestAskTheLowestPrice() {
        book.apply(event(EventType.ADD, 1, BookSide.BID, 2785000, 10));
        book.apply(event(EventType.ADD, 2, BookSide.BID, 2785500, 20));
        book.apply(event(EventType.ADD, 3, BookSide.ASK, 2786000, 30));
        book.apply(event(EventType.ADD, 4, BookSide.ASK, 2785900, 40));

        assertThat(book.bestBid()).contains(new PriceLevel(2785500, 20));
        assertThat(book.bestAsk()).contains(new PriceLevel(2785900, 40));
    }

    @Test
    void levelsAreReturnedBestFirstUpToTheDepth() {
        book.apply(event(EventType.ADD, 1, BookSide.BID, 2785000, 10));
        book.apply(event(EventType.ADD, 2, BookSide.BID, 2785500, 20));
        book.apply(event(EventType.ADD, 3, BookSide.BID, 2784000, 30));

        assertThat(book.bids(2)).containsExactly(
                new PriceLevel(2785500, 20),
                new PriceLevel(2785000, 10));
        assertThat(book.bids(10)).hasSize(3);
    }

    @Test
    void executeAndCancelReduceTheOrder() {
        book.apply(event(EventType.ADD, 1, BookSide.ASK, 2785900, 100));

        book.apply(event(EventType.EXECUTE, 1, BookSide.ASK, 2785900, 30));
        assertThat(book.bestAsk()).contains(new PriceLevel(2785900, 70));

        book.apply(event(EventType.CANCEL, 1, BookSide.ASK, 2785900, 20));
        assertThat(book.bestAsk()).contains(new PriceLevel(2785900, 50));

        book.apply(event(EventType.EXECUTE, 1, BookSide.ASK, 2785900, 50));
        assertThat(book.bestAsk()).isEmpty();
    }

    @Test
    void executionAtAnotherPriceReducesTheOrderAtItsOwnPrice() {
        // An ITCH 'C' message can execute an order at a different price than the order's price.
        book.apply(event(EventType.ADD, 1, BookSide.BID, 2785500, 100));

        book.apply(event(EventType.EXECUTE, 1, BookSide.BID, 2785400, 40));

        assertThat(book.bestBid()).contains(new PriceLevel(2785500, 60));
    }

    @Test
    void hiddenTradeDoesNotChangeTheBook() {
        book.apply(event(EventType.ADD, 1, BookSide.BID, 2785500, 100));

        book.apply(event(EventType.TRADE, 0, BookSide.BID, 2785700, 500));

        assertThat(book.bestBid()).contains(new PriceLevel(2785500, 100));
        assertThat(book.bestAsk()).isEmpty();
    }

    @Test
    void eventOfUnknownOrderIsIgnored() {
        book.apply(event(EventType.ADD, 1, BookSide.BID, 2785500, 100));

        book.apply(event(EventType.DELETE, 99, BookSide.BID, 2785500, 100));

        assertThat(book.bestBid()).contains(new PriceLevel(2785500, 100));
    }

    @Test
    void eventOfAnotherSymbolIsRejected() {
        MarketEvent msft = new MarketEvent(0, "MSFT", EventType.ADD, 1, BookSide.BID, 1, 1);

        assertThatThrownBy(() -> book.apply(msft))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MarketEvent event(
            EventType type, long orderId, BookSide side, long price, long quantity) {
        return new MarketEvent(0, "AAPL", type, orderId, side, price, quantity);
    }
}
