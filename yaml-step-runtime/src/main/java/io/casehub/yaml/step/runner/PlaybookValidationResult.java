package io.casehub.yaml.step.runner;

import io.casehub.yaml.core.error.YamlError;
import io.casehub.yaml.core.playbook.PlaybookFrontMatter;

import java.util.List;

public record PlaybookValidationResult(
        boolean valid,
        List<YamlError> diagnostics,
        PlaybookFrontMatter frontMatter,
        List<ValidationSuggestion> suggestions,
        LlmFixResult llmFix) {

    public PlaybookValidationResult {
        diagnostics = diagnostics != null ? List.copyOf(diagnostics) : List.of();
        suggestions = suggestions != null ? List.copyOf(suggestions) : List.of();
    }

    public PlaybookValidationResult(boolean valid, List<YamlError> diagnostics, PlaybookFrontMatter frontMatter) {
        this(valid, diagnostics, frontMatter, List.of(), null);
    }

    public List<String> errors() {
        return diagnostics.stream().map(YamlError::summary).toList();
    }
}
