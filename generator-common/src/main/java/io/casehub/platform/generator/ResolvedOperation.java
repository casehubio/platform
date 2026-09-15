package io.casehub.platform.generator;

import com.palantir.javapoet.TypeName;

import java.util.List;
import java.util.Set;

public record ResolvedOperation(
        String methodName,
        String returnTypeStr,
        TypeName returnTypeName,
        List<ResolvedParam> params,
        Set<String> typeImports,
        String declaringClassFqcn,
        String declaringClassSimple,
        OperationType type,
        String description,
        String restMethodOverride,
        String restPathOverride,
        int restStatusOverride,
        List<String> rolesAllowed,
        boolean paginated,
        String totalCountMethod
) {}
