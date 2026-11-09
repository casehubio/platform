package io.casehub.yaml.step.catalog.scannertest;

import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Plugin;

@Plugin(value = "all-optional-streaming", streaming = true)
public record AllOptionalStreamingPlugin(@Optional String language, @Optional String model) {
    @Execute
    public String transcribe(String audio) {
        return (language != null ? language + ":" : "") + audio;
    }
}
