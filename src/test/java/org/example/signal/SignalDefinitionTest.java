package org.example.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.example.marketdata.MarketEvent;
import org.example.marketdata.state.SymbolState;
import org.example.parameter.ParameterSpec;
import org.junit.jupiter.api.Test;

class SignalDefinitionTest {

    /** A made-up signal kind with two settings, only for testing the shared rules. */
    private static final SignalDefinition EXAMPLE = new SignalDefinition() {
        @Override
        public String id() {
            return "example";
        }

        @Override
        public String name() {
            return "Example";
        }

        @Override
        public String description() {
            return "Only for tests.";
        }

        @Override
        public List<ParameterSpec> parameters() {
            return List.of(
                    new ParameterSpec("window", "Seconds to look back.", 10, 1, 60, true),
                    new ParameterSpec(
                            "threshold", "Pressure needed to act.", 500, 0, 10_000, false));
        }

        @Override
        public Signal create(String symbol, Map<String, Double> values) {
            Map<String, Double> settings = resolve(values);
            return new Signal() {
                @Override
                public void update(MarketEvent event, SymbolState state) {
                }

                @Override
                public double conviction() {
                    return settings.get("threshold") / 10_000;
                }
            };
        }
    };

    @Test
    void missingSettingsGetTheirDefault() {
        Map<String, Double> resolved = EXAMPLE.resolve(Map.of("threshold", 800.0));

        assertThat(resolved).containsEntry("window", 10.0).containsEntry("threshold", 800.0);
    }

    @Test
    void unknownSettingIsRejected() {
        assertThatThrownBy(() -> EXAMPLE.resolve(Map.of("speed", 1.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown parameter speed");
    }

    @Test
    void valueOutsideTheRangeIsRejected() {
        assertThatThrownBy(() -> EXAMPLE.resolve(Map.of("window", 61.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window");
    }

    @Test
    void fractionIsRejectedForWholeNumberSetting() {
        assertThatThrownBy(() -> EXAMPLE.resolve(Map.of("window", 2.5)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createdSignalUsesTheChosenSettings() {
        Signal signal = EXAMPLE.create("AAPL", Map.of("threshold", 5_000.0));

        assertThat(signal.conviction()).isEqualTo(0.5);
    }

    @Test
    void defaultOutsideTheRangeIsRejected() {
        assertThatThrownBy(() -> new ParameterSpec("x", "Bad.", 100, 0, 10, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
