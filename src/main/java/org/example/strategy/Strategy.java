package org.example.strategy;

import org.example.marketdata.Candle;

public interface Strategy {

    int targetPosition(Candle candle);
}