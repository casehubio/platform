package io.casehub.yaml.test;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.PlatformQuery;

@McpDomain("playbooks")
public interface PlaybookApi {

    @PlatformMutation("Run a playbook with optional dry-run simulation")
    PlaybookRunResult run(PlaybookRunRequest request);

    @PlatformQuery("Validate a playbook structure without executing")
    PlaybookValidationResult validate(String playbookPath);
}
