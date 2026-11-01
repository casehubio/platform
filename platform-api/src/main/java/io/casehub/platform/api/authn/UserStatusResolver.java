package io.casehub.platform.api.authn;

import java.util.Optional;

public interface UserStatusResolver {
    Optional<String> resolveStatus(String actorId, String tenancyId);
}
