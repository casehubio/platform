package io.casehub.yaml.test;

import java.util.List;

public class ModuleNotFoundException extends RuntimeException {
    private final String moduleName;
    private final List<String> searchPaths;

    public ModuleNotFoundException(String moduleName, List<String> searchPaths) {
        super("Module '" + moduleName + "' not found. Searched: " + searchPaths);
        this.moduleName = moduleName;
        this.searchPaths = List.copyOf(searchPaths);
    }

    public String moduleName() { return moduleName; }
    public List<String> searchPaths() { return List.copyOf(searchPaths); }
}
