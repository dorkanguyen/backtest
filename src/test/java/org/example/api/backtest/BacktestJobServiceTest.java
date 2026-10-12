package org.example.api.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.example.api.backtest.BacktestRequest.ComponentChoice;
import org.example.api.backtest.BacktestRequest.SignalChoice;
import org.example.api.catalog.CatalogConfig;
import org.example.backtest.EventBacktester;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.ListMarketEventStore;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.MarketEventStoreFactory;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BacktestJobService}. The jobs run at once in the calling thread, on a small
 * made-up day: a book of bid 10.00 and ask 10.02 from 09:00, and one more bid at 09:31.
 */
class BacktestJobServiceTest {

    private static final LocalDate DAY = LocalDate.of(2025, 12, 9);

    private final List<MarketEvent> events = List.of(
            event(9, 0, 1, BookSide.BID, 100000),
            event(9, 0, 2, BookSide.ASK, 100200),
            event(9, 31, 3, BookSide.BID, 100100));

    private final BacktestJobService service = new BacktestJobService(
            new CatalogConfig().catalog(), () -> new ListMarketEventStore(events), Runnable::run);

    @Test
    void validRequestRunsToTheEnd() {
        BacktestJob job = service.submit(request("vwap", 100));

        assertThat(job.getStatus()).isEqualTo(BacktestJob.Status.DONE);
        assertThat(job.getProgress()).isEqualTo(1.0);
        assertThat(job.getResult().symbol()).isEqualTo("AAPL");
        assertThat(job.getError()).isNull();
    }

    @Test
    void finishedJobCanBeFound() {
        BacktestJob job = service.submit(request("vwap", 100));

        assertThat(service.find(job.getId())).contains(job);
        assertThat(service.find("no-such-job")).isEmpty();
    }

    @Test
    void unknownSignalIsRejectedBeforeStarting() {
        assertThatThrownBy(() -> service.submit(request("magic", 100)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown signal: magic");
    }

    @Test
    void missingSettingIsRejected() {
        assertThatThrownBy(() -> service.submit(request("vwap", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxPosition is required");
    }

    @Test
    void endBeforeStartIsRejected() {
        BacktestRequest backwards = new BacktestRequest("AAPL", DAY, LocalTime.of(10, 0),
                LocalTime.of(9, 30), new BigDecimal("10000"), 100,
                List.of(new SignalChoice("vwap", 1, Map.of())), List.of(),
                new ComponentChoice("throttled", Map.of()), null);

        assertThatThrownBy(() -> service.submit(backwards))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void errorWhileRunningFailsTheJob() {
        MarketEventStoreFactory broken = () -> {
            throw new IllegalStateException("Database not found");
        };
        BacktestJobService brokenService =
                new BacktestJobService(new CatalogConfig().catalog(), broken, Runnable::run);

        BacktestJob job = brokenService.submit(request("vwap", 100));

        assertThat(job.getStatus()).isEqualTo(BacktestJob.Status.FAILED);
        assertThat(job.getError()).isEqualTo("Database not found");
    }

    @Test
    void onlyTheMostRecentJobsAreRemembered() {
        BacktestJob first = service.submit(request("vwap", 100));
        for (int i = 0; i < BacktestJobService.MAX_JOBS; i++) {
            service.submit(request("vwap", 100));
        }

        assertThat(service.find(first.getId())).isEmpty();
    }

    private static BacktestRequest request(String signal, Integer maxPosition) {
        return new BacktestRequest("AAPL", DAY, LocalTime.of(9, 30), LocalTime.of(10, 0),
                new BigDecimal("10000"), maxPosition,
                List.of(new SignalChoice(signal, 1, Map.of())),
                List.of(new ComponentChoice("spread", Map.of("maxSpreadCents", 5.0))),
                new ComponentChoice("throttled", Map.of()), null);
    }

    private static MarketEvent event(int hour, int minute, long orderId, BookSide side,
            long price) {
        long nanos = DAY.atTime(hour, minute).atZone(EventBacktester.EXCHANGE_ZONE)
                .toInstant().toEpochMilli() * 1_000_000L;
        return new MarketEvent(nanos, "AAPL", EventType.ADD, orderId, side, price, 1000);
    }
}
