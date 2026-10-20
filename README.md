# Backtest

An event-driven backtesting engine for order book trading strategies, written in Java and built
step by step as a learning project.

It replays real exchange data message by message, rebuilds the limit order book, lets a set of
configurable signals decide on a target position, and simulates the resulting market orders
against the book to measure PnL, equity and drawdown.

> **Status:** work in progress. The event-driven backtest runs end to end on real Nasdaq data and
> can be started over a REST API. Next: making execution cheaper (cost-aware trading, limit orders);
> a React frontend comes later.

## Features

**Market data**

- Streaming parser for **Nasdaq TotalView-ITCH 5.0** files (reads the gzip file on the fly)
- Converts exchange messages into normalized market events: `ADD`, `EXECUTE`, `CANCEL`, `DELETE`, `TRADE`
- Stores events in SQLite (batched writes; tested with 67 million events for 22 symbols in one day)
- Rebuilds the full limit order book event by event (order-by-order, any depth)
- Tracks recent trades in a sliding window: aggressive buy / sell volume, imbalance, VWAP, trade count
- Order flow imbalance (OFI) at the best bid and ask

**Fair price models**

- Mid price, microprice, multi-level price
- Decaying levels (deeper levels count less)
- Impact price (average price of buying and selling a given quantity)

**Signals, filters and strategy**

- **Signals** give an opinion from −1 (strongly sell) to +1 (strongly buy):
  impact price, decaying levels, back to VWAP, order flow imbalance
- **Filters** stop trading in bad conditions (quiet market, wide spread); the position is then kept
- The **signal mix strategy** averages the opinions with user-chosen weights and scales the result
  to a target position
- Every signal, filter and execution algorithm **describes itself** (name, explanation, settings
  with default, min and max), so a user interface can build its settings page automatically

**Execution and backtest**

- Throttled execution: ignores small position changes and waits between trades to save commission
- Market orders are filled from the real book, level by level (partial fills when the book is thin)
- Tiered commission: broker fee per share with a minimum, plus exchange and clearing fees per share
  (takers pay the exchange fee, makers get a rebate)
  (default: $0.0035 per share with a $0.35 minimum, $0.003 exchange and $0.0002 clearing fee, so
  100 shares cost $0.67)
- Warm-up from the pre-market open, trading in a chosen period, position closed at the end
- Results: PnL, total commission, max drawdown, every fill, equity curve sampled every second
- `BigDecimal` for all money; prices from the exchange are kept as exact integers

**REST API**

- Catalog of all signals, filters and execution algorithms with their settings
- Backtests run as background jobs with progress reporting

## Architecture

```
ITCH file ──► market events (SQLite) ──► replay ──► order book + trade flow
                                                          │
                       signals (opinion −1…+1) ◄──────────┘
                              │  weighted average, filters
                              ▼
                      strategy: target position
                              │
                              ▼
          execution algorithm: when and how much to trade
                              │
                              ▼
        fill from the order book ──► portfolio ──► PnL / equity / drawdown
```

Design rules:

- A **strategy never trades directly.** It only returns a target position; a separate execution
  algorithm decides when and how to trade.
- Each concern lives in its own package, and the core knows nothing about Spring.
- Everything that will get more advanced later sits behind an interface with a simple first
  implementation (signals, filters, execution algorithms, commission, event storage).
- The same strategy code should later run on live data; only the data source and the execution
  layer would change.

| Package | Responsibility |
|---|---|
| `marketdata` | Market events and their storage (SQLite) |
| `marketdata.itch` | Nasdaq ITCH 5.0 reader, parser and command line importer |
| `marketdata.book` | Limit order book rebuilt from market events |
| `marketdata.flow` | Recent trades (volume, imbalance, VWAP) and order flow imbalance |
| `marketdata.state` | The order book and trade flow of each symbol together |
| `fairprice` | Fair price models |
| `parameter` | Self-describing settings shared by signals, filters and execution algorithms |
| `signal` | Signals and their definitions |
| `filter` | Trading filters and their definitions |
| `strategy` | Event-driven strategies (signal mix) |
| `order` | Orders and order side |
| `execution` | Execution algorithms, fills from the book, commission models |
| `portfolio` | Cash, position and equity |
| `backtest` | Event-driven backtest loop and results |
| `catalog` | The available signals, filters and execution algorithms |
| `api` | REST endpoints (Spring Boot) |
| `candle` | The first, candle-based learning version, kept separately |

## Tech stack

- Java 25, Maven
- Spring Boot 4.1 (REST API)
- SQLite
- JUnit 5 and AssertJ
- Checkstyle (Google Java Style with 4-space indentation)

## Getting started

### Requirements

- JDK 25
- Maven 3.9+ (or the Maven bundled with IntelliJ IDEA)
- Market data imported into a SQLite database (see below)

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

### Build and run

```bash
mvn clean package
mvn spring-boot:run
```

The application starts on `http://localhost:8080`. By default it reads market data from
`~/market-data/market-data.db`; set the `MARKET_DATA_DB` environment variable to use another file.

### REST endpoints

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/catalog` | All signals, filters and execution algorithms with their settings |
| `POST` | `/api/backtests` | Start a backtest in the background; answers `202` with a job id |
| `GET` | `/api/backtests/{id}` | Status and progress of a job, and its result when done |

Start a backtest:

```bash
curl -X POST http://localhost:8080/api/backtests \
  -H "Content-Type: application/json" \
  -d '{
        "symbol": "QQQ", "date": "2025-12-09", "start": "09:35", "end": "15:55",
        "startingCash": 100000, "maxPosition": 100,
        "signals": [
          { "id": "impactPrice",    "weight": 50 },
          { "id": "decayingLevels", "weight": 30 },
          { "id": "vwap",           "weight": 20 }
        ],
        "filters":    [ { "id": "tradeCount" }, { "id": "spread" } ],
        "execution":    { "id": "throttled", "parameters": { "cooldownSeconds": 300 } },
        "commission":   { "perShare": 0.0035, "minimum": 0.35,
                          "exchangePerShare": 0.003, "clearingPerShare": 0.0002,
                          "makerRebatePerShare": 0.002 }
      }'
```

Missing settings get their default values. Then ask for the job until its status is `DONE`:

```bash
curl http://localhost:8080/api/backtests/<job-id>
```

A wrong setting (for example an unknown signal id) is rejected at once with `400 Bad Request`.

## What the first results show

On one day of data (2025-12-09), the signals point in the right direction on average, but they
predict moves of only a fraction of a cent over the next seconds. Crossing the spread with market
orders and paying commission costs more than that, so the market-order version of the strategy
loses money on most symbols. A realistic tiered commission roughly halves the cost, but most of it
is the spread, so the strategy still loses even without any commission. This is the expected next
challenge: cost-aware trading and limit orders.

## Roadmap

- [x] Nasdaq ITCH parser, market event storage and order book reconstruction
- [x] Fair price models, trade flow and order flow imbalance
- [x] Self-describing signals and filters, weighted signal mix
- [x] Throttled execution and fills from the order book
- [x] Event-driven backtest with PnL, equity curve and max drawdown
- [x] REST API with background jobs
- [x] Realistic tiered commission (broker, exchange and clearing fees)
- [ ] React frontend: settings built from the catalog, equity chart, results and fills
- [ ] Cost-aware trading: trade only when the expected move beats spread and commission
- [ ] Limit orders and market making simulation (queue position, partial fills)
- [ ] More trading days, out-of-sample tests and parameter optimization
- [ ] Paper trading

## Disclaimer

This is a personal learning project. It is not investment advice, and it is not meant for trading
real money.

## License

[MIT](LICENSE)
