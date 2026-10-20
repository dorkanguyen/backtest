package org.example.candle;

import java.math.BigDecimal;
import org.example.execution.CommissionModel;
import org.example.execution.Fill;
import org.example.execution.Liquidity;
import org.example.order.Order;

/**
 * Simulates how orders are filled.
 *
 * <p>Simplified version: every order is filled completely at the close price of the candle, as a
 * taker.
 * Later this will be replaced by an order book based simulation.
 */
public class ExecutionEngine {

    private final CommissionModel commissionModel;

    /**
     * Creates an execution engine.
     *
     * @param commissionModel how commission is charged on each fill
     */
    public ExecutionEngine(CommissionModel commissionModel) {
        this.commissionModel = commissionModel;
    }

    /**
     * Fills an order at the close price of the given candle.
     *
     * @param order the order to fill
     * @param candle the candle on which the order is filled
     * @return the resulting fill, including commission
     */
    public Fill execute(Order order, Candle candle) {
        BigDecimal price = candle.close();
        BigDecimal commission =
                commissionModel.calculate(order.quantity(), price, Liquidity.TAKER);

        return new Fill(
                order.id(),
                order.symbol(),
                order.side(),
                order.quantity(),
                price,
                commission,
                Liquidity.TAKER,
                candle.openTime()
        );
    }
}
