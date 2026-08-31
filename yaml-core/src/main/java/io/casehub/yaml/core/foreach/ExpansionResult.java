package io.casehub.yaml.core.foreach;

import java.util.LinkedHashMap;
import java.util.Set;

public record ExpansionResult<E>(LinkedHashMap<String, E> elements, Set<String> excludedIds) {}
