package io.casehub.platform.mcp.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpAccessEvent;
import io.casehub.platform.api.mcp.McpEnrich;
import io.casehub.platform.api.mcp.McpParam;
import io.casehub.platform.api.mcp.McpParamFormat;
import io.casehub.platform.api.mcp.McpTrack;
import io.casehub.platform.api.mcp.McpTrackType;
import io.casehub.platform.api.mcp.McpCursorMap;
import io.casehub.platform.api.mcp.McpCursorType;
import io.casehub.platform.api.mcp.McpDelegate;
import io.casehub.platform.api.mcp.McpUpsert;
import io.casehub.platform.mcp.DomainModelRegistry;
import io.casehub.platform.mcp.OperationDescriptor;
import io.casehub.platform.mcp.ParameterDescriptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SpringOperationDispatcher {

    private final DomainModelRegistry registry;
    private final ApplicationContext context;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher eventPublisher;

    public SpringOperationDispatcher(DomainModelRegistry registry,
                                      ApplicationContext context,
                                      ObjectMapper mapper,
                                      ApplicationEventPublisher eventPublisher) {
        this.registry = registry;
        this.context = context;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
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

        Object result;
        if (method.isAnnotationPresent(McpDelegate.class)) {
            result = dispatchDelegate(method, effectiveParams);
        } else if (method.isAnnotationPresent(McpUpsert.class)) {
            result = dispatchUpsert(method, bean, args, effectiveParams, op);
        } else {
            result = method.invoke(bean, args);
        }

        if (method.isAnnotationPresent(McpTrack.class)) {
            fireTrackEvent(method.getAnnotation(McpTrack.class), domain, operation, effectiveParams);
        }

        if (method.isAnnotationPresent(McpEnrich.class) || method.getAnnotationsByType(McpEnrich.class).length > 0) {
            result = applyEnrichments(method, result, effectiveParams, op);
        } else if (op.type() == OperationDescriptor.OperationType.MUTATION && shouldWrapResult(method, result)) {
            result = wrapMutationResult(result);
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

    private Object dispatchDelegate(Method declaringMethod, Map<String, Object> params) throws Exception {
        McpDelegate delegate = declaringMethod.getAnnotation(McpDelegate.class);
        Object spiBean = context.getBean(delegate.spi());
        Method targetMethod = findMethod(delegate.spi(), delegate.method());
        Object[] delegateArgs = matchArgsWithCursorMap(targetMethod, params, declaringMethod);
        return targetMethod.invoke(spiBean, delegateArgs);
    }

    private Object[] matchArgsWithCursorMap(Method target, Map<String, Object> params,
                                             Method declaringMethod) {
        Parameter[] declaringParams = declaringMethod.getParameters();
        Map<String, McpCursorType> cursorMaps = new LinkedHashMap<>();
        for (Parameter p : declaringParams) {
            if (p.isAnnotationPresent(McpCursorMap.class)) {
                cursorMaps.put(p.getName(), p.getAnnotation(McpCursorMap.class).value());
            }
        }

        Parameter[] targetParams = target.getParameters();
        Object[] args = new Object[targetParams.length];
        for (int i = 0; i < targetParams.length; i++) {
            Parameter param = targetParams[i];
            if (param.isAnnotationPresent(ContextParam.class)) {
                args[i] = resolveContextParam(param.getAnnotation(ContextParam.class).value());
            } else {
                Object rawValue = params.get(param.getName());
                if (rawValue != null) {
                    McpCursorType cursorType = cursorMaps.get(param.getName());
                    if (cursorType == McpCursorType.INSTANT && rawValue instanceof String s) {
                        args[i] = Instant.parse(s);
                    } else if (param.isAnnotationPresent(McpParam.class)) {
                        args[i] = serialiseMcpParam(rawValue, param.getAnnotation(McpParam.class).format());
                    } else {
                        args[i] = mapper.convertValue(rawValue, param.getType());
                    }
                }
            }
        }
        return args;
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

    private Object dispatchUpsert(Method addMethod, Object bean, Object[] args,
                                   Map<String, Object> params, OperationDescriptor op) throws Exception {
        McpUpsert upsert = addMethod.getAnnotation(McpUpsert.class);
        Method checkMethod = findMethod(bean.getClass(), upsert.existsCheck());
        Object[] checkArgs = matchArgs(checkMethod, params, bean);
        Object existing = checkMethod.invoke(bean, checkArgs);
        boolean created = existing == null;

        Object result;
        if (created) {
            result = addMethod.invoke(bean, args);
        } else {
            Method updateMethod = findMethod(bean.getClass(), upsert.update());
            Object[] updateArgs = matchArgs(updateMethod, params, bean);
            result = updateMethod.invoke(bean, updateArgs);
        }

        Map<String, Object> wrapped = wrapMutationResult(result);
        wrapped.put("created", created);
        return wrapped;
    }

    @SuppressWarnings("unchecked")
    private Object applyEnrichments(Method method, Object primaryResult,
                                     Map<String, Object> params, OperationDescriptor op) throws Exception {
        Map<String, Object> enriched;
        if (primaryResult instanceof Map) {
            enriched = new LinkedHashMap<>((Map<String, Object>) primaryResult);
        } else {
            enriched = mapper.convertValue(primaryResult, Map.class);
            if (enriched == null) enriched = new LinkedHashMap<>();
        }

        for (McpEnrich enrich : method.getAnnotationsByType(McpEnrich.class)) {
            Object spiBean = context.getBean(enrich.spi());
            Method enrichMethod = findMethod(enrich.spi(), enrich.method());
            Object[] enrichArgs = matchArgs(enrichMethod, params, spiBean);
            Object enrichResult = enrichMethod.invoke(spiBean, enrichArgs);
            enriched.put(enrich.as(), enrichResult);
        }

        return enriched;
    }

    private void fireTrackEvent(McpTrack track, String domain, String operation,
                                 Map<String, Object> params) {
        if (track.value() == McpTrackType.ACCESS) {
            eventPublisher.publishEvent(new McpAccessEvent(domain, operation, params, Instant.now()));
        }
    }

    private Method findMethod(Class<?> type, String name) {
        return Arrays.stream(type.getMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Method '" + name + "' not found on " + type.getName()));
    }

    private Object[] matchArgs(Method target, Map<String, Object> params, Object bean) {
        Parameter[] targetParams = target.getParameters();
        Object[] args = new Object[targetParams.length];
        for (int i = 0; i < targetParams.length; i++) {
            Parameter param = targetParams[i];
            if (param.isAnnotationPresent(ContextParam.class)) {
                args[i] = resolveContextParam(param.getAnnotation(ContextParam.class).value());
            } else {
                Object rawValue = params.get(param.getName());
                if (rawValue != null) {
                    if (param.isAnnotationPresent(McpParam.class)) {
                        args[i] = serialiseMcpParam(rawValue, param.getAnnotation(McpParam.class).format());
                    } else {
                        args[i] = mapper.convertValue(rawValue, param.getType());
                    }
                }
            }
        }
        return args;
    }
}
