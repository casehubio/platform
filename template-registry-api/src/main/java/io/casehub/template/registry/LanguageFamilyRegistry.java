package io.casehub.template.registry;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class LanguageFamilyRegistry {

    private final ConcurrentMap<String, String> languageToFamily = new ConcurrentHashMap<>();

    public Optional<String> familyFor(String languageTag) {
        Objects.requireNonNull(languageTag, "languageTag");
        return Optional.ofNullable(languageToFamily.get(languageTag));
    }

    public void register(String languageTag, String familyId) {
        Objects.requireNonNull(languageTag, "languageTag");
        Objects.requireNonNull(familyId, "familyId");
        languageToFamily.put(languageTag, familyId);
    }

    public Set<String> registeredLanguages() {
        return Set.copyOf(languageToFamily.keySet());
    }
}
