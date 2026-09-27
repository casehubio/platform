package io.casehub.platform.api.process;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Default {@link ProcessExecutor} backed by {@link ProcessBuilder}. */
public class DefaultProcessExecutor implements ProcessExecutor {

    private final List<CommandPattern> allowList;

    public DefaultProcessExecutor() { this(null); }

    public DefaultProcessExecutor(List<CommandPattern> allowList) {
        this.allowList = allowList != null ? List.copyOf(allowList) : null;
    }

    @Override
    public ProcessResult execute(String... command) {
        return execute(ProcessCommand.of(command));
    }

    @Override
    public ProcessResult execute(ProcessCommand command) {
        if (allowList != null) {
            boolean allowed = false;
            for (CommandPattern pattern : allowList) {
                if (pattern.matches(command.command())) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new CommandNotAllowedException(command.command());
            }
        }

        var pb = new ProcessBuilder(command.command());
        if (command.workingDir() != null) {
            pb.directory(new File(command.workingDir()));
        }
        pb.redirectErrorStream(command.mergeStderr());

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new ProcessExecutionException(
                    "Failed to start process: " + String.join(" ", command.command()), e);
        }

        if (command.stdin() != null) {
            try (var os = process.getOutputStream()) {
                os.write(command.stdin());
                os.flush();
            } catch (IOException e) {
                process.destroyForcibly();
                throw new ProcessExecutionException("Failed to write stdin", e);
            }
        } else {
            try {process.getOutputStream().close();} catch (IOException ignored) {}
        }

        Long maxBytes     = command.maxOutputBytes();
        var  stdoutFuture = readAsync(process.getInputStream(), maxBytes);
        var  stderrFuture = command.mergeStderr() ? CompletableFuture.completedFuture("") : readAsync(process.getErrorStream(), maxBytes);

        try {
            boolean completed;
            if (command.timeout() != null) {
                completed = process.waitFor(command.timeout().toMillis(), TimeUnit.MILLISECONDS);
            } else {
                process.waitFor();
                completed = true;
            }

            if (!completed) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                String stdout = stdoutFuture.getNow("").trim();
                String stderr = stderrFuture.getNow("").trim();
                return new ProcessResult(-1, stdout, stderr, true);
            }

            String stdout = stdoutFuture.get().trim();
            String stderr = stderrFuture.get().trim();
            return new ProcessResult(process.exitValue(), stdout, stderr, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new ProcessExecutionException("Process interrupted", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ProcessExecutionException pex) {
                throw pex;
            }
            throw new ProcessExecutionException(
                    "Error reading process output: " + String.join(" ", command.command()), e);
        }
    }

    private static CompletableFuture<String> readAsync(InputStream stream, Long maxBytes) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (maxBytes == null || maxBytes <= 0) {
                    return new String(stream.readAllBytes());
                }
                return readBounded(stream, maxBytes);
            } catch (IOException e) {
                return "";
            }
        });
    }

    private static String readBounded(InputStream stream, long maxBytes) throws IOException {
        var    buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk  = new byte[8192];
        long   total  = 0;
        int    read;
        while ((read = stream.read(chunk)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new ProcessExecutionException(
                        "Process output exceeded limit of " + maxBytes + " bytes");
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toString();
    }

}
