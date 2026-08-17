package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.McpDomain;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;

@McpDomain("test")
@GraphQLApi
@ApplicationScoped
public class TestMutationResolver {

    @Mutation
    @Description("Store a value")
    public String store(String key, String value) {
        return key + "=" + value;
    }

    @Mutation
    @Description("Create from structured input")
    public String create(@Name("input") TestInput input) {
        return input.name() + ":" + input.count();
    }

}
