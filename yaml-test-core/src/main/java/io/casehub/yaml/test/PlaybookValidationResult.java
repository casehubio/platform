package io.casehub.yaml.test;

import io.casehub.yaml.core.playbook.PlaybookFrontMatter;

import java.util.List;

public record PlaybookValidationResult(
        boolean valid,
        List<String> errors,
        PlaybookFrontMatter frontMatter) {

    public PlaybookValidationResult {
        errors = errors != null ? List.copyOf(errors) : List.of();
    }
}
