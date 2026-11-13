package com.thropic.talki.session.interfaces.rest;

import com.thropic.talki.session.application.exception.AuthenticationRequiredException;
import com.thropic.talki.session.application.exception.SessionAccessDeniedException;
import com.thropic.talki.session.application.exception.SessionNotFoundException;
import com.thropic.talki.session.infrastructure.security.SecurityAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manejo de errores acotado a seguridad (SB1-02-B): solo las excepciones de
 * propiedad de sesión y autenticación. NO captura IllegalState ni
 * IllegalArgument (transiciones de estado del agregado: alcance de SB1-03).
 * Los cuerpos no exponen título, estado ni feedback de la sesión.
 */
@RestControllerAdvice
public class SessionSecurityExceptionHandler {

    private final SecurityAuditService auditService;

    public SessionSecurityExceptionHandler(SecurityAuditService auditService) {
        this.auditService = auditService;
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(SessionNotFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Not Found", request);
    }

    @ExceptionHandler(SessionAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> accessDenied(SessionAccessDeniedException e, HttpServletRequest request) {
        // QAS-SEC-01: auditoría del 403 con el userId del token (nunca el token).
        auditService.denied(e.getRequesterId(), request.getRequestURI(), request.getMethod(), 403);
        return build(HttpStatus.FORBIDDEN, "Forbidden", request);
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<Map<String, Object>> authRequired(AuthenticationRequiredException e, HttpServletRequest request) {
        auditService.denied(null, request.getRequestURI(), request.getMethod(), 401);
        return build(HttpStatus.UNAUTHORIZED, "Unauthorized", request);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String error, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", error);
        body.put("path", request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
