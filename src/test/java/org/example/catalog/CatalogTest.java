package org.example.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.example.execution.ThrottledExecutionDefinition;
import org.example.signal.OfiSignalDefinition;
import org.example.signal.SpreadFilterDefinition;
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
