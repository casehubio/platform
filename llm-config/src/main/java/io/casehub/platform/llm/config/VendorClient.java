package io.casehub.platform.llm.config;

import java.util.List;
import java.util.Map;

public interface VendorClient {
    String vendorKey();
    String backendKey();
    String displayName();
    String authMethod();
    List<String> requiredFields();
    ValidationResult listModels(Map<String, String> credentials);
}
