package io.casehub.yaml.step.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.yaml.core.step.StepParameter;
import io.casehub.yaml.step.StepCatalog;

import java.util.Map;
import java.util.Set;

public final class StepSchemaComposer {

    private static final Set<String> DECORATOR_KEYS = Set.of(
            "when", "on-success", "on-failure", "forEach", "loop",
            "retry", "timeout", "delay", "on-error", "trigger",
            "transform", "signal", "publish", "transition",
            "parallel", "semaphore", "barrier", "quorum", "race");

    private StepSchemaComposer() {}

    public static ObjectNode compose(StepCatalog catalog, ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode oneOf = root.putArray("oneOf");

        for (String action : catalog.availableActions()) {
            catalog.resolve(action).ifPresent(entry -> {
                ObjectNode variant = mapper.createObjectNode();
                ObjectNode variantProps = variant.putObject("properties");

                ObjectNode actionSchema = mapper.createObjectNode();
                actionSchema.put("type", "object");
                ObjectNode actionProps = actionSchema.putObject("properties");
                ArrayNode requiredArr = actionSchema.putArray("required");

                for (Map.Entry<String, StepParameter> param
                        : entry.definition().inputs().entrySet()) {
                    ObjectNode paramNode = actionProps.putObject(param.getKey());
                    paramNode.put("type", mapType(param.getValue().type()));
                    if (param.getValue().description() != null) {
                        paramNode.put("description", param.getValue().description());
                    }
                    if (param.getValue().required()) {
                        requiredArr.add(param.getKey());
                    }
                }

                variantProps.set(action, actionSchema);
                variant.putArray("required").add(action);
                oneOf.add(variant);
            });
        }

        ObjectNode invokeVariant = mapper.createObjectNode();
        invokeVariant.putObject("properties").putObject("invoke").put("type", "object");
        invokeVariant.putArray("required").add("invoke");
        oneOf.add(invokeVariant);

        ObjectNode sharedProps = root.putObject("properties");
        sharedProps.putObject("step").put("type", "string");
        for (String key : DECORATOR_KEYS) {
            sharedProps.putObject(key);
        }

        return root;
    }

    private static String mapType(io.casehub.yaml.core.step.StepParameterType type) {
        return switch (type) {
            case STRING -> "string";
            case INTEGER -> "integer";
            case NUMBER -> "number";
            case BOOLEAN -> "boolean";
            case ARRAY -> "array";
            case OBJECT -> "object";
        };
    }
}
