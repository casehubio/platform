package io.casehub.platform.notification.dispatch;

import io.casehub.platform.api.delivery.DeliveryResult;
import io.casehub.platform.api.delivery.DeliverySourceType;
import io.casehub.platform.api.delivery.DestinationScope;
import io.casehub.platform.api.delivery.DigestBuffer;
import io.casehub.platform.api.delivery.DigestBufferKey;
import io.casehub.platform.api.notification.NotificationInput;
import io.casehub.platform.api.notification.ReplyHint;
import io.casehub.platform.api.notification.ReplyHintResolver;
import io.casehub.platform.api.notification.settings.NotificationPreferenceStore;
import io.casehub.platform.api.notification.settings.NotificationPreferences;
import io.casehub.platform.api.notification.settings.QuietHours;
import io.casehub.platform.api.notification.settings.QuietHoursAction;
import io.casehub.platform.api.notification.settings.SuppressionStore;
import io.casehub.platform.api.subscription.ResolvedTarget;
import io.casehub.platform.api.subscription.Subscription;
import io.casehub.platform.api.subscription.SubscriptionMatched;
import io.casehub.platform.api.subscription.TargetKind;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class NotificationDispatcher {

    private static final Logger LOG = Logger.getLogger(NotificationDispatcher.class);

    private final TargetResolver              targetResolver;
    private final SuppressionEvaluator        suppressionEvaluator;
    private final ChannelRouter               channelRouter;
    private final NotificationPreferenceStore preferenceStore;
    private final SuppressionStore            suppressionStore;
    private final DigestBuffer                digestBuffer;
    private final DeliveryTracker             deliveryTracker;
    private final ReplyHintResolver          replyHintResolver;

    public NotificationDispatcher(final TargetResolver targetResolver,
                                  final SuppressionEvaluator suppressionEvaluator,
                                  final ChannelRouter channelRouter,
                                  final NotificationPreferenceStore preferenceStore,
                                  final SuppressionStore suppressionStore,
                                  final DigestBuffer digestBuffer,
                                  final DeliveryTracker deliveryTracker,
                                  final ReplyHintResolver replyHintResolver) {
        this.targetResolver       = targetResolver;
        this.suppressionEvaluator = suppressionEvaluator;
        this.channelRouter        = channelRouter;
        this.preferenceStore      = preferenceStore;
        this.suppressionStore     = suppressionStore;
        this.digestBuffer         = digestBuffer;
        this.deliveryTracker      = deliveryTracker;
        this.replyHintResolver    = replyHintResolver;
    }

    public void onMatch(final SubscriptionMatched event) {
        final Subscription subscription = event.subscription();
        final Object       pojo         = event.pojo();
        final String       tenancyId    = subscription.tenancyId();

        final Set<ResolvedTarget> recipients = targetResolver.resolve(subscription, pojo);
        if (recipients.isEmpty()) {
            return;
        }

        final Map<String, DeliveryResult> perTenantResults = new HashMap<>();

        for (final ResolvedTarget target : recipients) {
            if (target.kind() == TargetKind.NON_USER) {
                dispatchToNonUser(target.targetId(), tenancyId, subscription, pojo);
            } else {
                dispatchToUser(target.targetId(), tenancyId, subscription, pojo, perTenantResults);
            }
        }
    }

    private void dispatchToNonUser(final String targetId,
                                   final String tenancyId,
                                   final Subscription subscription,
                                   final Object pojo) {
        final NotificationInput rawInput = TemplateResolver.resolve(
                subscription.template(), pojo, targetId, tenancyId);
        if (rawInput == null) {
            return;
        }
        final ReplyHint resolvedHint = replyHintResolver.resolve(rawInput);
        final NotificationInput notificationInput = resolvedHint != rawInput.replyHint()
                ? new NotificationInput(rawInput.userId(), rawInput.tenancyId(),
                        rawInput.title(), rawInput.body(), rawInput.category(),
                        rawInput.severity(), rawInput.actionUrl(), rawInput.source(), resolvedHint)
                : rawInput;

        final Set<ResolvedChannel> channels = channelRouter.routeNonUser();

        for (final ResolvedChannel channel : channels) {
            try {
                final DeliveryResult result = channel.deliverer().deliver(notificationInput);
                if (result.success()) {
                    deliveryTracker.recordSuccess(
                            channel.channelId(), notificationInput, result.sourceId(),
                            DeliverySourceType.NOTIFICATION);
                } else {
                    LOG.warnf("Non-user delivery failed for channel '%s', target '%s': %s",
                              channel.channelId(), targetId, result.failureReason());
                    deliveryTracker.recordFailure(
                            channel.channelId(), notificationInput, result.sourceId(),
                            DeliverySourceType.NOTIFICATION,
                            null, result.failureReason());
                }
            } catch (Exception e) {
                LOG.warnf(e, "Non-user delivery error for channel '%s', target '%s'",
                          channel.channelId(), targetId);
                deliveryTracker.recordFailure(
                        channel.channelId(), notificationInput, null,
                        DeliverySourceType.NOTIFICATION,
                        null, e.getMessage());
            }
        }
    }


    private void dispatchToUser(final String userId,
                                final String tenancyId,
                                final Subscription subscription,
                                final Object pojo,
                                final Map<String, DeliveryResult> perTenantResults) {
        final var preferences  = preferenceStore.get(userId, tenancyId);
        final var activeMutes  = suppressionStore.activeMutes(userId, tenancyId);
        final var activeSnooze = suppressionStore.activeSnooze(userId, tenancyId);

        final var    template   = subscription.template();
        final String entityType = template.entityType();
        final String entityId   = TemplateResolver.extractField(pojo, template.entityIdField());
        final String category   = template.category();

        final QuietHours quietHours = preferences
                                              .map(NotificationPreferences::quietHours)
                                              .orElse(null);

        final Instant now = Instant.now();
        final var suppressionResult = suppressionEvaluator.evaluate(
                activeMutes, activeSnooze, quietHours,
                entityType,
                entityId != null ? entityId : "",
                category, now);

        if (suppressionResult.isMuted()) {
            return;
        }

        final NotificationInput rawInput = TemplateResolver.resolve(
                template, pojo, userId, tenancyId);
        if (rawInput == null) {
            return;
        }
        final ReplyHint resolvedHint = replyHintResolver.resolve(rawInput);
        final NotificationInput notificationInput = resolvedHint != rawInput.replyHint()
                ? new NotificationInput(rawInput.userId(), rawInput.tenancyId(),
                        rawInput.title(), rawInput.body(), rawInput.category(),
                        rawInput.severity(), rawInput.actionUrl(), rawInput.source(), resolvedHint)
                : rawInput;

        final Map<String, io.casehub.platform.api.notification.settings.ChannelPreference> channelDefaults =
                preferences.map(NotificationPreferences::channelDefaults).orElse(Map.of());

        final QuietHoursAction quietHoursAction = preferences
                                                          .map(NotificationPreferences::quietHours)
                                                          .map(QuietHours::action)
                                                          .orElse(null);

        final Set<ResolvedChannel> channels = channelRouter.route(
                channelDefaults, suppressionResult, notificationInput.severity(), quietHoursAction);

        String capturedSourceId = null;

        for (final ResolvedChannel channel : channels) {
            if (channel.digested()) {
                digestBuffer.add(
                        new DigestBufferKey(userId, tenancyId, channel.channelId()),
                        notificationInput);
                continue;
            }
            if (channel.suppressed()) {
                continue;
            }

            if (channel.destinationScope() == DestinationScope.PER_TENANT) {
                final DeliveryResult previous = perTenantResults.get(channel.channelId());
                if (previous != null) {
                    LOG.debugf("Per-tenant dedup: channel '%s' already delivered for tenancy '%s', "
                               + "propagating %s to user '%s'",
                               channel.channelId(), tenancyId,
                               previous.success() ? "success" : "failure", userId);
                    if (previous.success()) {
                        deliveryTracker.recordSuccess(
                                channel.channelId(), notificationInput, capturedSourceId, DeliverySourceType.NOTIFICATION);
                    } else {
                        deliveryTracker.recordFailure(
                                channel.channelId(), notificationInput, capturedSourceId,
                                DeliverySourceType.NOTIFICATION,
                                null, previous.failureReason());
                    }
                    continue;
                }
            }

            try {
                final DeliveryResult result = channel.deliverer().deliver(notificationInput);
                if (result.sourceId() != null) {
                    capturedSourceId = result.sourceId();
                }
                if (channel.destinationScope() == DestinationScope.PER_TENANT) {
                    perTenantResults.put(channel.channelId(), result);
                }
                if (result.success()) {
                    deliveryTracker.recordSuccess(
                            channel.channelId(), notificationInput, capturedSourceId, DeliverySourceType.NOTIFICATION);
                } else {
                    LOG.warnf("Delivery failed for channel '%s', user '%s': %s",
                              channel.channelId(), userId, result.failureReason());
                    deliveryTracker.recordFailure(
                            channel.channelId(), notificationInput, capturedSourceId,
                            DeliverySourceType.NOTIFICATION,
                            channel.guaranteedMinSeverity(), result.failureReason());
                }
            } catch (Exception e) {
                final var failedResult = new DeliveryResult(false, e.getMessage());
                if (channel.destinationScope() == DestinationScope.PER_TENANT) {
                    perTenantResults.put(channel.channelId(), failedResult);
                }
                LOG.warnf(e, "Delivery error for channel '%s', user '%s'",
                          channel.channelId(), userId);
                deliveryTracker.recordFailure(
                        channel.channelId(), notificationInput, capturedSourceId,
                        DeliverySourceType.NOTIFICATION,
                        channel.guaranteedMinSeverity(), e.getMessage());
            }
        }
    }
}
