package io.casehub.yaml.plugin.api;

public interface DomainVariableSource {
    String prefix();
    Object resolve(String path);
}
