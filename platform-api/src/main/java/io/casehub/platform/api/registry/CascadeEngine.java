package io.casehub.platform.api.registry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

public class CascadeEngine {

    private static final Logger LOG = Logger.getLogger(CascadeEngine.class.getName());

    @FunctionalInterface
    public interface EntryResolver {
        Optional<RegistryEntry> resolve(String id);
    }

    @FunctionalInterface
    public interface RelationshipQuery {
        List<Relationship> outboundFrom(String sourceId);
    }

    public interface EntryMutator {
        void deregister(String id);
        void markOrphaned(String id, Instant now);
    }

    public List<CascadeEffect> applyCascade(
            String sourceId,
            List<CascadeRule> rules,
            Function<CascadeRule, CascadeAction> actionSelector,
            EntryResolver resolver,
            RelationshipQuery relQuery,
            EntryMutator mutator,
            Consumer<RegistryEvent> eventSink,
            Set<String> visited,
            int maxDepth
    ) {
        visited.add(sourceId);
        if (visited.size() > maxDepth) {
            LOG.warning("Cascade depth limit reached at " + maxDepth + " for source " + sourceId);
            return List.of();
        }

        var ruleMap = new HashMap<String, CascadeRule>();
        for (var rule : rules) {
            ruleMap.put(rule.relationshipType(), rule);
        }

        var outbound = relQuery.outboundFrom(sourceId);
        var effects = new ArrayList<CascadeEffect>();

        for (var rel : outbound) {
            var rule = ruleMap.get(rel.type());
            if (rule == null) continue;

            var action = actionSelector.apply(rule);
            if (action == CascadeAction.NONE) continue;

            var targetId = rel.targetId();
            if (visited.contains(targetId)) continue;

            try {
                var targetOpt = resolver.resolve(targetId);
                if (targetOpt.isEmpty()) continue;
                var target = targetOpt.get();

                switch (action) {
                    case DEREGISTER -> {
                        mutator.deregister(targetId);
                        eventSink.accept(RegistryEvent.deregistered(target));
                        var childEffects = applyCascade(targetId, rules, actionSelector,
                                resolver, relQuery, mutator, eventSink, visited, maxDepth);
                        effects.add(new CascadeEffect(targetId, action, rel, null, null));
                        effects.addAll(childEffects);
                    }
                    case MARK_ORPHANED -> {
                        var now = Instant.now();
                        mutator.markOrphaned(targetId, now);
                        var orphaned = resolver.resolve(targetId).orElse(target.withOrphaned(now));
                        eventSink.accept(RegistryEvent.orphaned(orphaned));
                        effects.add(new CascadeEffect(targetId, action, rel, orphaned, null));
                    }
                    case NOTIFY -> {
                        eventSink.accept(RegistryEvent.cascadeNotified(target, rel));
                        effects.add(new CascadeEffect(targetId, action, rel, target, null));
                    }
                    default -> {}
                }
            } catch (Exception e) {
                effects.add(new CascadeEffect(targetId, action, rel, null, e.getMessage()));
            }
        }

        return effects;
    }
}
