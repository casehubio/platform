package io.casehub.plugin.catalog.core;

import io.casehub.plugin.catalog.PluginPackage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DependencyResolver {

    public List<PluginPackage> resolve(Collection<PluginPackage> packages) {
        if (packages.isEmpty()) return List.of();

        Map<String, PluginPackage> byCapability = new LinkedHashMap<>();
        for (PluginPackage pkg : packages) {
            for (String cap : pkg.manifest().provides()) {
                byCapability.put(cap, pkg);
            }
        }

        Map<PluginPackage, Set<PluginPackage>> deps = new LinkedHashMap<>();
        for (PluginPackage pkg : packages) {
            Set<PluginPackage> pkgDeps = new LinkedHashSet<>();
            for (String prim : pkg.manifest().requirements().primitives()) {
                PluginPackage provider = byCapability.get(prim);
                if (provider != null && provider != pkg) {
                    pkgDeps.add(provider);
                }
            }
            deps.put(pkg, pkgDeps);
        }

        List<PluginPackage> sorted = new ArrayList<>();
        Set<PluginPackage> visited = new LinkedHashSet<>();
        Set<PluginPackage> inStack = new LinkedHashSet<>();

        for (PluginPackage pkg : packages) {
            if (!visited.contains(pkg)) {
                visit(pkg, deps, visited, inStack, sorted);
            }
        }

        return List.copyOf(sorted);
    }

    private void visit(PluginPackage pkg,
                       Map<PluginPackage, Set<PluginPackage>> deps,
                       Set<PluginPackage> visited,
                       Set<PluginPackage> inStack,
                       List<PluginPackage> sorted) {
        if (inStack.contains(pkg)) {
            throw new IllegalStateException(
                    "Dependency cycle detected involving: " + pkg.coordinates());
        }
        if (visited.contains(pkg)) return;

        inStack.add(pkg);
        for (PluginPackage dep : deps.getOrDefault(pkg, Set.of())) {
            visit(dep, deps, visited, inStack, sorted);
        }
        inStack.remove(pkg);
        visited.add(pkg);
        sorted.add(pkg);
    }
}
