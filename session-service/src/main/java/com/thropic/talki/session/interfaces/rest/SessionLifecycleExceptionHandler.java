package com.thropic.talki.session.interfaces.rest;

import com.thropic.talki.session.domain.exceptions.InvalidSessionTransitionException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SB1-03: traduce una transición inválida del ciclo de vida a 409 Conflict,
 * con el estado actual y la acción rechazada, sin exponer título ni feedback.
 * La autorización se resuelve antes (SessionSecurityExceptionHandler), así
 * que un usuario ajeno recibe 403 y nunca este cuerpo.
 */
@RestControllerAdvice
public class SessionLifecycleExceptionHandler {

    @ExceptionHandler(InvalidSessionTransitionException.class)
    public ResponseEntity<Map<String, Object>> invalidTransition(InvalidSessionTransitionException e,
                                                                 HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", "Conflict");
        body.put("code", "INVALID_SESSION_TRANSITION");
        body.put("action", e.getAction());
        body.put("currentStatus", e.getCurrentStatus() == null ? null : e.getCurrentStatus().name());
        body.put("message", e.getMessage());
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
