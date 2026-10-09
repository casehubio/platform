package io.casehub.yaml.step.eval;

import io.casehub.yaml.core.orchestration.ExecutionScope;
import io.casehub.yaml.core.orchestration.OrcAccumulator;
import io.casehub.yaml.core.orchestration.OrcCounter;
import io.casehub.yaml.core.orchestration.OrcFlag;
import io.casehub.yaml.core.orchestration.OrcGauge;
import io.casehub.yaml.core.orchestration.OrcSignal;
import io.casehub.yaml.core.resolver.VariableSource;

public final class PrimitiveVariableSource implements VariableSource {
    private final ExecutionScope scope;

    public PrimitiveVariableSource(ExecutionScope scope) {
        this.scope = scope;
    }

    @Override
    public String resolve(String name) {
        int dot = name.indexOf('.');
        if (dot < 0) return null;
        String prefix = name.substring(0, dot);
        String primName = name.substring(dot + 1);

        try {
            return switch (prefix) {
                case "counter" -> String.valueOf(scope.primitive(primName, OrcCounter.class).get());
                case "flag" -> String.valueOf(scope.primitive(primName, OrcFlag.class).get());
                case "signal" -> String.valueOf(scope.primitive(primName, OrcSignal.class).isSignalled());
                case "gauge" -> String.valueOf(scope.primitive(primName, OrcGauge.class).get());
                case "accumulator" -> String.valueOf(scope.primitive(primName, OrcAccumulator.class).get());
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }
}
