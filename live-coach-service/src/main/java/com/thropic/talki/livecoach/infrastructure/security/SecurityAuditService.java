package com.thropic.talki.livecoach.infrastructure.security;

import com.thropic.talki.livecoach.infrastructure.messaging.producer.SecurityAuditPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Copia mínima del SecurityAuditService de session-service (QAS-SEC-01):
 * log estructurado + evento RabbitMQ por cada 401/403, sin token ni datos
 * de sesión.
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
