package io.casehub.yaml.core.playbook;

import java.util.List;
import java.util.Optional;

public interface PlaybookLevelRegistry {

    void register(PlaybookLevel level);

    Optional<PlaybookLevel> resolve(String name);

    Optional<PlaybookLevel> resolveByOrdinal(int ordinal);

    List<PlaybookLevel> all();

    default PlaybookLevel resolveOrThrow(String name) {
        return resolve(name).orElseThrow(() ->
                new IllegalArgumentException("Unknown playbook level: '" + name + "'"));
    }
}
