package io.casehub.platform.agent.react;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.Objects;

public record Tool(ToolSpecification spec, ToolExecutor executor) {
    public Tool {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(executor, "executor");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final ToolSpecification.Builder specBuilder = ToolSpecification.builder();
        private final JsonObjectSchema.Builder paramsBuilder = JsonObjectSchema.builder();
        private ToolExecutor executor;
        private boolean hasParams;

        public Builder name(String name) {
            specBuilder.name(name);
            return this;
        }

        public Builder description(String description) {
            specBuilder.description(description);
            return this;
        }

        public Builder stringParameter(String name) {
            paramsBuilder.addProperty(name, JsonStringSchema.builder().build());
            hasParams = true;
            return this;
        }

        public Builder stringParameter(String name, String description) {
            paramsBuilder.addProperty(name, JsonStringSchema.builder().description(description).build());
            hasParams = true;
            return this;
        }

        public Builder integerParameter(String name, String description) {
            paramsBuilder.addProperty(name, JsonIntegerSchema.builder().description(description).build());
            hasParams = true;
            return this;
        }

        public Builder numberParameter(String name, String description) {
            paramsBuilder.addProperty(name, JsonNumberSchema.builder().description(description).build());
            hasParams = true;
            return this;
        }

        public Builder booleanParameter(String name, String description) {
            paramsBuilder.addProperty(name, JsonBooleanSchema.builder().description(description).build());
            hasParams = true;
            return this;
        }

        public Builder executor(ToolExecutor executor) {
            this.executor = executor;
            return this;
        }

        public Tool build() {
            Objects.requireNonNull(executor, "executor");
            if (hasParams) {
                specBuilder.parameters(paramsBuilder.build());
            }
            return new Tool(specBuilder.build(), executor);
        }
    }
}
