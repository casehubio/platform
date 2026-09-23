package io.casehub.platform.spring.actuator;

import io.casehub.platform.agent.AgentBackend;
import io.casehub.platform.api.model.ModelRegistry;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;

import java.util.LinkedHashMap;
import java.util.List;

class PlatformInfoContributor implements InfoContributor {

    private final ModelRegistry modelRegistry;
    private final List<AgentBackend> backends;

    PlatformInfoContributor(ModelRegistry modelRegistry, List<AgentBackend> backends) {
        this.modelRegistry = modelRegistry;
        this.backends = backends;
    }

    @Override
    public void contribute(Info.Builder builder) {
        var info = new LinkedHashMap<String, Object>();

        var pkg = getClass().getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null) {
            info.put("version", pkg.getImplementationVersion());
        }

        if (backends != null) {
            info.put("agent.backends", backends.stream().map(AgentBackend::key).toList());
        }

        if (modelRegistry != null) {
            info.put("model.registry.size", modelRegistry.all().size());
        }

        builder.withDetail("casehub.platform", info);
    }
}
