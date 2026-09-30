package org.example.marketdata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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