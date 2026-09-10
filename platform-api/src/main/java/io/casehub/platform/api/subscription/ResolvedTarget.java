package io.casehub.platform.api.subscription;

import java.util.Objects;

/**
 * A resolved notification target with its kind. Produced by {@link TargetResolver} after expanding
 * groups and resolving event fields.
 *
 * @param targetId the resolved identifier (userId for USER, agentId for NON_USER)
 * @param kind     pipeline behavior discriminator
 */
public record ResolvedTarget(String targetId, TargetKind kind) {
  public ResolvedTarget {
    Objects.requireNonNull(targetId, "targetId");
    Objects.requireNonNull(kind, "kind");
  }

  public static ResolvedTarget user(String userId) {
    return new ResolvedTarget(userId, TargetKind.USER);
  }

  public static ResolvedTarget nonUser(String targetId) {
    return new ResolvedTarget(targetId, TargetKind.NON_USER);
  }
}
