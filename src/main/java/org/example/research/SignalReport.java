package org.example.research;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.api.catalog.CatalogConfig;
import org.example.backtest.TradingPeriod;
import org.example.catalog.Catalog;
import org.example.marketdata.MarketEventStore;
import org.example.marketdata.SqliteMarketEventStore;
import org.example.research.SignalEvaluationReport.GroupResult;
import org.example.research.SignalEvaluationReport.HorizonResult;
import org.example.signal.Signal;
import org.example.signal.SignalDefinition;

/**
 * Command line tool that evaluates every signal of the catalog, with its default settings, on
 * one symbol and one or more days, and prints the results as tables.
 *
 * <p>Usage: {@code SignalReport <database-file> <symbol> <dates> [start HH:mm] [end HH:mm]
 * [--max-group N]}, where the dates are separated by commas, for example
 * {@code 2025-12-08,2025-12-09}. The default period is 09:35 to 15:55; the book is warmed up
 * from 04:00 every day. By default only single signals and pairs are shown.
 */
public final class SignalReport {

    private static final LocalTime WARM_UP_START = LocalTime.of(4, 0);
    private static final LocalTime DEFAULT_START = LocalTime.of(9, 35);
    private static final LocalTime DEFAULT_END = LocalTime.of(15, 55);
    private static final Duration SAMPLE_INTERVAL = Duration.ofSeconds(1);
    private static final List<Duration> HORIZONS = List.of(Duration.ofSeconds(1),
            Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofMinutes(5));
    private static final double CONVICTION_THRESHOLD = 0.3;
    private static final int DEFAULT_MAX_GROUP = 2;
    private static final String MAX_GROUP_OPTION = "--max-group";
    private static final DateTimeFormatter DAY_COLUMN = DateTimeFormatter.ofPattern("MM-dd");

    private SignalReport() {
    }

    /**
     * Runs the evaluation and prints the report.
     *
     * @param args database file, symbol, dates, optionally start and end time, and optionally
     *         the largest group size
     */
    public static void main(String[] args) {
        List<String> positional = new ArrayList<>(Arrays.asList(args));
        int maxGroup = DEFAULT_MAX_GROUP;
        int option = positional.indexOf(MAX_GROUP_OPTION);
        if (option >= 0 && option + 1 < positional.size()) {
            maxGroup = Integer.parseInt(positional.get(option + 1));
            positional.subList(option, option + 2).clear();
        }
        if (positional.size() != 3 && positional.size() != 5) {
            System.err.println("Usage: SignalReport <database-file> <symbol> <dates, comma"
                    + " separated> [start HH:mm] [end HH:mm] [" + MAX_GROUP_OPTION + " N]");
            System.exit(1);
        }
        String symbol = positional.get(1);
        LocalTime start = positional.size() == 5 ? LocalTime.parse(positional.get(3))
                : DEFAULT_START;
        LocalTime end = positional.size() == 5 ? LocalTime.parse(positional.get(4))
                : DEFAULT_END;
        List<TradingPeriod> periods = Arrays.stream(positional.get(2).split(","))
                .map(LocalDate::parse)
                .map(day -> new TradingPeriod(day.atTime(WARM_UP_START), day.atTime(start),
                        day.atTime(end)))
                .toList();
        Catalog catalog = new CatalogConfig().catalog();

        System.out.printf("Evaluating %s on %d day(s)...%n", symbol, periods.size());
        final long started = System.nanoTime();
        SignalEvaluationReport report;
        try (MarketEventStore store = new SqliteMarketEventStore(positional.get(0))) {
            report = new SignalEvaluator(SAMPLE_INTERVAL, HORIZONS, CONVICTION_THRESHOLD,
                    maxGroup).evaluate(store, symbol, periods, () -> newSignals(catalog, symbol));
        }
        System.out.printf("%s %s-%s%n", symbol, start, end);
        System.out.print(format(report));
        System.out.printf("Done in %.0f seconds.%n", (System.nanoTime() - started) / 1e9);
    }

    /** Creates every signal of the catalog with its default settings. */
    private static Map<String, Signal> newSignals(Catalog catalog, String symbol) {
        Map<String, Signal> signals = new LinkedHashMap<>();
        for (SignalDefinition definition : catalog.signals()) {
            signals.put(definition.id(), definition.create(symbol, Map.of()));
        }
        return signals;
    }

    /**
     * Formats the report as text tables.
     *
     * @param report the results to show
     * @return the tables, ending with a line break
     */
    static String format(SignalEvaluationReport report) {
        StringBuilder text = new StringBuilder();
        text.append(String.format("Days: %s%n", report.days()));
        text.append(String.format("%,d samples, average half spread %.2f cents%n",
                report.sampleCount(), report.averageHalfSpreadCents()));
        text.append(String.format("Active = opinion stronger than %.1f; hit = moved its way;"
                + " move = average cents its way; IC = correlation of opinion and move;"
                + " good days = days with a positive move%n", CONVICTION_THRESHOLD));

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
            text.append(String.format("%-" + groupWidth + "s%9s%8s%9s%8s%11s",
                    "signals", "active", "hit", "move", "IC", "good days"));
            report.days().forEach(day -> text.append(String.format("%8s",
                    day.format(DAY_COLUMN))));
            text.append(String.format("%n"));
            for (GroupResult group : report.groups()) {
                HorizonResult result = group.horizons().get(h);
                text.append(String.format("%-" + groupWidth + "s%9d%8s%9s%8s%11s",
                        String.join(" + ", group.signals()), result.activeSamples(),
                        number(result.hitRate() * 100, "%.1f%%"),
                        number(result.averageMoveCents(), "%+.3f"),
                        number(result.informationCoefficient(), "%+.3f"),
                        result.goodDays() + "/" + report.days().size()));
                result.dailyMoveCents().forEach(move -> text.append(String.format("%8s",
                        number(move, "%+.2f"))));
                text.append(String.format("%n"));
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
