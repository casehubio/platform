package io.casehub.ts.core;

import java.util.List;

public record TsEvalResult(String json, List<TsError> errors) {

    public boolean success() {
        return errors.isEmpty();
    }
}
