package org.example.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
}