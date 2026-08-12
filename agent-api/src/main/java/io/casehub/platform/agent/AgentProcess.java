package io.casehub.platform.agent;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

/**
 * Handle to a running agent process. Returned by {@link AgentRuntime#spawn}.
 * Callers must close the process on all paths (use try-with-resources).
 */
public interface AgentProcess extends AutoCloseable {

    OutputStream stdin();

    InputStream stdout();

    InputStream stderr();

    CompletableFuture<Integer> exitCode();

    void destroy();

    void destroyForcibly();
}
