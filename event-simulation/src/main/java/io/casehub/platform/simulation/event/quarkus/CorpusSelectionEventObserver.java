package io.casehub.platform.simulation.event.quarkus;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionContext;
import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.platform.simulation.SimulationConfig;
import io.casehub.platform.simulation.SimulationRuntime;
import io.casehub.platform.simulation.TriggerConfig;
import io.cloudevents.CloudEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class CorpusSelectionEventObserver {

    private static final Logger LOG = Logger.getLogger(CorpusSelectionEventObserver.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    SimulationRuntime simulationRuntime;

    @Inject
    SimulationConfig simulationConfig;

    @Inject
    Instance<ExpressionEngineRegistry> expressionRegistryInstance;

    private final Map<String, List<TriggerBinding>> bindingsByEventType = new HashMap<>();

    @PostConstruct
    void init() {
        Map<String, TriggerConfig> triggers = simulationConfig.triggers();
        if (triggers.isEmpty()) {
            return;
        }

        ExpressionEngineRegistry registry = expressionRegistryInstance.isResolvable()
                ? expressionRegistryInstance.get() : null;

        for (var entry : triggers.entrySet()) {
            String triggerName = entry.getKey();
            TriggerConfig config = entry.getValue();
            CompiledExpression<Map, Boolean> compiled = null;

            if (config.condition() != null) {
                if (registry == null) {
                    LOG.warning("Trigger '" + triggerName + "' has a condition but no ExpressionEngineRegistry"
                            + " is available — trigger will fire on event type match only");
                } else {
                    String engineType = registry.resolveDefault(ExpressionContext.CONDITION);
                    if (engineType == null) {
                        LOG.warning("Trigger '" + triggerName + "' has a condition but no default CONDITION"
                                + " engine is registered — trigger will fire on event type match only");
                    } else {
                        compiled = registry.compile(engineType, config.condition(), Map.class, Boolean.class);
                    }
                }
            }

            bindingsByEventType
                    .computeIfAbsent(config.eventType(), k -> new ArrayList<>())
                    .add(new TriggerBinding(triggerName, compiled));
        }

        LOG.info("Registered " + triggers.size() + " corpus selection trigger(s) for "
                + bindingsByEventType.size() + " event type(s)");
    }

    @SuppressWarnings("unchecked")
    void onCloudEvent(@ObservesAsync CloudEvent event) {
        List<TriggerBinding> bindings = bindingsByEventType.get(event.getType());
        if (bindings == null) {
            return;
        }

        Map<String, Object> data = null;
        for (TriggerBinding binding : bindings) {
            if (binding.condition() != null) {
                if (data == null) {
                    data = extractData(event);
                    if (data == null) {
                        continue;
                    }
                }
                try {
                    Boolean result = binding.condition().eval(data);
                    if (!Boolean.TRUE.equals(result)) {
                        continue;
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Condition evaluation failed for trigger '"
                            + binding.triggerName() + "': " + e.getMessage(), e);
                    continue;
                }
            }
            simulationRuntime.advanceStateMachines(binding.triggerName());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractData(CloudEvent event) {
        if (event.getData() == null) {
            return null;
        }
        try {
            return JSON.readValue(event.getData().toBytes(), Map.class);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Could not parse CloudEvent data as JSON map: " + e.getMessage());
            return null;
        }
    }

    private record TriggerBinding(
            String triggerName,
            CompiledExpression<Map, Boolean> condition) {}
}
