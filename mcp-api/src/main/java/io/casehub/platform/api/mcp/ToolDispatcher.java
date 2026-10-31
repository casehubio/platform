package io.casehub.platform.api.mcp;

import java.util.Map;

@FunctionalInterface
public interface ToolDispatcher {
    Object dispatch(String domain, String operation, Map<String, Object> params) throws Exception;
}
