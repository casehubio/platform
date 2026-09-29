package io.casehub.yaml.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import io.casehub.yaml.core.module.YamlModuleFile;

public class YamlCoreJacksonModule extends SimpleModule {

    public YamlCoreJacksonModule() {
        super("yaml-core");
        addDeserializer(io.casehub.yaml.plugin.api.ParameterType.class,
                        new com.fasterxml.jackson.databind.deser.std.StdDeserializer<io.casehub.yaml.plugin.api.ParameterType>(
                                io.casehub.yaml.plugin.api.ParameterType.class) {
                            @Override
                            public io.casehub.yaml.plugin.api.ParameterType deserialize(
                                    com.fasterxml.jackson.core.JsonParser p,
                                    com.fasterxml.jackson.databind.DeserializationContext ctxt)
                                    throws java.io.IOException {
                                return io.casehub.yaml.plugin.api.ParameterType.fromString(p.getText());
                            }
                        });
        addDeserializer(io.casehub.yaml.core.step.InvokeBinding.class,
                        new InvokeBindingDeserializer());
        addDeserializer(io.casehub.yaml.core.step.MatchPattern.class,
                        new MatchPatternDeserializer());
        addDeserializer(io.casehub.yaml.core.step.MatchCase.class,
                        new MatchCaseDeserializer());
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.setMixInAnnotations(YamlModuleFile.class, YamlModuleFileMixin.class);
        context.setMixInAnnotations(YamlModuleFile.YamlModuleHeader.class,
                                    YamlModuleHeaderMixin.class);
        context.setMixInAnnotations(io.casehub.yaml.core.module.YamlModuleParameter.class,
                                    YamlModuleParameterMixin.class);
        context.setMixInAnnotations(io.casehub.yaml.core.step.DeclarationFile.class,
                                    DeclarationFileMixin.class);
        context.setMixInAnnotations(io.casehub.yaml.core.module.YamlImport.class,
                                    YamlImportMixin.class);
    }
}
