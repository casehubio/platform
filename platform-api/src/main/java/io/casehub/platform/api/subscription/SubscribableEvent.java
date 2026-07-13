package io.casehub.platform.api.subscription;

/**
 * Compile-time contract for POJOs that flow through the subscription system.
 *
 * <p>Any event POJO inserted into the notification DataSource must implement
 * this interface. The subscription engine uses {@link #type()} for event type
 * discrimination in the alpha network and {@link #tenancyId()} for tenant
 * isolation.
 *
 * <p>POJOs that do not implement this interface are silently rejected by the
 * subscription engine — they match no subscriptions and no tenant check passes.
 */
public interface SubscribableEvent {

    /**
     * Returns the event type string used for subscription matching.
     *
     * <p>Convention: reverse-DNS with dot-separated segments, e.g.
     * {@code "io.casehub.work.workitem.completed"}.
     */
    String type();

    /**
     * Returns the tenancy ID for tenant isolation.
     */
    String tenancyId();
}
