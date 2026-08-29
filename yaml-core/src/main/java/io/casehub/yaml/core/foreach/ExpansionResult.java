package io.casehub.yaml.core.foreach;

import java.util.List;
import java.util.Set;

public record ExpansionResult<E>(List<E> elements, Set<String> excludedIds) {}
