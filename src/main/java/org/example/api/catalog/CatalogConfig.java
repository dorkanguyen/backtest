package org.example.api.catalog;

import org.example.catalog.Catalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gives the web application the standard catalog of signals, filters and execution algorithms.
 *
 * <p>The list itself is in {@link Catalog#standard()}, which does not need Spring, so the
 * research tools can use the same list.
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
        return Catalog.standard();
    }
}
