package com.thropic.talki.session.infrastructure.security;

import com.thropic.talki.session.infrastructure.messaging.producer.SecurityAuditPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Auditoría de seguridad (QAS-SEC-01): por cada acceso denegado (401/403)
 * emite una línea de log estructurada (userId si existe, ruta, método,
 * resultado, timestamp — nunca el token ni datos de la sesión) y publica
 * el evento security.access.denied a RabbitMQ.
 */
@Component
public class SecurityAuditService {

    private static final Logger auditLog = LoggerFactory.getLogger("SECURITY_AUDIT");

    private final SecurityAuditPublisher publisher;

    public SecurityAuditService(SecurityAuditPublisher publisher) {
        this.publisher = publisher;
    }

    public void denied(Long userId, String path, String method, int status) {
        auditLog.warn("{\"event\":\"security.access.denied\",\"userId\":{},\"path\":\"{}\",\"method\":\"{}\",\"result\":{},\"timestamp\":\"{}\"}",
                userId, path, method, status, Instant.now().toString());
        publisher.publishDenied(userId, path, method, status);
    }
}
