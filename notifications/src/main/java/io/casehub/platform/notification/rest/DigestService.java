package io.casehub.platform.notification.rest;

import io.casehub.platform.api.delivery.DigestApi;
import io.casehub.platform.api.delivery.DigestBuffer;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class DigestService implements DigestApi {

    private final DigestBuffer digestBuffer;
    private final CurrentPrincipal principal;

    @Inject
    public DigestService(DigestBuffer digestBuffer, CurrentPrincipal principal) {
        this.digestBuffer = digestBuffer;
        this.principal = principal;
    }

    @Override
    public Map<String, Integer> status() {
        String userId = principal.actorId();
        String tenancyId = principal.tenancyId();

        Map<String, Integer> result = new LinkedHashMap<>();
        for (var key : digestBuffer.pendingKeysForUser(userId, tenancyId)) {
            int count = digestBuffer.pendingCount(key);
            if (count > 0) {
                result.put(key.channelId(), count);
            }
        }
        return result;
    }
}
