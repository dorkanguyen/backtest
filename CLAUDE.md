# CLAUDE.md – Backtest projekt

Ez a fájl a projekt tartós emlékezete. Csak olyat rögzítünk itt, ami később is segít a folytatásban.
Nyelv: a felhasználóval magyarul kommunikálunk.

## Projekt célja

Trading / backtesting alkalmazás, amely történelmi piaci adatokon teszteli a stratégiákat.

Hosszú távú cél: order book + trade adat alapú **fair price** stratégia:

```
market data → order book + trades → fair price → market price vs. fair price
  → target position / BUY / HOLD / SELL → order → execution engine → fill
  → portfolio → PnL / equity / drawdown
```

A stratégia paraméterei (fair price paraméterek, buy/sell threshold, max pozíció, pozícióméretezés,
risk per trade stb.) később konfigurálhatók legyenek a Java kód átírása nélkül.

Az első cél: egy **működő, egyszerű történelmi backtest**. Nem akarunk mindent egyszerre.

## Technológia

- Java, Maven, Spring Boot, IntelliJ IDEA
- SQLite kezdetben (PostgreSQL csak később, ha tényleg kell – első verzióban NEM)
- REST API, később React frontend
- Git + GitHub
- Adatforrás: Databento (kezdetben HTTP API-n keresztül Java-ból).
  Később: trades, BBO, MBP-10 (L2), esetleg MBO (L3).

## Architekturális szabályok

- A **Strategy** soha nem hajt végre trade-et közvetlenül. Csak target positiont vagy order-igényt ad vissza.
- Külön **Execution engine** szimulálja a teljesülést (fill) az order book / piaci adat alapján.
- Külön felelősségi területek (külön package-ek): **Market Data**, **Strategy**, **Order**, **Execution**, **Portfolio**.
- Ha egy architekturális döntésnek több értelmes megoldása van: röviden bemutatni az opciókat és a felhasználót megkérdezni.

## Biztonság

- API kulcs, jelszó, secret SOHA nem kerülhet: Java kódba, CLAUDE.md-be, dokumentációba, Git commitba, GitHubra.
- Secret kezelése: environment variable (pl. `DATABENTO_API_KEY`) vagy más biztonságos megoldás.

## Munkamódszer (a felhasználó kérése – mindig tartsd be)

- **Fájlt módosítani, beállítást változtatni vagy kódot írni csak a felhasználó kifejezett beleegyezése után szabad.**
  Előbb elmondani, mit és miért, megvárni az igent, utána megcsinálni és egyszerűen elmagyarázni.
- Mindent úgy magyarázz, hogy egy kezdő is értse: egyszerű szavakkal, a szakszavakat is elmagyarázva.
- A felhasználó **teljesen kezdő** programozásban és trading rendszerfejlesztésben. A cél a **megértés**, nem a gyorsaság. NE SIESS.
- Ne írj meg egyszerre komplett alkalmazást; ne ugorj rögtön a komplex engine-re.
- Minden lépés előtt röviden: **MIT** csinálunk, **MIÉRT**, **HOGYAN illeszkedik** a rendszerbe.
- Utána **egyetlen konkrét feladat**. IntelliJ-teendőknél pontos lépések. Kódnál: kód + egyszerű magyarázat.
- Ezután **megállni és megvárni a visszajelzést**. Soha ne adj 15–20 lépést egyszerre.
- Java: az alap szintaxist (int, double, String, változók, számítások) ismeri. OOP-t, osztályt, interfészt,
  package-et, dependency injectiont csak akkor magyarázz, amikor a projektben ténylegesen szükség van rá – mindig a projekt példáján.
- Trading fogalmak (candle, OHLCV, long/short, order, fill, PnL, equity, drawdown, bid/ask, spread, order book stb.):
  találkozott velük, de **nem érti igazán**. Ha szükség van rá, magyarázd el újra egyszerűen, konkrét számpéldával.
  Ne zúdíts rá sok elméletet egyszerre.
- Maven, Spring Boot, SQLite, Git: ne csak használd, hanem magyarázd el, miért és hogyan működik.
- Git: minden használt parancsot röviden magyarázz el. **Ne commitolj és ne csinálj Git műveletet engedély nélkül.**
  Mindig mutasd meg előre, mit készülünk csinálni. Fontos állapotoknál javasolj commitot (pl. "Add first REST endpoint").
- CLAUDE.md: fontos új döntésnél először szólj, hogy szerinted érdemes rögzíteni, majd frissítsd. Git-tel követjük.
- Dokumentáció (`docs/architecture.md`, `docs/trading-concepts.md` stb.) csak akkor készül, amikor tényleg kell.

## Fejlesztési terv (módosítható)

**Döntés (2026-09-28):** „előbb legyen működő dolog” – először egy egyszerű, végigfutó backtest
candle-adaton, és csak utána jön a Databento, az order book és a fair price. A komplex részek a már
működő motort bővítik (az execution engine-t ekkor át kell majd alakítani – ez vállalt kompromisszum).

1. Projekt alapok (Git, .gitignore, első commit, Maven alapok)
2. Spring Boot (indulás, első REST endpoint, HTTP request/response)
3. SQLite (tábla, sor, oszlop, primary key, Spring kapcsolat)
4. Market data modell (Candle / OHLCV, kis kézi adatsor)
5. **Egyszerű backtest vertikális szelet**: egyszerű Strategy → Order → egyszerű Execution (fill a candle árán)
   → Portfolio → PnL, equity curve, drawdown. Már itt külön package-ek: marketdata, strategy, order, execution, portfolio.
6. Databento kapcsolat (HTTP, env var API kulcs), valódi adat betöltése
7. Order book feldolgozás (BBO, majd MBP-10)
8. Fair price
9. Fair price stratégia
10. Order book alapú execution szimuláció (market/limit order, részteljesülés)
11. Backtest engine bővítése eseményalapú feldolgozásra (trades + order book időrendben)
12. REST API a backtesthez
13. Frontend (React)
14. Későbbi fejlesztések (PostgreSQL, L3/MBO, paraméteroptimalizálás, konfigurálható stratégiák)

## Aktuális állapot

- A projekt egy üres IntelliJ Maven sablon: `pom.xml` (groupId `org.example`, Java 24), `src/main/java/org/example/Main.java` (Hello World).
- Spring Boot még nincs a projektben.
- Git repository létrehozva, a fő ág neve `main`.
- `.gitignore` kiegészítve: `.env`, `*.db`, `*.sqlite`, `.idea/workspace.xml`.
- WSL-ben nincs telepítve Java/Maven (a fordítás IntelliJ-ből / Windowsról történik).

## Következő lépés

- Phase 1: első commit („Initial project setup”) – a felhasználó futtatja.
- Utána Phase 2: Spring Boot.
