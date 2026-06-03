package io.casehub.platform.agent;

import io.smallrye.mutiny.Multi;

/**
 * Platform SPI for single-shot AI agent invocation.
 *
 * <p>Implementations should be {@code @ApplicationScoped} — agent infrastructure
 * is shared across requests.
 *
 * <p>Terminal state via stream termination only:
 * <ul>
 *   <li>Normal completion → {@code Multi} completes
 *   <li>Timeout → {@code Multi} fails with {@link AgentTimeoutException}
 *   <li>Subprocess error → {@code Multi} fails with {@link AgentProcessException}
 * </ul>
 * Subscriber cancellation triggers subprocess cleanup — no exception raised.
 */
public interface AgentProvider {

    /**
     * Invoke the agent and stream response events.
     *
     * <p>The returned {@code Multi} is cold — the agent session starts on subscription.
     *
     * @param config invocation configuration — systemPrompt and userPrompt are required
     * @return a cold {@code Multi} that streams {@link AgentEvent.TextDelta} items
     * @throws AgentTimeoutException via onFailure() if the wall-clock timeout is exceeded
     * @throws AgentProcessException via onFailure() if the subprocess errors
     * @throws AgentSessionLimitException via onFailure() if the concurrent session cap is reached
     */
    Multi<AgentEvent> invoke(AgentSessionConfig config);
}
