package io.casehub.platform.graphql.generator.infer;

import java.util.List;

public record MethodCallInfo(String receiverName, String methodName, List<String> args) {
}
