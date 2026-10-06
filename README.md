# Backtest

A Java backtesting engine for trading strategies, built step by step as a learning project.

The long-term goal is a **fair price strategy** driven by order book and trade data: rebuild the
limit order book from exchange messages, estimate a fair price, compare it with the market price,
and simulate the resulting orders and fills to measure PnL, equity and drawdown.

> **Status:** work in progress. A simple candle-based backtest runs end to end, and the order book
> pipeline (Nasdaq ITCH parser → market events → order book → fair price models) is in place.
> The event-driven backtest on order book data is the next milestone.

## Features

**Candle backtest (working end to end)**

- Strategy → order → execution → portfolio pipeline, with each step in its own package
- Strategies return a *target position*; the engine computes and sends the order needed to reach it
- Per-share commission model (e.g. $0.005 per share, $1.00 minimum)
- Results: PnL, equity curve, max drawdown, total commission and the list of fills
- `BigDecimal` for all money and price calculations (no floating-point rounding errors)

**Order book data pipeline**

- Streaming parser for **Nasdaq TotalView-ITCH 5.0** files (reads the gzip file on the fly)
- Converts exchange messages into normalized market events: `ADD`, `EXECUTE`, `CANCEL`, `DELETE`, `TRADE`
- Stores events in SQLite (batched writes; tested with 67 million events for 22 symbols in one day)
- Rebuilds the full limit order book event by event (order-by-order, any depth)

**Fair price models**

- Mid price
- Microprice (best bid/ask weighted by the size on the opposite side)
- Multi-level price (the same idea over the top N price levels)

## Architecture

```
market data ──► order book + trades ──► fair price ──► strategy (target position)
                                                              │
     PnL / equity / drawdown ◄── portfolio ◄── fill ◄── execution engine ◄── order
```

Design rules:

- A **strategy never trades directly.** It only returns a target position.
- A separate **execution engine** simulates fills from market data.
- Market data, strategy, order, execution and portfolio live in separate packages.
- The same strategy code should later run on live data; only the data source and the execution
  layer would change.

| Package | Responsibility |
|---|---|
| `marketdata` | Candles, market events, event storage (SQLite) |
| `marketdata.itch` | Nasdaq ITCH 5.0 reader, parser and command line importer |
| `marketdata.book` | Limit order book rebuilt from market events |
| `fairprice` | Fair price models (mid, microprice, multi-level) |
| `strategy` | Strategy interface and example strategies |
| `order` | Orders and order side |
| `execution` | Execution engine, fills and commission models |
| `portfolio` | Cash, position and equity |
| `backtest` | Backtest loop and results |
| `api` | REST endpoints (Spring Boot) |

## Tech stack

- Java 25
- Spring Boot 4.1 (REST API, JDBC with `JdbcTemplate`)
- SQLite
- Maven
- Checkstyle (Google Java Style with 4-space indentation)

## Getting started

### Requirements

- JDK 25
- Maven 3.9+ (or the Maven bundled with IntelliJ IDEA)

### Build and run

```bash
mvn clean package
mvn spring-boot:run
```

The application starts on `http://localhost:8080` and creates a local `backtest.db` SQLite file
with an empty `candles` table.

### REST endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/candles` | List all candles in time order |
| `GET` | `/candles/count` | Number of stored candles |
| `POST` | `/candles` | Save one candle (JSON body) |
| `GET` | `/backtest` | Run the example strategy on the stored candles |

Add a candle:

```bash
curl -X POST http://localhost:8080/candles \
  -H "Content-Type: application/json" \
  -d '{"id": 0, "symbol": "AAPL", "openTime": "2026-09-30T15:30:00",
       "open": 170.00, "high": 171.20, "low": 169.50, "close": 170.80, "volume": 1200}'
```

After adding a few candles, open `http://localhost:8080/backtest` to see the result as JSON.

### Importing Nasdaq ITCH data

Market data is **not included** in this repository. Nasdaq publishes sample TotalView-ITCH 5.0
files (one full trading day each) at <https://emi.nasdaq.com/ITCH/Nasdaq%20ITCH/>. They are large
(several GB compressed) and subject to Nasdaq's terms of use.

The importer keeps a fixed list of 22 liquid symbols (see `ItchImporter.SYMBOLS`) and writes their
events into a separate SQLite database. Run the `org.example.marketdata.itch.ItchImporter` main
class (for example from IntelliJ IDEA) with three program arguments:

```
<itch-file.gz> <trading-date yyyy-mm-dd> <database-file>
```

One trading day takes a few minutes and about 4.6 GB of disk space. Re-running the import for the
same day replaces that day's events.

## Roadmap

- [x] Candle backtest with commission, PnL, equity curve and max drawdown
- [x] Nasdaq ITCH parser and market event storage
- [x] Order book reconstruction
- [x] Fair price models
- [ ] Event-driven backtest on order book data (market orders walking the book)
- [ ] Fair price strategy with configurable parameters (JSON / REST)
- [ ] React frontend: parameter form, equity chart, results and fills
- [ ] Limit orders and market making simulation (queue position, partial fills)
- [ ] Paper trading

## Disclaimer

This is a personal learning project. It is not investment advice, and it is not meant for trading
real money.

## License

[MIT](LICENSE)
