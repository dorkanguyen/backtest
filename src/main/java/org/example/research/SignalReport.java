package org.example.research;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.api.catalog.CatalogConfig;
import org.example.backtest.TradingPeriod;
import org.example.marketdata.MarketEventStore;
import org.example.marketdata.SqliteMarketEventStore;
import org.example.research.SignalEvaluationReport.GroupResult;
import org.example.research.SignalEvaluationReport.HorizonResult;
import org.example.signal.Signal;
import org.example.signal.SignalDefinition;

/**
 * Command line tool that evaluates every signal of the catalog, with its default settings, on
 * one symbol and one day, and prints the results as tables.
 *
 * <p>Usage: {@code SignalReport <database-file> <symbol> <date yyyy-mm-dd> [start HH:mm]
 * [end HH:mm]}. The default period is 09:35 to 15:55; the book is warmed up from 04:00.
 */
public final class SignalReport {

    private static final LocalTime WARM_UP_START = LocalTime.of(4, 0);
    private static final LocalTime DEFAULT_START = LocalTime.of(9, 35);
    private static final LocalTime DEFAULT_END = LocalTime.of(15, 55);
    private static final Duration SAMPLE_INTERVAL = Duration.ofSeconds(1);
    private static final List<Duration> HORIZONS = List.of(Duration.ofSeconds(1),
            Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofMinutes(5));
    private static final double CONVICTION_THRESHOLD = 0.3;

    private SignalReport() {
    }

    /**
     * Runs the evaluation and prints the report.
     *
     * @param args database file, symbol, date and optionally start and end time
     */
    public static void main(String[] args) {
        if (args.length != 3 && args.length != 5) {
            System.err.println("Usage: SignalReport <database-file> <symbol> <date yyyy-mm-dd>"
                    + " [start HH:mm] [end HH:mm]");
            System.exit(1);
        }
        String symbol = args[1];
        LocalDate date = LocalDate.parse(args[2]);
        LocalTime start = args.length == 5 ? LocalTime.parse(args[3]) : DEFAULT_START;
        LocalTime end = args.length == 5 ? LocalTime.parse(args[4]) : DEFAULT_END;
        TradingPeriod period = new TradingPeriod(date.atTime(WARM_UP_START), date.atTime(start),
                date.atTime(end));

        Map<String, Signal> signals = new LinkedHashMap<>();
        for (SignalDefinition definition : new CatalogConfig().catalog().signals()) {
            signals.put(definition.id(), definition.create(symbol, Map.of()));
        }

        final long started = System.nanoTime();
        SignalEvaluationReport report;
        try (MarketEventStore store = new SqliteMarketEventStore(args[0])) {
            report = new SignalEvaluator(SAMPLE_INTERVAL, HORIZONS, CONVICTION_THRESHOLD)
                    .evaluate(store, symbol, period, signals);
        }
        System.out.printf("%s %s %s-%s%n", symbol, date, start, end);
        System.out.print(format(report));
        System.out.printf("Done in %.0f seconds.%n", (System.nanoTime() - started) / 1e9);
    }

    /**
     * Formats the report as text tables.
     *
     * @param report the results to show
     * @return the tables, ending with a line break
     */
    static String format(SignalEvaluationReport report) {
        StringBuilder text = new StringBuilder();
        text.append(String.format("%,d samples, average half spread %.2f cents%n",
                report.sampleCount(), report.averageHalfSpreadCents()));
        text.append(String.format("Active = opinion stronger than %.1f; hit = moved its way;"
                + " move = average cents its way; IC = correlation of opinion and move%n",
                CONVICTION_THRESHOLD));

        List<String> names = report.signals();
        int width = Math.max(16, names.stream().mapToInt(String::length).max().orElse(0) + 2);
        text.append(String.format("%nCorrelation between signals%n"));
        text.append(String.format("%-" + width + "s", ""));
        names.forEach(name -> text.append(String.format("%" + width + "s", name)));
        text.append(String.format("%n"));
        for (int a = 0; a < names.size(); a++) {
            text.append(String.format("%-" + width + "s", names.get(a)));
            for (double value : report.correlations().get(a)) {
                text.append(String.format("%" + width + "s", number(value, "%.2f")));
            }
            text.append(String.format("%n"));
        }

        int groupWidth = report.groups().stream()
                .mapToInt(group -> String.join(" + ", group.signals()).length())
                .max().orElse(0) + 2;
        int horizonCount = report.groups().get(0).horizons().size();
        for (int h = 0; h < horizonCount; h++) {
            Duration horizon = report.groups().get(0).horizons().get(h).horizon();
            text.append(String.format("%nHorizon %s%n", label(horizon)));
            text.append(String.format("%-" + groupWidth + "s%9s%8s%9s%8s%n",
                    "signals", "active", "hit", "move", "IC"));
            for (GroupResult group : report.groups()) {
                HorizonResult result = group.horizons().get(h);
                text.append(String.format("%-" + groupWidth + "s%9d%8s%9s%8s%n",
                        String.join(" + ", group.signals()), result.activeSamples(),
                        number(result.hitRate() * 100, "%.1f%%"),
                        number(result.averageMoveCents(), "%+.3f"),
                        number(result.informationCoefficient(), "%+.3f")));
            }
        }
        return text.toString();
    }

    private static String number(double value, String format) {
        return Double.isNaN(value) ? "-" : String.format(format, value);
    }

    private static String label(Duration horizon) {
        return horizon.toSeconds() % 60 == 0
                ? horizon.toMinutes() + " min"
                : horizon.toSeconds() + " s";
    }
}
