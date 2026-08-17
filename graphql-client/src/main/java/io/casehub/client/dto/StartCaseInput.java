package io.casehub.client.dto;

import io.casehub.platform.graphql.scalar.Json;
import org.eclipse.microprofile.graphql.Input;

@Input("StartCaseInput")
public record StartCaseInput(String namespace, String name, String version, Json context) {
}
