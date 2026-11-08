package io.casehub.platform.simulation.event.quarkus;

import io.casehub.platform.api.expression.ExpressionContext;
import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Startup
public class TestExpressionDefaults {

    @Inject
    ExpressionEngineRegistry registry;

    void onStart(@jakarta.enterprise.event.Observes io.quarkus.runtime.StartupEvent event) {
        registry.registerDefault(ExpressionContext.CONDITION, "mvel");
    }
}
