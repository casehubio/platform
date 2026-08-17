package io.casehub.platform.graphql;

import org.eclipse.microprofile.graphql.Type;

@Type("PageInfo")
public record PageInfo(boolean hasNext, boolean hasPrevious, Integer totalCount, String cursor) {
}
