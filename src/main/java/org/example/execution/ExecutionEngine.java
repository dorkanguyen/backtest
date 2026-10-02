package org.example.execution;

import org.example.marketdata.Candle;
import org.example.order.Order;

import java.math.BigDecimal;

public class ExecutionEngine {

    private final CommissionModel commissionModel;

    public ExecutionEngine(CommissionModel commissionModel) {
        this.commissionModel = commissionModel;
    }

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