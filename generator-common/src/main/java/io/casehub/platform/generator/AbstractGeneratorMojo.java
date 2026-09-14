package io.casehub.platform.generator;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public abstract class AbstractGeneratorMojo extends AbstractMojo {

    @Parameter(required = true)
    protected File quarkusModule;

    @Parameter(defaultValue = "${project}")
    protected MavenProject project;

    protected abstract File getOutputDirectory();

    protected abstract String getGeneratorName();

    protected Index loadJandexIndex() throws MojoExecutionException {
        File jandexIdx = new File(quarkusModule, "target/classes/META-INF/jandex.idx");
        if (!jandexIdx.exists()) {
            throw new MojoExecutionException(
                    "Jandex index not found at " + jandexIdx.getAbsolutePath()
                    + ". Build the Quarkus module first.");
        }
        try (var fis = new FileInputStream(jandexIdx)) {
            return new IndexReader(fis).read();
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read Jandex index", e);
        }
    }

    protected void registerSourceRoot() {
        project.addCompileSourceRoot(getOutputDirectory().getAbsolutePath());
    }
}
