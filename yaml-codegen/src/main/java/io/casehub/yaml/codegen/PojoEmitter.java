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

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.sun.codemodel.JCodeModel;
import java.io.File;
import java.io.IOException;
import org.jsonschema2pojo.ContentResolver;
import org.jsonschema2pojo.DefaultGenerationConfig;
import org.jsonschema2pojo.GenerationConfig;
import org.jsonschema2pojo.Jackson2Annotator;
import org.jsonschema2pojo.RuleLogger;
import org.jsonschema2pojo.SchemaGenerator;
import org.jsonschema2pojo.SchemaMapper;
import org.jsonschema2pojo.SchemaStore;
import org.jsonschema2pojo.SourceType;
import org.jsonschema2pojo.rules.RuleFactory;

public class PojoEmitter {

    public void emit(File schemaFile, String targetPackage, String ruleFactoryClass,
            File outputDir) {
        try {
            GenerationConfig config = new DefaultGenerationConfig() {
                @Override
                public SourceType getSourceType() {
                    return SourceType.YAMLSCHEMA;
                }

                @Override
                public boolean isUsePrimitives() {
                    return false;
                }

                @Override
                public boolean isIncludeAdditionalProperties() {
                    return false;
                }
            };

            ContentResolver contentResolver = new ContentResolver(new YAMLFactory());
            SchemaStore schemaStore = new SchemaStore(contentResolver, createLogger());

            RuleFactory ruleFactory = resolveRuleFactory(ruleFactoryClass);
            ruleFactory.setGenerationConfig(config);
            ruleFactory.setAnnotator(new Jackson2Annotator(config));
            ruleFactory.setSchemaStore(schemaStore);

            SchemaMapper mapper = new SchemaMapper(ruleFactory, new SchemaGenerator());

            JCodeModel codeModel = new JCodeModel();
            mapper.generate(codeModel, "CaseHub", targetPackage,
                    schemaFile.toURI().toURL());

            outputDir.mkdirs();
            codeModel.build(outputDir);
        } catch (IOException e) {
            throw new RuntimeException("POJO generation failed for schema: " + schemaFile, e);
        }
    }

    private RuleFactory resolveRuleFactory(String ruleFactoryClass) {
        if (ruleFactoryClass == null || ruleFactoryClass.isBlank()) {
            return new RuleFactory();
        }
        try {
            Class<?> clazz = Class.forName(ruleFactoryClass);
            return (RuleFactory) clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(
                    "Failed to instantiate RuleFactory: " + ruleFactoryClass, e);
        }
    }

    private static RuleLogger createLogger() {
        return new RuleLogger() {
            public void debug(String msg) {}
            public void error(String msg) { System.err.println(msg); }
            public void error(String msg, Throwable t) { System.err.println(msg); }
            public void info(String msg) {}
            public void trace(String msg) {}
            public void warn(String msg) { System.err.println("WARN: " + msg); }
            public void warn(String msg, Throwable t) { System.err.println("WARN: " + msg); }
            public boolean isDebugEnabled() { return false; }
            public boolean isErrorEnabled() { return true; }
            public boolean isInfoEnabled() { return false; }
            public boolean isTraceEnabled() { return false; }
            public boolean isWarnEnabled() { return true; }
        };
    }
}
