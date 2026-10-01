package org.example.execution;

import org.example.marketdata.Candle;
import org.example.order.Order;

public class ExecutionEngine {

    public Fill execute(Order order, Candle candle) {
        return new Fill(
                order.symbol(),
                order.side(),
                order.quantity(),
                candle.close(),
                candle.openTime()
        );
    }
}
