package io.casehub.plugin.catalog.core;

import io.casehub.plugin.catalog.ConflictException;
import io.casehub.plugin.catalog.PluginPackage;

import java.util.Collection;

public class ConflictDetector {

    public void check(Collection<PluginPackage> installed,
                      PluginPackage incoming) throws ConflictException {
        for (PluginPackage existing : installed) {
            if (existing.coordinates().name().equals(incoming.coordinates().name())) {
                continue;
            }
            for (String capability : incoming.manifest().provides()) {
                if (existing.manifest().provides().contains(capability)) {
                    throw new ConflictException(
                            capability,
                            existing.coordinates(),
                            incoming.coordinates());
                }
            }
        }
    }
}
