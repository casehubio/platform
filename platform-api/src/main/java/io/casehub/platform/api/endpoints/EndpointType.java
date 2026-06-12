package io.casehub.platform.api.endpoints;

/**
 * Classifies what an endpoint <em>is</em> — independent of who can see it.
 *
 * <p>{@code EndpointType} and {@code tenancyId} are orthogonal. The type describes the
 * nature of the endpoint; {@code tenancyId} on {@link EndpointDescriptor} controls
 * visibility. A {@link #SERVICE} endpoint may be platform-global
 * ({@link io.casehub.platform.api.identity.TenancyConstants#PLATFORM_TENANT_ID}) or
 * tenant-specific — e.g. a tenant with a private Qhorus deployment registers
 * {@code type=SERVICE} with their own {@code tenancyId}.
 *
 * <p>{@code CASE} and {@code HUMAN} are intentionally excluded. An endpoint registry
 * describes how to connect to a system via a protocol. A case instance is an internal
 * domain concept; a human actor is reachable via connectors (Slack, email) or work
 * SPIs — not a protocol endpoint.
 */
public enum EndpointType {

    /** An external third-party system. Example: {@code external/salesforce/prod}. */
    SYSTEM,

    /** An internal platform service. Example: {@code casehubio/qhorus/api}. */
    SERVICE,

    /** A data processing worker. Example: {@code workers/camel/lead-enrichment}. */
    WORKER,

    /** An AI agent. Example: {@code agents/claude:analyst@v1}. */
    AGENT
}
