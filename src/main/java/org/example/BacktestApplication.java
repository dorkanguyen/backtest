package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of the backtest application; starts the Spring Boot server. */
@SpringBootApplication
public class BacktestApplication {
    public static void main(String[] args) {
        SpringApplication.run(BacktestApplication.class, args);
    }
}
