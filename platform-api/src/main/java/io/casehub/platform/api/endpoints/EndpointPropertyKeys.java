package io.casehub.platform.api.endpoints;

/**
 * Reserved cross-protocol property keys for {@link EndpointDescriptor#properties()}.
 *
 * <p>Platform-reserved keys use <b>kebab-case</b>. Consumer modules should follow the
 * same convention for protocol-specific extensions to avoid collisions.
 *
 * <p>These keys are conventions, not enforced constraints. Their purpose is to allow
 * workers and callers from different modules to read endpoint properties registered by
 * another module without independent key negotiation. The <em>values</em> are
 * endpoint-specific and defined by each registrar.
 *
 * <p>Only keys whose values must cross module boundaries are reserved here.
 * Deployment-local properties (bootstrap servers, TLS config, Camel route IDs) remain
 * as module-defined keys and are not reserved.
 */
public final class EndpointPropertyKeys {

    /**
     * The base URL or address of the endpoint.
     *
     * <p>Applies to:
     * <ul>
     *   <li>{@link EndpointProtocol#HTTP} — service root URL</li>
     *   <li>{@link EndpointProtocol#GRPC} — {@code host:port} or {@code grpc://...} URI</li>
     *   <li>{@link EndpointProtocol#MCP} — MCP server base URL</li>
     *   <li>{@link EndpointProtocol#CAMEL} — Camel endpoint URI (any valid Camel endpoint
     *       expression, not necessarily an HTTP URL)</li>
     *   <li>{@link EndpointProtocol#QHORUS} — Qhorus REST base URL</li>
     * </ul>
     *
     * <p>KAFKA and AMQP are excluded — broker connection for both is Quarkus-managed
     * via standard config (e.g. {@code kafka.bootstrap.servers},
     * {@code amqp-host}/{@code amqp-port}).
     */
    public static final String URL = "url";

    /**
     * The Kafka topic or AMQP queue/topic name.
     *
     * <p>Applies to: {@link EndpointProtocol#KAFKA} and {@link EndpointProtocol#AMQP}.
     *
     * <p>A producer registering {@link EndpointCapability#SEND} and a consumer registering
     * {@link EndpointCapability#RECEIVE} for the same logical endpoint must use this key
     * to interoperate — both read the topic name from the same property key.
     */
    public static final String TOPIC = "topic";

    /**
     * Logical CloudEvent {@code type} for a stream source — reverse-DNS, e.g.
     * {@code io.casehub.iot.temperature}. Stream modules that build CloudEvents from
     * raw payloads read this from {@link EndpointDescriptor#properties()} to set the
     * CloudEvent {@code type} field.
     *
     * <p>Applies to: {@link EndpointProtocol#KAFKA}, {@link EndpointProtocol#AMQP},
     * {@link EndpointProtocol#HTTP} ({@code streams-poll} only),
     * {@link EndpointProtocol#CAMEL}.
     *
     * <p><b>Not used by {@code streams-webhook}.</b> Webhook requests are already
     * structured CloudEvents; their {@code type} field is preserved from the incoming
     * event and not overridden by the descriptor.
     */
    public static final String STREAM_EVENT_TYPE = "stream-event-type";

    /**
     * Content type of the raw stream payload declared by the endpoint operator,
     * e.g. {@code "application/json"}, {@code "application/avro"},
     * {@code "application/octet-stream"}.
     *
     * <p>When present on an {@link EndpointDescriptor}, stream modules set
     * {@link io.cloudevents.core.builder.CloudEventBuilder#withDataContentType(String)}
     * to this value. When absent, {@code datacontenttype} is omitted from the CloudEvent —
     * the format is undeclared and consumers must determine it themselves.
     *
     * <p>GE rule 7 states "set {@code application/json} when data is ObjectMapper-serialised."
     * Stream processors do not serialise — they receive pre-serialised opaque bytes from
     * external systems. The format is operator-declared, not derivable from serialisation.
     * This key is the mechanism by which operators make that declaration explicit.
     *
     * <p><b>Scope:</b> applies to {@link EndpointProtocol#KAFKA},
     * {@link EndpointProtocol#AMQP}, {@link EndpointProtocol#HTTP}
     * ({@code streams-poll} only), and {@link EndpointProtocol#CAMEL}.
     *
     * <p><b>Not used by {@code streams-webhook}.</b> Webhook requests are structured
     * CloudEvents; their {@code datacontenttype} is preserved from the incoming event.
     *
     * <p><b>Note on naming:</b> this is an {@link EndpointDescriptor#properties()} key,
     * not a CloudEvent extension attribute name. CloudEvent extension names must be
     * lowercase letters and digits only (no hyphens). This key is scoped to
     * {@code EndpointDescriptor.properties()} exclusively and is never used as a
     * CloudEvent extension attribute.
     */
    public static final String STREAM_DATA_CONTENT_TYPE = "stream-data-content-type";

    private EndpointPropertyKeys() {}
}
