package io.casehub.platform.graphql.spring.generator;

import com.palantir.javapoet.JavaFile;
import io.casehub.platform.generator.AbstractGeneratorMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.jandex.Index;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GraphqlSpringGeneratorMojo extends AbstractGeneratorMojo {

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/graphql-spring-generator")
    private File outputDirectory;

    @Override
    protected File getOutputDirectory() { return outputDirectory; }

    @Override
    protected String getGeneratorName() { return "graphql-spring-generator"; }

    @Override
    public void execute() throws MojoExecutionException {
        Index index = loadJandexIndex();

        var scanner = new McpDomainScanner();
        List<DomainDescriptor> descriptors = scanner.scan(index);

        if (descriptors.isEmpty()) {
            getLog().info("No @McpDomain interfaces found — skipping generation.");
            return;
        }

        try {
            var graphqlWriter = new GraphqlControllerWriter();
            var restWriter = new DomainRestControllerWriter();
            int count = 0;

            for (DomainDescriptor desc : descriptors) {
                String targetPackage = "io.casehub.platform.graphql.spring.generated";

                JavaFile graphqlFile = graphqlWriter.generate(desc, targetPackage);
                graphqlFile.writeTo(outputDirectory);

                JavaFile restFile = restWriter.generate(desc, targetPackage);
                restFile.writeTo(outputDirectory);

                count += 2;
            }

            registerSourceRoot();
            getLog().info("Generated " + count + " Spring classes from "
                    + descriptors.size() + " @McpDomain interface(s)");

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate Spring GraphQL controllers", e);
        }
    }
}
