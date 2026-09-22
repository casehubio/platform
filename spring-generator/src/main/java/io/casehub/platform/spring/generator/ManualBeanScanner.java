package io.casehub.platform.spring.generator;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ManualBeanScanner {

    private static final Pattern BEAN_RETURN_TYPE = Pattern.compile(
            "public\\s+([\\w.]+)\\s*(?:<[^>]*>)?\\s+\\w+\\s*\\(");

    private ManualBeanScanner() {}

    public static Set<String> scan(Path sourceDir) throws IOException {
        Set<String> types = new HashSet<>();
        if (!Files.exists(sourceDir)) {
            return types;
        }
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    String content = Files.readString(file);
                    if (content.contains("@Bean")) {
                        Matcher m = BEAN_RETURN_TYPE.matcher(content);
                        while (m.find()) {
                            String type = m.group(1);
                            int dot = type.lastIndexOf('.');
                            types.add(dot >= 0 ? type.substring(dot + 1) : type);
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return types;
    }
}
