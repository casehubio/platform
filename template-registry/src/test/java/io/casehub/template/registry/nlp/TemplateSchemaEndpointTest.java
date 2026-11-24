package io.casehub.template.registry.nlp;

import io.casehub.template.registry.TemplateDefinition;
import io.casehub.template.registry.TemplateMatch;
import io.casehub.template.registry.TemplateNamespace;
import io.casehub.yaml.core.resolver.VariableConstraint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateSchemaEndpointTest {

    private TemplateSchemaEndpoint endpoint;

    @BeforeEach
    void setUp() {
        var registry = new NlpTemplateRegistry(new OpenNlpTokenizer());
        var vote = TemplateDefinition.builder("vote")
            .pattern("I ${decision} this ${subject}")
            .variable("decision", VariableConstraint.enumOf("approve", "reject"))
            .variable("subject", VariableConstraint.string())
            .build();
        var templates = new LinkedHashMap<String, TemplateDefinition>();
        templates.put("vote", vote);
        registry.register(new TemplateNamespace("protocol:review", templates));
        endpoint = new TemplateSchemaEndpoint(registry);
    }

    @Test
    void listSchemas() {
        var schemas = endpoint.listSchemas("tenant1");
        assertThat(schemas).hasSize(1);
        assertThat(schemas.get(0).templateName()).isEqualTo("vote");
    }

    @Test
    void getSchema() {
        var schema = endpoint.getSchema("tenant1", "protocol:review", "vote");
        assertThat(schema).isPresent();
        assertThat(schema.get().variables()).containsKey("decision");
    }

    @Test
    void getSchemaNotFound() {
        var schema = endpoint.getSchema("tenant1", "protocol:review", "nonexistent");
        assertThat(schema).isEmpty();
    }

    @Test
    void matchTemplate() {
        var result = endpoint.matchTemplate("tenant1", "protocol:review",
            "I approve this design");
        assertThat(result).isInstanceOf(TemplateMatch.Matched.class);
    }
}
