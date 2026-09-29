package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformQuery;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@McpDomain("foreign-cap-test")
@ApplicationScoped
public class ForeignCapabilityTestResolver {

    @PlatformQuery("Search documents via foreign provider")
    public String search(String query) {
        throw new ForeignCapabilityException(
                "SearchOperations", "s3-foreign",
                List.of("Upload", "Download", "Delete"),
                "Foreign S3 does not support search");
    }
}
