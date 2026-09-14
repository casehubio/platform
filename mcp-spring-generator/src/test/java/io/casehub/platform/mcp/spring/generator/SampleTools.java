package io.casehub.platform.mcp.spring.generator;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;

public class SampleTools {

    @Tool("List available sessions")
    public String listSessions(@ToolArg(description = "Tenant ID") String tenancyId) {
        return "";
    }

    @Tool("Create a new session")
    public String createSession(
            @ToolArg(description = "Session name") String name,
            @ToolArg(description = "Session type") String type) {
        return "";
    }

    public String helperMethod() {
        return "";
    }
}
