package org.example.execution;

import org.example.marketdata.Candle;
import org.example.order.Order;

import java.math.BigDecimal;

/**
 * Simulates how orders are filled.
 *
 * <p>Simplified version: every order is filled completely at the close price of the candle.
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
        BigDecimal commission = commissionModel.calculate(order.quantity(), price);

        return new Fill(
                order.symbol(),
                order.side(),
                order.quantity(),
                price,
                commission,
                candle.openTime()
        );
    }
}
