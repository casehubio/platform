package io.casehub.platform.graphql;

import org.eclipse.microprofile.graphql.Input;

@Input("PageInput")
public record PageInput(Integer offset, Integer limit, String cursor) {
}
