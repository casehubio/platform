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

public class OutputConfig {

    private String format;
    private String targetPackage;
    private String prefix = "Yaml";
    private String mappingsFile;
    private String ruleFactory;

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getTargetPackage() {
        return targetPackage;
    }

    public void setTargetPackage(String targetPackage) {
        this.targetPackage = targetPackage;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getMappingsFile() {
        return mappingsFile;
    }

    public void setMappingsFile(String mappingsFile) {
        this.mappingsFile = mappingsFile;
    }

    public String getRuleFactory() {
        return ruleFactory;
    }

    public void setRuleFactory(String ruleFactory) {
        this.ruleFactory = ruleFactory;
    }
}
