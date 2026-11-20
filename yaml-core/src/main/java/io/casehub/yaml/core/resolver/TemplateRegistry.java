package io.casehub.yaml.core.resolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class TemplateRegistry {

    private final List<Entry> entries;

    private TemplateRegistry(List<Entry> entries) {
        this.entries = entries;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Match> match(String input) {
        Objects.requireNonNull(input, "input");
        for (Entry entry : entries) {
            Optional<Map<String, String>> result = entry.extractor.extract(input);
            if (result.isPresent()) {
                return Optional.of(new Match(entry.name, result.get()));
            }
        }
        return Optional.empty();
    }

    public List<String> templateNames() {
        List<String> names = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            names.add(entry.name);
        }
        return Collections.unmodifiableList(names);
    }

    public record Match(String name, Map<String, String> values) {
        public Match {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(values, "values");
            values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }
    }

    private record Entry(String name, TemplateExtractor extractor) {}

    public static final class Builder {
        private final LinkedHashMap<String, TemplateExtractor> templates = new LinkedHashMap<>();

        private Builder() {}

        public Builder add(String name, String template) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(template, "template");
            if (templates.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate template name: " + name);
            }
            templates.put(name, TemplateExtractor.compile(template));
            return this;
        }

        public TemplateRegistry build() {
            List<Entry> entries = new ArrayList<>(templates.size());
            templates.forEach((name, extractor) -> entries.add(new Entry(name, extractor)));
            return new TemplateRegistry(Collections.unmodifiableList(entries));
        }
    }
}
