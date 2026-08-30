package io.casehub.ts.core;

import java.nio.file.Path;

public interface TsExecutor {

    TsEvalResult evaluate(String tsSource);

    TsEvalResult evaluate(Path tsFile);
}
