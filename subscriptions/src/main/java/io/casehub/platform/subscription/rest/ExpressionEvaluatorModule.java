package io.casehub.platform.subscription.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.casehub.platform.api.expression.ExpressionEvaluator;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExpressionEvaluatorModule implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper mapper) {
        var module = new SimpleModule();
        module.addDeserializer(ExpressionEvaluator.class,
                new ExpressionEvaluatorDeserializer());
        module.addSerializer(ExpressionEvaluator.class,
                new ExpressionEvaluatorSerializer());
        mapper.registerModule(module);
    }
}
