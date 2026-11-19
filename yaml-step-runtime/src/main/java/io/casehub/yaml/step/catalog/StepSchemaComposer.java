package io.casehub.yaml.step.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.yaml.core.playbook.PlaybookCapabilities;
import io.casehub.yaml.plugin.api.Parameter;
import io.casehub.yaml.plugin.api.ParameterType;
import io.casehub.yaml.plugin.api.PluginRegistry;

import java.util.Map;
import java.util.Set;

public final class StepSchemaComposer {

    private static final Set<String> DECORATOR_KEYS = StepKeywords.DECORATOR_KEYS;

    private StepSchemaComposer() {}

    public static ObjectNode compose(PluginRegistry registry, ObjectMapper mapper) {
        return compose(registry, mapper, Set.of(
                PlaybookCapabilities.CONTROL_FLOW, PlaybookCapabilities.CONCURRENCY,
                PlaybookCapabilities.MODULES, PlaybookCapabilities.ORCHESTRATION,
                PlaybookCapabilities.DECORATORS));
    }

    public static ObjectNode compose(PluginRegistry registry, ObjectMapper mapper,
                                     Set<String> capabilities) {
        ObjectNode root  = mapper.createObjectNode();
        ArrayNode  oneOf = root.putArray("oneOf");

        for (String action : registry.availableActions()) {
            registry.resolve(action).ifPresent(definition -> {
                ObjectNode variant      = mapper.createObjectNode();
                ObjectNode variantProps = variant.putObject("properties");

                ObjectNode actionSchema = mapper.createObjectNode();
                actionSchema.put("type", "object");
                ObjectNode actionProps = actionSchema.putObject("properties");
                ArrayNode  requiredArr = actionSchema.putArray("required");

                for (Map.Entry<String, Parameter> param
                        : definition.inputs().entrySet()) {
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

        if (capabilities.contains(PlaybookCapabilities.MODULES)) {
            ObjectNode invokeVariant = mapper.createObjectNode();
            invokeVariant.putObject("properties").putObject("invoke").put("type", "object");
            invokeVariant.putArray("required").add("invoke");
            oneOf.add(invokeVariant);
        }

        ObjectNode blockVariant = mapper.createObjectNode();
        ObjectNode blockProps   = blockVariant.putObject("properties");
        ObjectNode blockArray   = blockProps.putObject("block");
        blockArray.put("type", "array");
        blockVariant.putArray("required").add("block");
        oneOf.add(blockVariant);

        if (capabilities.contains(PlaybookCapabilities.CONTROL_FLOW)) {
            ObjectNode ifThenElseVariant = mapper.createObjectNode();
            ObjectNode ifProps           = ifThenElseVariant.putObject("properties");
            ifProps.putObject("if").put("type", "string");
            ObjectNode thenSchema = ifProps.putObject("then");
            thenSchema.put("type", "array");
            ifProps.putObject("else").put("type", "array");
            ifThenElseVariant.putArray("required").add("if").add("then");
            oneOf.add(ifThenElseVariant);

            ObjectNode matchVariant = mapper.createObjectNode();
            ObjectNode matchProps   = matchVariant.putObject("properties");
            matchProps.putObject("match").put("type", "string");
            matchProps.putObject("cases").put("type", "array");
            matchVariant.putArray("required").add("match").add("cases");
            oneOf.add(matchVariant);
        }

        if (capabilities.contains(PlaybookCapabilities.CONCURRENCY)) {
            ObjectNode parallelVariant = mapper.createObjectNode();
            ObjectNode parallelProps   = parallelVariant.putObject("properties");
            parallelProps.putObject("parallel").put("type", "array");
            ObjectNode advanceAfter = parallelProps.putObject("advance-after");
            ArrayNode  advanceOneOf = advanceAfter.putArray("oneOf");
            advanceOneOf.addObject().put("type", "integer");
            advanceOneOf.addObject().put("type", "string").putArray("enum").add("all");
            parallelProps.putObject("cancel-remaining").put("type", "boolean");
            parallelProps.putObject("collection-timeout").put("type", "string");
            parallelProps.putObject("concurrency").put("type", "integer");
            parallelVariant.putArray("required").add("parallel");
            oneOf.add(parallelVariant);

            ObjectNode raceVariant = mapper.createObjectNode();
            ObjectNode raceProps   = raceVariant.putObject("properties");
            raceProps.putObject("race").put("type", "array");
            raceVariant.putArray("required").add("race");
            oneOf.add(raceVariant);
        }

        if (capabilities.contains(PlaybookCapabilities.ORCHESTRATION)) {
            ObjectNode pauseVariant = mapper.createObjectNode();
            pauseVariant.putObject("properties").putObject("pause").put("type", "string");
            pauseVariant.putArray("required").add("pause");
            oneOf.add(pauseVariant);

            ObjectNode resumeVariant = mapper.createObjectNode();
            resumeVariant.putObject("properties").putObject("resume").put("type", "string");
            resumeVariant.putArray("required").add("resume");
            oneOf.add(resumeVariant);
        }

        ObjectNode sharedProps = root.putObject("properties");
        sharedProps.putObject("step").put("type", "string");

        if (capabilities.contains(PlaybookCapabilities.DECORATORS)) {
            for (String key : DECORATOR_KEYS) {
                sharedProps.putObject(key);
            }

            ObjectNode atSchema = sharedProps.putObject("at");
            ArrayNode  atOneOf  = atSchema.putArray("oneOf");
            atOneOf.addObject().put("type", "string");
            ObjectNode atArray = atOneOf.addObject();
            atArray.put("type", "array");
            atArray.putObject("items").put("type", "string");
            ObjectNode atObject = atOneOf.addObject();
            atObject.put("type", "object");
            ObjectNode atObjProps = atObject.putObject("properties");
            atObjProps.putObject("metric").put("type", "string");
            atObjProps.putObject("mode").putArray("enum").add("wait").add("guard");
            atObject.putArray("required").add("metric");

            ObjectNode onCompleteSchema = sharedProps.putObject("on-complete");
            onCompleteSchema.put("type", "array");
            onCompleteSchema.putObject("items").put("$ref", "#");

            sharedProps.putObject("resource").put("type", "string");

            sharedProps.putObject("priority").putArray("enum")
                       .add("background").add("normal").add("high");

            sharedProps.putObject("cancel").put("type", "string");
            sharedProps.putObject("background").put("type", "boolean");
        }

        return root;
    }


    private static String mapType(ParameterType type) {
        return switch (type) {
            case STRING -> "string";
            case INTEGER -> "integer";
            case NUMBER -> "number";
            case BOOLEAN -> "boolean";
            case ARRAY -> "array";
            case OBJECT -> "object";
            case EXPRESSION -> "string";
        };
    }
}
