package io.casehub.yaml.core.playbook;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MapPlaybookLevelRegistry implements PlaybookLevelRegistry {

    private final ConcurrentHashMap<String, PlaybookLevel> byName = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, PlaybookLevel> byOrdinal = new ConcurrentHashMap<>();

    public MapPlaybookLevelRegistry() {
        Set<String> cumulative = new LinkedHashSet<>();

        cumulative.addAll(Set.of(
                PlaybookCapabilities.STEPS,
                PlaybookCapabilities.VARIABLE_RESOLUTION,
                PlaybookCapabilities.CONTROL_FLOW));
        register(new PlaybookLevel("foundational", 1, Set.copyOf(cumulative)));

        cumulative.add(PlaybookCapabilities.DECORATORS);
        register(new PlaybookLevel("basic-patterns", 2, Set.copyOf(cumulative)));

        cumulative.addAll(Set.of(
                PlaybookCapabilities.CONCURRENCY,
                PlaybookCapabilities.MODULES));
        register(new PlaybookLevel("intermediate", 3, Set.copyOf(cumulative)));

        cumulative.addAll(Set.of(
                PlaybookCapabilities.ORCHESTRATION,
                PlaybookCapabilities.CORRELATION,
                PlaybookCapabilities.STATE_MACHINE));
        register(new PlaybookLevel("advanced", 4, Set.copyOf(cumulative)));

        cumulative.addAll(Set.of(
                PlaybookCapabilities.MCP_INVOKE,
                PlaybookCapabilities.GRAPHQL_INVOKE,
                PlaybookCapabilities.CODEGEN));
        register(new PlaybookLevel("expert", 5, Set.copyOf(cumulative)));
    }

    @Override
    public void register(PlaybookLevel level) {
        byName.put(level.name(), level);
        byOrdinal.put(level.ordinal(), level);
    }

    @Override
    public Optional<PlaybookLevel> resolve(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    @Override
    public Optional<PlaybookLevel> resolveByOrdinal(int ordinal) {
        return Optional.ofNullable(byOrdinal.get(ordinal));
    }

    @Override
    public List<PlaybookLevel> all() {
        return byName.values().stream()
                .sorted(Comparator.comparingInt(PlaybookLevel::ordinal))
                .toList();
    }
}
