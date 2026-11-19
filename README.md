# Backtest

I wanted to understand how trading firms test their ideas before risking money, so I started
building my own backtesting engine in Java. Step by step, starting from a tiny "buy if the price
went up" example on ten hand-typed candles, it grew into an event-driven engine that replays full
days of real Nasdaq data.

It reads the exchange's raw messages one by one, rebuilds the order book, asks a few signals what
they think the price will do, and simulates the trades that follow: market orders that take what
the book offers, and limit orders that wait in line like everyone else. At the end it tells me how
much the strategy made or lost, how much went to commission, how deep the worst drawdown was, and
whether the price tended to move for or against me right after each fill.

> **Where it stands:** the backtest runs end to end on six days of real data, with market or limit
> orders, and can be started through a REST API. Separate research tools measure each signal on its
> own. The strategy does not make money yet, and the latest work is about finding out exactly why:
> my limit orders tend to get filled just before the price moves against them. A React frontend
> will come later.

## What it can do

**Read real market data.** Nasdaq publishes sample TotalView-ITCH 5.0 files: every order placed,
changed, executed or cancelled on the exchange in a day. The parser streams through the gzip file
(about 590 million messages, 18.7 GB unpacked for one day) and keeps the events of 22 liquid
symbols, turning them into five simple event types: `ADD`, `EXECUTE`, `CANCEL`, `DELETE` and
`TRADE`. One day is 67 to 165 million events. Six days are imported so far (2025-12-08 to 12-12 and
2026-06-12), about 47 GB in SQLite.

**Rebuild the order book.** Replaying the events in order rebuilds the full book at any moment,
order by order and at any depth. Next to it, a trade flow tracker remembers the last few seconds
of trades: who was in a hurry to buy or sell, the VWAP, how many trades happened. There is also an
order flow imbalance (OFI) measure at the best bid and ask.

**Estimate a fair price.** Mid price, microprice, multi-level price, decaying levels (deeper levels
count less) and impact price (the average price of actually buying or selling a given quantity).

**Turn opinions into a position.** Each *signal* gives an opinion from −1 (strongly sell) to +1
(strongly buy). There are six: impact price, decaying levels, back to VWAP, order flow imbalance,
trade pressure and momentum. *Filters* pause trading when conditions are bad (a quiet market or a
wide spread). The signal mix strategy takes a weighted average of the opinions and turns it into a
target position.

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
  An optional guard pulls the order (or does not place it) when the book leans against it.

The hardest part was the queue. A limit order is filled only after everyone who was there before
it. Because the data is order by order, the simulator knows exactly which orders were ahead of mine
and follows them until they are executed or cancelled. Even then it is cautious: my order is filled
only when someone trades at my price after the queue in front of me is gone, or when the price
trades through me. Hidden trades do not count.

Time needs care too. A single large order can produce many events with the very same nanosecond
timestamp. The strategy only decides once every event of a moment has been applied, so it never
reacts to a half-updated book that never really existed.

**Count the costs.** The commission model follows a tiered broker plan: a broker fee per share with
a minimum per order, plus exchange and clearing fees per share. Takers pay the exchange fee and
makers get a rebate, so every fill records which side of that it was on. By default 100 shares cost
$0.67 as a taker and $0.17 as a maker. When a limit order is filled in parts, the broker minimum is
charged once for the whole order, not once per part, just like a real broker does it. All money is
`BigDecimal`, and exchange prices stay exact integers.

**Report the result.** PnL, total commission, max drawdown, every fill, an equity curve sampled
every second, and the *markout* of the fills: how many cents the mid price moved for or against me
1 and 10 seconds after each fill, separately for maker and taker fills. The backtest warms up from
the pre-market open, trades only in the chosen period, and closes the position at the end. If
there is no data at all for the chosen symbol and day, it fails with a clear message instead of
quietly reporting a profit of zero.

**Measure the signals on their own.** A backtest only says "won" or "lost". To see *why*, the
research tools record every signal's opinion once per second and then look at what the price
actually did 1 second, 10 seconds, 1 minute and 5 minutes later: hit rate, average move in cents,
correlation between opinion and move, and on how many days the result held. They also test signals
in pairs and show how similar they are to each other. A calibrator turns an opinion into an
expected move in cents, learning on some days and checking on days it has never seen.

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
        fill from the order book ──► portfolio ──► PnL / equity / drawdown / markouts
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
- **No peeking into the future.** Events are replayed strictly in time order, decisions wait until
  a whole moment is applied, research samples are taken from the state *before* each event, and
  calibration is checked on days it did not learn from.
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
| `backtest` | Event-driven backtest loop, results and markouts |
| `research` | Command line tools that evaluate and calibrate signals over several days |
| `catalog` | The available signals, filters and execution algorithms |
| `api` | REST endpoints (Spring Boot) |
| `candle` | The first, candle-based version I learned on, kept separately |

## Built with

Java 25, Maven, Spring Boot 4.1, SQLite, JUnit 5 and AssertJ (251 unit tests), and Checkstyle
(Google Java Style with 4-space indentation).

## Running it yourself

### Requirements

- JDK 25
- Maven 3.9+ (or the Maven bundled with IntelliJ IDEA)
- Market data imported into a SQLite database (see below)

### Importing Nasdaq ITCH data

The market data is **not in this repository**. Nasdaq publishes sample TotalView-ITCH 5.0 files
(one full trading day each) at <https://emi.nasdaq.com/ITCH/Nasdaq%20ITCH/>. They are large
(8 to 18 GB compressed) and subject to Nasdaq's terms of use.

The importer keeps a fixed list of 22 liquid symbols (see `ItchImporter.SYMBOLS`) and writes their
events into a separate SQLite database. Run the `org.example.marketdata.itch.ItchImporter` main
class (for example from IntelliJ IDEA) with three program arguments:

```
<itch-file.gz> <trading-date yyyy-mm-dd> <database-file>
```

One trading day takes several minutes and roughly 4.6 to 11 GB of disk space. Running the import
again for the same day replaces that day's events.

### Build and run

```bash
mvn clean package
mvn spring-boot:run
```

The application starts on `http://localhost:8080`. By default it reads market data from
`~/market-data/market-data.db`; set the `MARKET_DATA_DB` environment variable to use another file.
The file must already exist: the backtest never creates an empty database by accident.

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
`"execution": { "id": "joinBestQuote" }` (the guard is on by default; `"guardShares": 0` turns it
off). Then check the job until its status is `DONE`:

```bash
curl http://localhost:8080/api/backtests/<job-id>
```

A wrong setting (for example an unknown signal id) is rejected right away with `400 Bad Request`.
A symbol or day without data makes the job end as `FAILED` with an explanation.

### Research tools

The research tools are plain command line programs inside the same jar. From PowerShell (keep the
`-D...` part in quotes):

```
java '-Dloader.main=org.example.research.SignalReport' -cp target/backtest-1.0-SNAPSHOT.jar `
  org.springframework.boot.loader.launch.PropertiesLauncher `
  <database> <symbol> <dates,comma,separated> [start] [end] [--max-group N]
```

`org.example.research.CalibrationReport` takes
`<database> <symbol> <signal-id> <training dates> <test dates>` instead.

## What I have learned so far

The honest answer: the strategy does not make money yet, and the numbers explain why.

**The signals are real, but small.** Measured on their own over six days and all 22 symbols, the
two order book signals (impact price and decaying levels) point the right way one second ahead on
every symbol, on at least five of the six days, and ten seconds ahead on almost all of them. That
is the most stable result I have. But the moves they predict are tiny: around a tenth of the half
spread, at most about 0.4 cents. The other signals seem to depend on the kind of stock: trade
pressure and OFI look better on wide-spread stocks and the other way round on the most liquid
ones, though with this many combinations some of that may be luck. Nothing is reliable five
minutes ahead.

I also learned how easy it is to fool myself. After four days, momentum on NVDA had been wrong
every single day, and flipping it looked clever. On the next two days it was right both times.

**Market orders cannot win with signals this small.** A market order crosses the spread, which
costs more than the signals predict, so these runs lose money even before commission.

**Limit orders run into adverse selection.** Waiting at the best price should earn the spread
instead of paying it. But my orders tend to get filled exactly when the price is about to move
against them, so the markout of maker fills is negative. An early version of this table looked
profitable before commission, but that came from a bug: the strategy sometimes decided in the
middle of a burst of same-nanosecond events, and by luck that kept it out of bad fills. After the
fix, the picture is worse but more honest.

A simple guard that pulls the order when the book leans against it helps only a little: it cuts
4 to 10% of the fills and some commission, but the markout of the remaining fills does not
improve. Leaning of the book at the moment I place the order is apparently not what predicts the
bad fills. Finding out *when* they happen is my next step.

PnL in USD on 2025-12-09, 09:35–15:55, signal mix 50/30/20, default filters, max position 100,
default tiered commission:

| Symbol | Execution | Fills | Commission | PnL before commission | PnL |
|---|---|---:|---:|---:|---:|
| AAPL | throttled, 30 s | 746 | 368.72 | −434.77 | −803.49 |
| AAPL | throttled, 300 s | 77 | 38.51 | −281.50 | −320.01 |
| AAPL | join best quote, no guard | 1177 | 287.76 | −210.92 | −498.68 |
| AAPL | join best quote, guard 0.2 c | 1047 | 252.27 | −210.00 | −462.27 |
| NVDA | throttled, 30 s | 758 | 359.30 | −26.24 | −385.54 |
| NVDA | throttled, 300 s | 77 | 36.23 | −121.12 | −157.35 |
| NVDA | join best quote, no guard | 3151 | 807.23 | −341.22 | −1148.45 |
| NVDA | join best quote, guard 0.2 c | 3028 | 769.79 | −337.72 | −1107.51 |
| QQQ | throttled, 30 s | 760 | 354.20 | −69.96 | −424.16 |
| QQQ | throttled, 300 s | 77 | 35.39 | +69.31 | +33.92 |
| QQQ | join best quote, no guard | 696 | 208.83 | −3.57 | −212.40 |
| QQQ | join best quote, guard 0.2 c | 660 | 197.01 | +3.83 | −193.18 |

Markout of the limit order fills without the guard, in cents per share (positive is good for me).
A maker fill starts half a spread *ahead* compared to the mid price, so a negative number means
the price moved against me by more than that:

| Symbol | after 1 s | after 10 s |
|---|---:|---:|
| AAPL | −0.35 | −0.73 |
| NVDA | −0.57 | −0.65 |
| QQQ | −0.09 | −0.35 |

All of this is still one backtest day, which is far too little to draw real conclusions.

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
- [x] Six days of data and research tools: signals alone, in pairs, per horizon, calibrated in cents
- [x] Decisions only after every event of the same moment; clear errors on missing data
- [x] Markouts of fills; a first guard against adverse selection
- [ ] Find out when the bad limit order fills happen, and protect against exactly that
- [ ] Cost-aware trading: trade only when the expected move beats spread and commission
- [ ] Separate horizons: short signals steer execution, longer ones set the target position
- [ ] Smoother target position (fewer trades when the target jumps back and forth)
- [ ] Quoting on both sides, skewed towards the wanted position (real market making)
- [ ] More trading days, significance tests and walk-forward testing
- [ ] React frontend: settings built from the catalog, equity chart, results and fills
- [ ] Paper trading

## Disclaimer

This is a personal learning project. It is not investment advice, and it is not meant for trading
real money.

## License

[MIT](LICENSE)
