package io.casehub.platform.registry.memory;

import io.casehub.platform.api.registry.CascadeEngine;
import io.casehub.platform.api.registry.CascadeRule;
import io.casehub.platform.api.registry.HealthStatus;
import io.casehub.platform.api.registry.RegistryEntry;
import io.casehub.platform.api.registry.RegistryEvent;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class HeartbeatScheduler {

    private static final int MAX_CASCADE_DEPTH = 10;

    private final InMemoryRegistryService registry;
    private final Consumer<RegistryEvent> eventSink;
    private final CascadeEngine           cascadeEngine = new CascadeEngine();

    public HeartbeatScheduler(InMemoryRegistryService registry) {
        this(registry, e -> {});
    }

    public HeartbeatScheduler(InMemoryRegistryService registry, Consumer<RegistryEvent> eventSink) {
        this.registry = registry;
        this.eventSink = eventSink;
    }

    public void checkHeartbeats() {
        var now = Instant.now();
        var rules = registry.cascadeRules();

        for (var entry : registry.allEntries()) {
            if (entry.health() == HealthStatus.DOWN) {
                if (!rules.isEmpty()) {
                    applyCascadeIfGraceElapsed(entry, rules, now);
                }
                continue;
            }
            var lastSeen = entry.lastHeartbeat() != null ? entry.lastHeartbeat() : entry.registeredAt();
            if (now.isAfter(lastSeen.plus(entry.ttl()))) {
                var downEntry = entry.withHealth(HealthStatus.DOWN);
                registry.updateEntryForExpiry(downEntry);
                eventSink.accept(RegistryEvent.heartbeatExpired(downEntry));
                if (!rules.isEmpty()) {
                    applyCascadeIfGraceElapsed(downEntry, rules, now);
                }
            }
        }
    }

    private void applyCascadeIfGraceElapsed(RegistryEntry entry, List<CascadeRule> rules, Instant now) {
        var downSince = (entry.lastHeartbeat() != null ? entry.lastHeartbeat() : entry.registeredAt())
                .plus(entry.ttl());
        for (var rule : rules) {
            if (rule.cascadeGracePeriod() != null && now.isBefore(downSince.plus(rule.cascadeGracePeriod()))) {
                continue;
            }
            var outbound = registry.outboundRelationships(entry.id());
            var matchingRels = outbound.stream()
                    .filter(r -> r.type().equals(rule.relationshipType())).toList();
            if (matchingRels.isEmpty()) continue;

            cascadeEngine.applyCascade(entry.id(), List.of(rule),
                    CascadeRule::effectiveOnSourceExpired,
                    k -> registry.resolve(k),
                    registry::outboundRelationships,
                    new CascadeEngine.EntryMutator() {
                        @Override public void deregister(String id) {
                            registry.deregister(id);
                        }
                        @Override public void markOrphaned(String id, Instant orphanedAt) {
                            registry.resolve(id)
                                    .filter(e -> !e.isOrphaned())
                                    .ifPresent(e -> registry.updateEntryForExpiry(e.withOrphaned(orphanedAt)));
                        }
                    },
                    eventSink, new HashSet<>(Set.of(entry.id())), MAX_CASCADE_DEPTH);
        }
    }
}
