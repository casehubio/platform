package io.casehub.platform.rest.spring.generator;

import java.util.Map;

public record StatusBearingResult(int status, Object body) {
    public static StatusBearingResult ok(Object body) { return new StatusBearingResult(200, body); }
    public static StatusBearingResult notFound(String msg) { return new StatusBearingResult(404, Map.of("error", msg)); }
}
