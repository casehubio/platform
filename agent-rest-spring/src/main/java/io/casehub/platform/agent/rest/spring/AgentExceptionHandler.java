package io.casehub.platform.agent.rest.spring;

import io.casehub.platform.agent.AgentRateLimitException;
import io.casehub.platform.agent.AgentSessionLimitException;
import io.casehub.platform.agent.AgentTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class AgentExceptionHandler {

    @ExceptionHandler(AgentTimeoutException.class)
    public ResponseEntity<String> handleTimeout(AgentTimeoutException ex) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(ex.getMessage());
    }

    @ExceptionHandler(AgentSessionLimitException.class)
    public ResponseEntity<String> handleSessionLimit(AgentSessionLimitException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ex.getMessage());
    }

    @ExceptionHandler(AgentRateLimitException.class)
    public ResponseEntity<String> handleRateLimit(AgentRateLimitException ex) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After",
                String.valueOf((long) Math.ceil(ex.retryAfterMillis() / 1000.0)));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(ex.getMessage());
    }
}
