package io.casehub.platform.api.process;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessExecutorTest {

    private ProcessExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new DefaultProcessExecutor();
    }

    @Test
    void executeSimpleCommand() {
        var result = executor.execute("echo", "hello");
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("hello");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void capturesStdout() {
        var result = executor.execute("printf", "line1\nline2");
        assertThat(result.stdout()).isEqualTo("line1\nline2");
    }

    @Test
    void capturesStderr() {
        var result = executor.execute("sh", "-c", "echo err >&2");
        assertThat(result.stderr()).isEqualTo("err");
    }

    @Test
    void nonZeroExitCode() {
        var result = executor.execute("sh", "-c", "exit 42");
        assertThat(result.exitCode()).isEqualTo(42);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void withWorkingDirectory() {
        var result = executor.execute(
                ProcessCommand.of("pwd").workingDir("/tmp"));
        assertThat(result.stdout()).startsWith("/");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void withTimeout() {
        var result = executor.execute(
                ProcessCommand.of("sleep", "10").timeout(Duration.ofMillis(200)));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isTimedOut()).isTrue();
    }

    @Test
    void commandNotFound() {
        assertThatThrownBy(() -> executor.execute("nonexistent-command-xyz"))
                .isInstanceOf(ProcessExecutionException.class);
    }

    @Test
    void processCommandBuildsCorrectly() {
        var cmd = ProcessCommand.of("tmux", "new-session", "-d")
                .workingDir("/workspace")
                .timeout(Duration.ofSeconds(30));

        assertThat(cmd.command()).containsExactly("tmux", "new-session", "-d");
        assertThat(cmd.workingDir()).isEqualTo("/workspace");
        assertThat(cmd.timeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void processCommandDefaultsNullWorkingDirAndTimeout() {
        var cmd = ProcessCommand.of("echo", "test");
        assertThat(cmd.workingDir()).isNull();
        assertThat(cmd.timeout()).isNull();
    }

    @Test
    void processResultRecordFields() {
        var result = new ProcessResult(0, "out", "err", false);
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("out");
        assertThat(result.stderr()).isEqualTo("err");
        assertThat(result.isTimedOut()).isFalse();
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void processResultTimedOutIsNotSuccess() {
        var result = new ProcessResult(-1, "", "", true);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isTimedOut()).isTrue();
    }

    @Test
    void executeWithMergedStdoutStderr() {
        var result = executor.execute(
                ProcessCommand.of("sh", "-c", "echo out; echo err >&2").mergeStderr(true));
        assertThat(result.stdout()).contains("out");
        assertThat(result.stdout()).contains("err");
        assertThat(result.stderr()).isEmpty();
    }

    @Test
    void executeCheckSuccessOrThrow() {
        var result = executor.execute("echo", "ok");
        assertThat(result.successOrThrow().stdout()).isEqualTo("ok");
    }

    @Test
    void executeCheckSuccessOrThrowOnFailure() {
        var result = executor.execute("sh", "-c", "echo fail >&2; exit 1");
        assertThatThrownBy(result::successOrThrow)
                .isInstanceOf(ProcessExecutionException.class)
                .hasMessageContaining("fail");
    }

    @Test
    void processCommandStdinField() {
        byte[] input = "{\"key\":\"value\"}".getBytes();
        var    cmd   = ProcessCommand.of("cat").stdin(input);
        assertThat(cmd.stdin()).isEqualTo(input);
        assertThat(cmd.command()).containsExactly("cat");
    }

    @Test
    void processCommandStdinDefaultsToNull() {
        var cmd = ProcessCommand.of("echo", "test");
        assertThat(cmd.stdin()).isNull();
    }

    @Test
    void processCommandStdinDefensiveCopy() {
        byte[] input = "original".getBytes();
        var    cmd   = ProcessCommand.of("cat").stdin(input);
        input[0] = 'X';
        assertThat(cmd.stdin()).isEqualTo("original".getBytes());
    }

    @Test
    void processCommandMemoryLimitField() {
        var cmd = ProcessCommand.of("echo").memoryLimit(256 * 1024 * 1024L);
        assertThat(cmd.memoryLimitBytes()).isEqualTo(256 * 1024 * 1024L);
    }

    @Test
    void processCommandMemoryLimitDefaultsToNull() {
        var cmd = ProcessCommand.of("echo");
        assertThat(cmd.memoryLimitBytes()).isNull();
    }

    @Test
    void processCommandAllowedPathsField() {
        var cmd = ProcessCommand.of("python3").allowedPaths(java.util.List.of("/opt/scripts", "/tmp"));
        assertThat(cmd.allowedPaths()).containsExactly("/opt/scripts", "/tmp");
    }

    @Test
    void processCommandAllowedPathsDefaultsToNull() {
        var cmd = ProcessCommand.of("python3");
        assertThat(cmd.allowedPaths()).isNull();
    }

    @Test
    void executePipesStdinToProcess() {
        var cmd    = ProcessCommand.of("cat").stdin("hello from stdin".getBytes());
        var result = executor.execute(cmd);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stdout()).isEqualTo("hello from stdin");
    }

    @Test
    void executeWithoutStdinWorksNormally() {
        var cmd    = ProcessCommand.of("echo", "no stdin");
        var result = executor.execute(cmd);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stdout()).isEqualTo("no stdin");
    }

    @Test
    void allowListBlocksUnknownCommand() {
        var restricted = new DefaultProcessExecutor(java.util.List.of(
                new CommandPattern(java.util.List.of("echo"), CommandPattern.MatchMode.PREFIX)));
        assertThatThrownBy(() -> restricted.execute("ls"))
                .isInstanceOf(CommandNotAllowedException.class)
                .hasMessageContaining("ls");
    }

    @Test
    void allowListPermitsMatchingCommand() {
        var restricted = new DefaultProcessExecutor(java.util.List.of(
                new CommandPattern(java.util.List.of("echo"), CommandPattern.MatchMode.PREFIX)));
        var result = restricted.execute("echo", "allowed");
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.stdout()).isEqualTo("allowed");
    }

    @Test
    void nullAllowListPermitsEverything() {
        var unrestricted = new DefaultProcessExecutor(null);
        var result       = unrestricted.execute("echo", "anything");
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void maxOutputBytesEnforcedOnStdout() {
        var cmd = ProcessCommand.of("sh", "-c", "printf 'aaaaaaaaaa'")
                                .maxOutputBytes(5);
        assertThatThrownBy(() -> executor.execute(cmd))
                .isInstanceOf(ProcessExecutionException.class)
                .hasMessageContaining("exceeded limit of 5 bytes");
    }

    @Test
    void maxOutputBytesWithinLimit() {
        var cmd = ProcessCommand.of("echo", "hi")
                                .maxOutputBytes(1024);
        var result = executor.execute(cmd);
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("hi");
    }

    @Test
    void maxOutputBytesNullMeansUnlimited() {
        var result = executor.execute(ProcessCommand.of("echo", "hello"));
        assertThat(result.stdout()).isEqualTo("hello");
    }

}
