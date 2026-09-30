package org.example.api;

import org.example.marketdata.Candle;
import org.example.marketdata.CandleRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CandleController {

    private final CandleRepository candleRepository;

    public CandleController(CandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }

    @GetMapping("/candles/count")
    public Integer count() {
        return candleRepository.count();
    }

    @GetMapping("/candles")
    public List<Candle> findAll() {
        return candleRepository.findAll();
    }

    @PostMapping("/candles")
    public void save(@RequestBody Candle candle) {
        candleRepository.save(candle);
    }
}