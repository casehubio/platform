package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.McpDomain;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Query;

/** Domain with a slash in its name, used to verify tool-name normalisation. */
@McpDomain(value = "nested/child", app = "test-app")
@GraphQLApi
@ApplicationScoped
public class NestedDomainQueryResolver {

    @Query
    @Description("Ping the nested domain")
    public String ping() {
        return "pong";
    }
}
