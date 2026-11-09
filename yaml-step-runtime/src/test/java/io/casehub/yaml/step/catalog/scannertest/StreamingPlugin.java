package io.casehub.yaml.step.catalog.scannertest;

import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Plugin;
import io.casehub.yaml.plugin.api.Required;
@Plugin(value = "streaming-test", streaming = true)
public record StreamingPlugin(@Required String text) {
    @Execute
    public String process(String input) {
        return text + ":" + input;
    }
}
