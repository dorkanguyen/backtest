package org.example.execution;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.example.order.Side;

/**
 * An executed trade: the result of filling an {@link org.example.order.Order}.
 *
 * @param symbol traded instrument, for example {@code AAPL}
 * @param side buy or sell
 * @param quantity number of shares, always positive
 * @param price price per share
 * @param commission fee paid for this fill
 * @param time when the fill happened
 */
public record Fill(String symbol, Side side, int quantity, BigDecimal price, BigDecimal commission,
        LocalDateTime time) {

}
