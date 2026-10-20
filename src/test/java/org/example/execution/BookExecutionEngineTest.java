package org.example.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.OrderBook;
import org.example.order.Order;
import org.example.order.Side;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BookExecutionEngine} with a commission of 0.005 dollars per share, at least 1
 * dollar.
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
class BookExecutionEngineTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2025, 12, 9, 10, 0);

    private final BookExecutionEngine engine = new BookExecutionEngine(
            new PerShareCommission(new BigDecimal("0.005"), new BigDecimal("1.00")));
    private final OrderBook book = new OrderBook("AAPL");

    BookExecutionEngineTest() {
        add(1, BookSide.ASK, 2786100, 100);
        add(2, BookSide.ASK, 2786000, 200);
        add(3, BookSide.ASK, 2785900, 300);
        add(4, BookSide.BID, 2785500, 100);
        add(5, BookSide.BID, 2785400, 200);
        add(6, BookSide.BID, 2785300, 900);
    }

    @Test
    void smallBuyTakesTheBestAsk() {
        Fill fill = execute(Side.BUY, 100).orElseThrow();

        assertThat(fill.quantity()).isEqualTo(100);
        assertThat(fill.price()).isEqualByComparingTo("278.59");
        assertThat(fill.commission()).isEqualByComparingTo("1.00");
        assertThat(fill.liquidity()).isEqualTo(Liquidity.TAKER);
        assertThat(fill.time()).isEqualTo(TIME);
    }

    @Test
    void bigBuyWalksUpTheAsks() {
        // 300 x 278.59 + 100 x 278.60 = 111437 dollars for 400 shares
        Fill fill = execute(Side.BUY, 400).orElseThrow();

        assertThat(fill.price()).isEqualByComparingTo("278.5925");
        assertThat(fill.commission()).isEqualByComparingTo("2.00");
    }

    @Test
    void bigSellWalksDownTheBids() {
        // 100 x 278.55 + 200 x 278.54 + 100 x 278.53 = 111416 dollars for 400 shares
        Fill fill = execute(Side.SELL, 400).orElseThrow();

        assertThat(fill.side()).isEqualTo(Side.SELL);
        assertThat(fill.price()).isEqualByComparingTo("278.54");
    }

    @Test
    void orderBiggerThanTheBookIsFilledPartly() {
        Fill fill = execute(Side.BUY, 700).orElseThrow();

        assertThat(fill.quantity()).isEqualTo(600);
    }

    @Test
    void emptySideFillsNothing() {
        OrderBook onlyBids = new OrderBook("AAPL");
        onlyBids.apply(new MarketEvent(0, "AAPL", EventType.ADD, 1, BookSide.BID, 2785500, 100));

        assertThat(engine.execute(Order.market(1, "AAPL", Side.BUY, 10), onlyBids, TIME))
                .isEmpty();
    }

    @Test
    void limitOrderIsRejected() {
        Order limit = Order.limit(1, "AAPL", Side.BUY, 10, 2785500);

        assertThatThrownBy(() -> engine.execute(limit, book, TIME))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deepBookIsReadUntilTheOrderIsFilled() {
        OrderBook deep = new OrderBook("AAPL");
        for (int i = 0; i < 20; i++) {
            deep.apply(new MarketEvent(0, "AAPL", EventType.ADD, i, BookSide.ASK,
                    2785900 + i * 100L, 10));
        }

        Fill fill = engine.execute(Order.market(1, "AAPL", Side.BUY, 200), deep, TIME)
                .orElseThrow();

        assertThat(fill.quantity()).isEqualTo(200);
    }

    private Optional<Fill> execute(Side side, int quantity) {
        return engine.execute(Order.market(1, "AAPL", side, quantity), book, TIME);
    }

    private void add(long orderId, BookSide side, long price, long quantity) {
        book.apply(new MarketEvent(0, "AAPL", EventType.ADD, orderId, side, price, quantity));
    }
}
