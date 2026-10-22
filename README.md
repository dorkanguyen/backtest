# Backtest

I wanted to understand how trading firms test their ideas before risking money, so I started
building my own backtesting engine in Java. Step by step, starting from a tiny "buy if the price
went up" example on ten hand-typed candles, it grew into an event-driven engine that replays a full
day of real Nasdaq data.

It reads the exchange's raw messages one by one, rebuilds the order book, asks a few signals what
they think the price will do, and simulates the trades that follow: market orders that take what
the book offers, and limit orders that wait in line like everyone else. At the end it tells me how
much the strategy made or lost, how much went to commission, and how deep the worst drawdown was.

> **Where it stands:** the backtest runs end to end on real data, with market or limit orders, and
> can be started through a REST API. Next on my list: trading only when it is worth the cost, and
> testing on more days. A React frontend will come after that.

## What it can do

**Read real market data.** Nasdaq publishes sample TotalView-ITCH 5.0 files: every order placed,
changed, executed or cancelled on the exchange in a day. The parser streams through the gzip file
(about 590 million messages, 18.7 GB unpacked) and keeps the events of 22 liquid symbols, turning
them into five simple event types: `ADD`, `EXECUTE`, `CANCEL`, `DELETE` and `TRADE`. One day is
about 67 million events, stored in SQLite.

**Rebuild the order book.** Replaying the events in order rebuilds the full book at any moment,
order by order and at any depth. Next to it, a trade flow tracker remembers the last few seconds
of trades: who was in a hurry to buy or sell, the VWAP, how many trades happened. There is also an
order flow imbalance (OFI) measure at the best bid and ask.

**Estimate a fair price.** Mid price, microprice, multi-level price, decaying levels (deeper levels
count less) and impact price (the average price of actually buying or selling a given quantity).

**Turn opinions into a position.** Each *signal* gives an opinion from −1 (strongly sell) to +1
(strongly buy). The current ones are impact price, decaying levels, back to VWAP and order flow
imbalance. *Filters* pause trading when conditions are bad (a quiet market or a wide spread). The
signal mix strategy takes a weighted average of the opinions and turns it into a target position.

Every signal, filter and execution algorithm describes itself: its name, what it does, and each
setting with a default, a minimum and a maximum. A user interface can build its settings page from
that list, so adding a new signal does not mean touching the frontend.

**Trade it, as realistically as I can.** The strategy only says *what* position it wants. A
separate execution algorithm decides *how* to get there, by placing and cancelling orders:

- **Throttled** uses market orders. It ignores small changes and waits between trades to save
  commission. Market orders walk the real book level by level, so a big order gets a worse average
  price, and a thin book means a partial fill.
- **Join best quote** uses limit orders. It keeps one order at the best bid or ask, leaves it alone
  while it is still good, and moves it when someone offers a better price or the target changes.

The hardest part was the queue. A limit order is filled only after everyone who was there before
it. Because the data is order by order, the simulator knows exactly which orders were ahead of mine
and follows them until they are executed or cancelled. Even then it is cautious: my order is filled
only when someone trades at my price after the queue in front of me is gone, or when the price
trades through me. Hidden trades do not count.

**Count the costs.** The commission model follows a tiered broker plan: a broker fee per share with
a minimum per order, plus exchange and clearing fees per share. Takers pay the exchange fee and
makers get a rebate, so every fill records which side of that it was on. By default 100 shares cost
$0.67 as a taker and $0.17 as a maker. When a limit order is filled in parts, the broker minimum is
charged once for the whole order, not once per part, just like a real broker does it. All money is
`BigDecimal`, and exchange prices stay exact integers.

**Report the result.** PnL, total commission, max drawdown, every fill, and an equity curve sampled
every second. The backtest warms up from the pre-market open, trades only in the chosen period, and
closes the position at the end.

## How it fits together

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

The execution step in a bit more detail:

```
execution algorithm ──► instructions: place / cancel order
        ▲                         │
        │ fill reports            ▼
        └──────────── execution simulator
                       market order: walks the book at once (taker)
                       limit order:  waits in the queue (maker)
```

A few rules I tried to stick to:

- **The strategy never trades directly.** It only returns a target position. That way it can never
  buy twice by accident, and the "how" can change without touching the "what".
- Each part lives in its own package, and the core knows nothing about Spring.
- Anything that will get more advanced later sits behind an interface with a simple first version
  (signals, filters, execution algorithms, commission, event storage).
- The same strategy code should one day run on live data. Only the data source and the execution
  layer would change.

| Package | What lives there |
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
| `candle` | The first, candle-based version I learned on, kept separately |

## Built with

Java 25, Maven, Spring Boot 4.1, SQLite, JUnit 5 and AssertJ (179 unit tests), and Checkstyle
(Google Java Style with 4-space indentation).

## Running it yourself

### Requirements

- JDK 25
- Maven 3.9+ (or the Maven bundled with IntelliJ IDEA)
- Market data imported into a SQLite database (see below)

### Importing Nasdaq ITCH data

The market data is **not in this repository**. Nasdaq publishes sample TotalView-ITCH 5.0 files
(one full trading day each) at <https://emi.nasdaq.com/ITCH/Nasdaq%20ITCH/>. They are large
(several GB compressed) and subject to Nasdaq's terms of use.

The importer keeps a fixed list of 22 liquid symbols (see `ItchImporter.SYMBOLS`) and writes their
events into a separate SQLite database. Run the `org.example.marketdata.itch.ItchImporter` main
class (for example from IntelliJ IDEA) with three program arguments:

```
<itch-file.gz> <trading-date yyyy-mm-dd> <database-file>
```

One trading day takes a few minutes and about 4.6 GB of disk space. Running the import again for
the same day replaces that day's events.

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

Anything you leave out gets its default value. For limit orders, use
`"execution": { "id": "joinBestQuote" }`. Then check the job until its status is `DONE`:

```bash
curl http://localhost:8080/api/backtests/<job-id>
```

A wrong setting (for example an unknown signal id) is rejected right away with `400 Bad Request`.

## What I have learned so far

The honest answer: the strategy does not make money yet, and the numbers explain why.

On one day of data (2025-12-09) the signals point in the right direction on average, but they only
predict moves of a fraction of a cent over the next few seconds. A market order has to cross the
spread, which is usually 1 to 4 cents. So the market-order version loses money even before any
commission.

Limit orders flip that around: instead of paying the spread, the strategy earns it. Before
commission it was profitable on all three symbols I tested. Commission still takes more than it
earns, though.

PnL in USD, 09:35–15:55, signal mix 50/30/20, default filters, max position 100, default tiered
commission:

| Symbol | Execution | Fills | Commission | PnL before commission | PnL |
|---|---|---:|---:|---:|---:|
| AAPL | throttled, 30 s | 746 | 368.86 | −406.63 | −775.49 |
| AAPL | throttled, 300 s | 77 | 38.51 | −281.89 | −320.40 |
| AAPL | join best quote | 396 | 97.88 | +31.17 | −66.71 |
| NVDA | throttled, 30 s | 759 | 357.79 | −53.13 | −410.92 |
| NVDA | throttled, 300 s | 77 | 36.23 | −121.81 | −158.04 |
| NVDA | join best quote | 506 | 119.12 | +17.27 | −101.85 |
| QQQ | throttled, 30 s | 760 | 351.59 | −44.44 | −396.03 |
| QQQ | throttled, 300 s | 77 | 35.29 | +82.00 | +46.71 |
| QQQ | join best quote | 171 | 50.46 | +31.67 | −18.79 |

One day is far too little to draw real conclusions, but it shows where to look next: trade only
when the expected move is bigger than the cost, and test on more days. Charging the broker minimum
once per order instead of once per fill already cut the commission of the limit order runs by up
to a quarter.

## Roadmap

- [x] Nasdaq ITCH parser, market event storage and order book reconstruction
- [x] Fair price models, trade flow and order flow imbalance
- [x] Self-describing signals and filters, weighted signal mix
- [x] Throttled execution and fills from the order book
- [x] Event-driven backtest with PnL, equity curve and max drawdown
- [x] REST API with background jobs
- [x] Realistic tiered commission (broker, exchange and clearing fees)
- [x] Limit orders with queue position simulation, maker/taker fees, join best quote algorithm
- [x] Minimum commission per order instead of per fill (as brokers charge it)
- [ ] Cost-aware trading: trade only when the expected move beats spread and commission
- [ ] React frontend: settings built from the catalog, equity chart, results and fills
- [ ] More trading days, out-of-sample tests and parameter optimization
- [ ] Paper trading

## Disclaimer

This is a personal learning project. It is not investment advice, and it is not meant for trading
real money.

## License

[MIT](LICENSE)
