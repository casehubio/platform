package io.casehub.platform.spring.generator;

import io.casehub.platform.generator.AbstractVerifyMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.jandex.IndexView;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mojo(name = "verify", defaultPhase = LifecyclePhase.VERIFY,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class SpringVerifyMojo extends AbstractVerifyMojo {

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/spring-generator")
    private File outputDirectory;

    @Parameter(defaultValue = "${project.basedir}/src/main/java")
    private File sourceDir;

    @Override
    protected File getOutputDirectory() { return outputDirectory; }

    @Override
    protected String getGeneratorName() { return "spring-generator"; }

    @Override
    protected Set<String> collectSourceTypes(IndexView compositeIndex) {
        IndexView scanIndex;
        try {
            scanIndex = loadJandexIndex();
        } catch (org.apache.maven.plugin.MojoExecutionException e) {
            throw new RuntimeException(e);
        }
        var scanner = new JandexProducerScanner();
        List<ProducerDescriptor> quarkusProducers = scanner.scan(scanIndex, compositeIndex);
        Set<String> types = new HashSet<>();
        for (ProducerDescriptor d : quarkusProducers) {
            types.add(d.returnTypeSimpleName());
        }
        return types;
    }

    @Override
    protected Set<String> collectTargetTypes() {
        Set<String> types = new HashSet<>();
        try {
            var scanner = new ManualBeanScanner();
            types.addAll(scanner.scan(sourceDir.toPath()));
            types.addAll(scanner.scan(outputDirectory.toPath()));
        } catch (IOException e) {
            getLog().warn("Failed to scan Spring sources: " + e.getMessage());
        }
        return types;
    }

}
