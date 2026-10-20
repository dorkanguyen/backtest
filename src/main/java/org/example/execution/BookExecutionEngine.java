package org.example.execution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.marketdata.book.OrderBook;
import org.example.marketdata.book.PriceLevel;
import org.example.order.Order;
import org.example.order.OrderType;
import org.example.order.Side;

/**
 * Simulates market orders against the order book: a buy takes the asks level by level, starting
 * at the cheapest; a sell takes the bids, starting at the highest. Every fill is a taker fill.
 *
 * <p>If the book has fewer shares than the order, only the available shares are filled. The
 * simulated order does not change the book: in a backtest the book comes from history, so later
 * events still show the shares we took. This is fine for orders that are small compared to the
 * book.
 */
public class BookExecutionEngine {

    /** Number of levels read from the book first; doubled while it is not enough. */
    private static final int FIRST_DEPTH = 8;

    /** Decimal places of the average fill price. */
    private static final int PRICE_SCALE = 10;

    private final CommissionModel commissionModel;

    /**
     * Creates the engine.
     *
     * @param commissionModel calculates the commission of every fill
     */
    public BookExecutionEngine(CommissionModel commissionModel) {
        this.commissionModel = commissionModel;
    }

    /**
     * Fills a market order from the book.
     *
     * @param order the order to fill
     * @param book the current order book of the symbol
     * @param time the time of the fill
     * @return the fill at the average price of the taken levels, or empty if the needed side of
     *         the book is empty
     * @throws IllegalArgumentException if the order is not a market order
     */
    public Optional<Fill> execute(Order order, OrderBook book, LocalDateTime time) {
        if (order.type() != OrderType.MARKET) {
            throw new IllegalArgumentException("Only market orders are filled here: " + order);
        }
        long wanted = order.quantity();
        int depth = FIRST_DEPTH;
        while (true) {
            List<PriceLevel> levels =
                    order.side() == Side.BUY ? book.asks(depth) : book.bids(depth);
            long filled = 0;
            long totalUnits = 0;
            for (PriceLevel level : levels) {
                long taken = Math.min(level.quantity(), wanted - filled);
                filled += taken;
                totalUnits += taken * level.price();
                if (filled == wanted) {
                    break;
                }
            }
            if (filled == wanted || levels.size() < depth) {
                return toFill(order, (int) filled, totalUnits, time);
            }
            depth *= 2;
        }
    }

    private Optional<Fill> toFill(Order order, int filled, long totalUnits, LocalDateTime time) {
        if (filled == 0) {
            return Optional.empty();
        }
        BigDecimal unitsPerDollar = BigDecimal.valueOf(Math.round(PriceLevel.UNITS_PER_DOLLAR));
        BigDecimal averagePrice = BigDecimal.valueOf(totalUnits)
                .divide(unitsPerDollar.multiply(BigDecimal.valueOf(filled)), PRICE_SCALE,
                        RoundingMode.HALF_EVEN)
                .stripTrailingZeros();
        BigDecimal commission = commissionModel.calculate(filled, averagePrice, Liquidity.TAKER);
        return Optional.of(new Fill(order.id(), order.symbol(), order.side(), filled,
                averagePrice, commission, Liquidity.TAKER, time));
    }
}
