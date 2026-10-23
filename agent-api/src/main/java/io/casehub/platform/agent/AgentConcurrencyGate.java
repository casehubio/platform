package io.casehub.platform.agent;

/**
 * Marker SPI for centralized concurrency control. When a gate is active,
 * individual backends should disable their own semaphores to avoid
 * double-gating on the same call path.
 *
 * <p>The default (no gate module) returns {@code false}. Agent-gate
 * produces an active implementation when its concurrency limit is configured.
 */
public interface AgentConcurrencyGate {

    boolean isActive();
}
