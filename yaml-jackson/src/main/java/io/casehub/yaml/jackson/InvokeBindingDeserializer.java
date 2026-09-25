package io.casehub.yaml.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.casehub.yaml.core.step.InvokeBinding;
import io.casehub.yaml.core.step.StepDefinitionParser;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class InvokeBindingDeserializer extends StdDeserializer<InvokeBinding> {

    public InvokeBindingDeserializer() {
        super(InvokeBinding.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public InvokeBinding deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        Map<String, Object> map = p.getCodec().treeToValue(node, LinkedHashMap.class);
        return StepDefinitionParser.parseInvoke(map);
    }
}
