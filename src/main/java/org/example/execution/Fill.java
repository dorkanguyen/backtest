package org.example.execution;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.example.order.Side;

/**
 * An executed trade: the result of filling an {@link org.example.order.Order}.
 *
 * @param orderId id of the order this fill belongs to
 * @param symbol traded instrument, for example {@code AAPL}
 * @param side buy or sell
 * @param quantity number of shares, always positive
 * @param price price per share
 * @param commission fee paid for this fill; negative if a maker rebate was larger than the fees
 * @param liquidity whether our order added (maker) or took (taker) liquidity
 * @param time when the fill happened
 */
public record Fill(long orderId, String symbol, Side side, int quantity, BigDecimal price,
        BigDecimal commission, Liquidity liquidity, LocalDateTime time) {

}
