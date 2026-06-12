package io.casehub.platform.api.endpoints;

/**
 * Declares what an endpoint supports — its nominal contract, not the calling pattern.
 *
 * <p>Whether a Camel route is one-way or request-reply depends on route configuration;
 * whether an HTTP POST is asynchronous (202 Accepted) or synchronous (200 with body)
 * depends on the service. The descriptor declares intent — the caller must honour it.
 *
 * <p>An endpoint may declare multiple capabilities. Examples:
 * <ul>
 *   <li>Kafka topic: {@code SEND + RECEIVE}</li>
 *   <li>MCP server: {@code QUERY + DISPATCH}</li>
 *   <li>HTTP webhook receiver: {@code RECEIVE}</li>
 * </ul>
 *
 * <p>Single-owner rule: a single descriptor per {@code (path, tenancyId)} declares the
 * complete capability set. Two registrars writing the same key with different capability
 * subsets will silently overwrite each other — there is no merge semantics.
 */
public enum EndpointCapability {

    /** Caller can push data to this endpoint (Kafka produce, HTTP POST) — fire-and-forget. */
    SEND,

    /** Endpoint can push data to the caller (webhook delivery, Kafka consume, SSE). */
    RECEIVE,

    /** Caller can issue a read request and receive a synchronous response (HTTP GET, MCP query). */
    QUERY,

    /** Caller can invoke an operation and receive a response or acknowledgement (MCP tool call, Camel request-reply, agent invocation). */
    DISPATCH
}
