# Backtest

An event-driven backtesting engine for order book trading strategies, written in Java and built
step by step as a learning project.

It replays real exchange data message by message, rebuilds the limit order book, lets a set of
configurable signals decide on a target position, and simulates the resulting market and limit
orders against the book (including the queue position of limit orders) to measure PnL, equity and
drawdown.

> **Status:** work in progress. The event-driven backtest runs end to end on real Nasdaq data with
> market or limit orders and can be started over a REST API. Next: cost-aware trading and more
> trading days; a React frontend comes later.

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

- Execution algorithms turn the target position into instructions (place or cancel an order) and
  are told about every fill
- **Throttled execution** (market orders): ignores small position changes and waits between trades
  to save commission
- **Join best quote** (limit orders): rests one order at the best bid or ask, leaves it alone while
  it is still good, and moves it when others offer a better price or the target changes
- Market orders are filled from the real book, level by level (partial fills when the book is thin)
- Limit orders wait in the queue: the simulator follows the orders that were there before ours
  (order by order, from the L3 data) and fills ours only when they are gone and someone trades at
  our price, or the price trades through us (a cautious rule; hidden trades are ignored)
- Every fill records whether it added liquidity (maker) or took it (taker)
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

In more detail, the execution step works like this:

```
execution algorithm ──► instructions: place / cancel order
        ▲                         │
        │ fill reports            ▼
        └──────────── execution simulator
                       market order: walks the book at once (taker)
                       limit order:  waits in the queue (maker)
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
| `order` | Orders: side, type (market or limit), id and limit price |
| `execution` | Execution algorithms, order instructions, fills from the book, limit order queue simulation, commission models |
| `portfolio` | Cash, position and equity |
| `backtest` | Event-driven backtest loop and results |
| `catalog` | The available signals, filters and execution algorithms |
| `api` | REST endpoints (Spring Boot) |
| `candle` | The first, candle-based learning version, kept separately |

## Tech stack

- Java 25, Maven
- Spring Boot 4.1 (REST API)
- SQLite
- JUnit 5 and AssertJ (175 unit tests)
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

Missing settings get their default values. To trade with limit orders instead, use
`"execution": { "id": "joinBestQuote" }`. Then ask for the job until its status is `DONE`:

```bash
curl http://localhost:8080/api/backtests/<job-id>
```

A wrong setting (for example an unknown signal id) is rejected at once with `400 Bad Request`.

## What the first results show

On one day of data (2025-12-09), the signals point in the right direction on average, but they
predict moves of only a fraction of a cent over the next seconds. Crossing the spread with market
orders costs more than that, so the market-order version loses money even without any commission.

With limit orders the strategy earns the spread instead of paying it, and it becomes profitable
before commission on all three tested symbols, but commission still takes more than it earns:

PnL in USD, 09:35–15:55, signal mix 50/30/20, default filters, max position 100, default tiered
commission:

| Symbol | Execution | Fills | Commission | PnL before commission | PnL |
|---|---|---:|---:|---:|---:|
| AAPL | throttled, 30 s | 746 | 368.86 | −406.63 | −775.49 |
| AAPL | throttled, 300 s | 77 | 38.51 | −281.89 | −320.40 |
| AAPL | join best quote | 396 | 122.60 | +31.17 | −91.43 |
| NVDA | throttled, 30 s | 759 | 357.79 | −53.13 | −410.92 |
| NVDA | throttled, 300 s | 77 | 36.23 | −121.81 | −158.04 |
| NVDA | join best quote | 506 | 156.82 | +17.27 | −139.55 |
| QQQ | throttled, 30 s | 760 | 351.59 | −44.44 | −396.03 |
| QQQ | throttled, 300 s | 77 | 35.29 | +82.00 | +46.71 |
| QQQ | join best quote | 171 | 52.56 | +31.67 | −20.89 |

This is one day of data, so it is not a reliable conclusion, only a direction for the next steps:
cost-aware trading, charging the minimum commission per order instead of per fill, and more data.

## Roadmap

- [x] Nasdaq ITCH parser, market event storage and order book reconstruction
- [x] Fair price models, trade flow and order flow imbalance
- [x] Self-describing signals and filters, weighted signal mix
- [x] Throttled execution and fills from the order book
- [x] Event-driven backtest with PnL, equity curve and max drawdown
- [x] REST API with background jobs
- [x] Realistic tiered commission (broker, exchange and clearing fees)
- [x] Limit orders with queue position simulation, maker/taker fees, join best quote algorithm
- [ ] Cost-aware trading: trade only when the expected move beats spread and commission
- [ ] Minimum commission per order instead of per fill (as brokers charge it)
- [ ] React frontend: settings built from the catalog, equity chart, results and fills
- [ ] More trading days, out-of-sample tests and parameter optimization
- [ ] Paper trading

## Disclaimer

This is a personal learning project. It is not investment advice, and it is not meant for trading
real money.

## License

[MIT](LICENSE)
