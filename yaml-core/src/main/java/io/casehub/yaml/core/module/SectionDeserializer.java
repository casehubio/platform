package io.casehub.yaml.core.module;

import java.util.Map;

@FunctionalInterface
public interface SectionDeserializer {
    Object deserialize(String sectionName, String entryKey, Map<String, Object> rawEntry);
}
