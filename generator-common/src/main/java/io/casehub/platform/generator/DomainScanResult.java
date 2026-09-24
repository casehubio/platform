package io.casehub.platform.generator;

import java.util.ArrayList;
import java.util.List;

public record DomainScanResult(
        String domainName,
        String app,
        String summary,
        String sourceFqcn,
        String sourceSimple,
        boolean isInterface,
        String basePath,
        List<ResolvedOperation> operations
) {
    public static DomainScanResult of(String domainName, String sourceFqcn,
                                       String sourceSimple, boolean isInterface) {
        return new DomainScanResult(domainName, "", "", sourceFqcn, sourceSimple,
                                    isInterface, null, new ArrayList<>());
    }

    public static DomainScanResult of(String domainName, String sourceFqcn,
                                       String sourceSimple, boolean isInterface,
                                       String basePath) {
        return new DomainScanResult(domainName, "", "", sourceFqcn, sourceSimple,
                                    isInterface, basePath, new ArrayList<>());
    }

    public static DomainScanResult of(String domainName, String app, String summary,
                                       String sourceFqcn, String sourceSimple,
                                       boolean isInterface, String basePath) {
        return new DomainScanResult(domainName, app, summary, sourceFqcn, sourceSimple,
                                    isInterface, basePath, new ArrayList<>());
    }

    public String resolvedBasePath() {
        return basePath != null && !basePath.isEmpty() ? basePath : "/api/" + domainName;
    }
}
