package org.example.marketdata.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;
import org.junit.jupiter.api.Test;

/** Tests for {@link TradeFlow} with a 10 second window. Times are in seconds for readability. */
class TradeFlowTest {

    private static final long SECOND = 1_000_000_000L;

    private final TradeFlow flow = new TradeFlow("AAPL", Duration.ofSeconds(10));

    @Test
    void newTradeFlowIsEmpty() {
        assertThat(flow.lastPrice()).isEmpty();
        assertThat(flow.totalVolume()).isZero();
        assertThat(flow.imbalance()).isZero();
    }

    @Test
    void executionOfAskIsBuyAndOfBidIsSell() {
        flow.apply(event(1, EventType.EXECUTE, BookSide.ASK, 2785900, 300));
        flow.apply(event(2, EventType.EXECUTE, BookSide.BID, 2785500, 100));

        assertThat(flow.buyVolume()).isEqualTo(300);
        assertThat(flow.sellVolume()).isEqualTo(100);
    }

    @Test
    void hiddenTradeCountsOnlyInTotalVolume() {
        flow.apply(event(1, EventType.EXECUTE, BookSide.ASK, 2785900, 300));
        flow.apply(event(2, EventType.EXECUTE, BookSide.BID, 2785500, 100));
        flow.apply(event(3, EventType.TRADE, BookSide.BID, 2785700, 50));

        assertThat(flow.buyVolume()).isEqualTo(300);
        assertThat(flow.sellVolume()).isEqualTo(100);
        assertThat(flow.totalVolume()).isEqualTo(450);
        // (300 - 100) / (300 + 100) = 0.5; the hidden trade has no known side.
        assertThat(flow.imbalance()).isEqualTo(0.5);
    }

    @Test
    void imbalanceIsPlusOneForOnlyBuyersAndMinusOneForOnlySellers() {
        flow.apply(event(1, EventType.EXECUTE, BookSide.ASK, 2785900, 100));
        assertThat(flow.imbalance()).isEqualTo(1.0);

        TradeFlow sellers = new TradeFlow("AAPL", Duration.ofSeconds(10));
        sellers.apply(event(1, EventType.EXECUTE, BookSide.BID, 2785500, 100));
        assertThat(sellers.imbalance()).isEqualTo(-1.0);
    }

    @Test
    void lastPriceIsThePriceOfTheLatestTrade() {
        flow.apply(event(1, EventType.EXECUTE, BookSide.ASK, 2785900, 100));
        flow.apply(event(2, EventType.TRADE, BookSide.BID, 2785700, 50));

        assertThat(flow.lastPrice()).hasValue(2785700);
    }

    @Test
    void ordersThatAreNotTradesDoNotCount() {
        flow.apply(event(1, EventType.ADD, BookSide.BID, 2785500, 100));
        flow.apply(event(2, EventType.CANCEL, BookSide.BID, 2785500, 50));
        flow.apply(event(3, EventType.DELETE, BookSide.BID, 2785500, 50));

        assertThat(flow.totalVolume()).isZero();
        assertThat(flow.lastPrice()).isEmpty();
    }

    @Test
    void oldTradesLeaveTheWindow() {
        flow.apply(event(1, EventType.EXECUTE, BookSide.ASK, 2785900, 300));
        flow.apply(event(5, EventType.EXECUTE, BookSide.BID, 2785500, 100));

        // At 11 s the window is (1 s, 11 s]: the trade at 1 s has left, the one at 5 s is in.
        flow.apply(event(11, EventType.ADD, BookSide.BID, 2785500, 10));
        assertThat(flow.buyVolume()).isZero();
        assertThat(flow.sellVolume()).isEqualTo(100);
        assertThat(flow.totalVolume()).isEqualTo(100);

        // At 15 s the trade at 5 s has left too; the last price is still remembered.
        flow.apply(event(15, EventType.ADD, BookSide.BID, 2785500, 10));
        assertThat(flow.totalVolume()).isZero();
        assertThat(flow.imbalance()).isZero();
        assertThat(flow.lastPrice()).hasValue(2785500);
    }

    @Test
    void windowMustBePositive() {
        assertThatThrownBy(() -> new TradeFlow("AAPL", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventOfAnotherSymbolIsRejected() {
        MarketEvent msft = new MarketEvent(0, "MSFT", EventType.TRADE, 0, BookSide.BID, 1, 1);

        assertThatThrownBy(() -> flow.apply(msft))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static MarketEvent event(
            long seconds, EventType type, BookSide side, long price, long quantity) {
        return new MarketEvent(seconds * SECOND, "AAPL", type, 1, side, price, quantity);
    }
}
