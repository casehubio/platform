package io.casehub.platform.mcp;

import io.casehub.platform.generator.DomainScanResult;
import io.casehub.platform.generator.McpDomainJandexScanner;
import io.casehub.platform.generator.OperationType;
import io.casehub.platform.generator.ResolvedParam;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
 * Builds a full-scale DomainModelRegistry by scanning casehub class files
 * from slot target/classes directories and/or Maven local repository JARs.
 */
final class FullScaleCatalogBuilder {

    private FullScaleCatalogBuilder() {}

    static DomainModelRegistry buildFromSlotAndLocalRepo(Path slotRoot, Path m2CasehubRoot) throws IOException {
        Indexer indexer = new Indexer();
        int slotClasses = indexSlotTargetClasses(indexer, slotRoot);
        int jarClasses = indexM2Jars(indexer, m2CasehubRoot);
        System.out.printf("Indexed %d classes from slot target/classes, %d from .m2 JARs%n",
                slotClasses, jarClasses);
        Index index = indexer.complete();
        List<DomainScanResult> scanResults = new McpDomainJandexScanner().scan(index);
        return toRegistry(scanResults);
    }

    static DomainModelRegistry buildFromCatalogJson(Path catalogJson) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> domains = mapper.readValue(
                Files.readString(catalogJson), new TypeReference<>() {});
        DomainModelRegistry registry = new DomainModelRegistry();
        for (Map<String, Object> d : domains) {
            String name = (String) d.get("name");
            String app = (String) d.getOrDefault("app", "");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ops = (List<Map<String, Object>>) d.getOrDefault("operations", List.of());
            List<OperationDescriptor> operations = ops.stream().map(op -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> params = (List<Map<String, Object>>) op.getOrDefault("params", List.of());
                return new OperationDescriptor(
                        (String) op.get("name"),
                        switch ((String) op.getOrDefault("type", "QUERY")) {
                            case "MUTATION" -> OperationDescriptor.OperationType.MUTATION;
                            case "STREAM" -> OperationDescriptor.OperationType.STREAM;
                            default -> OperationDescriptor.OperationType.QUERY;
                        },
                        (String) op.getOrDefault("summary", ""),
                        params.stream().map(p -> new ParameterDescriptor(
                                (String) p.get("name"), (String) p.getOrDefault("type", "Object"),
                                true, "", Map.of())).toList(),
                        (String) op.getOrDefault("returns", "void"),
                        null, null);
            }).toList();
            registry.register(new DomainModel(name, app, "", operations, List.of(), Map.of()));
        }
        return registry;
    }

    static DomainModelRegistry buildFromLocalRepo(Path m2CasehubRoot) throws IOException {
        Indexer indexer = new Indexer();
        indexM2Jars(indexer, m2CasehubRoot);
        Index index = indexer.complete();
        List<DomainScanResult> scanResults = new McpDomainJandexScanner().scan(index);
        return toRegistry(scanResults);
    }

    private static int indexSlotTargetClasses(Indexer indexer, Path slotRoot) throws IOException {
        if (!Files.isDirectory(slotRoot)) return 0;
        int[] count = {0};
        Files.walkFileTree(slotRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String name = dir.getFileName().toString();
                if (name.equals(".git") || name.equals(".idea") || name.equals("node_modules")
                        || name.startsWith("wsp-")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String path = file.toString();
                if (path.contains("/target/classes/") && path.endsWith(".class")
                        && !path.contains("module-info") && !path.contains("/test-classes/")) {
                    try (InputStream is = Files.newInputStream(file)) {
                        indexer.index(is);
                        count[0]++;
                    } catch (Exception ignored) {}
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return count[0];
    }

    private static int indexM2Jars(Indexer indexer, Path m2Root) throws IOException {
        if (!Files.isDirectory(m2Root)) return 0;
        List<Path> jars = discoverJars(m2Root);
        int count = 0;
        for (Path jar : jars) {
            try (JarFile jf = new JarFile(jar.toFile())) {
                Enumeration<JarEntry> entries = jf.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class") && !entry.getName().contains("module-info")) {
                        try (InputStream is = jf.getInputStream(entry)) {
                            indexer.index(is);
                            count++;
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
        }
        return count;
    }

    static List<Path> discoverJars(Path root) throws IOException {
        List<Path> jars = new ArrayList<>();
        if (!Files.isDirectory(root)) return jars;
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
