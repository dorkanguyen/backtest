package org.example.fairprice;

import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.OrderBook;

/** Order books for the fair price tests. Prices are in 1/10000 dollars: 278.55 is 2785500. */
final class ExampleBooks {

    private ExampleBooks() {
    }

    /**
     * Returns a book where the best level has more sellers, but the first three levels together
     * have more buyers.
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
    static OrderBook threeLevels() {
        OrderBook book = new OrderBook("AAPL");
        add(book, 1, BookSide.ASK, 2786100, 100);
        add(book, 2, BookSide.ASK, 2786000, 200);
        add(book, 3, BookSide.ASK, 2785900, 300);
        add(book, 4, BookSide.BID, 2785500, 100);
        add(book, 5, BookSide.BID, 2785400, 200);
        add(book, 6, BookSide.BID, 2785300, 900);
        return book;
    }

    static void add(OrderBook book, long orderId, BookSide side, long price, long quantity) {
        book.apply(new MarketEvent(0, "AAPL", EventType.ADD, orderId, side, price, quantity));
    }
}
