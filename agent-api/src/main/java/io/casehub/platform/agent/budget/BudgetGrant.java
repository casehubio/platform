package io.casehub.platform.agent.budget;

import java.util.Objects;

public sealed interface BudgetGrant {

    record Granted(BudgetPressure pressure, String correlationId) implements BudgetGrant {
        public Granted {
            Objects.requireNonNull(pressure, "pressure must not be null");
            Objects.requireNonNull(correlationId, "correlationId must not be null");
        }
    }

    record Denied(String reason, BudgetPressure pressure) implements BudgetGrant {
        public Denied {
            Objects.requireNonNull(reason, "reason must not be null");
            Objects.requireNonNull(pressure, "pressure must not be null");
        }
    }
}
