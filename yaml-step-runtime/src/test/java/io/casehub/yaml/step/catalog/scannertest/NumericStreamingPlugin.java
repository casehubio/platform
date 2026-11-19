package io.casehub.yaml.step.catalog.scannertest;

import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Required;

@Plugin(value = "numeric-streaming", streaming = true)
public record NumericStreamingPlugin(@Required int threshold, @Optional String mode) {
    @Execute
    public String filter(String input) {
        return input.length() >= threshold ? input : "";
    }
}
