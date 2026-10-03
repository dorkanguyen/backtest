package org.example.marketdata.itch;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.example.marketdata.MarketEventStore;
import org.example.marketdata.SqliteMarketEventStore;

/**
 * Command line tool that imports one Nasdaq ITCH file into the market event database.
 *
 * <p>Usage: {@code ItchImporter <itch-file.gz> <trading-date yyyy-mm-dd> <database-file>}.
 * Events of the trading day that are already in the database are deleted first, so the import
 * can be repeated safely.
 */
public final class ItchImporter {

    /** The symbols we keep; all other stocks in the file are skipped. */
    static final Set<String> SYMBOLS = Set.of(
            "AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "TSLA", "AVGO", "AMD", "INTC",
            "QCOM", "AMAT", "NFLX", "COST", "PEP", "ADBE", "CSCO", "SBUX", "QQQ", "SPY", "VOO",
            "TTWO");

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private ItchImporter() {
    }

    /**
     * Runs the import.
     *
     * @param args ITCH file, trading date and database file
     * @throws IOException if the ITCH file cannot be read
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.err.println(
                    "Usage: ItchImporter <itch-file.gz> <trading-date yyyy-mm-dd> <database-file>");
            System.exit(1);
        }
        Path itchFile = Path.of(args[0]);
        LocalDate tradingDate = LocalDate.parse(args[1]);
        String databaseFile = args[2];

        long start = System.nanoTime();
        long[] count = {0};
        try (MarketEventStore store = new SqliteMarketEventStore(databaseFile);
                ItchReader reader = new ItchReader(itchFile)) {
            store.deleteBetween(startOfDayNanos(tradingDate),
                    startOfDayNanos(tradingDate.plusDays(1)));
            ItchParser parser = new ItchParser(tradingDate, SYMBOLS, event -> {
                store.save(event);
                count[0]++;
                if (count[0] % 10_000_000 == 0) {
                    System.out.printf("%,d events saved...%n", count[0]);
                }
            });
            byte[] message;
            while ((message = reader.nextMessage()) != null) {
                parser.parse(message);
            }
        }
        double seconds = (System.nanoTime() - start) / 1e9;
        System.out.printf("Done: %,d events in %.0f seconds.%n", count[0], seconds);
    }

    private static long startOfDayNanos(LocalDate date) {
        return date.atStartOfDay(NEW_YORK).toEpochSecond() * 1_000_000_000L;
    }
}
