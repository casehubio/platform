package test.plugins;

import io.casehub.yaml.plugin.api.*;
import java.io.InputStream;
import java.io.ByteArrayInputStream;

@Plugin(value = "denoise", streaming = true)
public record StreamingWithConfigPlugin(@Optional String model) {
    @Execute
    public InputStream apply(InputStream audio) {
        return audio;
    }
}
