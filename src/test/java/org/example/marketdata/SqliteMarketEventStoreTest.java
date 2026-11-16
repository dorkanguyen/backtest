package org.example.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link SqliteMarketEventStore}, on a database in a temporary folder. */
class SqliteMarketEventStoreTest {

    @TempDir
    Path folder;

    @Test
    void openExistingDoesNotCreateMissingFile() {
        Path missing = folder.resolve("typo.db");

        assertThatThrownBy(() -> SqliteMarketEventStore.openExisting(missing.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Market data database not found");
        assertThat(missing).doesNotExist();
    }

    @Test
    void openOrCreateMakesNewDatabaseThatCanBeReadBack() {
        String file = folder.resolve("market-data.db").toString();
        MarketEvent add = new MarketEvent(1_000, "AAPL", EventType.ADD, 7, BookSide.BID,
                2_785_500, 100);
        MarketEvent other = new MarketEvent(2_000, "NVDA", EventType.ADD, 8, BookSide.ASK,
                1_802_500, 50);
        try (MarketEventStore store = SqliteMarketEventStore.openOrCreate(file)) {
            store.save(add);
            store.save(other);
        }

        List<MarketEvent> replayed = new ArrayList<>();
        try (MarketEventStore store = SqliteMarketEventStore.openExisting(file)) {
            store.replay("AAPL", 0, 10_000, replayed::add);
        }

        assertThat(replayed).containsExactly(add);
    }
}
