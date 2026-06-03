package io.casehub.platform.agent;

import java.util.List;
import java.util.Map;

public sealed interface AgentMcpServer
        permits AgentMcpServer.Stdio, AgentMcpServer.Sse, AgentMcpServer.Http {

    /**
     * Stdio MCP server: launched as a subprocess by the Claude CLI.
     *
     * <p>env: MERGED over the parent process environment (not a replacement).
     * PATH and system variables are preserved. Set a key to {@code ""} to unset it.
     */
    record Stdio(String command, List<String> args, Map<String, String> env)
            implements AgentMcpServer {
        public Stdio(String command) { this(command, List.of(), Map.of()); }
        public Stdio(String command, List<String> args) { this(command, args, Map.of()); }
    }

    /**
     * SSE MCP server: legacy HTTP transport (Server-Sent Events).
     * Prefer {@link Http} for new servers.
     */
    record Sse(String url, Map<String, String> headers) implements AgentMcpServer {
        public Sse(String url) { this(url, Map.of()); }
    }

    /**
     * Streamable HTTP MCP server: current MCP transport standard.
     * Prefer this over {@link Sse} for new deployments.
     */
    record Http(String url, Map<String, String> headers) implements AgentMcpServer {
        public Http(String url) { this(url, Map.of()); }
    }
}
