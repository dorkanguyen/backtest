package org.example.marketdata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Repository
public class CandleRepository {

    private final JdbcTemplate jdbcTemplate;

    public CandleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Integer count() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM candles", Integer.class);
    }

    public List<Candle> findAll() {
        return jdbcTemplate.query(
                "SELECT id, symbol, open_time, open, high, low, close, volume FROM candles ORDER BY open_time",
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

    public void save(Candle candle) {
        jdbcTemplate.update(
                "INSERT INTO candles (symbol, open_time, open, high, low, close, volume) VALUES (?, ?, ?, ?, ?, ?, ?)",
                candle.symbol(),
                candle.openTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume()
        );
    }
}