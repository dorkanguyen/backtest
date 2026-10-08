package org.example.marketdata;

import java.util.function.Consumer;

/**
 * Storage for order book events.
 *
 * <p>Hides where and how the events are stored, so the storage (for example SQLite or Parquet)
 * can be replaced without changing the code that produces or reads the events.
 */
public interface MarketEventStore extends AutoCloseable {

    /**
     * Deletes all events in a time range, so that a day can be imported again without
     * duplicates.
     *
     * @param fromInclusive start of the range, nanoseconds since 1970-01-01 UTC
     * @param toExclusive end of the range, nanoseconds since 1970-01-01 UTC
     */
    void deleteBetween(long fromInclusive, long toExclusive);

    /**
     * Stores one event. Events may be buffered; they are surely stored after {@link #close()}.
     *
     * @param event the event to store
     */
    void save(MarketEvent event);

    /**
     * Reads the events of one symbol in a time range and hands them to the listener one by one,
     * in the order they happened. Events are streamed, so a whole day never has to fit in memory.
     *
     * @param symbol instrument, for example {@code AAPL}
     * @param fromInclusive start of the range, nanoseconds since 1970-01-01 UTC
     * @param toExclusive end of the range, nanoseconds since 1970-01-01 UTC
     * @param listener receives every event of the range
     */
    void replay(String symbol, long fromInclusive, long toExclusive,
            Consumer<MarketEvent> listener);

    /** Writes buffered events and releases the storage. */
    @Override
    void close();
}
