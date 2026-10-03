package org.example.marketdata.itch;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.function.Consumer;
import org.example.marketdata.BookSide;
import org.example.marketdata.EventType;
import org.example.marketdata.MarketEvent;

/**
 * Interprets raw Nasdaq TotalView-ITCH 5.0 messages and turns them into {@link MarketEvent}s.
 *
 * <p>Messages must be passed in file order. In ITCH most messages identify a stock only by a
 * number (the stock locate), so the parser remembers which number belongs to which symbol from
 * the Stock Directory messages sent at the start of the day. Only the requested symbols produce
 * events; all other messages are skipped.
 */
public class ItchParser {

    /** Stock locate is a 2-byte number, so there are at most 65 536 different values. */
    private static final int MAX_STOCK_LOCATE = 65_536;

    /** ITCH timestamps are nanoseconds since midnight in New York time. */
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private final String[] symbols = new String[MAX_STOCK_LOCATE];
    private final boolean[] wanted = new boolean[MAX_STOCK_LOCATE];
    private final Set<String> wantedSymbols;
    private final long midnightNanos;
    private final Consumer<MarketEvent> listener;

    /**
     * Creates a parser for one trading day.
     *
     * @param tradingDate the day the file belongs to (needed to convert timestamps to UTC)
     * @param wantedSymbols symbols to produce events for, for example {@code AAPL}
     * @param listener receives every produced event, in file order
     */
    public ItchParser(LocalDate tradingDate, Set<String> wantedSymbols,
            Consumer<MarketEvent> listener) {
        Instant midnight = tradingDate.atStartOfDay(NEW_YORK).toInstant();
        this.midnightNanos = midnight.getEpochSecond() * 1_000_000_000L + midnight.getNano();
        this.wantedSymbols = wantedSymbols;
        this.listener = listener;
    }

    /**
     * Processes one message.
     *
     * @param message raw message bytes from {@link ItchReader}
     */
    public void parse(byte[] message) {
        switch (message[0]) {
            case 'R' -> parseStockDirectory(message);
            case 'A', 'F' -> parseAddOrder(message);
            default -> {
                // Other message types are not handled yet.
            }
        }
    }

    /**
     * Returns the symbol that belongs to a stock locate number.
     *
     * @param stockLocate the number used in ITCH messages
     * @return the symbol, or {@code null} if no Stock Directory message was seen for it
     */
    public String symbolOf(int stockLocate) {
        return symbols[stockLocate];
    }

    private void parseStockDirectory(byte[] message) {
        int stockLocate = readUnsignedShort(message, 1);
        String symbol = new String(message, 11, 8, StandardCharsets.US_ASCII).trim();
        symbols[stockLocate] = symbol;
        wanted[stockLocate] = wantedSymbols.contains(symbol);
    }

    /** Add Order ('A') and Add Order with attribution ('F') share the same first 36 bytes. */
    private void parseAddOrder(byte[] message) {
        int stockLocate = readUnsignedShort(message, 1);
        if (!wanted[stockLocate]) {
            return;
        }
        long timestamp = midnightNanos + readUnsigned(message, 5, 6);
        long orderId = readUnsigned(message, 11, 8);
        BookSide side = message[19] == 'B' ? BookSide.BID : BookSide.ASK;
        long quantity = readUnsigned(message, 20, 4);
        long price = readUnsigned(message, 32, 4);
        listener.accept(new MarketEvent(timestamp, symbols[stockLocate], EventType.ADD, orderId,
                side, price, quantity));
    }

    private static int readUnsignedShort(byte[] bytes, int offset) {
        return (int) readUnsigned(bytes, offset, 2);
    }

    /** Reads a big-endian unsigned number of the given length (at most 8 bytes). */
    private static long readUnsigned(byte[] bytes, int offset, int length) {
        long value = 0;
        for (int i = 0; i < length; i++) {
            value = (value << 8) | (bytes[offset + i] & 0xFF);
        }
        return value;
    }
}
