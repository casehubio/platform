package io.casehub.platform.api.label;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;
import io.casehub.platform.api.path.Path;

import java.util.List;
import java.util.Optional;

@McpDomain("labels")
public interface LabelApi {

    @PlatformQuery("List immediate children at a label path. Pass empty string for top-level nodes.")
    List<LabelDescriptor> children(String parentPath);

    @PlatformQuery("Resolve a single label node by path")
    Optional<LabelDescriptor> resolve(String path);

    @PlatformQuery("List all descendants of a label path. Pass empty string for the entire tree.")
    List<LabelDescriptor> subtree(String rootPath);
}
