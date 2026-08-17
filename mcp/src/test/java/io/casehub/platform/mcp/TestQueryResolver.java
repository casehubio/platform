package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.McpDomain;
import io.smallrye.graphql.api.Subscription;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

@McpDomain("test")
@GraphQLApi
@ApplicationScoped
public class TestQueryResolver {

    @Query
    @Description("Echo the input back")
    public String echo(@Name("message") @Description("The message to echo") String message) {
        return message;
    }

    @Query
    @Description("Return a greeting")
    public String hello() {
        return "Hello from CaseHub";
    }

    @Subscription
    @Description("Case state changes")
    public Multi<String> caseLifecycle(@Name("caseId") String caseId) {
        return Multi.createFrom().empty();
    }

    public String notExposed() {
        return "should not be invocable";
    }
}
