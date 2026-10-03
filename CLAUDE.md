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

- A Git commitok szerzői e-mail címe a GitHub rejtett címe (`...+dorkanguyen@users.noreply.github.com`).
  A felhasználó személyes e-mail címe ne kerüljön a Git történetébe (a repo később nyilvános lehet).
- API kulcs, jelszó, secret SOHA nem kerülhet: Java kódba, CLAUDE.md-be, dokumentációba, Git commitba, GitHubra.
- Secret kezelése: environment variable (pl. `DATABENTO_API_KEY`) vagy más biztonságos megoldás.

## Munkamódszer (a felhasználó kérése – mindig tartsd be)

- **Fájlt módosítani, beállítást változtatni vagy kódot írni csak a felhasználó kifejezett beleegyezése után szabad.**
  Előbb elmondani, mit és miért, megvárni az igent, utána megcsinálni és egyszerűen elmagyarázni.
- **Kivétel: a CLAUDE.md.** Ezt Claude magától, engedélykérés nélkül frissíti, de **utólag mindig leírja**, mit módosított.
  (A CLAUDE.md commitolása továbbra is csak engedéllyel történik.)
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
- CLAUDE.md: fontos új döntést magától rögzít, és utólag jelzi. Git-tel követjük.
- Dokumentáció (`docs/architecture.md`, `docs/trading-concepts.md` stb.) csak akkor készül, amikor tényleg kell.
- **Ne kérdezd, hogy „mára elég-e”** – a felhasználó szól, ha abba akarja hagyni. Egy lépés után egyszerűen a
  következő lépés jöhet (a megállás és visszajelzés-várás lépésenként továbbra is marad).
- **Ahol lehet, a profi megoldást választjuk** (nem a leggyorsabbat) – a felhasználó kérése (2026-10-02).
- A `git add .` használható, de előtte mindig `git status` (nehogy secret vagy nem kívánt fájl kerüljön be).

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

- A projekt egy üres IntelliJ Maven sablon: `pom.xml` (groupId `org.example`, eredetileg Java 24, most Java 25), `src/main/java/org/example/Main.java` (Hello World).
- Spring Boot még nincs a projektben.
- **Phase 1 (Git rész) kész:** Git repository (`main` ág), `.gitignore` kiegészítve
  (`.env`, `*.db`, `*.sqlite`, `.idea/workspace.xml`), első commit: „Initial project setup”.
- GitHub: https://github.com/dorkanguyen/backtest – **privát**, később nyilvános lehet. Remote neve: `origin`.
  A Git a `gh` CLI bejelentkezését használja (`gh auth setup-git`).
- WSL-ben nincs telepítve Java/Maven (a fordítás IntelliJ-ből / Windowsról történik).
- **Phase 1 teljesen kész (2026-09-29):** Maven elmagyarázva (`pom.xml`, `target/` mappa, `clean`, `package`);
  a felhasználó IntelliJ-ből sikeresen futtatta. A buildet az IntelliJ Maven ablakából (Lifecycle) indítja.
- A Windows gépen telepített JDK-k: 1.8, Corretto 22, OpenJDK 24, OpenJDK 25 (`~/.jdks` és `Program Files/Java`).
- **Java 25-re átállva (2026-09-29):** `pom.xml` (`maven.compiler.source/target` = 25) és IntelliJ Project SDK = `openjdk-25`.
  `clean` + `package` Java 25-tel is BUILD SUCCESS.

## Következő lépés

- **Döntés (2026-09-29): Java 25 LTS** (a 24 már nem támogatott; a 25 telepítve van).
- Commit kész: „Switch to Java 25 LTS” (pusholva).
- **Döntés (2026-09-29): a Spring Bootot kézzel adjuk hozzá a meglévő `pom.xml`-hez** (nem Spring Initializr-rel),
  lépésenként: 1) `parent`, 2) `spring-boot-starter-webmvc` dependency (a régi `spring-boot-starter-web` a 4.x-ben elavult), 3) `spring-boot-maven-plugin`, 4) indító osztály.
  Verzió: Spring Boot 4.1.1 (a legfrissebb stabil verzió 2026-09-29-én a Maven Centralon).
- **Kész (2026-09-29):** `parent` blokk (a felhasználó maga írta be), Reload + `clean package` → BUILD SUCCESS.
- **Kész (2026-09-29):** `spring-boot-starter-webmvc` dependency (a felhasználó írta be), Reload + `clean package` → BUILD SUCCESS.
- IntelliJ tippek a felhasználónak: Reload = dupla `Shift` → „Reload All Maven Projects” (vagy `Ctrl+Shift+O` a `pom.xml`-ben),
  mert az ikont nehezen találja. Ha a felhasználó a terminálból másol kódot, a Claude Code oldalsávjának szövege is
  bekerülhet → a módosított fájlt mindig ellenőrizni kell. **A sorvégi szóközöket is** (pl. `od -c`/`cat -A` sor
  közepén; fájl végén a `cat -A` nem mutatja!): 2026-09-30-án egy `always ` (szóközzel) érték miatt nem indult a Spring
  („No enum constant ...DatabaseInitializationMode.ALWAYS ”) – a `.properties` fájl a sorvégi szóközt az érték részének veszi.
- **Kész (2026-09-29):** `spring-boot-maven-plugin` (Claude írta be). `clean package` → BUILD SUCCESS, a jar ~2 KB-ról
  ~19,9 MB-ra nőtt (fat jar). A buildet Claude futtatta a felhasználó kérésére (Windows PowerShellből, az IntelliJ beépített
  Mavenjével és az `openjdk-25.0.1` JDK-val), mert a felhasználónak sietnie kellett.
- Commit: „Add Spring Boot parent, webmvc starter and Maven plugin” (pusholva).
- **Kész (2026-09-29): Spring Boot indító osztály (4/4).** A felhasználó a `Main.java`-t IntelliJ Refactor → Rename-mel
  átnevezte `BacktestApplication.java`-ra, és maga írta be a `@SpringBootApplication` + `SpringApplication.run` kódot.
  (Tisztáztuk: a Git ág neve `main` és a Java osztály neve `Main` független egymástól.)
- **Kész (2026-09-29): első indítás** (`spring-boot:run` a Maven ablak Plugins mappájából) sikeres, a felhasználó látta a
  Whitelabel Error Page-et (404) a `http://localhost:8080` címen → a szerver fut. **Phase 2 Spring Boot alapjai készen.**
- Commit: „Add Spring Boot application class” (pusholva).
- **Kész (2026-09-29): első REST endpoint.** `org.example.api.HelloController` (`@RestController`, `@GetMapping("/hello")`
  → „Hello, backtest!”), a felhasználó írta be, böngészőben kipróbálta. Elmagyarázva: HTTP request/response, GET/POST,
  package, component scan (a Spring csak a `BacktestApplication` package-ében és alatta keres).
- Később rendbe teendő: a `HelloController.java` CRLF sorvégű (IntelliJ), a többi fájl LF → `.gitattributes` javaslat.
- Commit: „Add first REST endpoint” (pusholva). **Phase 2 kész.**
- **Phase 3 (SQLite) elkezdve (2026-09-29):** fogalmak elmagyarázva egy `prices` tábla példáján (tábla, sor, oszlop,
  primary key, auto increment, SQL `SELECT`, driver). A felhasználó jól válaszolt a primary key kérdésekre.
- **Döntés (2026-09-29): JdbcTemplate** (`spring-boot-starter-jdbc` + `org.xerial:sqlite-jdbc`), NEM JPA/Hibernate.
  Ok: tanuláshoz átlátható (mi írjuk az SQL-t), SQLite-tal gond nélkül megy, sok adatnál gyors. JPA esetleg később (PostgreSQL).
  Mindkét dependency verzióját a Spring Boot 4.1.1 parent kezeli (sqlite-jdbc 3.53.2.1) → nem kell `<version>`.
- **Kész (2026-09-29):** a két dependency a `pom.xml`-ben (a felhasználó írta be), Reload + `clean package` → BUILD SUCCESS,
  a jar ~19,9 MB-ról ~33 MB-ra nőtt (az sqlite-jdbc sok operációs rendszer natív könyvtárát tartalmazza).
- **Kész (2026-09-30):** `src/main/resources/application.properties` → `spring.datasource.url=jdbc:sqlite:backtest.db`
  (a felhasználó hozta létre és adta a Githez). `spring-boot:run` hibátlanul indul. A `backtest.db` MÉG NEM jön létre,
  és Hikari log sincs, mert a Spring csak az első valódi DB-használatkor kapcsolódik (ezt Claude előbb rosszul jósolta).
- A felhasználó most tanulta meg, mi a log (Run ablak alul, `Alt+4`; INFO/WARN/ERROR), és hogyan kell olvasni.
- Commit: „Add SQLite database configuration” (pusholva).
- **Döntés (2026-09-30): az első tábla rögtön a `candles` tábla** (nem egy `prices` gyakorló tábla) – a felhasználó
  választása, tudva, hogy így a candle/OHLCV fogalom (Phase 4 eleje) most jön. A táblát `schema.sql` hozza létre indításkor.
- **Kész (2026-09-30):** candle / OHLCV elmagyarázva (AAPL napi példa, gyertya-rajz, zöld/piros, timeframe). A felhasználó
  egy 4 trade-es 1 perces példán hibátlanul kiszámolta az OHLCV-t és a színt.
- **Kész (2026-09-30):** a felhasználó megírta a `schema.sql`-t és a `spring.sql.init.mode=always` sort (Claude egy
  memóriabeli SQLite-ban kétszer lefuttatva ellenőrizte: működik). Az első indítás a sorvégi szóköz miatt elhasalt, javítás után sikeres:
  létrejött a `backtest.db` (12 KB, a projekt gyökerében, a `.gitignore` kizárja), benne a `candles` tábla (0 sor) és az SQLite
  saját `sqlite_sequence` táblája. Az IntelliJ Project ablaka nem mutatja azonnal az új fájlt → Reload from Disk.
- Commit: „Add candles table schema” (pusholva).
- Tábla megtekintése: IntelliJ Database ablak (a DatabaseTools plugin telepítve, de Ultimate-funkció lehet) vagy
  DB Browser for SQLite (ingyenes).
- **Kész (2026-09-30):** a felhasználó megnézte a `candles` táblát a **DB Browser for SQLite**-ban (portable zip változat,
  kicsomagolva: `Downloads\DB.Browser.for.SQLite-v3.13.1-win64\DB Browser for SQLite.exe`), és látta mind a 8 oszlopot.
  Nagyon apró lépésekben kell vezetni (a „kicsomagolás = telepítés” sem volt egyértelmű).
- **Kész (2026-09-30):** az első sort kézzel beírta (DB Browser → Execute SQL → `INSERT`, majd Write Changes):
  `AAPL, 2026-09-30T15:30:00, 170.00, 171.20, 169.50, 170.80, 1200` → id = 1. (A DB nincs a Gitben, ez csak helyi adat.)
- **Kész (2026-09-30):** `org.example.api.CandleController` (`GET /candles/count` → `SELECT COUNT(*) FROM candles`
  JdbcTemplate-tel, constructor injection). Futtatva a böngészőben **1**-et adott (az 1 kézi sor). Még nincs commitolva
  (a fájl CRLF sorvégű, mint a `HelloController`).
- **Kész (2026-09-30): dependency injection elmagyarázva** a `CandleController` konstruktorán (szakács/étterem-vezető
  hasonlat; a Spring hozza létre a `JdbcTemplate`-et és adja be a konstruktornak). Az ellenőrző kérdésre jól válaszolt
  (üres konstruktor → nem kapja meg → hiba). Kiegészítve: `final` → fordítási hiba; `final` nélkül → `null` →
  `NullPointerException` → HTTP 500.
- Commit: „Add candle count endpoint” (pusholva).
- **Döntés (2026-09-30): a `Candle` Java `record`** (nem klasszikus class), a felhasználó választása. Helye:
  `org.example.marketdata.Candle`. Mezők: `long id, String symbol, LocalDateTime openTime, double open, high, low,
  close, long volume`. Árak egyelőre `double` (BigDecimal esetleg később). Elmagyarázva: osztály = sablon/űrlap,
  objektum = kitöltött példány.
- **Kész (2026-09-30):** a felhasználó létrehozta a `marketdata` package-et és a `Candle` recordot (IntelliJ New →
  Java Class → Record; import `Alt+Enter`-rel). Claude ellenőrizte (hibátlan, CRLF), `clean package` → BUILD SUCCESS.
- Commit: „Add Candle record” (pusholva).
- Elmagyarázva (2026-09-30): miért külön package a `marketdata` és az `api` (étterem: api = pincér, marketdata =
  hozzávalók, strategy = szakács; a backtest HTTP nélkül is fut). A felhasználó szerint világos.
- **Kész (2026-09-30):** `GET /candles` a `CandleController`-ben (JdbcTemplate `query` + RowMapper lambdával →
  `List<Candle>` → JSON), a felhasználó írta be. A böngésző az AAPL candle-t mutatta JSON-ban. Egyelőre az SQL a
  controllerben van (egy új fogalom egyszerre); **később** átköltöztetni egy `CandleRepository`-ba (a strategy/backtest
  is olvasni fog candle-t, HTTP nélkül).
- **Tanulság (2026-09-30):** 404 jött, mert egy **régi Spring példány** (19:09-kor indítva) tovább futott és foglalta a
  8080-as portot, az új indítás ezért elhasalt. Diagnózis WSL-ből: `powershell.exe` → `Get-NetTCPConnection -LocalPort 8080`
  + `Win32_Process` (java.exe, CreationDate). Megoldás: az IntelliJ Run ablak minden fülén Stop, majd újraindítás.
  Elmagyarázva: port = „ajtószám”.
- JSON elmagyarázva (lista `[ ]`, objektum `{ }`, Jackson automatikusan alakít). Commit: „Add endpoint to list candles” (pusholva).
- **Döntés (2026-09-30): előbb `CandleRepository`** (A), utána candle írása Java-ból POST-tal (B) – a felhasználó választása.
- **Folyamatban:** 1) `org.example.marketdata.CandleRepository` (`@Repository`, JdbcTemplate constructor injection,
  `count()` + `findAll()` a RowMapperrel) létrehozása; 2) utána a `CandleController` átírása, hogy a repositoryt kapja
  (DI a saját osztállyal). Ezután: POST + INSERT a repositoryban.
  Figyelmeztetés: a Spring indítása előtt a DB Browserben Close Database.
- **Kész (2026-09-30):** 1) `CandleRepository` (a felhasználó írta be, Claude ellenőrizte: hibátlan, CRLF, nincs
  sorvégi szóköz). 2) `CandleController` átírva a repository használatára (a felhasználó írta be, Claude ellenőrizte).
  **Kipróbálva (2026-09-30):** `/candles/count` → 1, `/candles` → AAPL – a refaktorálás működik.
  Commit: „Move candle queries to CandleRepository” (pusholva).
- Elmagyarázva (2026-09-30): mi a JPA / Spring Data JPA és miért NEM azt használjuk (a felhasználó kérdezte; jól
  válaszolt: a JPA az SQL-t és a RowMappert írná meg helyettünk). INSERT, `?` helyőrző (SQL injection), GET vs. POST, request body.
- **Kész (2026-09-30):** `CandleRepository.save(Candle)` (`jdbcTemplate.update` + INSERT, `id` nélkül). Az `open_time`-ot
  `DateTimeFormatter.ISO_LOCAL_DATE_TIME`-mal írjuk (a `LocalDateTime.toString()` a :00 másodpercet lehagyná).
- **Kész (2026-09-30):** `@PostMapping("/candles")` + `@RequestBody Candle` a controllerben (a felhasználó írta be,
  Claude ellenőrizte; kis lépésekre bontva kellett – a hosszabb magyarázatnál elveszett, mit kell tennie).
- **Kész (2026-09-30): POST kipróbálva** az IntelliJ termináljából (`Alt+F12`, PowerShell `Invoke-RestMethod`; a HTTP Client
  Ultimate-funkció lehet). count → 2, id = 2, a DB-ben `open_time` = `2026-09-30T15:31:00` (egységes formátum).
  Tanulság: a hosszú parancs másoláskor két sorra tört → PowerShell-parancsot rövid sorokra bontva adni (`$b`, `$u` változók).
  Commit: „Add endpoint to save candles” (pusholva). Figyelem: Spring Boot 4 = Jackson 3; ha a JSON-ból hiányzik az `id` (primitív `long`),
  hibát adhat → a próbakérésben `"id": 0`-t küldjünk (a `save` úgyis figyelmen kívül hagyja).
- **Kész (2026-10-01): kis kézi adatsor (Phase 4 vége).** A felhasználó DB Browserben egy több soros `INSERT`-tel
  beírt 8 új AAPL 1 perces gyertyát (15:32–15:39) → összesen 10 sor (id 1–10). A „történet”: 170.60-ról 173.00-ig
  emelkedik (csúcs 15:35), majd 170.50-ig visszaesik; minden open = előző close. (Csak helyi adat, nincs a Gitben.)
  DB megnyitása: Open Database (`Ctrl+O`) → teljes útvonal a Fájlnév mezőbe. Böngészőben ellenőrizve: count = 10,
  `/candles` mind a 10-et mutatja. **Phase 4 kész → Phase 5 (egyszerű backtest) következik.**
- Elmagyarázva (2026-10-01): miért előbb a candle-backtest (autó + egyszerű motor hasonlat; a váz marad, csak az „agy”
  cserélődik fair price-ra), és hogy a stratégia csak *becsül* (nem lát a jövőbe), a backtest pedig méri, jól becsül-e.
- **Döntés (2026-10-01): a Strategy célpozíciót (target position) ad vissza**, nem BUY/SELL jelzést – a felhasználó
  választása. Ok: nincs véletlen dupla vétel (a rendszer a különbséget számolja: cél − jelenlegi), mennyiséget is ki tud
  fejezni (fair price eltérés mérete), a max pozíció könnyen betartható.
- **Kész (2026-10-01):** `org.example.strategy.Strategy` interfész (`int targetPosition(Candle candle);`; interfész =
  „álláshirdetés”: mit kell tudni, nem hogyan). A felhasználó írta be, Claude ellenőrizte (hibátlan, CRLF). Az IntelliJ
  magától `git add`-olta (staged). A felhasználó kérésére az interfészt az első stratégiával **együtt** commitoljuk.
- **Döntés (2026-10-01): a stratégia maga jegyzi meg az előzményt** (mező az osztályban, pl. `previousClose`), az
  interfész marad `targetPosition(Candle)` – a felhasználó választása (B lett volna: `List<Candle>` minden hívásnál;
  lassú sok adatnál). A fair price stratégia is így gyűjti majd az állapotot.
- **Kész (2026-10-01):** `org.example.strategy.RisingPriceStrategy implements Strategy`: close > előző close → 1,
  különben 0; első gyertyánál (previousClose == 0) → 0. A 10 gyertyán: vétel 15:32 (171.20), eladás 15:36 (172.40), +1.20.
  A felhasználó írta be (először lemaradt a `package` sor → Claude észrevette, javítva). Elmagyarázva: mező vs. helyi
  változó, `private`, `@Override`, `&&`, a hasonlítás/feljegyzés sorrendje. Claude futtatta a `clean package`-et
  (PowerShell, IntelliJ 2025.2.1 beépített Maven, `openjdk-25.0.1`) → sikeres.
  Commit: „Add Strategy interface and RisingPriceStrategy” (pusholva).
- A felhasználó (2026-10-01) rögtön az order/execution/portfolio részt kérte, **több fájlt egyszerre** (csomagokban).
- **Phase 5 terve (2026-10-01):** 1) `order.Side` (enum BUY/SELL), 2) `order.Order` (record: symbol, side, quantity),
  3) `execution.Fill` (record: symbol, side, quantity, price, time), 4) `execution.ExecutionEngine` (order + candle →
  fill a close áron), 5) `portfolio.Portfolio` (cash, position, equity = cash + position × ár), 6) `backtest.Backtester`
  (karmester: candle → strategy → diff = cél − pozíció → order → fill → portfolio → equity), 7) `api.BacktestController`
  (`GET /backtest`). Egyszerűsítések: close áron fill, nincs jutalék, csak long (0/1). Kezdő pénz: 1000.
  Csomagok: (1–3), (4–5), (6–7).
- **Döntés (2026-10-01): `Side` enum** (BUY/SELL), nem előjeles mennyiség – a felhasználó választása.
- **Kész (2026-10-02):** 1. csomag (`Side`, `Order`, `Fill`) – a felhasználó írta be, Claude ellenőrizte (hibátlan,
  CRLF, nincs sorvégi szóköz), `clean package` sikeres. Commit: „Add Order, Side and Fill” (pusholva).
- **Kész (2026-10-02):** 2. csomag: `execution.ExecutionEngine` (`Fill execute(Order, Candle)`, close áron,
  time = candle.openTime()) és `portfolio.Portfolio` (konstruktor kezdő pénzzel, `apply(Fill)`, `equity(price)`,
  `getCash()`, `getPosition()`). A felhasználó írta be, Claude ellenőrizte (hibátlan), `clean package` sikeres.
  Elmagyarázva: konstruktor, `this`, `void`, `==` enumon, getter, equity számpéldával.
  Commit: „Add ExecutionEngine and Portfolio” (pusholva).
- **Kész (2026-10-02): 3. csomag** – a felhasználó beírta, Claude ellenőrizte (hibátlan), `clean package` sikeres:
  a) `CandleRepository.findAll()` SQL-je kap `ORDER BY open_time`-ot (backtestnél kötelező az időrend);
  b) `backtest.BacktestResult` record (startingCash, finalEquity, pnl, `List<Fill> fills`, `List<Double> equityCurve`);
  c) `backtest.Backtester` (sima osztály, `run(candles, strategy, startingCash)`, for ciklus, diff → Order → Fill →
  Portfolio, equity minden gyertya után); d) `api.BacktestController` (`GET /backtest`, CandleRepository DI,
  minden kérésnél `new RisingPriceStrategy()` – mert a stratégiának állapota van!). Drawdown: KÉSŐBB, külön lépés.
  Várt eredmény: BUY 15:32 @171.20, SELL 15:36 @172.40, finalEquity 1001.20, pnl ≈ 1.20 (double kerekítési hiba lehet);
  equity: 1000, 1000, 1000, 1000.70, 1001.40, 1001.80, 1001.20 ×4.
  **Kipróbálva (2026-10-02):** `GET /backtest` pontosan a várt eredményt adta (pnl 1.1999999999999318 – double
  kerekítési hiba, elmagyarázva; BigDecimal később). **AZ ELSŐ VÉGIGFUTÓ BACKTEST MŰKÖDIK.**
  Commit: „Add backtester and backtest endpoint” (pusholva).
- **Következő lépés (javaslat):** max drawdown számolása (equityCurve-ből, számpéldával elmagyarázva), utána
  esetleg: double kerekítés kezelése (BigDecimal vagy kerekítés a kimenetnél), jutalék, `HelloController` törlése,
  `.gitattributes` (CRLF/LF). Utána Phase 6: Databento.
- **Cél-kérdés (2026-10-02):** a felhasználó azt kérdezte, kész lehet-e 2 hét alatt „egy egész oké verzió”.
  Claude válasza: a teljes terv (MBP-10, részteljesülés, eseményalapú engine, React) nem reális 2 hét alatt;
  egy szűkített, de valódi verzió (Databento BBO adat + egyszerű fair price, pl. mid/microprice + backtest eredmény
  REST-en) reális lehet napi munkával. **Folytatáskor közösen meg kell határozni, mi az „oké verzió”**, és ahhoz
  igazítani a sorrendet. Elmagyarázva a RisingPriceStrategy (momentum/trendkövetés, késés, oldalazó piac, költségek).
- **Döntés (2026-10-02): „B” irány – minőség a gyorsaság előtt.** A felhasználó egy ténylegesen **szép appot** akar,
  **nagyon jó frontenddel** és „egészen oké” backenddel; a 2 hetes határidő nem kötelező. Előbb a mostani motor
  csiszolása (max drawdown → kerekítés → jutalék → takarítás: `HelloController`, `.gitattributes`), csak utána Databento.
  Nyitott kérdés későbbre: a frontend (React) előrébb hozása (pl. a mostani candle-backtesthez már legyen felület).
- **Kész (2026-10-02): max drawdown.** Elmagyarázva (csúcs − equity, számpéldával); a felhasználó a valódi
  görbén hibátlanul kiszámolta (0.60, 1001.80 → 1001.20). Kód: `BacktestResult` új mező `double maxDrawdown`;
  `Backtester` a meglévő ciklusban követi a `peak`-et és a `maxDrawdown`-t. A felhasználó írta be (egy vessző
  lemaradt a recordból → Claude észrevette). `clean package` sikeres; `/backtest` → `maxDrawdown: 0.6000000000000227`.
  Commit: „Add max drawdown to backtest result” (pusholva).
- **Döntés (2026-10-02): `BigDecimal` minden ár és pénz helyett** (nem csak kerekítés a kimenetnél) – a felhasználó
  választása („ahol lehet, profin”). Érintett: `Candle` (OHLC), `CandleRepository`, `RisingPriceStrategy`, `Fill`,
  `ExecutionEngine`, `Portfolio`, `Backtester`, `BacktestResult`. Szabályok: Stringből/`valueOf`-fal létrehozni
  (nem `new BigDecimal(double)`), `add/subtract/multiply`, összehasonlítás `compareTo`-val (nem `equals`).
  Később eldöntendő: a DB-ben az árak tárolása (most REAL); a Databento fix pontos egészeket (1e-9) használ.
- **Kész (2026-10-02): BigDecimal átállás** – a felhasználó kérésére **Claude írta át** a 8 fájlt (+ `BacktestController`:
  `new BigDecimal("1000")`). Olvasás: `BigDecimal.valueOf(rs.getDouble(...))` (a REAL-ből; a `valueOf` a rövid alakot
  adja, pl. 171.2). `RisingPriceStrategy.previousClose` kezdőértéke `null` (= még nincs előző gyertya, a régi 0 helyett).
  `ExecutionEngine` változatlan (a `candle.close()` már BigDecimal). Build sikeres; Claude a jar-t a 8099-es porton
  kipróbálta: `pnl 1.2`, `maxDrawdown 0.6`, equity pontos – kerekítési hiba eltűnt. Commit: „Use BigDecimal for prices and money” (pusholva).
  A felhasználó a 8080-on előbb a régi eredményt látta (13:55-kor indított régi példány) → újraindítás után 1.2 / 0.6.
  Elmagyarázva: a futó program nem frissül magától, kódváltozás után mindig újraindítani.
- **Cél pontosítva (2026-10-02):** a felhasználó **„quant cég stílusú”** rendszert akar (order book, fair price) –
  ez egyezik a hosszú távú tervvel. Elmagyarázva röviden: költségek a quant világban (bróker jutalék darabonként,
  tőzsdei maker/taker díj + rebate, spread/slippage – ez utóbbit majd az order book execution kezeli).
- **Döntés (2026-10-02): jutalék `CommissionModel` interfésszel** (nem egyetlen fix szám) – bővíthető (per-share,
  maker/taker stb.) az `ExecutionEngine` átírása nélkül.
- **Következő lépés (terv kész, jóváhagyásra vár – 2026-10-02):** 1) ÚJ `execution.CommissionModel`
  (`BigDecimal calculate(int quantity, BigDecimal price)`); 2) ÚJ `execution.PerShareCommission` (konstruktor: díj/db
  + minimum; IBKR-szerű 0.005 $/db, min. 1.00 $); 3) `Fill` + `BigDecimal commission`; 4) `ExecutionEngine` konstruktorban
  kapja a modellt; 5) `Portfolio` mindkét irányban levonja; 6) `Backtester` továbbadja, `BacktestResult` + `totalCommission`,
  `BacktestController` hozza létre a modellt. Várt: pnl −0.80, maxDrawdown 1.60, totalCommission 2.00, equity
  1000, 1000, 999, 999.7, 1000.4, 1000.8, 999.2 ×4. **Folytatáskor megkérdezni: Claude írja be, vagy a felhasználó?**
  → **A felhasználó írja be** (2026-10-02). Két csomag: (A) a két új fájl (`CommissionModel`, `PerShareCommission`) –
  önmagában is lefordul; (B) a 6 meglévő fájl módosítása (`Fill`, `ExecutionEngine`, `Portfolio`, `Backtester`,
  `BacktestResult`, `BacktestController`) – ezek csak együtt fordulnak le.
- **Kész (2026-10-02): jutalék.** Mindkét csomagot a felhasználó írta be, hibátlanul (Claude ellenőrizte, build sikeres).
  Claude a 8099-es porton kipróbálta: pnl **−0.80**, maxDrawdown **1.60**, totalCommission **2.00**, fill-enként
  `commission: 1.00` – pontosan a várt. Tanulság a felhasználónak: jutalékkal a RisingPriceStrategy veszteséges.
  Kozmetikai: az equityCurve-ben vegyes tizedesjegy (1000.0 vs 999.00) – a BigDecimal megjegyzi a scale-t;
  később egységesíteni (pl. `setScale(2)` a kimenetnél vagy a frontend formáz). Commit: „Add commission model to execution” (pusholva).
- (Régi jegyzet:) `candles` tábla: `schema.sql` (`CREATE TABLE IF NOT EXISTS`, id/symbol/open_time TEXT ISO-8601/
  open/high/low/close REAL/volume INTEGER) + `spring.sql.init.mode=always` az `application.properties`-be.
- (Régi jegyzet:) az első tábla létrehozása (pl. `schema.sql`;
  nem beágyazott DB-nél kell hozzá `spring.sql.init.mode=always`).
- (Régi jegyzet:) `application.properties` (a `resources` mappa már létezik, üresen – az IntelliJ sablon hozta létre; a Git üres mappát nem követ, ezért nem látszott)
  (`spring.datasource.url`) – enélkül a `spring-boot:run` „Failed to configure a DataSource” hibával leáll.
- (Régi jegyzet:) **Phase 2 lépése (4/4): Spring Boot indító osztály** (`Main.java` helyett), majd `spring-boot:run`.
  Folytatáskor röviden ismételd át a felhasználóval: mit csinál a plugin (fat jar, `spring-boot:run`), a Plugins mappa
  a Maven ablakban; a felhasználó a jar-méret kísérletet és a Plugins mappa megnézését még nem csinálta meg maga.
- **Kész (2026-10-02): takarítás.** `HelloController.java` törölve. `.gitattributes` (a felhasználó írta be; először a
  terminálból másolt 2 vezető szóköz és csonka sor volt benne → javítva): `* text=auto eol=lf`, `*.cmd`/`*.bat` →
  `eol=crlf`. A Gitben minden szöveges fájl CRLF volt → `git add --renormalize . ':!CLAUDE.md'` (22 fájl, csak sorvég;
  `git diff --cached --ignore-cr-at-eol` ellenőrizve). A Gitben most minden LF; a lemezen a fájlok még CRLF-ek lehetnek
  (ez nem gond, a Git commitkor normalizál). Commit: „Add .gitattributes, normalize line endings and remove HelloController” (pusholva).
- **Következő lépés:** a motor csiszolása kész (drawdown, BigDecimal, jutalék, takarítás). Nyitott: equityCurve scale
  egységesítése; frontend előrébb hozása vs. Phase 6 (Databento) – a felhasználóval eldönteni.
- **Döntés (2026-10-02): kódstílus = Google Java Style, de 4 szóközös behúzással** (a Google 2-t ír elő), Checkstyle-lal
  a Maven buildbe kötve + `.editorconfig`. Az IntelliJ-ben a CheckStyle-IDEA plugin már telepítve van (Sun/Google Checks).
  **Javadoc angolul**, tömören: minden osztály/record/interfész + nem triviális public metódus; triviális getter nem kell;
  a „mit/miért”-et írja le, nem a „hogyan”-t. Sorrend: 1) `.editorconfig`, 2) Checkstyle, 3) Javadoc, 4) döntés: frontend vs. Databento.
- **Kész (2026-10-02): `.editorconfig`** (a felhasználó írta be: utf-8, LF, final newline, trailing whitespace törlés,
  4 szóköz; `.cmd/.bat` CRLF; `.md`-ben a trailing whitespace marad). IntelliJ: „Ensure every saved file ends with a line
  break” bekapcsolva (alapból ki volt → ezért nem volt a Java fájlok végén sorvég). IntelliJ-beállítások helye:
  `AppData\Roaming\JetBrains\IntelliJIdea2025.2\options` (futás közben NE írjuk, az IntelliJ felülírja).
  Commit: „Add .editorconfig” (pusholva).
- **Folyamatban (2026-10-02): Javadoc** – Claude beírta mind a 16 Java fájlba (154 sor, csak komment; a fájlok
  most LF-esek a lemezen is). Build sikeres; `mvn javadoc:javadoc -Ddoclint=all` → 0 hiba, 18 „hiányzó komment”
  figyelmeztetés, mind szándékos (getterek, enum értékek, alap/DI konstruktorok, `main`, egysoros leírásnál nincs
  `@param/@return`). A felhasználó átnézte → commit: „Add Javadoc to all classes” (pusholva).
- **Folyamatban (2026-10-02): Checkstyle**, 3 lépés: 1) szabályfájl, 2) `maven-checkstyle-plugin` (eleinte csak
  figyelmeztet), 3) hibák javítása (pl. 7 sor > 100 karakter), utána a build hibát ad szabálysértésnél.
  **1) kész (nincs commitolva):** `config/checkstyle/checkstyle.xml` = a Checkstyle **14.3.0** (legfrissebb, 2026-09-27)
  `google_checks.xml`-je, egyetlen módosítással: Indentation basicOffset/caseIndent/arrayInitIndent 4,
  braceAdjustment 0, throwsIndent/lineWrappingIndentation 8. Plugin: `maven-checkstyle-plugin` 3.6.0 + checkstyle
  14.3.0 dependency. Az IntelliJ CheckStyle-IDEA plugin 12.1.0-t használ → át kell állítani 14.3.0-ra.
  **2) kész (nincs commitolva):** plugin a `pom.xml`-ben (`validate` fázis, `consoleOutput`, `violationSeverity=warning`
  – enélkül „0 violations”-t ír, mert a Google config minden hibát warningnak jelöl –, `failOnViolation=false`).
  Első futás: **42 figyelmeztetés**: 29 CustomImportOrder (Google: egy import blokk, ASCII sorrend; az IntelliJ alapból
  a `java.*`-t külön a végére teszi), 7 LineLength, 4 MissingJavadocMethod (3 Spring-konstruktor + `main`),
  2 EmptyLineSeparator. **Döntés:** előbb az IntelliJ kódstílus Google-re állítása, különben újra rossz importokat ír.
  Elkészítve: `config/intellij/backtest-google-style.xml` (google/styleguide `intellij-java-google-style.xml`, commit
  505ba68, csak a Java/egyéb behúzás 4/8). **Kész:** a felhasználó importálta (előbb IDE-szintű sémaként, majd „Copy to
  Project...”) → `.idea/codeStyles/Project.xml` + `codeStyleConfig.xml` (`USE_PER_PROJECT_SETTINGS`), nincs gitignore-olva,
  a Gitbe kerül. (Az IntelliJ csak az alapértéktől eltérő beállításokat menti – a 4/8 behúzás alapérték, ezért nem látszik.)
  Az első Reformat rossz lett (folytatósor 4, `@param` oszlopba igazítva, egysoros Javadoc 3 sorra bontva – a 2018-as
  Google XML egyes opcióit az új IntelliJ nem vette át) → `.editorconfig` `[*.java]`: `ij_continuation_indent_size = 8`,
  `ij_java_align_multiline_records = false`, `ij_java_doc_align_param_comments = false`,
  `ij_java_doc_do_not_wrap_if_one_line = true` (az `ij_` opciók felülírják a kódstílust); `git restore src/main/java`,
  majd újra Reformat (+ Optimize imports) → jó. Checkstyle: 42 → **6** (4 MissingJavadocMethod: 3 Spring-konstruktor +
  `main`; 2 LineLength: SQL stringek a `CandleRepository`-ban – az IntelliJ stringet nem tör).
  **3) kész:** Claude beírta a 4 Javadocot, az SQL-t Java **text block**-ba (`"""`) tette, `failOnViolation=true`
  (a build mostantól megáll szabálysértésnél – rögtön el is kapott egy 101 karakteres Javadoc sort). Build: **0 violation**,
  BUILD SUCCESS. 8099-es porton kipróbálva: count 10, `/candles` 10 db időrendben, `/backtest` pnl −0.80, maxDrawdown 1.60,
  totalCommission 2.00 – változatlan. A POST (INSERT) nincs kipróbálva (ne kerüljön próbasor a DB-be).
  Commit: „Add Checkstyle with Google style and IntelliJ code style” (pusholva). **Checkstyle kész.**
- **Döntés (2026-10-02): a projekt Checkstyle 14.1.0-ra állt vissza** (14.3.0 helyett). Ok: a frissített
  CheckStyle-IDEA plugin (26.18.2, a legújabb) legfeljebb 14.1.0-t tud, és a 14.3-as Google config
  `GoogleRightCurly` modulja 14.1-gyel nem tölt be → egyetlen szabályfájl, IntelliJ = Maven. A
  `config/checkstyle/checkstyle.xml` most a 14.1.0 `google_checks.xml`-je + ugyanaz a 4/8-as behúzás; `pom.xml`
  checkstyle dependency 14.1.0. Build sikeres, 0 violation (Claude ellenőrizte). Ha a plugin később tudja a 14.3-at → visszaváltani.
  Nincs commitolva.
- **Kész (2026-10-02): IntelliJ CheckStyle-IDEA beállítva** (a felhasználó kattintotta végig, nem akarta bezárni az
  IntelliJ-t): verzió fixen **14.1.0** (NEM „latest” – a Mavennel egyezzen), Scan Scope = Java + tesztek, aktív config
  „Backtest” = `$PROJECT_DIR$/config/checkstyle/checkstyle.xml` (PROJECT_RELATIVE) → `.idea/checkstyle-idea.xml`
  (a Gitbe kerül). Check Project → „no problems found”.
  Commitok: „Switch Checkstyle to 14.1.0 and share IntelliJ Checkstyle settings” + CLAUDE.md (pusholva).
- **Döntés (2026-10-02): B – előbb Databento (Phase 6), a frontend utána.** Ok (Claude elmagyarázta, a felhasználó
  elfogadta): a valódi adat átalakítja az adatmodellt (UTC nanoszekundumos idő, fix pontos árak 1e-9, trades/BBO),
  a nagy adatmennyiség kihozza a backend gyengéit (SQLite, `findAll()` mindent memóriába), a kockázatos/ismeretlen
  rész kerüljön előre, és valódi adaton értelmes a backtest. A frontend stabil adatmodellre épüljön.
- **Phase 6 terve (vázlat):** 1) Databento fiók + API kulcs (a kulcsot SOHA ne illessze be a chatbe/kódba);
  2) `DATABENTO_API_KEY` Windows környezeti változó; 3) első HTTP hívás a terminálból – előbb költség lekérdezése
  (`metadata.get_cost`), csak utána letöltés; 4) kis mennyiségű **ohlcv-1m** valódi adat → a meglévő candle-backtest
  valódi adaton (minimális modellváltozás); 5) Java-ból letöltés; utána trades/BBO.
- **Databento ingyenesség (2026-10-02, Claude utánanézett):** a felhasználó **semennyit nem akar fizetni**.
  Regisztrációkor **125 $ kredit** történelmi adatra, **6 hónapig** érvényes, egyszer jár. **Bankkártya kell** a
  regisztrációhoz (csalásszűrés), de csak a krediten felül terhelnek. Havidíjas csomagok (199 $-tól) csak élő adathoz –
  NEM kellenek. Védelem: 1) regisztráció után ELSŐKÉNT **havi költségkorlát** (Billing → spending limit, a lehető
  legkisebb, ha lehet 0 $); 2) minden letöltés előtt árlekérdezés; 3) kis adatmennyiség.
  Nagyságrendek: ohlcv-1m pár nap/hét → centek; trades/BBO 1–2 nap 1 részvény → pár $; mbp-10 → drága, csak később, kicsiben.
- **Adatstratégia (2026-10-02):** hosszú távon sokféle adat kell (több részvény, hosszú időszak → robusztusság,
  overfitting elkerülése). A 125 $ beosztása: előbb kis adattal megépíteni a rendszert (a kód symbol-független);
  **minden letöltött adatot elmenteni a saját DB-be (soha ne fizessünk kétszer)**; olcsó gyertyából sokat, drága
  order bookból keveset (néhány likvid részvény, néhány nap); a kreditet a 6 hónap lejárta előtt felhasználni.
  A Databento-kreditet főleg order book / trades adatra tartogatni; gyertya később ingyenes forrásból is jöhet.
  Licencfeltételeket regisztráció után megnézni.
- **Több API (2026-10-02, elv):** később `MarketDataSource` interfész (mint `Strategy`, `CommissionModel`), pl.
  `DatabentoSource` + egy ingyenes gyertya-forrás; mindegyik a mi `Candle`-ünkre alakít és a saját DB-be ment, a
  backtest nem tudja, honnan jött az adat. Figyelni: eltérő adatok, időzóna (egységesen tárolni), split-korrigált árak
  (ne keverjük), `source` oszlop a táblában, egy részvény egy időszakára egy forrás, felhasználási feltételek (ToS).
  **Most még csak egy forrás (Databento)**; az interfész a második forrásnál jön.
- **Kártya nélküli források (2026-10-03, Claude utánanézett és ellenőrizte, hogy elérhetők):**
  1) **Nasdaq TotalView-ITCH 5.0 minták** – `https://emi.nasdaq.com/ITCH/Nasdaq%20ITCH/`, regisztráció nélkül,
  a teljes Nasdaq order-by-order (L3) adat **egy-egy napra, minden részvényre** (pl. `S120825-v50.txt.gz` = 2025-12-08,
  4–18 GB/nap tömörítve; régebbiek 2018–2019). Ebből bármely szinten (BBO, MBP-10) order book építhető. Bináris,
  parser kell. Licenc: nyilvános minta – saját tanulásra/kutatásra; ne tegyük közzé (a repo később nyilvános lehet!).
  2) **IEX HIST (DEEP/TOPS)** – ingyenes, regisztráció nélkül, az elmúlt 12 hónap bármely napja (JSON lista:
  `https://iextrading.com/api/1.0/hist?date=YYYYMMDD`), pcap, ~12 GB/nap/feed. Csak az IEX tőzsde könyve (kis piaci
  részesedés → vékony könyv). 3) **LOBSTER minták** – kész CSV, 10 szintes könyv, AAPL/AMZN/GOOG/INTC/MSFT,
  egyetlen nap (2012-06-21), pár MB – a legkönnyebb kezdés. 4) **Alpaca** – ingyenes fiók kártya nélkül, API kulcs;
  gyertyák sok részvényre, sok évre + historical trades/quotes (nem teljes order book).
  Lemezen ~320 GB szabad (C:). Az adat NEM kerülhet a Gitbe (külön mappa, `.gitignore`).
- **Döntés (2026-10-03): Nasdaq ITCH (a) + Java parser a projektben (A)** – a felhasználó választása. A Databento
  és a kártya egyelőre NEM kell (az alábbi Databento-regisztrációs pont ezzel félretéve). Terv: 1 nap, ~20 likvid
  részvény (pl. AAPL, MSFT, NVDA, AMZN, GOOGL, META, TSLA, AVGO, AMD, NFLX…) kiszűrése, order book rekonstrukció, mentés.
  Nyers adat a projekten KÍVÜL: `C:\Users\kimdo\market-data\nasdaq-itch\` (nem Git, az IntelliJ se indexelje).
  **Letöltve és ellenőrizve (2026-10-03):** `S120925-v50.txt.gz` = **2025-12-09** (kedd), 7 929 915 419 bájt; nincs
  md5 a fájlhoz (a `.done` 404) → ellenőrzés: méret + `gzip -t`. (A 2025-11-28 félnapos kereskedés → kihagyva.)
- **Döntés (2026-10-03): a tick/order book adat tárolása SQLite** (összehasonlítva: Parquet+DuckDB hibrid, PostgreSQL
  (+TimescaleDB) – Claude a hibridet ajánlotta hosszú távra; a felhasználó az SQLite-ot választotta, tudva a
  hátrányokat: nincs tömörítés → ~2–4 GB/nap/20 részvény becslés, egy nagy fájl, egyszerre egy író, batch írás kell).
  **Három szabály, hogy később cserélhető legyen:** 1) **interfész** mögött (`MarketEventStore`, most
  `SqliteMarketEventStore`, később pl. `ParquetMarketEventStore`); 2) **tiszta formátum**: idő = egész nanoszekundum,
  ár = egész (ITCH: 4 tizedes, 170.0100 → 1700100); csak **eseményeket** mentünk (MBO-szerű: add/execute/cancel/delete),
  nem 10 szintes képeket – a könyvet a backtest építi újra; 3) **külön DB-fájl** (`market-data.db` a `market-data`
  mappában, nem a `backtest.db`). A nyers ITCH vagy az SQLite közül legalább egyet mindig megtartani.
  Váltás később: újra-import a nyers fájlból, vagy DuckDB-vel SQLite → Parquet.
  Hosszú távú profi felállás: Parquet a piaci adatnak, PostgreSQL az alkalmazás adatainak.
- **Kész (2026-10-03): eseménymodell** a `marketdata` package-ben (a felhasználó írta be): `BookSide` enum
  (BID/ASK – szándékosan NEM az `order.Side`, mert a marketdata a legalsó réteg, nem függhet az order-től),
  `EventType` enum (ADD, EXECUTE, CANCEL, DELETE, TRADE; az ITCH Replace = DELETE + ADD), `MarketEvent` record
  (`long timestamp` ns UTC epoch, `symbol`, `type`, `long orderId`, `side`, `long price` 1/10000 $, `long quantity`).
  Közben: a fájl `Bookside.java` néven jött létre (kis s) → `Shift+F6`-tal átnevezve; az átnevezésnél elveszett a
  `package` sor → visszaírva. Build sikeres, 0 Checkstyle hiba. Elmagyarázva: ajánlat (ADD, vár) vs. megtörtént
  kötés (EXECUTE/TRADE) vs. visszavonás; miért kell mindkettő (kötés = tényleges ár, könyv = kereslet/kínálat → fair price).
  Import-lánc terve: `ItchReader` (üzenetekre darabol) → `ItchParser` (csak a 20 részvény) → `MarketEvent` →
  `MarketEventStore` (SQLite).
- **Kész (2026-10-03): `marketdata.itch.ItchReader`** (a felhasználó írta be; először a `marketdata`-ba került →
  `Alt+Enter` → Move to package): gzip menet közbeni kitömörítés, `byte[] nextMessage()` (null = fájl vége),
  `AutoCloseable`. Elmagyarázva: bájt, bináris vs. szöveg, hossz-előtag, big-endian, stream-„csövek”, `throws`,
  EOF, miért külön `itch` package (forrás-specifikus kód vs. közös modell; később `iex/`, `alpaca/`).
  **Mérés (Claude, scratchpad-program, nem a projektben):** a teljes 2025-12-09 fájl 87 s alatt, 589 560 010 üzenet,
  18,7 GB kitömörítve; A 213M, F 2,9M, D 205M, U 134M, X 8,9M, E 13,3M, C 0,4M, P 6,2M, R 12 111 (értékpapír).
  Tanulság (elmagyarázva): ~minden 10. ajánlatból lesz kötés (market makerek folyton módosítanak).
- **Kész (2026-10-03): `marketdata.itch.ItchParser` 1. rész** (a felhasználó kérésére Claude írta be): csak az „R”
  (Stock Directory) üzenet → `String[65_536] symbols` (stock locate → symbol), `symbolOf(int)`, `switch` nyíllal,
  `readUnsignedShort` (`& 0xFF`, `<< 8`). Ezen a napon pl. AAPL=24, MSFT=7131, NVDA=7705 (a számok naponta változhatnak).
  Build sikeres. Nincs commitolva (ItchReader + ItchParser).
- **Mérés (2026-10-03, 2025-12-09 nap): események részvényenként** (A+F+D+X+E+C+P, U=2 esemény). Összesen 717M.
  A javasolt 20 (AAPL, MSFT, NVDA, AMZN, GOOGL, META, TSLA, AVGO, AMD, INTC, QCOM, AMAT, NFLX, COST, PEP, ADBE, CSCO,
  SBUX, QQQ, SPY) = **64,2M esemény**; legaktívabb: QQQ 15,2M, SPY 8,9M, NVDA 6,9M, GOOGL 6,7M, TSLA 6,2M; legkevesebb
  AMAT 0,3M. A 40 legaktívabb együtt 138M (sok tőkeáttételes/kripto ETF: SQQQ, TQQQ, SOXL, NVDL, IBIT…).
  **SQLite méret (mérve, 1M soros próba):** ~46 bájt/sor index nélkül, **~68 bájt/sor** `(symbol, ts)` indexszel
  → 20 részvény ≈ **4,4 GB/nap** (a korábbi 2–4 GB-os becslés alacsony volt); 40 legaktívabb ≈ 9,4 GB/nap.
- **Döntés (2026-10-03): 22 részvény** = a fenti 20 + **VOO** (Vanguard S&P 500 ETF, 3,0M esemény) + **TTWO**
  (Take-Two Interactive, 0,1M). A felhasználó a „Vanguard S&P 500 **Dist**”-et kérte: ez az európai UCITS változat
  (VUSA/VUSD, London/Amszterdam) – a Nasdaq ITCH-ben NINCS benne (ellenőrizve) → helyette az amerikai VOO.
  Összesen ~67,2M esemény/nap ≈ 4,6 GB SQLite. Elmagyarázva: a tárhely nem nő magától, csak minden további letöltött
  tőzsdei nappal (~12 GB/nap nyers + DB); quant cégtípusok (market maker/HFT, stat arb, faktor), a projekt célja a tanulás.
- **Kész (2026-10-03): `ItchParser` 2. rész – Add Order** (Claude írta be, a felhasználó kérésére). Konstruktor:
  `ItchParser(LocalDate tradingDate, Set<String> wantedSymbols, Consumer<MarketEvent> listener)`; `wanted[]` boolean
  tömb (locate → kell-e), `midnightNanos` = a nap éjfele New Yorkban (`ZoneId America/New_York`, téli/nyári idő
  automatikus) UTC epoch ns-ben; 'A' és 'F' (azonos első 36 bájt) → `MarketEvent` ADD; `readUnsigned(bytes, offset,
  length)` általános big-endian olvasó. Kipróbálva a teljes napon (91 s): 18 891 770 ADD esemény a 22 részvényre;
  AAPL első ajánlata 04:00:00.128 NY = 09:00 UTC (pre-market nyitás), ár $278.10 – hihető. Nincs commitolva.
- ITCH fájlformátum (ellenőrizve): bináris, minden üzenet előtt 2 bájtos hossz (big-endian), a „.txt” név ellenére.
- **Folyamatban (este folytatjuk):** a felhasználó még NEM regisztrált. Utolsó kérdés: mehet-e a Databento-regisztráció
  kártyával + költségkorláttal (vagy kártya nélküli alternatíva, akkor order book nélkül). Utána: költségkorlát
  beállítása, API kulcs (soha ne a chatbe!), `DATABENTO_API_KEY` környezeti változó.
  Nyitott apróság: equityCurve scale egységesítése.

