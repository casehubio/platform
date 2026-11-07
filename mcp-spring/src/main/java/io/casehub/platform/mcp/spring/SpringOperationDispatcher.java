package io.casehub.platform.mcp.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpParam;
import io.casehub.platform.api.mcp.McpParamFormat;
import io.casehub.platform.mcp.DomainModelRegistry;
import io.casehub.platform.mcp.OperationDescriptor;
import io.casehub.platform.mcp.ParameterDescriptor;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SpringOperationDispatcher {

    private final DomainModelRegistry registry;
    private final ApplicationContext context;
    private final ObjectMapper mapper;

    public SpringOperationDispatcher(DomainModelRegistry registry,
                                      ApplicationContext context,
                                      ObjectMapper mapper) {
        this.registry = registry;
        this.context = context;
        this.mapper = mapper;
    }

    public Object dispatch(String domain, String operation, Map<String, Object> params)
            throws Exception {
        OperationDescriptor op = registry.getOperation(domain, operation)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown operation: " + domain + "." + operation));

        Map<String, Object> effectiveParams = params != null ? params : Map.of();
        validateParams(op, effectiveParams);

        Object bean = context.getBean(op.resolverClass());

        Method method = op.method();
        Parameter[] methodParams = method.getParameters();
        Object[] args = new Object[methodParams.length];

        for (int i = 0; i < methodParams.length; i++) {
            Parameter param = methodParams[i];
            if (param.isAnnotationPresent(ContextParam.class)) {
                args[i] = resolveContextParam(param.getAnnotation(ContextParam.class).value());
                continue;
            }
            Object rawValue = effectiveParams.get(param.getName());
            if (rawValue != null) {
                if (param.isAnnotationPresent(McpParam.class)) {
                    args[i] = serialiseMcpParam(rawValue, param.getAnnotation(McpParam.class).format());
                } else {
                    args[i] = mapper.convertValue(rawValue, param.getType());
                }
            }
        }

        Object result = method.invoke(bean, args);
        if (op.type() == OperationDescriptor.OperationType.MUTATION && shouldWrapResult(method, result)) {
            return wrapMutationResult(result);
        }
        return result;
    }

    private Object resolveContextParam(String key) {
        return switch (key) {
            case "tenancyId" -> {
                var principal = context.getBean(
                        io.casehub.platform.api.identity.CurrentPrincipal.class);
                yield principal.tenancyId();
            }
            case "actorId" -> {
                var principal = context.getBean(
                        io.casehub.platform.api.identity.CurrentPrincipal.class);
                yield principal.actorId();
            }
            default -> throw new IllegalArgumentException("Unknown @ContextParam key: " + key);
        };
    }

    private void validateParams(OperationDescriptor op, Map<String, Object> params) {
        List<String> errors = new ArrayList<>();

        Set<String> knownNames = op.params().stream()
                .map(ParameterDescriptor::name)
                .collect(Collectors.toSet());

        for (String provided : params.keySet()) {
            if (!knownNames.contains(provided)) {
                errors.add("Unknown parameter '" + provided + "'");
            }
        }

        for (ParameterDescriptor expected : op.params()) {
            if (expected.required() && !params.containsKey(expected.name())) {
                errors.add("Required parameter '" + expected.name()
                        + "' (type: " + expected.typeName() + ") is missing");
            }
        }

        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Invalid params for ").append(op.name()).append(": ");
            sb.append(String.join("; ", errors));
            sb.append(". Expected: ");
            sb.append(op.params().stream()
                    .map(p -> p.name() + ": " + p.typeName()
                            + (p.required() ? " (required)" : " (optional)")
                            + (p.fields().isEmpty() ? "" : " " + p.fields()))
                    .collect(Collectors.joining(", ")));
            throw new IllegalArgumentException(sb.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private String serialiseMcpParam(Object value, McpParamFormat format) {
        return switch (format) {
            case MAP -> {
                Map<String, Object> map = (Map<String, Object>) value;
                yield map.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining("|"));
            }
            case LIST -> {
                List<String> list = (List<String>) value;
                yield String.join("|", list);
            }
        };
    }

    private boolean shouldWrapResult(Method method, Object result) {
        Class<?> returnType = method.getReturnType();
        return returnType == void.class || returnType == Void.class
                || returnType == String.class;
    }

    private Map<String, Object> wrapMutationResult(Object result) {
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put("success", true);
        wrapped.put("timestamp", Instant.now().toString());
        if (result instanceof String s && !s.isBlank()) {
            wrapped.put("id", s);
        }
        return wrapped;
    }
}
