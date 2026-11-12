package com.thropic.talki.session.infrastructure.messaging.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.thropic.talki.session.infrastructure.messaging.config.RabbitMQConfig;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publica el evento de auditoría {@code security.access.denied} (QAS-SEC-01)
 * sobre talki.events. El payload nunca incluye el token ni el sessionId.
 * La auditoría no debe romper la respuesta: si el broker no está disponible
 * se degrada a log (el log estructurado es la fuente de verdad).
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
