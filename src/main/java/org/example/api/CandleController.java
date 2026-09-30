package org.example.api;

import org.example.marketdata.Candle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class CandleController {

    private final JdbcTemplate jdbcTemplate;

    public CandleController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/candles/count")
    public Integer count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM candles", Integer.class);
    }

    @GetMapping("/candles")
    public List<Candle> findAll() {
        return jdbcTemplate.query(
                "SELECT id, symbol, open_time, open, high, low, close, volume FROM candles",
                (rs, rowNum) -> new Candle(
                        rs.getLong("id"),
                        rs.getString("symbol"),
                        LocalDateTime.parse(rs.getString("open_time")),
                        rs.getDouble("open"),
                        rs.getDouble("high"),
                        rs.getDouble("low"),
                        rs.getDouble("close"),
                        rs.getLong("volume")
                )
        );
    }
}