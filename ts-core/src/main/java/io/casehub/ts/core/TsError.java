package io.casehub.ts.core;

public record TsError(String message, String file, int line, int column) {}
