package io.casehub.platform.spring.generator;

import com.palantir.javapoet.JavaFile;
import io.casehub.platform.generator.AbstractGeneratorMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.jandex.IndexView;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class SpringGeneratorMojo extends AbstractGeneratorMojo {

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/spring-generator")
    private File outputDirectory;

    @Parameter(defaultValue = "${project.basedir}/src/main/java")
    private File sourceDir;

    @Override
    protected File getOutputDirectory() { return outputDirectory; }

    @Override
    protected String getGeneratorName() { return "spring-generator"; }

    @Override
    public void execute() throws MojoExecutionException {
        List<File> modules = resolveModules();
        IndexView resolveIndex = loadCompositeIndex();

        Set<String> manualBeanTypes;
        try {
            manualBeanTypes = new ManualBeanScanner().scan(sourceDir.toPath());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to scan manual beans", e);
        }

        var allImports = new java.util.LinkedHashSet<String>();

        Path handWrittenImports = sourceDir.toPath().getParent()
                .resolve("resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");
        if (Files.exists(handWrittenImports)) {
            try {
                Files.readAllLines(handWrittenImports).stream()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .forEach(allImports::add);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to read hand-written imports", e);
            }
        }

        var scanner = new JandexProducerScanner();
        boolean anyGenerated = false;

        for (File module : modules) {
            IndexView scanIndex = loadSingleIndex(module);
            List<ProducerDescriptor> descriptors = scanner.scan(scanIndex, resolveIndex);

            if (descriptors.isEmpty()) {
                getLog().info("No @Produces methods found in " + module.getName() + " — skipping.");
                continue;
            }

            if (!manualBeanTypes.isEmpty()) {
                descriptors = descriptors.stream()
                        .filter(d -> {
                            boolean excluded = manualBeanTypes.contains(d.returnTypeSimpleName())
                                    || manualBeanTypes.contains(d.effectiveReturnTypeSimpleName());
                            if (excluded) {
                                getLog().info("Skipping " + d.effectiveReturnTypeSimpleName()
                                        + " — already defined in manual config");
                            }
                            return !excluded;
                        })
                        .toList();
            }

            if (descriptors.isEmpty()) {
                getLog().info("All @Produces in " + module.getName() + " covered by manual config — skipping.");
                continue;
            }

            try {
                String sourcePackage = deriveSpringPackage(descriptors.get(0).producerClassName());
                String configClassName = deriveConfigClassName(module.getName());

                var writer = new AutoConfigurationWriter();
                List<JavaFile> javaFiles = writer.generate(sourcePackage, configClassName, descriptors);

                for (JavaFile javaFile : javaFiles) {
                    javaFile.writeTo(outputDirectory.toPath());
                }

                allImports.add(sourcePackage + "." + configClassName);
                anyGenerated = true;

                getLog().info("Generated " + configClassName + " with " + javaFiles.size()
                        + " file(s) from " + descriptors.size() + " @Produces method(s) in " + module.getName());

            } catch (IOException e) {
                throw new MojoExecutionException("Failed to generate for module " + module.getName(), e);
            }
        }

        if (!anyGenerated && allImports.isEmpty()) {
            getLog().info("No @Produces methods found in any module — skipping generation.");
            return;
        }

        try {
            Path metaInf = outputDirectory.toPath().resolve("META-INF/spring");
            Files.createDirectories(metaInf);
            Files.writeString(
                    metaInf.resolve("org.springframework.boot.autoconfigure.AutoConfiguration.imports"),
                    String.join("\n", allImports) + "\n");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write imports file", e);
        }

        registerSourceRoot();
    }

    private String deriveSpringPackage(String quarkusClassName) {
        int lastDot = quarkusClassName.lastIndexOf('.');
        String basePackage = lastDot >= 0 ? quarkusClassName.substring(0, lastDot) : quarkusClassName;
        if (basePackage.endsWith(".quarkus")) {
            basePackage = basePackage.substring(0, basePackage.length() - ".quarkus".length());
        }
        return basePackage + ".spring";
    }

    String deriveConfigClassName(String moduleName) {
        String[] parts = moduleName.replace("platform-", "").split("-");
        var sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1));
            }
        }
        sb.append("AutoConfiguration");
        return sb.toString();
    }
}
