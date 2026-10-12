package org.example.api;

import java.util.List;
import org.example.catalog.Catalog;
import org.example.signal.Definition;
import org.example.signal.ParameterSpec;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoint that tells a user interface what a backtest can be built from. */
@RestController
public class CatalogController {

    private final Catalog catalog;

    /**
     * Creates the controller. Spring calls this and passes in the catalog (dependency injection).
     *
     * @param catalog the available signals, filters and execution algorithms
     */
    public CatalogController(Catalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Returns every signal, filter and execution algorithm with its settings.
     *
     * @return the catalog in a form that is easy to turn into JSON
     */
    @GetMapping("/api/catalog")
    public CatalogView catalog() {
        return new CatalogView(
                views(catalog.signals()),
                views(catalog.filters()),
                views(catalog.executionAlgos()));
    }

    private static List<DefinitionView> views(List<? extends Definition> definitions) {
        return definitions.stream()
                .map(d -> new DefinitionView(d.id(), d.name(), d.description(), d.parameters()))
                .toList();
    }

    /**
     * The JSON answer of {@code GET /api/catalog}.
     *
     * @param signals the kinds of signals
     * @param filters the kinds of trading filters
     * @param executionAlgos the kinds of execution algorithms
     */
    public record CatalogView(
            List<DefinitionView> signals,
            List<DefinitionView> filters,
            List<DefinitionView> executionAlgos) {

    }

    /**
     * One signal, filter or execution algorithm in the JSON answer.
     *
     * @param id the identifier to use in a backtest request
     * @param name the name shown to the user
     * @param description a short explanation
     * @param parameters the settings
     */
    public record DefinitionView(
            String id, String name, String description, List<ParameterSpec> parameters) {

    }
}
