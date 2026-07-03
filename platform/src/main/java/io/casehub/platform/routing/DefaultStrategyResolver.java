package io.casehub.platform.routing;

import io.casehub.platform.api.routing.NamedStrategy;
import io.casehub.platform.api.routing.StrategyResolver;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.*;
import java.util.stream.StreamSupport;

@Singleton
public class DefaultStrategyResolver implements StrategyResolver {

    private final Map<Class<?>, Map<String, NamedStrategy>> index;
    private final Map<Class<?>, NamedStrategy> defaults;

    @Inject
    public DefaultStrategyResolver(@Any Instance<NamedStrategy> strategies) {
        this(StreamSupport.stream(strategies.spliterator(), false).toList());
    }

    DefaultStrategyResolver(List<? extends NamedStrategy> strategies) {
        this.index = new HashMap<>();
        this.defaults = new HashMap<>();
        for (NamedStrategy strategy : strategies) {
            for (Class<?> iface : resolveStrategyTypes(strategy.getClass())) {
                var byId = index.computeIfAbsent(iface, k -> new LinkedHashMap<>());
                NamedStrategy existing = byId.put(strategy.id(), strategy);
                if (existing != null) {
                    throw new IllegalStateException(
                        "Duplicate strategy id '" + strategy.id() + "' for type "
                        + iface.getSimpleName() + ": " + existing.getClass().getName()
                        + " and " + strategy.getClass().getName());
                }
                defaults.putIfAbsent(iface, strategy);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends NamedStrategy> T resolve(Class<T> type, String id) {
        if (id == null) {
            return defaultStrategy(type);
        }
        var byId = index.get(type);
        if (byId == null || !byId.containsKey(id)) {
            var available = byId == null ? Set.of() : byId.keySet();
            throw new IllegalArgumentException(
                "No strategy with id '" + id + "' for type " + type.getSimpleName()
                + ". Available: " + available);
        }
        return (T) byId.get(id);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends NamedStrategy> Optional<T> find(Class<T> type, String id) {
        var byId = index.get(type);
        if (byId == null) return Optional.empty();
        return Optional.ofNullable((T) byId.get(id));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends NamedStrategy> T defaultStrategy(Class<T> type) {
        T def = (T) defaults.get(type);
        if (def == null) {
            throw new IllegalArgumentException(
                "No default strategy for type " + type.getSimpleName());
        }
        return def;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends NamedStrategy> List<T> available(Class<T> type) {
        var byId = index.get(type);
        if (byId == null) return List.of();
        return byId.values().stream().map(s -> (T) s).toList();
    }

    private static Set<Class<?>> resolveStrategyTypes(Class<?> clazz) {
        Set<Class<?>> result = new LinkedHashSet<>();
        for (Class<?> iface : clazz.getInterfaces()) {
            if (NamedStrategy.class.isAssignableFrom(iface) && iface != NamedStrategy.class) {
                result.add(iface);
            }
        }
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null && superclass != Object.class) {
            result.addAll(resolveStrategyTypes(superclass));
        }
        for (Class<?> iface : clazz.getInterfaces()) {
            result.addAll(resolveStrategyTypes(iface));
        }
        result.remove(NamedStrategy.class);
        return result;
    }
}
