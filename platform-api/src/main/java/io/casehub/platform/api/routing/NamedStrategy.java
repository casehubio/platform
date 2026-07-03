package io.casehub.platform.api.routing;

/**
 * Marker interface for named, CDI-discoverable routing strategies.
 *
 * <p>Domain-specific strategy interfaces (e.g. AgentRoutingStrategy,
 * WorkerSelectionStrategy) extend this marker. Strategies are resolved
 * by {@link StrategyResolver} using the {@link #id()} as a stable key.
 */
public interface NamedStrategy {
    String id();
}
