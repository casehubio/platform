package io.casehub.ts.core;

import dev.tsj.compiler.backend.jvm.TsjEvaluator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class TsjTsExecutor implements TsExecutor {

    private final TsjEvaluator evaluator = new TsjEvaluator();

    @Override
    public TsEvalResult evaluate(String tsSource) {
        try {
            var tmpFile = Files.createTempFile("tsj-eval-", ".ts");
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
        TsjEvaluator.Result result = evaluator.evaluate(tsFile);
        if (result.success()) {
            return new TsEvalResult(result.stdout(), List.of());
        } else {
            String errorMsg = result.stderr() != null ? result.stderr() : "TSJ evaluation failed";
            return new TsEvalResult(null,
                    List.of(new TsError(errorMsg, tsFile.toString(), 0, 0)));
        }
    }
}
