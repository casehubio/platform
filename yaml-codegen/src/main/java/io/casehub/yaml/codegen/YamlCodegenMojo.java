/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.yaml.codegen;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class YamlCodegenMojo extends AbstractMojo {

    @Parameter(required = true)
    private File schemaFile;

    @Parameter(required = true)
    private List<OutputConfig> outputs;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        if (!schemaFile.exists()) {
            throw new MojoExecutionException("Schema file not found: " + schemaFile);
        }

        File outputDir =
                new File(project.getBuild().getDirectory(), "generated-sources/yaml-codegen");
        project.addCompileSourceRoot(outputDir.getAbsolutePath());

        for (OutputConfig output : outputs) {
            try {
                switch (output.getFormat()) {
                    case "record" -> generateRecords(output, outputDir);
                    case "pojo" -> generatePojos(output, outputDir);
                    default ->
                            throw new MojoExecutionException(
                                    "Unknown format: " + output.getFormat());
                }
            } catch (MojoExecutionException e) {
                throw e;
            } catch (Exception e) {
                throw new MojoExecutionException(
                        "Generation failed for format " + output.getFormat(), e);
            }
        }
    }

    private void generateRecords(OutputConfig output, File outputDir) throws IOException {
        TypeGraph graph = new SchemaParser().parse(schemaFile);

        MappingConfig mapping =
                output.getMappingsFile() != null
                        ? MappingConfig.load(new File(output.getMappingsFile()))
                        : MappingConfig.empty();

        RecordEmitter.EmitConfig emitConfig =
                new RecordEmitter.EmitConfig(output.getTargetPackage(), output.getPrefix());

        List<RecordEmitter.GeneratedFile> files =
                new RecordEmitter().emit(graph, mapping, emitConfig);

        for (RecordEmitter.GeneratedFile file : files) {
            Path dir = outputDir.toPath().resolve(output.getTargetPackage().replace('.', '/'));
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(file.fileName()), file.content());
        }

        getLog().info(
                "Generated " + files.size() + " record files to " + output.getTargetPackage());
    }

    private void generatePojos(OutputConfig output, File outputDir) {
        new PojoEmitter()
                .emit(schemaFile, output.getTargetPackage(), output.getRuleFactory(), outputDir);
        getLog().info("Generated POJOs to " + output.getTargetPackage());
    }
}
