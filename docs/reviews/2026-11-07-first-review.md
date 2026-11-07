# Első független review – 2026-11-07

- **Kód állapota:** `54e3e9a`
- **Hatókör:** az egész projekt
- **Reviewer:** `.claude/agents/project-reviewer.md`. Tiszta lappal indult, nem látta a fejlesztés
  menetét.
- **Ellenőrzés:** az F4, F10 és F11 találatot utólag a kódban is megnéztük, ezek igazak.

## Összefoglaló

KRITIKUS (biztosan rossz számot adó) hibát nem talált. Amit külön kiemelt:
- az ITCH bájt-offsetek a 5.0-s specifikáció szerint helyesek;
- a sorban állás szimulációja óvatos és jól tesztelt;
- a mintavétel az esemény előtti állapotot használja, így nem lát előre;
- a pénz `BigDecimal`, a rétegek tiszták, az eredmény determinisztikus;
- a kézzel újraszámolt tesztszámok stimmelnek.

## Találatok

### FONTOS

**F1 – Döntés azonos időbélyegű eseménysorozat közepén, nulla késéssel**
(`backtest/EventBacktester.java` 162–181)
- **Mi a gond:** az ITCH-ben gyakran jön több esemény ugyanabban a nanoszekundumban. Ilyen egy
  söprés (több EXECUTE), vagy a replace (U), amelyből DELETE + ADD lesz. A backtester minden
  esemény után külön dönt és köt, így olyan félkész könyvre is reagálhat, amely a valóságban nem
  létezett.
- **Javítás:** az azonos `ts`-ű eseményeket kötegben dolgozzuk fel, és csak a köteg végén döntünk.
  Később jöhet egy `LatencyModel`.

**F2 – Hiba helyett csendes „0 $ PnL, DONE”**
(`marketdata/SqliteMarketEventStore.java` 48, `EventBacktester.java` 183–208)
- **Mi a gond:** három eset vezet ide: rossz symbol (pl. kisbetűs `aapl`), be nem importált vagy
  hétvégi dátum, rossz adatbázis-útvonal. Rossz útvonalnál az SQLite ráadásul létrehoz egy üres
  fájlt.
- **Javítás:** 0 esemény → hiba; a meglévő adatbázis megnyitása nem hoz létre fájlt; a symbol
  nagybetűs lesz.

**F3 – A kiértékelőben nincs szórás / t-érték, miközben sok kombinációt tesztelünk**
(`research/SignalEvaluator.java` 170–218)
- **Mi a gond:** egy haszontalan jel 6 napból legalább 5-ön ~11% eséllyel „jó”. 528 kombinációnál
  ez ~58 véletlen „sikert” jelent.
  - Az impactPrice 22/22 messze a véletlen felett van.
  - A VWAP- és OFI-mintázatok részvényenként beleférhetnek a véletlenbe.
- **Javítás:** standard hiba / t-érték napi átlagokból vagy nem átfedő ablakokból, és a tesztelt
  kombinációk számának kiírása.

### JAVASLAT

- **F4 – A `research` a Spring-es `api.catalog.CatalogConfig`-tól függ.**
  Javítás: a lista a `Catalog.standard()`-ba kerül.
- **F5 – Nincs teszt** az `ItchParser`, az `ItchReader`, a `SqliteMarketEventStore` és a
  `Portfolio` osztályra.
- **F6 – Hiányos zárásnál rossz a végeredmény.** Ha a nap végén nincs mid ár, vagy a zárás csak
  részben teljesül, a `finalEquity` a zárás előtti pontból jön, és nincs figyelmeztetés.
- **F7 – A `JoinBestQuote` ottmaradó cetlije „locked” állapotba kerülhet.** Ha valaki pont a mi
  árunkra tesz ellenoldali ajánlatot, a szimuláció nem tölti ki a cetlinket. Ez pesszimista
  torzítás, a maker-filleket alábecsülheti.
- **F8 – A `TradeFlow` / `OrderFlowImbalance` ablaka csak új eseménynél öregszik.** Csendes
  részvénynél ez torzítja a mintavételt. Javítás: `advanceTo(now)`.
- **F9 – ITCH apróságok.** A C üzenet Printable jelzőjét a parser nem nézi; a B (Broken Trade)
  üzenetet nem kezeli; a Q (cross) kimarad (ezt dokumentálni kell).
- **F10 – Lassú import.** Az import előtti `DELETE` symbol nélkül szűr, ezért nem használja az
  indexet, és a teljes táblát végigolvassa.
- **F11 – Elavult Javadoc.** A `BacktestRequest` szerint a minimum „of one fill”, de már
  „of one order”.
- **F12 – Felesleges várakozás.** A `ThrottledExecution` cooldownja akkor is elindul, ha a market
  order nem teljesült.

## Javítási terv

1. **1. csomag – F2 + F11 + F4** (a terv elfogadva, még nincs megírva)
   - **F2a:** ha a kereskedési időszakban 0 esemény van → hiba, érthető üzenettel. Ezt az
     `EventBacktester` és a `SignalSampler` is megkapja; a REST-es job ilyenkor FAILED lesz.
   - **F2b:** a `SqliteMarketEventStore` két gyártó metódust kap:
     - `openOrCreate` – csak az importer használja;
     - `openExisting` – a backtest és a kutató eszközök használják; ha nincs fájl, hibát ad és
       nem hoz létre semmit.
     Csak olvasható módot nem kapcsolunk be, mert WAL módban gondot okozhat.
   - **F2c:** a symbol nagybetűsre alakul (`toUpperCase(Locale.ROOT)`), a REST-ben és a CLI-kben is.
   - **F11:** a Javadoc „fill” helyett „order” lesz.
   - **F4:** új `Catalog.standard()`. A `CatalogConfig` és a CLI-k ezt hívják.
   - **Új tesztek:** üres időszak (backtester, sampler); `openExisting` hiányzó fájllal;
     `openOrCreate`; kisbetűs symbol a job service-ben; `Catalog.standard()`.
   - **Ellenőrzés:**
     - teljes build;
     - a 8099-es porton a QQQ throttled300 eredmény változatlan (+46.71);
     - a hibás kérések (szombat, kisbetű, rossz útvonal) hibát adnak.
   - **Javasolt commit:** „Fail loudly on missing market data and move the catalog out of Spring”.
2. **F1 – kötegelés azonos időbélyegre.** Utána a backtest eredmények összevetése a mostaniakkal.
3. **F3 – szórás / t-érték a kiértékelőbe**, és a tesztelt kombinációk száma.
4. **F5 – tesztek:** parser, tároló, portfólió.
5. **A többi (F6–F10, F12)** később, a szakértői terv mellett (`docs/plans/expert-feedback-plan.md`).
