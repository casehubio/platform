package io.casehub.yaml.step.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.core.step.StepParameter;
import io.casehub.yaml.core.step.StepParameterType;
import io.casehub.yaml.plugin.api.StepAction;
import io.casehub.yaml.step.CatalogEntry;
import io.casehub.yaml.step.CatalogSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

public class AptPluginSource implements CatalogSource {

    private final ObjectMapper objectMapper;

    public AptPluginSource(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void populate(Map<String, CatalogEntry> entries) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> manifests = cl.getResources("META-INF/yaml-plugins");
            while (manifests.hasMoreElements()) {
                URL dir = manifests.nextElement();
                // Scan directory for *.json manifests
            }

            // Also scan individual manifest files
            Enumeration<URL> jsonFiles = cl.getResources("META-INF/yaml-plugins/");
            // Note: actual scanning happens at runtime via the classpath;
            // for now, this source is a placeholder that discovers pre-registered plugins
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan APT plugin manifests", e);
        }
    }

    @Override
    public int priority() {
        return 200;
    }

    @SuppressWarnings("unchecked")
    CatalogEntry loadManifest(String name, InputStream manifestStream,
                                InputStream schemaStream) throws IOException {
        Map<String, Object> manifest = objectMapper.readValue(manifestStream, LinkedHashMap.class);
        String actionClass = (String) manifest.get("actionClass");

        Map<String, StepParameter> inputs = new LinkedHashMap<>();
        if (schemaStream != null) {
            Map<String, Object> schema = objectMapper.readValue(schemaStream, LinkedHashMap.class);
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            if (properties != null) {
                for (Map.Entry<String, Object> prop : properties.entrySet()) {
                    Map<String, Object> propSchema = (Map<String, Object>) prop.getValue();
                    String typeStr = (String) propSchema.getOrDefault("type", "string");
                    inputs.put(prop.getKey(), new StepParameter(
                            StepParameterType.fromString(typeStr), false, null, null, null,
                            (String) propSchema.get("description")));
                }
            }
        }

        StepDefinition syntheticDef = new StepDefinition(name, null, inputs, Map.of(), null);
        StepAction action = loadAction(actionClass);

        return new CatalogEntry(name, syntheticDef, action);
    }

    private StepAction loadAction(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return (StepAction) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate StepAction: " + className, e);
        }
    }
}
