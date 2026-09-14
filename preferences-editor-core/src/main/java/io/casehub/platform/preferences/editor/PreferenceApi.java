package io.casehub.platform.preferences.editor;

import io.casehub.platform.api.mcp.HttpMethod;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.mcp.RestMethod;
import io.casehub.platform.api.mcp.RestPath;
import io.casehub.platform.api.preferences.PreferenceRecord;

import java.util.List;

@McpDomain("preferences")
public interface PreferenceApi {

    @PlatformMutation("Set a preference value")
    @RestMethod(HttpMethod.PUT)
    void set(String scope, PreferenceInput input);

    @PlatformMutation("Delete a single preference")
    @RestMethod(HttpMethod.DELETE)
    @RestPath("delete")
    void delete(String scope, String namespace, String name, String subKey);

    @PlatformMutation("Delete all preferences in a namespace")
    @RestMethod(HttpMethod.DELETE)
    @RestPath("delete-namespace")
    void deleteNamespace(String scope, String namespace);

    @PlatformQuery("List raw preference records")
    List<PreferenceRecord> list(String scope);

    @PlatformQuery("Get resolved preferences for a scope")
    ResolvedPreferencesResponse resolved(String scope);
}
