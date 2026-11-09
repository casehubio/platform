package io.casehub.yaml.step.catalog.scannertest;

import io.casehub.yaml.plugin.api.Execute;
import io.casehub.yaml.plugin.api.Optional;
import io.casehub.yaml.plugin.api.Plugin;

@Plugin(value = "record-output-streaming", streaming = true)
public record RecordOutputStreamingPlugin(@Optional String language) {

    @Execute
    public TranscriptionResult transcribe(String audio) {
        return new TranscriptionResult(audio, language != null ? language : "en", 0.95);
    }
}
