package io.casehub.platform.notification.rest;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.notification.settings.MuteRule;
import io.casehub.platform.api.notification.settings.MuteRuleInput;
import io.casehub.platform.api.notification.settings.NotificationSuppressionApi;
import io.casehub.platform.api.notification.settings.Snooze;
import io.casehub.platform.api.notification.settings.SnoozeInput;
import io.casehub.platform.api.notification.settings.SuppressionStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class NotificationSuppressionService implements NotificationSuppressionApi {

    private final SuppressionStore store;
    private final CurrentPrincipal principal;

    @Inject
    public NotificationSuppressionService(SuppressionStore store, CurrentPrincipal principal) {
        this.store = store;
        this.principal = principal;
    }

    @Override
    public MuteRule addMute(MuteRuleInput input) {
        var sanitizedInput = new MuteRuleInput(
            principal.actorId(), principal.tenancyId(),
            input.scope(), input.scopeId(), input.entityType(), input.expiresAt());
        return store.addMute(sanitizedInput);
    }

    @Override
    public List<MuteRule> listMutes() {
        return store.activeMutes(principal.actorId(), principal.tenancyId());
    }

    @Override
    public void removeMute(String id) {
        boolean removed = store.removeMute(id, principal.actorId(), principal.tenancyId());
        if (!removed) throw new NotFoundException("Mute rule not found");
    }

    @Override
    public Snooze activateSnooze(SnoozeInput input) {
        var sanitizedInput = new SnoozeInput(principal.actorId(), principal.tenancyId(), input.until());
        return store.activateSnooze(sanitizedInput);
    }

    @Override
    public Optional<Snooze> getSnooze() {
        return store.activeSnooze(principal.actorId(), principal.tenancyId());
    }

    @Override
    public void cancelSnooze() {
        boolean cancelled = store.cancelSnooze(principal.actorId(), principal.tenancyId());
        if (!cancelled) throw new NotFoundException("No active snooze");
    }
}
