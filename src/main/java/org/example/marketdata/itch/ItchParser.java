package org.example.marketdata.itch;

import java.nio.charset.StandardCharsets;

/**
 * Interprets raw Nasdaq TotalView-ITCH 5.0 messages.
 *
 * <p>Messages must be passed in file order. In ITCH most messages identify a stock only by a
 * number (the stock locate), so the parser remembers which number belongs to which symbol from
 * the Stock Directory messages sent at the start of the day.
 */
public class ItchParser {

    /** Stock locate is a 2-byte number, so there are at most 65 536 different values. */
    private static final int MAX_STOCK_LOCATE = 65_536;

    private final String[] symbols = new String[MAX_STOCK_LOCATE];

    /**
     * Processes one message.
     *
     * @param message raw message bytes from {@link ItchReader}
     */
    public void parse(byte[] message) {
        switch (message[0]) {
            case 'R' -> parseStockDirectory(message);
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
    }

    private static int readUnsignedShort(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }
}
