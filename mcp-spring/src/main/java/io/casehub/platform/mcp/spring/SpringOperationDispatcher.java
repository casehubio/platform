package io.casehub.platform.mcp.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.mcp.ContextParam;
import io.casehub.platform.api.mcp.McpEnrich;
import io.casehub.platform.api.mcp.McpParam;
import io.casehub.platform.api.mcp.McpParamFormat;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.ReadTracker;
import io.casehub.platform.api.mcp.McpCursorMap;
import io.casehub.platform.api.mcp.McpCursorType;
import io.casehub.platform.api.mcp.McpDelegate;
import io.casehub.platform.api.mcp.McpAutoRoute;
import io.casehub.platform.api.mcp.McpTransform;
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
    private final ReadTracker readTracker;

    public SpringOperationDispatcher(DomainModelRegistry registry,
                                      ApplicationContext context,
                                      ObjectMapper mapper,
                                      ReadTracker readTracker) {
        this.registry = registry;
        this.context = context;
        this.mapper = mapper;
        this.readTracker = readTracker;
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
        } else if (method.isAnnotationPresent(McpAutoRoute.class)) {
            result = dispatchAutoRoute(method, bean, args, effectiveParams, op);
        } else {
            result = method.invoke(bean, args);
        }

        if (method.isAnnotationPresent(PlatformQuery.class) && method.getAnnotation(PlatformQuery.class).track()) {
            recordAccess(domain, effectiveParams);
        }

        if (method.isAnnotationPresent(McpTransform.class)) {
            result = applyTransform(method, result, effectiveParams);
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

    @SuppressWarnings("unchecked")
    private Object applyTransform(Method method, Object result, Map<String, Object> params) throws Exception {
        McpTransform transform = method.getAnnotation(McpTransform.class);
        Object spiBean = context.getBean(transform.spi());
        Method transformMethod = findMethod(transform.spi(), transform.method());

        if (transform.perElement() && result instanceof List<?> list) {
            List<Object> transformed = new ArrayList<>(list.size());
            for (Object element : list) {
                transformed.add(invokeTransform(transformMethod, spiBean, element, params));
            }
            return transformed;
        }

        return invokeTransform(transformMethod, spiBean, result, params);
    }

    private Object invokeTransform(Method transformMethod, Object spiBean,
                                    Object result, Map<String, Object> params) throws Exception {
        Parameter[] tParams = transformMethod.getParameters();
        Object[] args = new Object[tParams.length];
        boolean resultPlaced = false;
        for (int i = 0; i < tParams.length; i++) {
            Object rawValue = params.get(tParams[i].getName());
            if (rawValue != null) {
                args[i] = mapper.convertValue(rawValue, tParams[i].getType());
            } else if (!resultPlaced && tParams[i].getType().isAssignableFrom(result.getClass())) {
                args[i] = result;
                resultPlaced = true;
            }
        }
        if (!resultPlaced) {
            args[0] = result;
        }
        return transformMethod.invoke(spiBean, args);
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

    private Object dispatchAutoRoute(Method addMethod, Object bean, Object[] args,
                                   Map<String, Object> params, OperationDescriptor op) throws Exception {
        McpAutoRoute route = addMethod.getAnnotation(McpAutoRoute.class);
        Method checkMethod = findMethod(bean.getClass(), route.check());
        Object[] checkArgs = matchArgs(checkMethod, params, bean);
        Object existing = checkMethod.invoke(bean, checkArgs);
        boolean created = existing == null;

        Object result;
        if (created) {
            result = addMethod.invoke(bean, args);
        } else {
            Method updateMethod = findMethod(bean.getClass(), route.alternate());
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

    private void recordAccess(String domain, Map<String, Object> params) {
        try {
            var principal = context.getBean(io.casehub.platform.api.identity.CurrentPrincipal.class);
            String agentId = principal.actorId();
            String resourceKey = extractResourceKey(params);
            if (resourceKey != null) {
                readTracker.recordRead(agentId, domain, resourceKey);
            }
        } catch (Exception ignored) {
        }
    }

    private String extractResourceKey(Map<String, Object> params) {
        for (String candidate : List.of("entryId", "id", "path", "caseId", "resourceId")) {
            Object value = params.get(candidate);
            if (value != null) return value.toString();
        }
        return null;
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
