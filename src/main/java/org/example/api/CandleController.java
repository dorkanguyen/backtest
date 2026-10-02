package org.example.api;

import org.example.marketdata.Candle;
import org.example.marketdata.CandleRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** REST endpoints for reading and saving candles. */
@RestController
public class CandleController {

    private final CandleRepository candleRepository;

    public CandleController(CandleRepository candleRepository) {
        this.candleRepository = candleRepository;
    }

    /** Returns the number of stored candles. */
    @GetMapping("/candles/count")
    public Integer count() {
        return candleRepository.count();
    }

    /** Returns all stored candles, ordered by open time. */
    @GetMapping("/candles")
    public List<Candle> findAll() {
        return candleRepository.findAll();
    }

    /** Saves a new candle; its {@code id} is ignored and assigned by the database. */
    @PostMapping("/candles")
    public void save(@RequestBody Candle candle) {
        candleRepository.save(candle);
    }
}
