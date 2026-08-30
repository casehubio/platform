package io.casehub.ts.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class NodeTsExecutor implements TsExecutor {

    private static final long TIMEOUT_SECONDS = 30;
    private final Path runnerScript;

    public NodeTsExecutor() {
        try {
            this.runnerScript = extractRunner();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to extract ts-runner.mjs", e);
        }
    }

    @Override
    public TsEvalResult evaluate(String tsSource) {
        try {
            var tmpFile = Files.createTempFile("ts-eval-", ".ts");
            try {
                Files.writeString(tmpFile, tsSource);
                return evaluate(tmpFile);
            } finally {
                Files.deleteIfExists(tmpFile);
            }
        } catch (IOException e) {
            return new TsEvalResult(null,
                    List.of(new TsError(e.getMessage(), "<string>", 0, 0)));
        }
    }

    @Override
    public TsEvalResult evaluate(Path tsFile) {
        try {
            var pb = new ProcessBuilder(
                    "npx", "tsx",
                    runnerScript.toAbsolutePath().toString(),
                    tsFile.toAbsolutePath().toString());
            pb.environment().put("NODE_NO_WARNINGS", "1");
            var process = pb.start();

            var stdout = readStream(process.getInputStream());
            var stderr = readStream(process.getErrorStream());

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new TsEvalResult(null,
                        List.of(new TsError("Evaluation timed out after " + TIMEOUT_SECONDS + "s",
                                tsFile.toString(), 0, 0)));
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorMsg = stderr.isBlank()
                        ? "Process exited with code " + exitCode
                        : cleanNpxWarnings(stderr);
                return new TsEvalResult(null,
                        List.of(new TsError(errorMsg, tsFile.toString(), 0, 0)));
            }

            return new TsEvalResult(stdout, List.of());
        } catch (IOException e) {
            return new TsEvalResult(null,
                    List.of(new TsError("Failed to start Node.js: " + e.getMessage()
                            + ". Ensure Node.js 20+ and npx are on PATH.",
                            tsFile.toString(), 0, 0)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TsEvalResult(null,
                    List.of(new TsError("Evaluation interrupted", tsFile.toString(), 0, 0)));
        }
    }

    private static String readStream(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    private static String cleanNpxWarnings(String stderr) {
        return stderr.lines()
                     .filter(line -> !line.startsWith("npm warn"))
                     .reduce("", (a, b) -> a.isBlank() ? b : a + "\n" + b)
                     .trim();
    }


    private static Path extractRunner() throws IOException {
        var tmpDir = Files.createTempDirectory("ts-core-");
        var runner = tmpDir.resolve("ts-runner.mjs");
        try (var in = NodeTsExecutor.class.getResourceAsStream("ts-runner.mjs")) {
            if (in == null) {
                throw new IOException("ts-runner.mjs not found on classpath");
            }
            Files.copy(in, runner);
        }
        runner.toFile().deleteOnExit();
        tmpDir.toFile().deleteOnExit();
        return runner;
    }
}
