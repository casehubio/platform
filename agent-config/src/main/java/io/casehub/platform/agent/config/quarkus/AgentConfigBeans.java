package io.casehub.platform.agent.config.quarkus;

import io.casehub.platform.agent.config.LocalModelReconciler;
import io.casehub.platform.agent.config.ManifestCredentialResolver;
import io.casehub.platform.agent.config.ManifestLoader;
import io.casehub.platform.agent.config.ManifestProcessor;
import io.casehub.platform.agent.config.ManifestResult;
import io.casehub.platform.api.credentials.CredentialResolver;
import io.casehub.platform.api.credentials.LlmCredentialStore;
import io.casehub.platform.api.model.MutableModelRegistry;
import io.casehub.platform.llm.config.LlmConfigApi;
import io.casehub.platform.llm.config.OllamaClient;
import io.casehub.platform.llm.config.PullRequest;
import io.casehub.platform.llm.config.VendorClient;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AgentConfigBeans {

    private static final Logger LOG = Logger.getLogger(AgentConfigBeans.class);

    @Inject LlmCredentialStore credentialStore;
    @Inject MutableModelRegistry modelRegistry;
    @Inject CredentialResolver credentialResolver;
    @Inject @Any Instance<VendorClient> vendorClients;
    @Inject @Any Instance<LlmConfigApi> configApi;

    private ManifestResult result;

    void onStartup(@Observes @Priority(50) StartupEvent event) {
        var vendorReqs = buildVendorRequirements();
        var reconciler = buildReconciler();
        var resolver = new ManifestCredentialResolver(credentialResolver);
        var loader = new ManifestLoader();
        var profile = resolveProfile();

        var projectDir = Path.of(System.getProperty("user.dir"));
        var manifest = loader.load(projectDir, profile);

        var processor = new ManifestProcessor(
                credentialStore, modelRegistry, resolver, vendorReqs, reconciler);
        this.result = processor.process(manifest);

        LOG.infof("Agent config manifest processed: %d aliases, default backend: %s",
                result.aliases().size(),
                result.defaultBackendKey() != null ? result.defaultBackendKey() : "(not set)");
    }

    @Produces
    @jakarta.inject.Singleton
    public ManifestResult manifestResult() {
        return result != null ? result : ManifestResult.empty();
    }

    private Map<String, List<String>> buildVendorRequirements() {
        var reqs = new HashMap<String, List<String>>();
        for (var client : vendorClients) {
            reqs.put(client.vendorKey(), client.requiredFields());
        }
        return Map.copyOf(reqs);
    }

    private LocalModelReconciler buildReconciler() {
        return modelId -> {
            if (configApi.isResolvable()) {
                try {
                    configApi.get().pullModel(new PullRequest(modelId));
                    LOG.infof("Triggered pull for local model: %s", modelId);
                } catch (Exception e) {
                    LOG.warnf("Failed to pull local model %s: %s", modelId, e.getMessage());
                }
            } else {
                LOG.warnf("LlmConfigApi not available — cannot ensure local model: %s", modelId);
            }
        };
    }

    private String resolveProfile() {
        String profile = System.getenv("CASEHUB_AGENT_PROFILE");
        if (profile == null || profile.isBlank()) {
            profile = System.getenv("QUARKUS_PROFILE");
        }
        if (profile != null) {
            LOG.infof("Agent config profile: %s", profile);
        }
        return profile;
    }
}
