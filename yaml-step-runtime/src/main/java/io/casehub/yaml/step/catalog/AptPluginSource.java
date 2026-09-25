package io.casehub.yaml.step.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.core.step.StepDefinition;
import io.casehub.yaml.core.step.StepParameter;
import io.casehub.yaml.core.step.StepParameterType;
import io.casehub.yaml.plugin.api.StepAction;
import io.casehub.yaml.step.CatalogEntry;
import io.casehub.yaml.step.CatalogSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class AptPluginSource implements CatalogSource {

    private static final String MANIFEST_DIR = "META-INF/yaml-plugins/";

    private final ObjectMapper objectMapper;

    public AptPluginSource(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void populate(Map<String, CatalogEntry> entries) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> dirs = cl.getResources(MANIFEST_DIR);
            while (dirs.hasMoreElements()) {
                URL dirUrl = dirs.nextElement();
                scanDirectory(dirUrl, cl, entries);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan APT plugin manifests", e);
        }
    }

    private void scanDirectory(URL dirUrl, ClassLoader cl, Map<String, CatalogEntry> entries)
            throws IOException {
        String protocol = dirUrl.getProtocol();
        if ("file".equals(protocol)) {
            scanFileDirectory(new File(dirUrl.getPath()), cl, entries);
        } else if ("jar".equals(protocol)) {
            scanJarDirectory(dirUrl, cl, entries);
        }
    }

    private void scanFileDirectory(File dir, ClassLoader cl, Map<String, CatalogEntry> entries)
            throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".json") && !name.endsWith(".schema.json")) {
                String pluginName = name.substring(0, name.length() - ".json".length());
                if (!entries.containsKey(pluginName)) {
                    loadAndRegister(pluginName, cl, entries);
                }
            }
        }
    }

    private void scanJarDirectory(URL dirUrl, ClassLoader cl, Map<String, CatalogEntry> entries)
            throws IOException {
        JarURLConnection jarConn = (JarURLConnection) dirUrl.openConnection();
        try (JarFile jarFile = jarConn.getJarFile()) {
            Enumeration<JarEntry> jarEntries = jarFile.entries();
            while (jarEntries.hasMoreElements()) {
                JarEntry jarEntry = jarEntries.nextElement();
                String entryName = jarEntry.getName();
                if (entryName.startsWith(MANIFEST_DIR)
                        && entryName.endsWith(".json")
                        && !entryName.endsWith(".schema.json")
                        && !jarEntry.isDirectory()) {
                    String fileName = entryName.substring(MANIFEST_DIR.length());
                    String pluginName = fileName.substring(0, fileName.length() - ".json".length());
                    if (!entries.containsKey(pluginName)) {
                        loadAndRegister(pluginName, cl, entries);
                    }
                }
            }
        }
    }

    private void loadAndRegister(String pluginName, ClassLoader cl, Map<String, CatalogEntry> entries)
            throws IOException {
        String manifestPath = MANIFEST_DIR + pluginName + ".json";
        String schemaPath = MANIFEST_DIR + pluginName + ".schema.json";

        try (InputStream manifestStream = cl.getResourceAsStream(manifestPath);
             InputStream schemaStream = cl.getResourceAsStream(schemaPath)) {
            if (manifestStream == null) return;
            CatalogEntry entry = loadManifest(pluginName, manifestStream, schemaStream);
            entries.putIfAbsent(pluginName, entry);
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
