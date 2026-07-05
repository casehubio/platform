package io.casehub.platform.notification.settings;

import io.casehub.platform.api.notification.UUIDv7;
import io.casehub.platform.api.notification.settings.MuteRule;
import io.casehub.platform.api.notification.settings.MuteRuleInput;
import io.casehub.platform.api.notification.settings.Snooze;
import io.casehub.platform.api.notification.settings.SnoozeInput;
import io.casehub.platform.api.notification.settings.SuppressionStore;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * No-op {@link SuppressionStore} — active when no backend module is on the classpath.
 *
 * <p>{@link #addMute(MuteRuleInput)} returns a structurally valid {@link MuteRule}
 * (UUID v7 id, current timestamp) so callers that use the return value get valid data.
 * {@link #activeMutes(String, String)} returns empty list. {@link #removeMute(String, String, String)}
 * returns false.
 *
 * <p>{@link #activateSnooze(SnoozeInput)} returns a structurally valid {@link Snooze}
 * (current timestamp). {@link #activeSnooze(String, String)} returns empty.
 * {@link #cancelSnooze(String, String)} returns false.
 *
 * <p>Does NOT fire CDI events per protocol — no-op implementations must not fire events.
 *
 * <p>Displaced by any {@code @Alternative} or bare {@code @ApplicationScoped}
 * {@link SuppressionStore} implementation on the classpath, per the
 * {@code @DefaultBean} CDI displacement contract.
 */
@DefaultBean
@ApplicationScoped
public class NoOpSuppressionStore implements SuppressionStore {

    @Override
    public MuteRule addMute(final MuteRuleInput input) {
        return new MuteRule(
                UUIDv7.generate(),
                input.userId(),
                input.tenancyId(),
                input.scope(),
                input.scopeId(),
                input.entityType(),
                Instant.now(),
                input.expiresAt()
        );
    }

    @Override
    public List<MuteRule> activeMutes(final String userId, final String tenancyId) {
        return List.of();
    }

    @Override
    public boolean removeMute(final String muteId, final String userId, final String tenancyId) {
        return false;
    }

    @Override
    public Snooze activateSnooze(final SnoozeInput input) {
        return new Snooze(
                input.userId(),
                input.tenancyId(),
                input.until(),
                Instant.now()
        );
    }

    @Override
    public Optional<Snooze> activeSnooze(final String userId, final String tenancyId) {
        return Optional.empty();
    }

    @Override
    public boolean cancelSnooze(final String userId, final String tenancyId) {
        return false;
    }
}
