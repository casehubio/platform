package io.casehub.platform.subscription.engine;

import io.casehub.platform.api.datasource.ClassObjectType;
import io.casehub.platform.api.datasource.DataProcessor;
import io.casehub.platform.api.datasource.DataSource;
import io.casehub.platform.api.datasource.DataSourceDeregistered;
import io.casehub.platform.api.datasource.DataSourceDescriptor;
import io.casehub.platform.api.datasource.DataSourceRegistry;
import io.casehub.platform.api.datasource.FilterExpression;
import io.casehub.platform.api.datasource.SubscriptionHandle;
import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionEvaluator;
import io.casehub.platform.api.expression.ExpressionEngineRegistry;
import io.casehub.platform.api.expression.JQExpressionEvaluator;
import io.casehub.platform.api.expression.MvelExpressionEvaluator;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscribableEvent;
import io.casehub.platform.api.subscription.SubscriptionCreated;
import io.casehub.platform.api.subscription.SubscriptionDeleted;
import io.casehub.platform.api.subscription.SubscriptionMatched;
import io.casehub.platform.api.subscription.SubscriptionStore;
import io.casehub.platform.api.subscription.SubscriptionUpdated;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.casehub.platform.api.identity.TenancyConstants.PLATFORM_TENANT_ID;
import static io.casehub.platform.api.subscription.SubscriptionConstants.NOTIFICATION_DATASOURCE_PATH;

@ApplicationScoped
public class SubscriptionEngine {

    private static final Logger LOG = Logger.getLogger(SubscriptionEngine.class);

    @SuppressWarnings("unchecked")
    private static final Class<Map<String, Object>> MAP_TYPE =
            (Class<Map<String, Object>>) (Class<?>) Map.class;

    private final    DataSourceRegistry                            dataSourceRegistry;
    private final    SubscriptionStore                             subscriptionStore;
    private final    Event<SubscriptionMatched>                    matchEvent;
    private final    ExpressionEngineRegistry                      expressionRegistry;
    private final    ConcurrentHashMap<String, SubscriptionHandle> handles = new ConcurrentHashMap<>();
    private volatile DataSource<Object>                            notificationDataSource;

    @Inject
    public SubscriptionEngine(final DataSourceRegistry dataSourceRegistry,
                              final SubscriptionStore subscriptionStore,
                              final Event<SubscriptionMatched> matchEvent,
                              final ExpressionEngineRegistry expressionRegistry) {
        this.dataSourceRegistry = dataSourceRegistry;
        this.subscriptionStore  = subscriptionStore;
        this.matchEvent         = matchEvent;
        this.expressionRegistry = expressionRegistry;
    }

    @SuppressWarnings("unchecked")
    void onStartup(@Observes final StartupEvent event) {
        var descriptor = new DataSourceDescriptor(
                NOTIFICATION_DATASOURCE_PATH,
                PLATFORM_TENANT_ID,
                new ClassObjectType<>(Object.class),
                null, Set.of(), Map.of(), Map.of());
        notificationDataSource = (DataSource<Object>) dataSourceRegistry.register(descriptor);

        LOG.info("Notification DataSource registered at " + NOTIFICATION_DATASOURCE_PATH);

        subscriptionStore.findAllEnabled().forEach(this::wireSubscription);

        LOG.infof("Subscription engine started — %d subscriptions wired", handles.size());
    }

    private void wireSubscription(final Subscription subscription) {
        if (notificationDataSource == null) {
            LOG.warnf("Cannot wire subscription %s — notification DataSource not available", subscription.id());
            return;
        }
        var objectType = new EventTypeObjectType(subscription.eventType());
        var filter     = compileFilter(subscription);

        DataProcessor<Object> processor = pojo ->
                                                  matchEvent.fireAsync(new SubscriptionMatched(subscription, pojo));

        var handle = notificationDataSource.subscribe(objectType, filter, processor);

        handles.compute(subscription.id(), (id, existing) -> {
            if (existing != null) {
                existing.unsubscribe();
            }
            return handle;
        });
    }

    private SubscriptionHandle wireAndReturnHandle(final Subscription subscription) {
        if (notificationDataSource == null) {
            LOG.warnf("Cannot wire subscription %s — notification DataSource not available", subscription.id());
            return null;
        }
        var objectType = new EventTypeObjectType(subscription.eventType());
        var filter     = compileFilter(subscription);

        DataProcessor<Object> processor = pojo ->
                                                  matchEvent.fireAsync(new SubscriptionMatched(subscription, pojo));

        return notificationDataSource.subscribe(objectType, filter, processor);
    }

    private FilterExpression<Object> compileFilter(final Subscription subscription) {
        final String                    tenancyId = subscription.tenancyId();
        final String                    ownerId   = subscription.ownerId();
        final List<ExpressionEvaluator> filters   = subscription.filters();

        final Predicate<Object> tenantCheck = obj ->
                                                      obj instanceof SubscribableEvent event
                                                      && tenancyId.equals(event.tenancyId());

        if (filters.isEmpty()) {
            return new FilterExpression<>("none",
                                          "tenant=" + tenancyId + ":true", tenantCheck);
        }

        final Map<String, Object> variables = Map.of("$me", ownerId);

        final List<CompiledExpression<Map<String, Object>, Boolean>> compiled =
                filters.stream()
                       .map(f -> expressionRegistry.compile(
                               f.type(), extractExpression(f),
                               MAP_TYPE, Boolean.class, variables))
                       .toList();

        final String canonicalExpr = filters.stream()
                                            .map(f -> f.type() + ":" + extractExpression(f))
                                            .collect(Collectors.joining(" && "));
        final String expressionKey = "tenant=" + tenancyId + ":" + canonicalExpr;

        final Predicate<Object> predicate = obj -> {
            if (!tenantCheck.test(obj)) {return false;}
            if (!(obj instanceof SubscribableEvent)) {return false;}
            Map<String, Object> context = extractProperties(obj);
            return compiled.stream().allMatch(c -> c.eval(context));
        };

        return new FilterExpression<>(filters.get(0).type(), expressionKey, predicate);
    }

    private static String extractExpression(ExpressionEvaluator evaluator) {
        if (evaluator instanceof MvelExpressionEvaluator m) {return m.expression();}
        if (evaluator instanceof JQExpressionEvaluator j) {return j.expression();}
        throw new IllegalArgumentException("Unknown evaluator type: " + evaluator.type());
    }

    private static Map<String, Object> extractProperties(Object obj) {
        var result = new HashMap<String, Object>();
        for (var method : obj.getClass().getMethods()) {
            if (method.getParameterCount() != 0) {continue;}
            if (method.getDeclaringClass() == Object.class) {continue;}
            String name = method.getName();
            if (name.startsWith("get") && name.length() > 3) {
                String prop = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                try {result.put(prop, method.invoke(obj));} catch (Exception ignored) {}
            } else if (name.startsWith("is") && name.length() > 2
                       && (method.getReturnType() == boolean.class
                           || method.getReturnType() == Boolean.class)) {
                String prop = Character.toLowerCase(name.charAt(2)) + name.substring(3);
                try {result.put(prop, method.invoke(obj));} catch (Exception ignored) {}
            } else if (!name.equals("hashCode") && !name.equals("toString")
                       && !name.equals("getClass") && !name.equals("notify")
                       && !name.equals("notifyAll") && !name.equals("wait")) {
                try {result.put(name, method.invoke(obj));} catch (Exception ignored) {}
            }
        }
        return result;
    }

    void onDataSourceDeregistered(@ObservesAsync final DataSourceDeregistered event) {
        if (!NOTIFICATION_DATASOURCE_PATH.equals(event.descriptor().path())) {
            return;
        }
        handles.forEach((id, handle) -> handle.unsubscribe());
        handles.clear();
        notificationDataSource = null;
        LOG.info("Notification DataSource deregistered — all subscriptions unwired");
    }

    void onCreated(@ObservesAsync final SubscriptionCreated event) {
        if (event.subscription().enabled()) {
            wireSubscription(event.subscription());
            LOG.debugf("Wired new subscription %s", event.subscription().id());
        }
    }

    void onUpdated(@ObservesAsync final SubscriptionUpdated event) {
        handles.compute(event.subscription().id(), (id, existing) -> {
            if (existing != null) {
                existing.unsubscribe();
            }
            if (event.subscription().enabled()) {
                return wireAndReturnHandle(event.subscription());
            }
            return null;
        });
        LOG.debugf("Rewired subscription %s (enabled=%s)",
                   event.subscription().id(), event.subscription().enabled());
    }

    void onDeleted(@ObservesAsync final SubscriptionDeleted event) {
        handles.compute(event.subscription().id(), (id, existing) -> {
            if (existing != null) {
                existing.unsubscribe();
            }
            return null;
        });
        LOG.debugf("Unwired deleted subscription %s", event.subscription().id());
    }
}
