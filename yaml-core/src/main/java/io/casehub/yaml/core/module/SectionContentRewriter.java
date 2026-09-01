package io.casehub.yaml.core.module;

import java.util.Set;

@FunctionalInterface
public interface SectionContentRewriter {
    Object rewrite(String sectionName, String entryKey, Object entryValue,
                   String alias, Set<String> moduleKeys);
}
