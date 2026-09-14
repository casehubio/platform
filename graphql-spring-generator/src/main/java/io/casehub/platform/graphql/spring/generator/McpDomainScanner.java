package io.casehub.platform.graphql.spring.generator;

import io.casehub.platform.generator.JandexTypeConverter;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.MethodParameterInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class McpDomainScanner {

    private static final DotName MCP_DOMAIN = DotName.createSimple("io.casehub.platform.api.mcp.McpDomain");
    private static final DotName PLATFORM_QUERY = DotName.createSimple("io.casehub.platform.api.mcp.PlatformQuery");
    private static final DotName PLATFORM_MUTATION = DotName.createSimple("io.casehub.platform.api.mcp.PlatformMutation");

    public List<DomainDescriptor> scan(Index index) {
        Map<String, DomainBuilder> domains = new LinkedHashMap<>();

        for (AnnotationInstance ann : index.getAnnotations(MCP_DOMAIN)) {
            if (ann.target().kind() != AnnotationTarget.Kind.CLASS) {
                continue;
            }

            ClassInfo classInfo = ann.target().asClass();
            if (!java.lang.reflect.Modifier.isInterface(classInfo.flags())) {
                continue;
            }

            String domainName = ann.value().asString();
            String spiName = classInfo.name().toString();

            List<DomainDescriptor.OperationDescriptor> ops = new ArrayList<>();

            for (MethodInfo method : classInfo.methods()) {
                AnnotationInstance queryAnn = method.annotation(PLATFORM_QUERY);
                AnnotationInstance mutAnn = method.annotation(PLATFORM_MUTATION);

                if (queryAnn != null) {
                    String desc = queryAnn.value() != null ? queryAnn.value().asString() : "";
                    ops.add(buildOperation(method, DomainDescriptor.OperationType.QUERY, desc));
                } else if (mutAnn != null) {
                    String desc = mutAnn.value() != null ? mutAnn.value().asString() : "";
                    ops.add(buildOperation(method, DomainDescriptor.OperationType.MUTATION, desc));
                }
            }

            domains.put(domainName, new DomainBuilder(domainName, spiName, ops));
        }

        return domains.values().stream()
                .map(DomainBuilder::build)
                .toList();
    }

    private DomainDescriptor.OperationDescriptor buildOperation(MethodInfo method,
                                                                  DomainDescriptor.OperationType type,
                                                                  String description) {
        List<DomainDescriptor.ParameterDescriptor> params = new ArrayList<>();
        for (MethodParameterInfo param : method.parameters()) {
            String name = param.name() != null ? param.name() : "arg" + params.size();
            params.add(new DomainDescriptor.ParameterDescriptor(
                    name, JandexTypeConverter.toTypeName(param.type())));
        }

        return new DomainDescriptor.OperationDescriptor(
                method.name(), type, description,
                JandexTypeConverter.toTypeName(method.returnType()),
                params);
    }

    private record DomainBuilder(String domainName, String spiName,
                                  List<DomainDescriptor.OperationDescriptor> ops) {
        DomainDescriptor build() {
            return new DomainDescriptor(domainName, spiName, ops);
        }
    }
}
