package io.casehub.yaml.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import io.casehub.yaml.core.module.YamlModuleFile;

public class YamlCoreJacksonModule extends SimpleModule {

    public YamlCoreJacksonModule() {
        super("yaml-core");
        addDeserializer(io.casehub.yaml.core.module.ParameterType.class,
                        new com.fasterxml.jackson.databind.deser.std.StdDeserializer<io.casehub.yaml.core.module.ParameterType>(
                                io.casehub.yaml.core.module.ParameterType.class) {
                            @Override
                            public io.casehub.yaml.core.module.ParameterType deserialize(
                                    com.fasterxml.jackson.core.JsonParser p,
                                    com.fasterxml.jackson.databind.DeserializationContext ctxt)
                                    throws java.io.IOException {
                                return io.casehub.yaml.core.module.ParameterType.valueOf(
                                        p.getText().toUpperCase(java.util.Locale.ROOT));
                            }
                        });
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.setMixInAnnotations(YamlModuleFile.class, YamlModuleFileMixin.class);
        context.setMixInAnnotations(YamlModuleFile.YamlModuleHeader.class,
                                    YamlModuleHeaderMixin.class);
        context.setMixInAnnotations(io.casehub.yaml.core.module.YamlModuleParameter.class,
                                    YamlModuleParameterMixin.class);}
}
