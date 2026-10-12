package org.example.signal;

import java.util.Optional;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.book.PriceLevel;
import org.example.marketdata.state.SymbolState;

/**
 * Allows trading only while the spread is small, because a wide spread makes every trade more
 * expensive. Without a bid or an ask there is no spread, so trading is not allowed then.
 */
public class SpreadFilter implements TradingFilter {

    private final long maxSpread;
    private boolean allowed;

    /**
     * Creates the filter.
     *
     * @param maxSpread the widest allowed spread in 1/10000 dollars, for example 200 for 2 cents
     * @throws IllegalArgumentException if maxSpread is not positive
     */
    public SpreadFilter(long maxSpread) {
        if (maxSpread <= 0) {
            throw new IllegalArgumentException("maxSpread must be positive, was " + maxSpread);
        }
        this.maxSpread = maxSpread;
    }

    @Override
    public void update(MarketEvent event, SymbolState state) {
        Optional<PriceLevel> bid = state.book().bestBid();
        Optional<PriceLevel> ask = state.book().bestAsk();
        allowed = bid.isPresent() && ask.isPresent()
                && ask.get().price() - bid.get().price() <= maxSpread;
    }

    @Override
    public boolean allowsTrading() {
        return allowed;
    }
}
