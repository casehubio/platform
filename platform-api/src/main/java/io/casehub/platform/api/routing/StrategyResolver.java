package io.casehub.platform.api.routing;

import java.util.List;
import java.util.Optional;

/**
 * Resolves {@link NamedStrategy} beans by type and id.
 *
 * <p>Resolution: look up by {@code (type, id)}. If {@code id} is null,
 * return the {@code @DefaultBean} instance. If no bean with that id exists,
 * throw {@link IllegalArgumentException}.
 */
public interface StrategyResolver {

    <T extends NamedStrategy> T resolve(Class<T> type, String id);

    <T extends NamedStrategy> Optional<T> find(Class<T> type, String id);

    <T extends NamedStrategy> T defaultStrategy(Class<T> type);

    <T extends NamedStrategy> List<T> available(Class<T> type);
}
