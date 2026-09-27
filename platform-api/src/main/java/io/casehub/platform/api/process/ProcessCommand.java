package io.casehub.platform.api.process;

import java.time.Duration;
import java.util.List;

/** Immutable command descriptor with optional working directory, timeout, and stderr merge. */
public final class ProcessCommand {

    private final List<String> command;
    private final String workingDir;
    private final Duration timeout;
    private final boolean mergeStderr;
    private final byte[] stdin;
    private final Long memoryLimitBytes;
    private final List<String> allowedPaths;

    private ProcessCommand(List<String> command, String workingDir, Duration timeout,
                           boolean mergeStderr, byte[] stdin, Long memoryLimitBytes,
                           List<String> allowedPaths) {
        this.command = List.copyOf(command);
        this.workingDir = workingDir;
        this.timeout = timeout;
        this.mergeStderr = mergeStderr;
        this.stdin = stdin != null ? stdin.clone() : null;
        this.memoryLimitBytes = memoryLimitBytes;
        this.allowedPaths = allowedPaths != null ? List.copyOf(allowedPaths) : null;
    }

    public static ProcessCommand of(String... command) {
        return new ProcessCommand(List.of(command), null, null, false, null, null, null);
    }

    public ProcessCommand workingDir(String workingDir) {
        return new ProcessCommand(command, workingDir, timeout, mergeStderr, stdin, memoryLimitBytes, allowedPaths);
    }

    public ProcessCommand timeout(Duration timeout) {
        return new ProcessCommand(command, workingDir, timeout, mergeStderr, stdin, memoryLimitBytes, allowedPaths);
    }

    public ProcessCommand mergeStderr(boolean merge) {
        return new ProcessCommand(command, workingDir, timeout, merge, stdin, memoryLimitBytes, allowedPaths);
    }

    public ProcessCommand stdin(byte[] input) {
        return new ProcessCommand(command, workingDir, timeout, mergeStderr, input, memoryLimitBytes, allowedPaths);
    }

    public ProcessCommand memoryLimit(long bytes) {
        return new ProcessCommand(command, workingDir, timeout, mergeStderr, stdin, bytes, allowedPaths);
    }

    public ProcessCommand allowedPaths(List<String> paths) {
        return new ProcessCommand(command, workingDir, timeout, mergeStderr, stdin, memoryLimitBytes, paths);
    }

    public List<String> command() { return command; }
    public String workingDir() { return workingDir; }
    public Duration timeout() { return timeout; }
    public boolean mergeStderr() { return mergeStderr; }
    public byte[] stdin() { return stdin != null ? stdin.clone() : null; }
    public Long memoryLimitBytes() { return memoryLimitBytes; }
    public List<String> allowedPaths() { return allowedPaths; }
}
