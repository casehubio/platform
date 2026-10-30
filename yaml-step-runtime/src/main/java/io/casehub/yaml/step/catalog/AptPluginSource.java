package io.casehub.yaml.step.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.plugin.api.Action;
import io.casehub.yaml.plugin.api.Definition;
import io.casehub.yaml.plugin.api.Parameter;
import io.casehub.yaml.plugin.api.ParameterType;
import io.casehub.yaml.plugin.api.PluginRegistry;
import io.casehub.yaml.plugin.api.Portability;
import io.casehub.yaml.plugin.api.StreamingStep;
import io.casehub.yaml.plugin.api.StreamingStepFactory;
import io.casehub.yaml.plugin.api.StreamingTypeInfo;

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

public class AptPluginSource {

    private static final String MANIFEST_DIR = "META-INF/yaml-plugins/";

    private final ObjectMapper objectMapper;

    public AptPluginSource(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void populate(PluginRegistry registry) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> dirs = cl.getResources(MANIFEST_DIR);
            while (dirs.hasMoreElements()) {
                URL dirUrl = dirs.nextElement();
                scanDirectory(dirUrl, cl, registry);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan APT plugin manifests", e);
        }
    }

    private void scanDirectory(URL dirUrl, ClassLoader cl, PluginRegistry registry)
            throws IOException {
        String protocol = dirUrl.getProtocol();
        if ("file".equals(protocol)) {
            scanFileDirectory(new File(dirUrl.getPath()), cl, registry);
        } else if ("jar".equals(protocol)) {
            scanJarDirectory(dirUrl, cl, registry);
        }
    }

    private void scanFileDirectory(File dir, ClassLoader cl, PluginRegistry registry)
            throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".json") && !name.endsWith(".schema.json")) {
                String pluginName = name.substring(0, name.length() - ".json".length());
                loadAndRegister(pluginName, cl, registry);
            }
        }
    }

    private void scanJarDirectory(URL dirUrl, ClassLoader cl, PluginRegistry registry)
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
                    loadAndRegister(pluginName, cl, registry);
                }
            }
        }
    }

    private void loadAndRegister(String pluginName, ClassLoader cl, PluginRegistry registry)
            throws IOException {
        String manifestPath = MANIFEST_DIR + pluginName + ".json";
        String schemaPath = MANIFEST_DIR + pluginName + ".schema.json";

        try (InputStream manifestStream = cl.getResourceAsStream(manifestPath);
             InputStream schemaStream = cl.getResourceAsStream(schemaPath)) {
            if (manifestStream == null) return;
            Definition definition = loadManifest(pluginName, manifestStream, schemaStream);
            registry.register(definition);
        }
    }

    @SuppressWarnings("unchecked")
    Definition loadManifest(String name, InputStream manifestStream,
                            InputStream schemaStream) throws IOException {
        Map<String, Object> manifest = objectMapper.readValue(manifestStream, LinkedHashMap.class);
        String actionClass = (String) manifest.get("actionClass");
        String portabilityStr = (String) manifest.get("portability");
        Portability portability = portabilityStr != null
                ? Portability.valueOf(portabilityStr) : Portability.JAVA;
        String capabilityStr = (String) manifest.get("capability");

        Definition.Builder builder = Definition.of(name)
                .portability(portability)
                .capability(capabilityStr)
                .execute(loadAction(actionClass));

        if (schemaStream != null) {
            Map<String, Object> schema = objectMapper.readValue(schemaStream, LinkedHashMap.class);
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            if (properties != null) {
                for (Map.Entry<String, Object> prop : properties.entrySet()) {
                    Map<String, Object> propSchema = (Map<String, Object>) prop.getValue();
                    String typeStr = (String) propSchema.getOrDefault("type", "string");
                    builder.input(prop.getKey(), new Parameter(
                            ParameterType.fromString(typeStr), false, null, null, null,
                            (String) propSchema.get("description")));
                }
            }
        }

        Map<String, Object> streaming = (Map<String, Object>) manifest.get("streaming");
        if (streaming != null) {
            String inputTypeName = (String) streaming.get("inputType");
            String outputTypeName = (String) streaming.get("outputType");
            String inputKey = (String) streaming.get("inputKey");
            String streamingStepClassName = (String) streaming.get("streamingStepClass");
            try {
                Class<?> inputType = Class.forName(inputTypeName);
                Class<?> outputType = Class.forName(outputTypeName);
                builder.streamingType(new StreamingTypeInfo(inputType, outputType, inputKey,
                    loadStreamingStepFactory(streamingStepClassName, actionClass)));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Failed to load streaming types for plugin: " + name, e);
            }
        }

        return builder.build();
    }

    private Action loadAction(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            return (Action) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate StepAction: " + className, e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private StreamingStepFactory<?, ?> loadStreamingStepFactory(String streamingStepClassName, String actionClassName) {
        try {
            Class<?> stepClass    = Class.forName(streamingStepClassName);
            var      constructors = stepClass.getDeclaredConstructors();
            return (params, services) -> {
                try {
                    var ctor       = constructors[0];
                    var paramTypes = ctor.getParameterTypes();
                    var args       = new Object[paramTypes.length];
                    for (int i = 0; i < paramTypes.length; i++) {
                        args[i] = params.get(toKebabCase(ctor.getParameters()[i].getName()));
                        if (args[i] == null && !paramTypes[i].isPrimitive()) {
                            try {args[i] = services.lookup(paramTypes[i]);} catch (Exception e) {
                                java.util.logging.Logger.getLogger(AptPluginSource.class.getName())
                                    .fine("No param or service for " + ctor.getParameters()[i].getName() + ": " + e.getMessage());
                            }
                        }
                    }
                    return (StreamingStep) ctor.newInstance(args);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to create streaming step: " + streamingStepClassName, e);
                }
            };
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load streaming step class: " + streamingStepClassName, e);
        }
    }

    private static String toKebabCase(String camelCase) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('-');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }


}
