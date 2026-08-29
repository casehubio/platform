package io.casehub.platform.mcp;

import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.ModelEnricher;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

@McpDomain("test")
@ApplicationScoped
public class TestModelEnricher implements ModelEnricher {

    @Override
    public String summary() { return "Test domain — echo messages, store values, create items"; }

    @Override
    public Map<String, Object> state() { return Map.of("itemCount", 3); }
}
