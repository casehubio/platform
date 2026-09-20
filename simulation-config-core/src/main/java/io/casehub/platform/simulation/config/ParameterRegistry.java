package io.casehub.platform.simulation.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public class ParameterRegistry {

    private static final String RESOURCE = "META-INF/simulation-parameters.properties";

    private final Map<String, MethodParams> methods;

    private ParameterRegistry(Map<String, MethodParams> methods) {
        this.methods = methods;
    }

    public static ParameterRegistry loadFromClasspath() {
        Properties merged = new Properties();
        try {
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader()
                    .getResources(RESOURCE);
            while (resources.hasMoreElements()) {
                try (InputStream is = resources.nextElement().openStream()) {
                    Properties props = new Properties();
                    props.load(is);
                    props.forEach((k, v) -> merged.putIfAbsent(k, v));
                }
            }
        } catch (IOException e) {
            return new ParameterRegistry(Map.of());
        }
        return parse(merged);
    }

    public static ParameterRegistry parse(Properties properties) {
        Map<String, MethodParams> methods = new HashMap<>();
        properties.forEach((key, value) -> {
            String qn = key.toString().trim();
            String spec = value.toString().trim();
            methods.put(qn, parseMethodParams(spec));
        });
        return new ParameterRegistry(methods);
    }

    public Optional<MethodParams> paramsFor(String qualifiedName) {
        return Optional.ofNullable(methods.get(qualifiedName));
    }

    private static MethodParams parseMethodParams(String spec) {
        if (spec.isEmpty()) {
            return new MethodParams(Map.of(), 0);
        }
        Map<String, Integer> nameToPosition = new LinkedHashMap<>();
        String[] parts = spec.split(",");
        for (String part : parts) {
            String[] kv = part.trim().split(":");
            if (kv.length < 2) continue;
            nameToPosition.put(kv[0].trim(), Integer.parseInt(kv[1].trim()));
        }
        return new MethodParams(nameToPosition, nameToPosition.size());
    }

    public record MethodParams(Map<String, Integer> nameToPosition, int totalParams) {
        public Optional<Integer> positionOf(String paramName) {
            return Optional.ofNullable(nameToPosition.get(paramName));
        }
    }
}
