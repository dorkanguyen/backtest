package org.example.marketdata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;

/**
 * Stores order book events in an SQLite database file.
 *
 * <p>Events are written in batches inside transactions, because writing millions of rows one by
 * one would take hours. The table keeps the insertion order, so events with the same timestamp
 * can be read back in their original order.
 *
 * <p>There are two ways to open it: {@link #openOrCreate} for the importer, which fills a new or
 * existing database, and {@link #openExisting} for everything that only reads, so that a wrong
 * path gives an error instead of a new empty database.
 */
public class SqliteMarketEventStore implements MarketEventStore {

    private static final int BATCH_SIZE = 10_000;

    private static final String INSERT_SQL =
            """
            INSERT INTO market_events (ts, symbol, type, order_id, side, price, quantity)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String REPLAY_SQL =
            """
            SELECT ts, symbol, type, order_id, side, price, quantity
            FROM market_events
            WHERE symbol = ? AND ts >= ? AND ts < ?
            ORDER BY ts, rowid
            """;

    private final Connection connection;
    private final PreparedStatement insert;
    private int pending;

    /**
     * Opens the database file, or creates it if it does not exist yet, and makes sure the table
     * exists. Meant for the importer.
     *
     * @param databaseFile path of the database file, for example
     *     {@code C:/Users/me/market-data/market-data.db}
     * @return the open store; the caller must close it
     */
    public static SqliteMarketEventStore openOrCreate(String databaseFile) {
        return new SqliteMarketEventStore(databaseFile, true);
    }

    /**
     * Opens a database file that already exists. Meant for backtests and research tools, which
     * only read: a mistyped path must not silently create a new, empty database.
     *
     * @param databaseFile path of the database file
     * @return the open store; the caller must close it
     * @throws IllegalArgumentException if there is no such file
     */
    public static SqliteMarketEventStore openExisting(String databaseFile) {
        if (!Files.isRegularFile(Path.of(databaseFile))) {
            throw new IllegalArgumentException(
                    "Market data database not found: " + databaseFile);
        }
        return new SqliteMarketEventStore(databaseFile, false);
    }

    private SqliteMarketEventStore(String databaseFile, boolean createTable) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
                if (createTable) {
                    createTable(statement);
                }
            }
            connection.setAutoCommit(false);
            insert = connection.prepareStatement(INSERT_SQL);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot open database " + databaseFile, e);
        }
    }

    private static void createTable(Statement statement) throws SQLException {
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
    public void replay(String symbol, long fromInclusive, long toExclusive,
            Consumer<MarketEvent> listener) {
        try (PreparedStatement select = connection.prepareStatement(REPLAY_SQL)) {
            select.setString(1, symbol);
            select.setLong(2, fromInclusive);
            select.setLong(3, toExclusive);
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    listener.accept(new MarketEvent(
                            rows.getLong("ts"),
                            rows.getString("symbol"),
                            EventType.valueOf(rows.getString("type")),
                            rows.getLong("order_id"),
                            BookSide.valueOf(rows.getString("side")),
                            rows.getLong("price"),
                            rows.getLong("quantity")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot replay events of " + symbol, e);
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
