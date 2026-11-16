package org.example.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.OrderBook;
import org.example.order.Order;
import org.example.order.Side;
import org.junit.jupiter.api.Test;

/**
 * Tests for the guard of {@link JoinBestQuote}: 300 shares, 0.2 cents.
 *
 * <p>The leaning book: ask 10.01 x 900, bid 10.00 x 100 and 9.99 x 500. Buying 300 costs 10.01,
 * selling 300 brings (100 x 10.00 + 200 x 9.99) / 300 = 9.99333, so the impact price is 10.00167.
 * The mid is 10.005: the book leans 0.33 cents down, against a buy and in favour of a sell.
 */
class JoinBestQuoteGuardTest {

    private static final long BID = 100000;
    private static final long ASK = 100100;

    private final OrderBook book = new OrderBook("AAPL");

    @Test
    void buyIsNotPlacedWhenTheBookLeansAgainstIt() {
        leaningBook();

        assertThat(decide(guarded(0.2), 0, 100)).isEmpty();
    }

    @Test
    void sellIsPlacedWhenTheBookLeansItsWay() {
        leaningBook();

        assertThat(decide(guarded(0.2), 100, 0)).containsExactly(place(1, Side.SELL, 100, ASK));
    }

    @Test
    void smallLeanDoesNotStopTheBuy() {
        leaningBook();

        assertThat(decide(guarded(0.5), 0, 100)).containsExactly(place(1, Side.BUY, 100, BID));
    }

    @Test
    void withoutGuardTheBuyIsPlaced() {
        leaningBook();
        ExecutionAlgo noGuard = new JoinBestQuoteDefinition()
                .create(Map.of("toleranceShares", 10.0, "guardShares", 0.0));

        assertThat(decide(noGuard, 0, 100)).containsExactly(place(1, Side.BUY, 100, BID));
    }

    @Test
    void waitingBuyIsCancelledWhenTheBookTurnsAgainstIt() {
        // balanced at first: the impact price is the mid, so the buy is placed
        book.apply(event(EventType.ADD, 1, BookSide.BID, BID, 900));
        book.apply(event(EventType.ADD, 2, BookSide.ASK, ASK, 900));
        ExecutionAlgo algo = guarded(0.2);
        assertThat(decide(algo, 0, 100)).containsExactly(place(1, Side.BUY, 100, BID));

        // most buyers at 10.00 leave: the book now leans 0.33 cents down
        book.apply(event(EventType.CANCEL, 1, BookSide.BID, BID, 800));
        book.apply(event(EventType.ADD, 3, BookSide.BID, 99900, 500));

        assertThat(decide(algo, 0, 100)).containsExactly(new Instruction.Cancel(1));
    }

    @Test
    void buyIsPlacedAgainWhenTheBookIsBalancedAgain() {
        leaningBook();
        ExecutionAlgo algo = guarded(0.2);
        decide(algo, 0, 100);

        // new buyers arrive at 10.00: 800 shares on both sides, the impact price is the mid
        book.apply(event(EventType.ADD, 4, BookSide.BID, BID, 800));

        assertThat(decide(algo, 0, 100)).containsExactly(place(1, Side.BUY, 100, BID));
    }

    @Test
    void negativeGuardIsAnError() {
        assertThatThrownBy(() -> new JoinBestQuote(10, 300, -0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JoinBestQuote(10, -1, 0.2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void leaningBook() {
        book.apply(event(EventType.ADD, 1, BookSide.BID, BID, 100));
        book.apply(event(EventType.ADD, 3, BookSide.BID, 99900, 500));
        book.apply(event(EventType.ADD, 2, BookSide.ASK, ASK, 900));
    }

    private static ExecutionAlgo guarded(double cents) {
        return new JoinBestQuoteDefinition().create(Map.of(
                "toleranceShares", 10.0, "guardShares", 300.0, "guardCents", cents));
    }

    private List<Instruction> decide(ExecutionAlgo algo, int current, int target) {
        return algo.decide(0, "AAPL", current, target, book);
    }

    private static Instruction place(long id, Side side, int quantity, long price) {
        return new Instruction.Place(Order.limit(id, "AAPL", side, quantity, price));
    }

    private static MarketEvent event(
            EventType type, long orderId, BookSide side, long price, long quantity) {
        return new MarketEvent(0, "AAPL", type, orderId, side, price, quantity);
    }
}
