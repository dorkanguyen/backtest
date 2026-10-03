package org.example.marketdata;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Stores order book events in an SQLite database file.
 *
 * <p>Events are written in batches inside transactions, because writing millions of rows one by
 * one would take hours. The table keeps the insertion order, so events with the same timestamp
 * can be read back in their original order.
 */
public class SqliteMarketEventStore implements MarketEventStore {

    private static final int BATCH_SIZE = 10_000;

    private static final String INSERT_SQL =
            """
            INSERT INTO market_events (ts, symbol, type, order_id, side, price, quantity)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final Connection connection;
    private final PreparedStatement insert;
    private int pending;

    /**
     * Opens (or creates) the database file and makes sure the table exists.
     *
     * @param databaseFile path of the database file, for example
     *     {@code C:/Users/me/market-data/market-data.db}
     */
    public SqliteMarketEventStore(String databaseFile) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
                statement.execute(
                        """
                        CREATE TABLE IF NOT EXISTS market_events (
                            ts       INTEGER NOT NULL,
                            symbol   TEXT    NOT NULL,
                            type     TEXT    NOT NULL,
                            order_id INTEGER NOT NULL,
                            side     TEXT    NOT NULL,
                            price    INTEGER NOT NULL,
                            quantity INTEGER NOT NULL
                        )
                        """);
                statement.execute(
                        """
                        CREATE INDEX IF NOT EXISTS market_events_symbol_ts
                        ON market_events (symbol, ts)
                        """);
            }
            connection.setAutoCommit(false);
            insert = connection.prepareStatement(INSERT_SQL);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open database " + databaseFile, e);
        }
    }

    @Override
    public void deleteBetween(long fromInclusive, long toExclusive) {
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM market_events WHERE ts >= ? AND ts < ?")) {
            delete.setLong(1, fromInclusive);
            delete.setLong(2, toExclusive);
            delete.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot delete events", e);
        }
    }

    @Override
    public void save(MarketEvent event) {
        try {
            insert.setLong(1, event.timestamp());
            insert.setString(2, event.symbol());
            insert.setString(3, event.type().name());
            insert.setLong(4, event.orderId());
            insert.setString(5, event.side().name());
            insert.setLong(6, event.price());
            insert.setLong(7, event.quantity());
            insert.addBatch();
            pending++;
            if (pending == BATCH_SIZE) {
                writePending();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot save event " + event, e);
        }
    }

    @Override
    public void close() {
        try {
            writePending();
            insert.close();
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot close database", e);
        }
    }

    private void writePending() throws SQLException {
        if (pending > 0) {
            insert.executeBatch();
            connection.commit();
            pending = 0;
        }
    }
}
