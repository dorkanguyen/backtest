package org.example.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.example.execution.ExecutionAlgoDefinition;
import org.example.execution.ThrottledExecutionDefinition;
import org.example.filter.FilterDefinition;
import org.example.filter.SpreadFilterDefinition;
import org.example.signal.OfiSignalDefinition;
import org.example.signal.SignalDefinition;
import org.example.signal.VwapSignalDefinition;
import org.junit.jupiter.api.Test;

class CatalogTest {

    private final Catalog catalog = new Catalog(
            List.of(new OfiSignalDefinition(), new VwapSignalDefinition()),
            List.of(new SpreadFilterDefinition()),
            List.of(new ThrottledExecutionDefinition()));

    @Test
    void findsEverythingById() {
        assertThat(catalog.signal("vwap")).isInstanceOf(VwapSignalDefinition.class);
        assertThat(catalog.filter("spread")).isInstanceOf(SpreadFilterDefinition.class);
        assertThat(catalog.executionAlgo("throttled"))
                .isInstanceOf(ThrottledExecutionDefinition.class);
    }

    @Test
    void standardCatalogHasEverything() {
        Catalog standard = Catalog.standard();

        assertThat(standard.signals()).extracting(SignalDefinition::id).containsExactly(
                "impactPrice", "decayingLevels", "vwap", "ofi", "tradeImbalance", "momentum");
        assertThat(standard.filters()).extracting(FilterDefinition::id)
                .containsExactly("tradeCount", "spread");
        assertThat(standard.executionAlgos()).extracting(ExecutionAlgoDefinition::id)
                .containsExactly("throttled", "joinBestQuote");
    }

    @Test
    void unknownIdIsRejected() {
        assertThatThrownBy(() -> catalog.signal("magic"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown signal: magic");
    }

    @Test
    void sameIdTwiceIsRejected() {
        assertThatThrownBy(() -> new Catalog(
                List.of(new OfiSignalDefinition(), new OfiSignalDefinition()),
                List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ofi");
    }
}
