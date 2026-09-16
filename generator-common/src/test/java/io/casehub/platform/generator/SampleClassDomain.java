package io.casehub.platform.generator;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;
import java.util.List;

@McpDomain("test-class-domain")
public class SampleClassDomain {

    @PlatformQuery("List class items")
    public List<String> listItems(String tenancyId) {
        return List.of();
    }

    @PlatformMutation("Create class item")
    public String createItem(String name) {
        return name;
    }

    public String helperNotExposed() {
        return "not exposed";
    }
}
