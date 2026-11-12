package com.thropic.talki.session.infrastructure.messaging.producer;

import com.thropic.talki.session.infrastructure.messaging.config.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecurityAuditPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RabbitTemplate brokenTemplate;

    @Test
    @SuppressWarnings("unchecked")
    void denied_shouldPublishEventWithoutTokenOrSessionData() {
        new SecurityAuditPublisher(rabbitTemplate).publishDenied(7L, "/v1/sessions/3", "GET", 403);

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.EXCHANGE),
                eq(SecurityAuditPublisher.ROUTING_KEY), payload.capture());

        Map<String, Object> event = payload.getValue();
        assertThat(event)
                .containsEntry("event", "security.access.denied")
                .containsEntry("userId", 7L)
                .containsEntry("path", "/v1/sessions/3")
                .containsEntry("method", "GET")
                .containsEntry("result", 403)
                .containsKey("timestamp");
        // QAS-SEC-01: sin token ni datos de la sesión
        assertThat(event.toString()).doesNotContain("Bearer").doesNotContain("sessionId");
    }

    @Test
    void whenBrokerUnavailable_shouldNotThrow() {
        doThrow(new RuntimeException("connection refused"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        assertThatCode(() -> new SecurityAuditPublisher(rabbitTemplate).publishDenied(null, "/v1/sessions", "GET", 401))
                .doesNotThrowAnyException();
    }
}
