package io.casehub.platform.graphql;

import org.eclipse.microprofile.graphql.Type;

import java.net.URI;
import java.util.Map;

@Type("GraphQLError")
public record GraphQLError(URI type, String title, Integer status, String detail,
                           Map<String, Object> extensions) {
}
