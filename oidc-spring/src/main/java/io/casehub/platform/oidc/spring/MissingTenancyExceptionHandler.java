package io.casehub.platform.oidc.spring;

import io.casehub.platform.api.identity.MissingTenancyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class MissingTenancyExceptionHandler {

    @ExceptionHandler(MissingTenancyException.class)
    public ResponseEntity<String> handle(MissingTenancyException ex) {
        String body = "{\"error\":\"missing_tenancy\","
                + "\"message\":\"No tenancy identifier found — checked JWT claims\","
                + "\"actorId\":\"" + ex.actorId().replace("\"", "\\\"") + "\"}";
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
