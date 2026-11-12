package com.thropic.talki.livecoach.infrastructure.messaging.producer;

import com.thropic.talki.livecoach.infrastructure.messaging.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Copia mínima del SecurityAuditPublisher de session-service (QAS-SEC-01):
 * publica security.access.denied en talki.events, sin token ni datos de
 * sesión; degrada a log si el broker no está disponible.
 */
@Component
public class SecurityAuditPublisher {

    public static final String ROUTING_KEY = "security.access.denied";

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public SecurityAuditPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishDenied(Long userId, String path, String method, int status) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event", ROUTING_KEY);
            event.put("userId", userId);
            event.put("path", path);
            event.put("method", method);
            event.put("result", status);
            event.put("timestamp", Instant.now().toString());
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, ROUTING_KEY, event);
        } catch (Exception e) {
            log.warn("security.access.denied no publicado (broker no disponible): {}", e.getMessage());
        }
    }
}
