package org.example.marketdata.itch;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
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
 *
 * <p>Execute, cancel and delete messages only contain the order id, so the parser also keeps the
 * live orders and fills in the symbol, side and price of every event. An order replace becomes
 * a {@link EventType#DELETE} of the old order followed by an {@link EventType#ADD} of the new one.
 * The price of an {@link EventType#EXECUTE} is the price the trade happened at, which can differ
 * from the price of the order, so a book must be rebuilt by order id. The side of a
 * {@link EventType#TRADE} is not meaningful: Nasdaq always sends it as buy.
 */
public class ItchParser {

    /** Stock locate is a 2-byte number, so there are at most 65 536 different values. */
    private static final int MAX_STOCK_LOCATE = 65_536;

    /** ITCH timestamps are nanoseconds since midnight in New York time. */
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private final String[] symbols = new String[MAX_STOCK_LOCATE];
    private final boolean[] wanted = new boolean[MAX_STOCK_LOCATE];
    private final Map<Long, LiveOrder> liveOrders = new HashMap<>();
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
            case 'E' -> parseExecuted(message, false);
            case 'C' -> parseExecuted(message, true);
            case 'X' -> parseCancel(message);
            case 'D' -> parseDelete(message);
            case 'U' -> parseReplace(message);
            case 'P' -> parseTrade(message);
            default -> {
                // Other message types do not change the order book of a stock.
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

    /**
     * Returns how many orders are currently in the books of the wanted symbols.
     *
     * @return number of live orders
     */
    public int liveOrderCount() {
        return liveOrders.size();
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
        long orderId = readUnsigned(message, 11, 8);
        BookSide side = message[19] == 'B' ? BookSide.BID : BookSide.ASK;
        long quantity = readUnsigned(message, 20, 4);
        long price = readUnsigned(message, 32, 4);
        addOrder(timestampOf(message), orderId,
                new LiveOrder(symbols[stockLocate], side, price, quantity));
    }

    /** Order Executed ('E') and Order Executed With Price ('C'). */
    private void parseExecuted(byte[] message, boolean withPrice) {
        if (!wanted[readUnsignedShort(message, 1)]) {
            return;
        }
        long orderId = readUnsigned(message, 11, 8);
        LiveOrder order = liveOrders.get(orderId);
        if (order == null) {
            return;
        }
        long executed = readUnsigned(message, 19, 4);
        long price = withPrice ? readUnsigned(message, 32, 4) : order.price();
        emit(timestampOf(message), EventType.EXECUTE, orderId, order, price, executed);
        reduceOrder(orderId, order, executed);
    }

    private void parseCancel(byte[] message) {
        if (!wanted[readUnsignedShort(message, 1)]) {
            return;
        }
        long orderId = readUnsigned(message, 11, 8);
        LiveOrder order = liveOrders.get(orderId);
        if (order == null) {
            return;
        }
        long canceled = readUnsigned(message, 19, 4);
        emit(timestampOf(message), EventType.CANCEL, orderId, order, order.price(), canceled);
        reduceOrder(orderId, order, canceled);
    }

    private void parseDelete(byte[] message) {
        if (!wanted[readUnsignedShort(message, 1)]) {
            return;
        }
        deleteOrder(timestampOf(message), readUnsigned(message, 11, 8));
    }

    private void parseReplace(byte[] message) {
        if (!wanted[readUnsignedShort(message, 1)]) {
            return;
        }
        long timestamp = timestampOf(message);
        LiveOrder old = deleteOrder(timestamp, readUnsigned(message, 11, 8));
        if (old == null) {
            return;
        }
        long newOrderId = readUnsigned(message, 19, 8);
        long quantity = readUnsigned(message, 27, 4);
        long price = readUnsigned(message, 31, 4);
        addOrder(timestamp, newOrderId, new LiveOrder(old.symbol(), old.side(), price, quantity));
    }

    /** Trade ('P'): an execution against an order that was not visible in the book. */
    private void parseTrade(byte[] message) {
        int stockLocate = readUnsignedShort(message, 1);
        if (!wanted[stockLocate]) {
            return;
        }
        BookSide side = message[19] == 'B' ? BookSide.BID : BookSide.ASK;
        long quantity = readUnsigned(message, 20, 4);
        long price = readUnsigned(message, 32, 4);
        listener.accept(new MarketEvent(timestampOf(message), symbols[stockLocate],
                EventType.TRADE, 0, side, price, quantity));
    }

    private void addOrder(long timestamp, long orderId, LiveOrder order) {
        liveOrders.put(orderId, order);
        emit(timestamp, EventType.ADD, orderId, order, order.price(), order.quantity());
    }

    /** Removes an order completely and returns it, or returns {@code null} if it is unknown. */
    private LiveOrder deleteOrder(long timestamp, long orderId) {
        LiveOrder order = liveOrders.remove(orderId);
        if (order != null) {
            emit(timestamp, EventType.DELETE, orderId, order, order.price(), order.quantity());
        }
        return order;
    }

    private void reduceOrder(long orderId, LiveOrder order, long amount) {
        long remaining = order.quantity() - amount;
        if (remaining > 0) {
            liveOrders.put(orderId, order.withQuantity(remaining));
        } else {
            liveOrders.remove(orderId);
        }
    }

    private void emit(long timestamp, EventType type, long orderId, LiveOrder order, long price,
            long quantity) {
        listener.accept(new MarketEvent(timestamp, order.symbol(), type, orderId, order.side(),
                price, quantity));
    }

    private long timestampOf(byte[] message) {
        return midnightNanos + readUnsigned(message, 5, 6);
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

    /** An order that is currently in the book, with its remaining quantity. */
    private record LiveOrder(String symbol, BookSide side, long price, long quantity) {

        LiveOrder withQuantity(long newQuantity) {
            return new LiveOrder(symbol, side, price, newQuantity);
        }
    }
}
