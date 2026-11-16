package org.example.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.example.execution.Fill;
import org.example.execution.Liquidity;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.OrderBook;
import org.example.order.Side;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link MarkoutTracker}, with horizons of 1 and 10 seconds. Times are in seconds,
 * prices in 1/10000 dollars.
 */
class MarkoutTrackerTest {

    private static final long SECOND = 1_000_000_000L;

    private final MarkoutTracker tracker =
            new MarkoutTracker(List.of(Duration.ofSeconds(1), Duration.ofSeconds(10)));

    @Test
    void buyIsGoodWhenTheMidGoesUp() {
        // maker buy at the bid 10.00; one second later the book is 10.01 / 10.03, mid 10.02
        tracker.add(fill(Side.BUY, 100, "10.00", Liquidity.MAKER), 0);
        tracker.evaluateUntil(SECOND, book(100100, 100300));

        Markout oneSecond = find(1000, Liquidity.MAKER);
        assertThat(oneSecond.fills()).isEqualTo(1);
        assertThat(oneSecond.shares()).isEqualTo(100);
        assertThat(oneSecond.averageCents()).isCloseTo(2.0, within(1e-9));
    }

    @Test
    void sellIsGoodWhenTheMidGoesDown() {
        // taker sell at 10.00; later mid 9.99: we sold one cent above it
        tracker.add(fill(Side.SELL, 100, "10.00", Liquidity.TAKER), 0);
        tracker.evaluateUntil(SECOND, book(99800, 100000));

        assertThat(find(1000, Liquidity.TAKER).averageCents()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void averageIsWeightedByShares() {
        // 100 shares +2 cents and 300 shares -2 cents: (200 - 600) / 400 = -1 cent
        tracker.add(fill(Side.BUY, 100, "10.00", Liquidity.MAKER), 0);
        tracker.add(fill(Side.SELL, 300, "10.00", Liquidity.MAKER), 0);
        tracker.evaluateUntil(SECOND, book(100100, 100300));

        Markout oneSecond = find(1000, Liquidity.MAKER);
        assertThat(oneSecond.fills()).isEqualTo(2);
        assertThat(oneSecond.averageCents()).isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void fillWaitsUntilItsHorizonHasPassed() {
        tracker.add(fill(Side.BUY, 100, "10.00", Liquidity.MAKER), 0);
        tracker.evaluateUntil(SECOND - 1, book(100100, 100300));

        assertThat(find(1000, Liquidity.MAKER).fills()).isZero();

        // at one second the 1 second markout is measured, the 10 second one still waits
        tracker.evaluateUntil(SECOND, book(100100, 100300));
        assertThat(find(1000, Liquidity.MAKER).fills()).isEqualTo(1);
        assertThat(find(10_000, Liquidity.MAKER).fills()).isZero();

        // ten seconds later the mid is 9.99: the buy at 10.00 is one cent behind
        tracker.evaluateUntil(10 * SECOND, book(99800, 100000));
        assertThat(find(10_000, Liquidity.MAKER).averageCents()).isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void oneSidedBookIsSkipped() {
        OrderBook onlyBids = new OrderBook("AAPL");
        onlyBids.apply(new MarketEvent(0, "AAPL", EventType.ADD, 1, BookSide.BID, 100000, 100));
        tracker.add(fill(Side.BUY, 100, "10.00", Liquidity.MAKER), 0);
        tracker.evaluateUntil(SECOND, onlyBids);

        assertThat(find(1000, Liquidity.MAKER).fills()).isZero();
        assertThat(find(1000, Liquidity.MAKER).averageCents()).isZero();
    }

    @Test
    void summaryHasEveryHorizonAndLiquidity() {
        assertThat(tracker.summary()).extracting(Markout::horizonMillis)
                .containsExactly(1000L, 1000L, 10_000L, 10_000L);
    }

    @Test
    void rejectsZeroHorizon() {
        assertThatThrownBy(() -> new MarkoutTracker(List.of(Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Markout find(long horizonMillis, Liquidity liquidity) {
        return tracker.summary().stream()
                .filter(m -> m.horizonMillis() == horizonMillis && m.liquidity() == liquidity)
                .findFirst().orElseThrow();
    }

    private static Fill fill(Side side, int quantity, String price, Liquidity liquidity) {
        return new Fill(1, "AAPL", side, quantity, new BigDecimal(price), BigDecimal.ZERO,
                liquidity, LocalDateTime.of(2025, 12, 9, 10, 0));
    }

    private static OrderBook book(long bid, long ask) {
        OrderBook book = new OrderBook("AAPL");
        book.apply(new MarketEvent(0, "AAPL", EventType.ADD, 1, BookSide.BID, bid, 100));
        book.apply(new MarketEvent(0, "AAPL", EventType.ADD, 2, BookSide.ASK, ask, 100));
        return book;
    }
}
