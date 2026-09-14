package io.casehub.platform.preferences.editor.quarkus;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.platform.api.path.Path;
import io.casehub.platform.api.preferences.PreferenceProvider;
import io.casehub.platform.api.preferences.PreferenceQuery;
import io.casehub.platform.api.preferences.PreferenceRecord;
import io.casehub.platform.api.preferences.PreferenceSchemaDescriptor;
import io.casehub.platform.api.preferences.PreferenceSchemaRegistry;
import io.casehub.platform.api.preferences.PreferenceStore;
import io.casehub.platform.api.preferences.Preferences;
import io.casehub.platform.api.preferences.SettingsScope;
import io.casehub.platform.preferences.editor.PreferenceApi;
import io.casehub.platform.preferences.editor.PreferenceInput;
import io.casehub.platform.preferences.editor.PreferenceValidator;
import io.casehub.platform.preferences.editor.ResolvedPreferencesResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class PreferenceService implements PreferenceApi {

    private final PreferenceStore store;
    private final PreferenceProvider provider;
    private final PreferenceSchemaRegistry schemaRegistry;
    private final PreferenceValidator validator;
    private final CurrentPrincipal principal;

    @Inject
    public PreferenceService(PreferenceStore store, PreferenceProvider provider,
                             PreferenceSchemaRegistry schemaRegistry, PreferenceValidator validator,
                             CurrentPrincipal principal) {
        this.store = store;
        this.provider = provider;
        this.schemaRegistry = schemaRegistry;
        this.validator = validator;
        this.principal = principal;
    }

    @Override
    public void set(String scope, PreferenceInput input) {
        Path scopePath = parseScopePath(scope);
        String qualifiedName = input.namespace() + "." + input.name();
        Optional<PreferenceSchemaDescriptor> descriptor = schemaRegistry.resolve(qualifiedName);
        if (descriptor.isPresent()) {
            List<String> violations = validator.validate(descriptor.get(), input.value());
            if (!violations.isEmpty()) {
                throw new BadRequestException("Validation failed: " + String.join(", ", violations));
            }
        }
        store.set(principal.tenancyId(), scopePath, input.namespace(), input.name(), input.subKey(), input.value());
    }

    @Override
    public void delete(String scope, String namespace, String name, String subKey) {
        if (name == null || name.isBlank()) throw new BadRequestException("name is required for single-delete");
        if (namespace == null || namespace.isBlank()) throw new BadRequestException("namespace is required for single-delete");
        Path scopePath = parseScopePath(scope);
        store.delete(principal.tenancyId(), scopePath, namespace, name, subKey != null ? subKey : "");
    }

    @Override
    public void deleteNamespace(String scope, String namespace) {
        if (namespace == null || namespace.isBlank()) throw new BadRequestException("namespace is required");
        Path scopePath = parseScopePath(scope);
        store.deleteAll(principal.tenancyId(), scopePath, namespace);
    }

    @Override
    public List<PreferenceRecord> list(String scope) {
        Path scopePath = (scope == null || scope.isBlank()) ? null : parseScopePath(scope);
        return store.list(new PreferenceQuery(principal.tenancyId(), scopePath, null));
    }

    @Override
    public ResolvedPreferencesResponse resolved(String scope) {
        Path scopePath = parseScopePath(scope);
        Preferences resolved = provider.resolve(SettingsScope.of(principal.tenancyId(), scopePath));
        Map<String, String> values = new HashMap<>();
        resolved.asMap().forEach((k, v) -> values.put(k, String.valueOf(v)));
        return new ResolvedPreferencesResponse(scope != null ? scope : "", values);
    }

    private static Path parseScopePath(String scopeParam) {
        if (scopeParam == null || scopeParam.isBlank()) return Path.root();
        return Path.of(scopeParam.split("/"));
    }
}
