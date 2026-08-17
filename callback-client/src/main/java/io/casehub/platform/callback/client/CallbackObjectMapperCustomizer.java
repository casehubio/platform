package io.casehub.platform.callback.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.casehub.platform.api.preferences.MapPreferences;
import io.casehub.platform.api.preferences.Preferences;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

@Singleton
public class CallbackObjectMapperCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(final ObjectMapper mapper) {
        final var module = new SimpleModule("callback-types");
        module.addAbstractTypeMapping(Preferences.class, MapPreferences.class);
        mapper.registerModule(module);
    }
}
