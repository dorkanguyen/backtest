package org.example.execution;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
 * Tests for {@link JoinBestQuote} with a tolerance of 10 shares. The book: bid 9.00 x 300
 * (order 1), ask 9.02 x 300 (order 2).
 */
class JoinBestQuoteTest {

    private static final long BID = 90000;
    private static final long ASK = 90200;

    private final OrderBook book = new OrderBook("AAPL");
    private final ExecutionAlgo algo =
            new JoinBestQuoteDefinition().create(Map.of("toleranceShares", 10.0));

    JoinBestQuoteTest() {
        book.apply(event(EventType.ADD, 1, BookSide.BID, BID, 300));
        book.apply(event(EventType.ADD, 2, BookSide.ASK, ASK, 300));
    }

    @Test
    void buyJoinsTheBestBid() {
        assertThat(decide(0, 100)).containsExactly(place(1, Side.BUY, 100, BID));
    }

    @Test
    void sellJoinsTheBestAsk() {
        assertThat(decide(100, 0)).containsExactly(place(1, Side.SELL, 100, ASK));
    }

    @Test
    void goodOrderIsLeftAlone() {
        decide(0, 100);

        assertThat(decide(0, 100)).isEmpty();
    }

    @Test
    void betterBidOfOthersMovesTheOrderUp() {
        decide(0, 100);
        book.apply(event(EventType.ADD, 3, BookSide.BID, 90100, 50));

        assertThat(decide(0, 100)).containsExactly(
                new Instruction.Cancel(1), place(2, Side.BUY, 100, 90100));
    }

    @Test
    void lowerBestBidDoesNotMoveTheOrder() {
        decide(0, 100);
        // the bids at 9.00 are gone: in reality our order would now be the best bid
        book.apply(event(EventType.EXECUTE, 1, BookSide.BID, BID, 300));
        book.apply(event(EventType.ADD, 3, BookSide.BID, 89900, 50));

        assertThat(decide(0, 100)).isEmpty();
    }

    @Test
    void partlyFilledOrderKeepsWaitingForTheRest() {
        decide(0, 100);
        algo.onFill(fill(1, 40));

        assertThat(decide(40, 100)).isEmpty();
    }

    @Test
    void completelyFilledOrderIsForgotten() {
        decide(0, 100);
        algo.onFill(fill(1, 100));

        assertThat(decide(100, 100)).isEmpty();
        assertThat(decide(100, 0)).containsExactly(place(2, Side.SELL, 100, ASK));
    }

    @Test
    void fillOfAnotherOrderIsIgnored() {
        decide(0, 100);
        algo.onFill(fill(99, 40));

        assertThat(decide(0, 100)).isEmpty();
    }

    @Test
    void bigTargetChangeReplacesTheOrder() {
        decide(0, 100);

        assertThat(decide(0, 50)).containsExactly(
                new Instruction.Cancel(1), place(2, Side.BUY, 50, BID));
    }

    @Test
    void smallTargetChangeKeepsTheOrder() {
        decide(0, 100);

        assertThat(decide(0, 95)).isEmpty();
    }

    @Test
    void oppositeDirectionCancelsAndSells() {
        decide(0, 100);

        assertThat(decide(0, -50)).containsExactly(
                new Instruction.Cancel(1), place(2, Side.SELL, 50, ASK));
    }

    @Test
    void reachedTargetCancelsTheOrder() {
        decide(0, 100);

        assertThat(decide(100, 100)).containsExactly(new Instruction.Cancel(1));
    }

    @Test
    void smallDifferenceIsNotTraded() {
        assertThat(decide(0, 5)).isEmpty();
    }

    @Test
    void closingIgnoresTheTolerance() {
        assertThat(decide(5, 0)).containsExactly(place(1, Side.SELL, 5, ASK));
    }

    @Test
    void emptySideOfTheBookSendsNothing() {
        OrderBook onlyAsks = new OrderBook("AAPL");
        onlyAsks.apply(event(EventType.ADD, 2, BookSide.ASK, ASK, 300));

        assertThat(algo.decide(0, "AAPL", 0, 100, onlyAsks)).isEmpty();
    }

    private List<Instruction> decide(int current, int target) {
        return algo.decide(0, "AAPL", current, target, book);
    }

    private static Instruction place(long id, Side side, int quantity, long price) {
        return new Instruction.Place(Order.limit(id, "AAPL", side, quantity, price));
    }

    private static Fill fill(long orderId, int quantity) {
        return new Fill(orderId, "AAPL", Side.BUY, quantity, new BigDecimal("9.00"),
                BigDecimal.ZERO, Liquidity.MAKER, LocalDateTime.of(2025, 12, 9, 10, 0));
    }

    private static MarketEvent event(
            EventType type, long orderId, BookSide side, long price, long quantity) {
        return new MarketEvent(0, "AAPL", type, orderId, side, price, quantity);
    }
}
