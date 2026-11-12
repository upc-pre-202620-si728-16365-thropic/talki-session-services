package com.thropic.talki.livecoach.interfaces.rest;

import com.thropic.talki.livecoach.application.services.LiveCoachOrchestrator;
import com.thropic.talki.livecoach.domain.event.SessionLiveFinalizedEvent;
import com.thropic.talki.livecoach.infrastructure.messaging.producer.SecurityAuditPublisher;
import com.thropic.talki.livecoach.infrastructure.security.JwtAuthenticationFilter;
import com.thropic.talki.livecoach.infrastructure.security.SecurityAuditService;
import com.thropic.talki.livecoach.interfaces.rest.dto.LiveTokenResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SB1-02-B en live-coach: /finalize toma el userId del token (ignora el
 * query param, que se eliminó); /modes y /live-token exigen token válido
 * sin ligarse a usuario.
 */
@WebMvcTest(LiveCoachController.class)
@Import({JwtAuthenticationFilter.class, SecurityAuditService.class, SecurityAuditPublisher.class})
@TestPropertySource(properties = "jwt.secret=test-secret-para-sb1-02b-de-32-bytes-minimo!!")
class LiveCoachControllerAuthorizationTest {

    private static final SecretKey KEY = Keys.hmacShaKeyFor(
            "test-secret-para-sb1-02b-de-32-bytes-minimo!!".getBytes(StandardCharsets.UTF_8));
    private static final SecretKey OTHER_KEY = Keys.hmacShaKeyFor(
            "secreto-distinto-de-test-sb1-02b-32-bytes!!".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LiveCoachOrchestrator orchestrator;

    @MockBean
    private RabbitTemplate rabbitTemplate; // la auditoría publica security.access.denied; el slice no levanta broker

    @Test
    void finalize_usesUserIdFromToken_ignoresClientValue() throws Exception {
        mockMvc.perform(post("/v1/coach/session-123/finalize")
                        .header("Authorization", bearer(7L))
                        .param("userId", "999") // restos del contrato viejo: ya no existe el query param
                        .param("mode", "interview")
                        .param("wordsPerMinute", "120"))
                .andExpect(status().isAccepted());

        ArgumentCaptor<String> userId = ArgumentCaptor.forClass(String.class);
        verify(orchestrator).finalizeAndPublish(eq("session-123"), userId.capture(),
                eq("interview"), isNull(), eq(""), eq(120), eq(0.0), eq(0.0), eq(300), eq("ciclos_6_10"));
        assertThat(userId.getValue()).isEqualTo("7");
    }

    @Test
    void modes_withValidToken_shouldReturn200() throws Exception {
        mockMvc.perform(get("/v1/coach/modes").header("Authorization", bearer(7L)))
                .andExpect(status().isOk());
    }

    @Test
    void modes_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(get("/v1/coach/modes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void liveToken_withValidToken_shouldNotBeUserBound() throws Exception {
        when(orchestrator.createLiveSession(anyString(), any())).thenReturn(
                new LiveTokenResponse("ephemeral", "gemini-2.5-flash-native-audio-latest",
                        "2026-01-01T00:30:00Z", "interview", 15));
        mockMvc.perform(post("/v1/coach/live-token")
                        .header("Authorization", bearer(7L))
                        .param("mode", "interview"))
                .andExpect(status().isOk());
    }

    @Test
    void finalize_withoutToken_shouldReturn401() throws Exception {
        mockMvc.perform(post("/v1/coach/session-123/finalize").param("mode", "interview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void finalize_withBadSignature_shouldReturn401() throws Exception {
        String forged = Jwts.builder()
                .setSubject("a@talki.pe")
                .claim("userId", "7")
                .setExpiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(OTHER_KEY, SignatureAlgorithm.HS256)
                .compact();
        mockMvc.perform(post("/v1/coach/session-123/finalize")
                        .header("Authorization", "Bearer " + forged)
                        .param("mode", "interview"))
                .andExpect(status().isUnauthorized());
    }

    private static String bearer(Long userId) {
        return "Bearer " + Jwts.builder()
                .setSubject("a@talki.pe")
                .claim("userId", userId.toString())
                .setExpiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }
}
