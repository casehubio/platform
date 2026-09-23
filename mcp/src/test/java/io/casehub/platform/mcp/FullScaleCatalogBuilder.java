package io.casehub.platform.mcp;

import io.casehub.platform.generator.DomainScanResult;
import io.casehub.platform.generator.McpDomainJandexScanner;
import io.casehub.platform.generator.OperationType;
import io.casehub.platform.generator.ResolvedParam;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Builds a full-scale DomainModelRegistry by scanning casehub JARs
 * from the Maven local repository via Jandex.
 */
final class FullScaleCatalogBuilder {

    private FullScaleCatalogBuilder() {}

    static DomainModelRegistry buildFromLocalRepo(Path m2CasehubRoot) throws IOException {
        List<Path> jars = discoverJars(m2CasehubRoot);
        Index index = buildCompositeIndex(jars);
        List<DomainScanResult> scanResults = new McpDomainJandexScanner().scan(index);
        return toRegistry(scanResults);
    }

    static List<Path> discoverJars(Path root) throws IOException {
        List<Path> jars = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return jars;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                if (name.endsWith(".jar")
                        && !name.endsWith("-sources.jar")
                        && !name.endsWith("-javadoc.jar")
                        && !name.endsWith("-tests.jar")
                        && !name.contains("deployment")
                        && !name.contains("generator")
                        && !name.contains("testing")) {
                    jars.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return jars;
    }

    static Index buildCompositeIndex(List<Path> jars) throws IOException {
        Indexer indexer = new Indexer();
        for (Path jar : jars) {
            try (JarFile jf = new JarFile(jar.toFile())) {
                Enumeration<JarEntry> entries = jf.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class") && !entry.getName().contains("module-info")) {
                        try (InputStream is = jf.getInputStream(entry)) {
                            indexer.index(is);
                        } catch (Exception ignored) {
                            // Skip classes that can't be indexed (e.g., broken bytecode)
                        }
                    }
                }
            } catch (Exception ignored) {
                // Skip JARs that can't be opened
            }
        }
        return indexer.complete();
    }

    static DomainModelRegistry toRegistry(List<DomainScanResult> scanResults) {
        DomainModelRegistry registry = new DomainModelRegistry();

        for (DomainScanResult scan : scanResults) {
            List<OperationDescriptor> operations = scan.operations().stream()
                    .map(op -> new OperationDescriptor(
                            op.methodName(),
                            toRuntimeType(op.type()),
                            op.description(),
                            op.params().stream()
                                    .filter(p -> !p.isContextParam())
                                    .map(FullScaleCatalogBuilder::toParamDescriptor)
                                    .toList(),
                            op.returnTypeStr(),
                            null,
                            null))
                    .toList();

            String app = scan.app();
            if (app == null || app.isEmpty()) {
                app = inferAppFromPackage(scan.sourceFqcn());
            }

            DomainModel model = new DomainModel(
                    scan.domainName(),
                    app,
                    "",
                    operations,
                    List.of(),
                    Map.of());

            registry.register(model);
        }
        return registry;
    }

    static String inferAppFromPackage(String fqcn) {
        if (fqcn == null || !fqcn.startsWith("io.casehub.")) return "";
        String rest = fqcn.substring("io.casehub.".length());
        int dot = rest.indexOf('.');
        if (dot < 0) return "";
        String segment = rest.substring(0, dot);
        return switch (segment) {
            case "platform", "api" -> "";
            default -> segment;
        };
    }

    private static OperationDescriptor.OperationType toRuntimeType(OperationType genType) {
        return switch (genType) {
            case QUERY -> OperationDescriptor.OperationType.QUERY;
            case MUTATION -> OperationDescriptor.OperationType.MUTATION;
            case STREAM -> OperationDescriptor.OperationType.STREAM;
        };
    }

    private static ParameterDescriptor toParamDescriptor(ResolvedParam p) {
        return new ParameterDescriptor(
                p.name(),
                p.typeStr(),
                !p.isPathParam() && p.defaultValue() == null,
                "",
                Map.of());
    }
}
