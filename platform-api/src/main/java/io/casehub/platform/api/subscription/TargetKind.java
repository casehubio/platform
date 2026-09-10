package io.casehub.platform.api.subscription;

/**
 * Discriminates notification targets for pipeline behavior.
 *
 * <p>{@code USER} targets receive the full pipeline: preferences, suppression, digest, inbox
 * persistence. {@code NON_USER} targets (agents, systems) skip suppression and receive
 * fire-and-forget delivery.
 */
public enum TargetKind {
  USER,
  NON_USER
}
