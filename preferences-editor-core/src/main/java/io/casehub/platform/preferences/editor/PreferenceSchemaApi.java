package io.casehub.platform.preferences.editor;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.preferences.PreferenceSchemaDescriptor;

import java.util.List;

@McpDomain("preference-schemas")
public interface PreferenceSchemaApi {

    @PlatformQuery("List preference schema descriptors")
    List<PreferenceSchemaDescriptor> schema(String namespace);
}
