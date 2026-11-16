package org.example.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.example.backtest.EventBacktester;
import org.example.backtest.TradingPeriod;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.ListMarketEventStore;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.state.SymbolState;
import org.example.signal.Signal;
import org.junit.jupiter.api.Test;

/** Tests for {@link SignalSampler}: a day without data must be an error, not empty samples. */
class SignalSamplerTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2025, 12, 9, 0, 0);

    private final SignalSampler sampler = new SignalSampler(Duration.ofSeconds(1), 8);

    private final ListMarketEventStore store = new ListMarketEventStore(List.of(
            event(DAY.withHour(9).withMinute(30), 1, BookSide.BID, 100000),
            event(DAY.withHour(9).withMinute(30).withSecond(1), 2, BookSide.ASK, 100200)));

    @Test
    void dayWithEventsIsSampled() {
        List<SignalSamples> samples = sampler.sample(store, "AAPL",
                List.of(period(DAY)), () -> Map.of("silent", new SilentSignal()));

        assertThat(samples).hasSize(1);
    }

    @Test
    void dayWithoutEventsIsAnError() {
        assertThatThrownBy(() -> sampler.sample(store, "AAPL",
                List.of(period(DAY), period(DAY.plusDays(1))),
                () -> Map.of("silent", new SilentSignal())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No market events for AAPL")
                .hasMessageContaining("2025-12-10");
    }

    @Test
    void unknownSymbolIsAnError() {
        assertThatThrownBy(() -> sampler.sample(store, "MSFT", List.of(period(DAY)),
                () -> Map.of("silent", new SilentSignal())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No market events for MSFT");
    }

    private static TradingPeriod period(LocalDateTime day) {
        return new TradingPeriod(day.withHour(9), day.withHour(9).withMinute(30),
                day.withHour(9).withMinute(31));
    }

    private static MarketEvent event(LocalDateTime time, long orderId, BookSide side,
            long price) {
        return new MarketEvent(EventBacktester.toNanos(time), "AAPL", EventType.ADD, orderId,
                side, price, 100);
    }

    /** A signal without any opinion. */
    private static final class SilentSignal implements Signal {

        @Override
        public void update(MarketEvent event, SymbolState state) {
        }

        @Override
        public double conviction() {
            return 0;
        }
    }
}
