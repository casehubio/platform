package io.casehub.platform.generator;

import java.util.ArrayList;
import java.util.List;

public record DomainScanResult(
        String domainName,
        String spiInterfaceFqcn,
        String spiInterfaceSimple,
        List<ResolvedOperation> operations
) {
    public static DomainScanResult of(String domainName, String spiInterfaceFqcn, String spiInterfaceSimple) {
        return new DomainScanResult(domainName, spiInterfaceFqcn, spiInterfaceSimple, new ArrayList<>());
    }
}
