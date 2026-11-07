package org.example.research;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.example.api.catalog.CatalogConfig;
import org.example.backtest.TradingPeriod;
import org.example.marketdata.MarketEventStore;
import org.example.marketdata.SqliteMarketEventStore;
import org.example.research.CalibrationTable.Band;
import org.example.research.CalibrationTable.Cell;
import org.example.signal.SignalDefinition;

/**
 * Command line tool that calibrates one signal of the catalog, with its default settings, on
 * one symbol: the table is made on the training days and checked on the test days.
 *
 * <p>Usage: {@code CalibrationReport <database-file> <symbol> <signal-id> <training dates>
 * <test dates>}, where the dates are separated by commas, for example
 * {@code 2025-12-08,2025-12-09}. The period is 09:35 to 15:55; the book is warmed up from
 * 04:00 every day.
 */
public final class CalibrationReport {

    private static final LocalTime WARM_UP_START = LocalTime.of(4, 0);
    private static final LocalTime START = LocalTime.of(9, 35);
    private static final LocalTime END = LocalTime.of(15, 55);
    private static final Duration SAMPLE_INTERVAL = Duration.ofSeconds(1);
    private static final List<Duration> HORIZONS = List.of(Duration.ofSeconds(1),
            Duration.ofSeconds(10), Duration.ofMinutes(1));
    private static final List<Double> BAND_EDGES = List.of(0.3, 0.5, 0.7, 0.9, 1.0);

    private CalibrationReport() {
    }

    /**
     * Runs the calibration and prints the tables.
     *
     * @param args database file, symbol, signal id, training dates and test dates
     */
    public static void main(String[] args) {
        if (args.length != 5) {
            System.err.println("Usage: CalibrationReport <database-file> <symbol> <signal-id>"
                    + " <training dates, comma separated> <test dates, comma separated>");
            System.exit(1);
        }
        String symbol = args[1];
        String signalId = args[2];
        SignalDefinition definition = new CatalogConfig().catalog().signal(signalId);
        List<TradingPeriod> training = periods(args[3]);
        List<TradingPeriod> test = periods(args[4]);

        System.out.printf("Calibrating %s on %s...%n", signalId, symbol);
        final long started = System.nanoTime();
        SignalSampler sampler = new SignalSampler(SAMPLE_INTERVAL, 1);
        SignalCalibrator calibrator = new SignalCalibrator(HORIZONS, BAND_EDGES);
        CalibrationTable trainingTable;
        CalibrationTable testTable;
        try (MarketEventStore store = new SqliteMarketEventStore(args[0])) {
            trainingTable = calibrator.calibrate(sampler.sample(store, symbol, training,
                    () -> Map.of(signalId, definition.create(symbol, Map.of()))), signalId);
            testTable = calibrator.calibrate(sampler.sample(store, symbol, test,
                    () -> Map.of(signalId, definition.create(symbol, Map.of()))), signalId);
        }
        System.out.printf("%s %s, %s-%s%n", signalId, symbol, START, END);
        System.out.printf("Training days: %s%nTest days: %s%n", dates(training), dates(test));
        System.out.print(format(trainingTable, testTable));
        System.out.printf("Done in %.0f seconds.%n", (System.nanoTime() - started) / 1e9);
    }

    private static List<TradingPeriod> periods(String dates) {
        return Arrays.stream(dates.split(","))
                .map(LocalDate::parse)
                .map(day -> new TradingPeriod(day.atTime(WARM_UP_START), day.atTime(START),
                        day.atTime(END)))
                .toList();
    }

    private static List<LocalDate> dates(List<TradingPeriod> periods) {
        return periods.stream().map(period -> period.start().toLocalDate()).toList();
    }

    /**
     * Formats a training table and a test table of the same signal side by side.
     *
     * @param training the table made on the training days
     * @param test the table made on the test days, with the same bands and horizons
     * @return the table as text, ending with a line break
     */
    static String format(CalibrationTable training, CalibrationTable test) {
        StringBuilder text = new StringBuilder();
        text.append(String.format("Expected move in cents in the direction of the opinion:"
                + " training | test%n"));
        text.append(String.format("%-11s%10s%17s", "opinion", "half sprd", "samples"));
        for (Duration horizon : training.horizons()) {
            text.append(String.format("%17s", label(horizon)));
        }
        text.append(String.format("%n"));
        for (int b = 0; b < training.bands().size(); b++) {
            Band trainingBand = training.bands().get(b);
            Band testBand = test.bands().get(b);
            text.append(String.format("%-11s%10s%17s",
                    String.format("%.1f-%.1f", trainingBand.from(), trainingBand.to()),
                    number(trainingBand.averageHalfSpreadCents(), "%.2f"),
                    String.format("%,d | %,d", trainingBand.cells().get(0).samples(),
                            testBand.cells().get(0).samples())));
            for (int h = 0; h < trainingBand.cells().size(); h++) {
                Cell trainingCell = trainingBand.cells().get(h);
                Cell testCell = testBand.cells().get(h);
                text.append(String.format("%17s",
                        number(trainingCell.averageMoveCents(), "%+.3f") + " | "
                                + number(testCell.averageMoveCents(), "%+.3f")));
            }
            text.append(String.format("%n"));
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
