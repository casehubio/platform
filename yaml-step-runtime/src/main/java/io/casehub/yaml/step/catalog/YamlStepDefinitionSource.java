package io.casehub.yaml.step.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.core.step.StepDefinitionFile;
import io.casehub.yaml.core.step.StepDefinitionParser;
import io.casehub.yaml.plugin.api.StepAction;
import io.casehub.yaml.step.CatalogEntry;
import io.casehub.yaml.step.CatalogSource;
import io.casehub.yaml.step.InvokeHandler;
import io.casehub.yaml.step.ValidatingStepAction;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class YamlStepDefinitionSource implements CatalogSource {

    private final List<String> definitionFiles;
    private final List<InvokeHandler> handlers;
    private final Consumer<io.casehub.yaml.step.StepExecutionEvent> eventSink;

    public YamlStepDefinitionSource(List<String> definitionFiles,
                                     List<InvokeHandler> handlers,
                                     Consumer<io.casehub.yaml.step.StepExecutionEvent> eventSink) {
        this.definitionFiles = definitionFiles;
        this.handlers = handlers;
        this.eventSink = eventSink;
    }

    @Override
    public void populate(Map<String, CatalogEntry> entries) {
        for (String file : definitionFiles) {
            try {
                loadFile(file, entries);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failed to load step definition file: " + file, e);
            }
        }
    }

    @Override
    public int priority() {
        return 100;
    }

    @SuppressWarnings("unchecked")
    private void loadFile(String path, Map<String, CatalogEntry> entries) throws IOException {
        InputStream is = Thread.currentThread().getContextClassLoader()
                               .getResourceAsStream(path);
        if (is == null) {
            throw new IOException("Step definition file not found on classpath: " + path);
        }

        Map<String, Object> raw;
        try (is) {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
            raw = yamlMapper.readValue(is, Map.class);
        }

        Map<String, Object> actionsRaw = (Map<String, Object>) raw.get("actions");
        if (actionsRaw == null) {
            Map<String, Object> adjusted = new java.util.LinkedHashMap<>();
            adjusted.put("namespace", raw.getOrDefault("namespace", ""));
            Map<String, Object> actionEntries = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                if (!"namespace".equals(entry.getKey())) {
                    actionEntries.put(entry.getKey(), entry.getValue());
                }
            }
            adjusted.put("actions", actionEntries);
            raw = adjusted;
        }

        StepDefinitionFile defFile = StepDefinitionParser.parse(raw);

        for (Map.Entry<String, StepDefinition> entry : defFile.actions().entrySet()) {
            StepDefinition def     = entry.getValue();
            InvokeBinding  binding = def.invoke();

            StepAction action    = resolveHandler(binding).create(def, binding);
            StepAction validated = new ValidatingStepAction(def, action, eventSink);

            String qualifiedName = def.qualifiedName(defFile.namespace());
            entries.putIfAbsent(qualifiedName, new CatalogEntry(qualifiedName, def, validated));
            if (!defFile.namespace().isEmpty()) {
                entries.putIfAbsent(def.name(), new CatalogEntry(def.name(), def, validated));
            }
        }
    }

    private InvokeHandler resolveHandler(InvokeBinding binding) {
        for (InvokeHandler handler : handlers) {
            if (handler.supports(binding)) {
                return handler;
            }
        }
        throw new IllegalStateException(
                "No InvokeHandler supports binding type: " + binding.getClass().getSimpleName());
    }
}
