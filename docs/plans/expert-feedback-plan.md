# Szakértői visszajelzés – mit csináltunk és mi a terv

Állapot: 2026-11-07 (utolsó commit `54e3e9a`).

A szakértő visszajelzése 2026-10-24-én érkezett, angolul. Ez a fájl a jegyzeteinkből készült, nem
szó szerinti idézet. Ha megvan az eredeti szöveg, érdemes mellé bemásolni.

---

## 1. rész – A szakértő 5 tanácsa részletesen

### 1. tanács – Teszteld a tanácsadókat külön, párban és kombinációkban

**Mit mondott:** a tanácsadók gyakran ugyanazt mérik, csak kicsit másképp. Ilyen például az impact
price és a lecsengő szintek: mindkettő a könyv „mérlegéből” számol. Ha kettőt összekeverünk, ami
ugyanazt mondja, az nem ad hozzá új információt, csak úgy tűnik, mintha két vélemény lenne. Ezért
meg kell nézni:
- mennyit ér egy tanácsadó **egyedül**,
- mennyit tesz hozzá egy **második** (párban jobb-e, mint egyedül),
- mennyire **hasonlítanak** egymásra (korreláció).

**Almás példa:** két barátod mondja, hogy drágul az alma. Ha mindketten ugyanazt az újságcikket
olvasták, az valójában egy vélemény. Ha az egyik a piacon kérdezősködött, a másik az időjárást
figyeli, az kettő.

### 2. tanács – Mérd meg, mi történik az árral a jel után

**Mit mondott:** mielőtt a jelet bekötjük a kereskedésbe, nézzük meg közvetlenül: amikor a tanácsadó
„vegyél”-t mond, tényleg emelkedik-e utána az ár? Mennyit? Pár másodperc múlva? Egy perc múlva?
Ez tisztább, mint a backtest, mert a backtest eredményébe sok más is belekeveredik (spread,
jutalék, végrehajtás).

### 3. tanács – Valódi market making: jegyezz mindkét oldalon, eltolva

**Mit mondott:** egy market maker egyszerre tart kint vételi **és** eladási cetlit, és a spreadet
keresi meg. A kívánt pozíció nem az, hogy „melyik oldalra teszek ki cetlit”, hanem hogy **hova
tolom el** a két cetlit.

Példa: vételi irányba szeretnék menni →
- a vételi cetli **agresszív**: a legjobb bid-en, hogy hamar teljesüljön;
- az eladási cetli **óvatos**: hátrébb, a 2–3. ask szinten, hogy csak jó áron adjak el.

Ha eladni szeretnék, fordítva. Így mindig kereskedem, de a pozícióm lassan a kívánt irányba megy,
és közben a spreadet is megkeresem.

**Almás példa:** a piaci árus egyszerre vesz és ad el almát. Ha kevés almája van, a vételi árát
kicsit felemeli (hogy többen eladjanak neki), az eladási árát pedig magasabbra teszi (hogy lassabban
fogyjon).

### 4. tanács – Legyen kifejezett előrejelzési időtáv

**Mit mondott:** minden jelnek legyen egy időtávja: mire jósol, 1 másodpercre, 1 percre vagy 5
percre? A különböző időtávú jelekhez különböző stratégia tartozik. Egy 1 másodperces jellel nem
lehet 5 percig tartani a pozíciót, és fordítva. Akár több stratégia is futhat párhuzamosan, mindegyik
a saját időtávjával.

### 5. tanács – Kevésbé érzékeny végrehajtás: simítsd a célpozíciót

**Mit mondott:** ha a célpozíció ugrál (5 → 2 → 5), ne kössünk mindegyik változásnál, mert minden
kötés pénzbe kerül (spread + jutalék). A célpozíciót egy időablakban kell „szétteríteni”, átlagolni,
és csak a tartós változást követni.

**Termosztát példa:** a fűtés nem kapcsol be-ki minden apró hőmérséklet-ingadozásnál, csak ha
tartósan hideg van.

---

## 2. rész – Amit már megcsináltunk a tanácsok alapján

### 1. tanácsra – kész ✅

**Kiértékelő (`research.SignalEvaluator` + `SignalReport`, 2026-10-24/25):**
- minden tanácsadót mér egyedül, párban és nagyobb csoportban (egyenlő súllyal; a maximális
  csoportméret beállítható: `--max-group`);
- kiszámolja a tanácsadók közti korrelációs mátrixot.

**Eredmények (6 nap, 22 részvény):**
- Az impactPrice ↔ decayingLevels korreláció 0.56–0.82, párban alig jobbak, mint egyedül → **a
  szakértőnek igaza volt**, a kettőből egy is elég.
- A VWAP és a momentum szinte egymás tükörképe (korreláció ≈ −0.7).
- A legjobb pár: NVDA-n az impactPrice + VWAP 1 percre, 6/6 napon jó, +0.66 cent.
- Az OFI-t a VWAP-pal keverve a keverék rosszabb lett.

### 2. tanácsra – kész ✅

**Kiértékelő:** másodpercenként feljegyzi a mid árat, a fél spreadet és minden véleményt (az esemény
**előtti** állapotból, hogy ne nézzen a jövőbe). Ezután 4 időtávra (1 mp / 10 mp / 1 perc / 5 perc)
méri:
- a találati arányt,
- az átlagos mozgást centben, a vélemény irányában,
- az IC-t (a vélemény és a mozgás korrelációját),
- a „jó napok” számát (hány napon volt pozitív az átlagos mozgás, pl. 6/6).

**Kalibráló (`research.SignalCalibrator` + `CalibrationReport`, 2026-10-25):** a véleményeket
erősség szerint sávokba osztja (0.3–0.5, 0.5–0.7, 0.7–0.9, 0.9–1.0), és minden sávra megadja a
**várt mozgást centben**, időtávonként. Tanuló napokon tanul, vizsga napokon ellenőriz.

**Adat:** 1 nap helyett **6 nap** (2025-12-08 … 12-12 és 2026-06-12), mind a 22 részvény (~46,7 GB).

**Eredmények:**
- Az impactPrice és a decayingLevels **mind a 22 részvényen** jó 1 mp-re (6-ból legalább 5 napon),
  a nyári napon is → ezek a legstabilabb jelek.
- A mozgás pici: a fél spread ~10–13%-a → market orderrel sosem éri meg.
- A kalibrált táblázat a vizsganapokon is működik 1 és 10 mp-re (általánosít). Erősebb vélemény →
  nagyobb mozgás NVDA-n (0.10 → 0.32 cent) és QQQ-n, AAPL-en lapos. A legerősebb sáv is csak
  ≤ ~0.4 cent.
- Az OFI és a kötési nyomás a széles spreadű részvényeken (ADBE, COST, QCOM, META…) inkább jó, a
  nagyon likvideken (NVDA, QQQ, SPY) fordított → a jel a részvény típusától függ.

### 4. tanácsra – félig kész 🟡

**Kész a mérés:** tudjuk, melyik tanácsadó melyik időtávon működik.

| Időtáv | Mi működik |
|---|---|
| 1–10 mp | impactPrice, decayingLevels (minden részvényen) |
| 1 perc | VWAP – de csak NVDA-n és NFLX-en; impactPrice kb. a részvények felén |
| 5 perc | semmi sem megbízható (napról napra előjelet vált) |

**Hiányzik:** a stratégia ezt még **nem használja**. A tanácsadók egyetlen −1…+1 véleményt adnak,
időtáv nélkül, és a keverő ezeket átlagolja.

### 3. és 5. tanácsra – még nincs ❌

- A `JoinBestQuote` egyetlen cetlit tart kint, egy oldalon.
- A túlkereskedés ellen csak a tűrés (kis változásnál nem kötünk) és a várakozás (két kötés között
  legalább X mp) véd.

### Mellékes tanulságok

- **Két új tanácsadó:** kötési nyomás (`tradeImbalance`) és `momentum`. Egyik sem következetes.
- **Túlillesztés-lecke:** 4 nap után a momentum NVDA-n 0/4 volt (minden nap rossz irányba mutatott),
  ezért csábító lett volna megfordítani. A következő 2 napon mindkétszer jó volt. → Kevés napból
  soha ne döntsünk, és ne a vizsga napokon hangoljunk.

---

## 3. rész – A teljes terv a tanácsok alapján

A sorrend úgy épül fel, hogy minden lépés használja az előzőt, és mindegyik után backtesttel mérünk
(ugyanazokon a napokon, a JoinBestQuote mostani eredményéhez hasonlítva).

### 1. lépés – Jeltudatos limit cetli (a 3. tanács első darabja)

**Mit:** a `JoinBestQuote` kap egy kalibrált jelet. Ha a jel szerint erős mozgás jön **a cetlink
ellen**, a cetlit levesszük, vagy ki sem tesszük. Ha a jel megnyugszik, visszatesszük.

**Miért:** a kint lévő cetli épp akkor teljesül a legkönnyebben, amikor a piac ellenünk mozdul
(„adverse selection”). Ha erre számítunk, kevesebb rossz kötésünk lesz.

**Paraméter:** levételi küszöb centben (pl. „ha a várt mozgás ellenünk > 0.3 cent → levesz”).

**Mérés:** a maker kötések átlagos eredménye 1 mp / 10 mp után (a markout), plusz PnL, a kötések
száma és a jutalék.

### 2. lépés – Várt mozgás centben = költségtudatos kereskedés (a 4. tanács befejezése)

**Mit:**
- A kalibrációs táblázatot elmentjük (tanuló napokból), és a tanácsadók mellé betöltjük.
- Egy új komponens (pl. `ExpectedMoveModel`) a véleményből **várt mozgást** ad centben,
  **időtávonként**.
- A keverő a véleményátlag helyett a várt mozgásokat kombinálja.
- Csak akkor kereskedünk, ha a várt mozgás nagyobb a költségnél:
  - market ordernél: fél spread + jutalék/db;
  - limit ordernél: jutalék − rebate.

**Taxis példa:** csak akkor indulunk el, ha a fuvardíj több, mint a benzin.

**Fontos:** a kalibráló táblázatot csak tanuló napokból készítjük, a backtestet vizsga napokon
futtatjuk (különben túlillesztünk).

### 3. lépés – Időtávonkénti stratégiák (a 4. tanács második fele)

**Mit:** a rövid (1–10 mp) és a hosszabb (1 perc) jeleket külön kezeljük. Például:
- a rövid távú jelek csak a **végrehajtást** irányítják (mikor és melyik oldalon legyen cetli);
- az 1 perces jelek a **célpozíciót** adják.

Ez a quant cégeknél bevett felosztás: „alpha” (hova menjünk) ↔ „execution” (hogyan menjünk oda olcsón).

### 4. lépés – Célpozíció simítása (5. tanács)

**Mit:** egy új, paraméterezhető réteg a stratégia és a végrehajtás között. Lehetőségek (mérni
kell, melyik a jobb):
- **Mozgó átlag** (EMA): a cél lassan követi a nyers célt (paraméter: felezési idő mp-ben).
- **Hiszterézis**: belépéshez erősebb vélemény kell, mint a bennmaradáshoz (pl. belép > 0.6,
  kilép < 0.2).
- **Minimális tartási idő**: egy pozíciót legalább X mp-ig tartunk.

**Mérés:** a kötések száma és a jutalék csökken-e, miközben a jutalék nélküli eredmény nem romlik.

### 5. lépés – Kétoldalú, eltolt jegyzés = valódi market making (3. tanács teljes)

**Mit:** új végrehajtó (pl. `SkewedQuoting`), amely egyszerre tart kint **vételi és eladási** cetlit:
- a kívánt irány oldalán a legjobb árra áll be, a másik oldalon hátrébb (2–3. szint);
- az eltolás mértéke a célpozíció és a mostani pozíció különbségétől és a jelektől függ;
- **készletkorlát** (max hosszú / rövid pozíció), és ha a pozíció túl nagy → csak a csökkentő oldalon
  jegyez.

**Mi kell hozzá:** az `ExecutionSimulator` már tud több cetlit kezelni. A végrehajtó-interfész már
utasítás-listát ad (`Place`/`Cancel`) és visszajelzést kap a kötésekről (`onFill`) → nagy
átalakítás nem kell.

**Kockázat:** ez a legnehezebben hihetően szimulálható rész (sorban állás, a saját cetlink hatása
a piacra) → óvatos teljesülési szabály marad.

### 6. lépés – Megbízhatóbb mérés (az 1–2. tanács továbbvitele)

- **Több nap adat** (a 6 kevés; cél: legalább 20, különböző piaci napok: trendes, oldalazó, volatilis).
- **Statisztikai szignifikancia:** egymást nem átfedő minták, t-érték / konfidencia-intervallum
  (az 5 perces mintákból naponta csak ~76 független van).
- **Részvényenkénti beállítások** (a mérés szerint a széles és a szűk spreadű részvény másképp
  viselkedik), esetleg „piaci rezsim” felismerés (trend vs. visszarendeződés).
- **Szigorú szétválasztás:** tanuló napok → hangolás, vizsga napok → csak ellenőrzés
  (walk-forward).

### Összefoglaló táblázat

| Lépés | Melyik tanács | Állapot |
|---|---|---|
| Kiértékelő (külön/pár/korreláció, időtávok) | 1, 2 | ✅ kész |
| Kalibráló (várt mozgás centben, tanuló/vizsga) | 2, 4 | ✅ kész |
| 6 nap adat, 22 részvény | 1, 2 | ✅ kész |
| 1. Jeltudatos limit cetli | 3 | ⏭ következő |
| 2. Költségtudatos kereskedés | 4 | tervben |
| 3. Időtávonkénti stratégiák (alpha ↔ execution) | 4 | tervben |
| 4. Célpozíció simítása | 5 | tervben |
| 5. Kétoldalú, eltolt jegyzés | 3 | tervben |
| 6. Több nap, szignifikancia, részvényenkénti beállítás | 1, 2 | folyamatos |
