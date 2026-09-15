package io.casehub.platform.generator;

import com.palantir.javapoet.TypeName;

public record ResolvedParam(
        String name,
        String typeStr,
        String typeFqcn,
        TypeName typeName,
        boolean isPathParam,
        String pathParamName,
        boolean isSimpleType,
        String restName
) {}
