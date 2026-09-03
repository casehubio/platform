package io.casehub.yaml.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import io.casehub.yaml.core.module.YamlModuleFile;

public class YamlCoreJacksonModule extends SimpleModule {

    public YamlCoreJacksonModule() {
        super("yaml-core");
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.setMixInAnnotations(YamlModuleFile.class, YamlModuleFileMixin.class);
        context.setMixInAnnotations(YamlModuleFile.YamlModuleHeader.class,
                                    YamlModuleHeaderMixin.class);
    }
}
