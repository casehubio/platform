package io.casehub.platform.spring.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ManualBeanScanner {

    private static final Pattern BEAN_RETURN_TYPE = Pattern.compile(
            "@Bean[^}]*?public\\s+([A-Za-z][A-Za-z0-9_.]+(?:<[^>]+>)?)\\s+\\w+\\s*\\(");

    public Set<String> scan(Path sourceRoot) throws IOException {
        Set<String> types = new HashSet<>();
        if (sourceRoot == null || !Files.isDirectory(sourceRoot)) {
            return types;
        }
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(p -> p.getFileName().toString().endsWith("ManualConfig.java"))
                .forEach(p -> {
                    try {
                        collectReturnTypes(p, types);
                    } catch (IOException ignored) {
                    }
                });
        }
        return types;
    }

    private void collectReturnTypes(Path javaFile, Set<String> types) throws IOException {
        String content = Files.readString(javaFile);
        String packageName = extractPackage(content);
        Set<String> imports = extractImports(content);
        Matcher matcher = BEAN_RETURN_TYPE.matcher(content);
        while (matcher.find()) {
            String simpleType = matcher.group(1);
            int angleIdx = simpleType.indexOf('<');
            if (angleIdx > 0) {
                simpleType = simpleType.substring(0, angleIdx);
            }
            String resolved = resolveType(simpleType, packageName, imports);
            if (resolved != null) {
                types.add(resolved);
            }
        }
    }

    private String resolveType(String simpleName, String packageName, Set<String> imports) {
        if (simpleName.contains(".")) {
            return simpleName;
        }
        for (String imp : imports) {
            if (imp.endsWith("." + simpleName)) {
                return imp;
            }
        }
        if (packageName != null) {
            return packageName + "." + simpleName;
        }
        return simpleName;
    }

    private String extractPackage(String content) {
        Matcher m = Pattern.compile("^package\\s+([^;]+);", Pattern.MULTILINE).matcher(content);
        return m.find() ? m.group(1).trim() : null;
    }

    private Set<String> extractImports(String content) {
        Set<String> imports = new HashSet<>();
        Matcher m = Pattern.compile("^import\\s+([^;]+);", Pattern.MULTILINE).matcher(content);
        while (m.find()) {
            imports.add(m.group(1).trim());
        }
        return imports;
    }
}
