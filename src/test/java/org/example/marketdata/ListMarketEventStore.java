package org.example.marketdata;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** An event store that keeps its events in a list, for tests. */
public final class ListMarketEventStore implements MarketEventStore {

    private final List<MarketEvent> events;

    /**
     * Creates a store with the given events.
     *
     * @param events the events, in time order
     */
    public ListMarketEventStore(List<MarketEvent> events) {
        this.events = new ArrayList<>(events);
    }

    @Override
    public void deleteBetween(long fromInclusive, long toExclusive) {
        events.removeIf(e -> e.timestamp() >= fromInclusive && e.timestamp() < toExclusive);
    }

    @Override
    public void save(MarketEvent event) {
        events.add(event);
    }

    @Override
    public void replay(String symbol, long fromInclusive, long toExclusive,
            Consumer<MarketEvent> listener) {
        for (MarketEvent event : events) {
            if (event.symbol().equals(symbol) && event.timestamp() >= fromInclusive
                    && event.timestamp() < toExclusive) {
                listener.accept(event);
            }
        }
    }

    @Override
    public void close() {
    }
}
