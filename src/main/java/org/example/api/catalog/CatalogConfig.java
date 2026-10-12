package org.example.api.catalog;

import java.util.List;
import org.example.catalog.Catalog;
import org.example.execution.ThrottledExecutionDefinition;
import org.example.filter.SpreadFilterDefinition;
import org.example.filter.TradeCountFilterDefinition;
import org.example.signal.DecayingLevelSignalDefinition;
import org.example.signal.ImpactPriceSignalDefinition;
import org.example.signal.OfiSignalDefinition;
import org.example.signal.VwapSignalDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one place where the available signals, filters and execution algorithms are listed.
 *
 * <p>A new kind of signal needs one new line here. The signals themselves know nothing about
 * Spring, so they also work outside the web application.
 */
@Configuration
public class CatalogConfig {

    /**
     * Creates the catalog. Spring calls this once and passes the result to whoever needs it.
     *
     * @return the catalog of everything a backtest can be built from
     */
    @Bean
    public Catalog catalog() {
        return new Catalog(
                List.of(
                        new ImpactPriceSignalDefinition(),
                        new DecayingLevelSignalDefinition(),
                        new VwapSignalDefinition(),
                        new OfiSignalDefinition()),
                List.of(
                        new TradeCountFilterDefinition(),
                        new SpreadFilterDefinition()),
                List.of(
                        new ThrottledExecutionDefinition()));
    }
}
