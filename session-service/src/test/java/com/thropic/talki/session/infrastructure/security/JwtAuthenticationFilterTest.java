package com.thropic.talki.session.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thropic.talki.session.application.acl.SessionContextFacade;
import com.thropic.talki.session.domain.model.valueobjects.SessionUserContext;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verificación local del JWT de identity-service (HS256, claim userId,
 * expiración) y del 401 con cuerpo JSON controlado. QAS-SEC-01: cada
 * rechazo audita sin token ni datos de sesión.
 */
class JwtAuthenticationFilterTest {

    private static final SecretKey KEY = Keys.hmacShaKeyFor(
            "test-secret-de-32-bytes-minimo-para-hs256!!".getBytes(StandardCharsets.UTF_8));
    private static final SecretKey OTHER_KEY = Keys.hmacShaKeyFor(
            "otro-secreto-de-32-bytes-minimo-hs256!!!".getBytes(StandardCharsets.UTF_8));

    private final SessionContextFacade facade = mock(SessionContextFacade.class);
    private final SecurityAuditService audit = mock(SecurityAuditService.class);
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ProbeController())
            .addFilters(new JwtAuthenticationFilter(facade, audit, new ObjectMapper(), new String(KEY.getEncoded(), StandardCharsets.UTF_8)))
            .build();

    @BeforeEach
    void stubFacade() {
        when(facade.fromJwtClaims(anyMap()))
                .thenReturn(new SessionUserContext(7L, "a@talki.pe", "userA", "ciclos_6_10"));
    }

    @Test
    void withoutToken_shouldReturn401Json() throws Exception {
        mockMvc.perform(get("/v1/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/v1/anything"));
    }

    @Test
    void withExpiredToken_shouldReturn401() throws Exception {
        String token = token("7", new Date(System.currentTimeMillis() - 60_000));
        mockMvc.perform(get("/v1/anything").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withBadSignature_shouldReturn401() throws Exception {
        String token = Jwts.builder()
                .setSubject("a@talki.pe")
                .claim("userId", "7")
                .setExpiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(OTHER_KEY, SignatureAlgorithm.HS256)
                .compact();
        mockMvc.perform(get("/v1/anything").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withoutUserIdClaim_shouldReturn401() throws Exception {
        String token = Jwts.builder()
                .setSubject("a@talki.pe")
                .setExpiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
        mockMvc.perform(get("/v1/anything").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withValidToken_shouldPropagateSessionUserContext() throws Exception {
        String token = token("7", new Date(System.currentTimeMillis() + 600_000));
        mockMvc.perform(get("/v1/anything").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(ProbeController.lastUser.get().userId()).isEqualTo(7L);
    }

    @Test
    void publicPaths_shouldNotRequireToken() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private String token(String userId, Date expiration) {
        return Jwts.builder()
                .setSubject("a@talki.pe")
                .claim("userId", userId)
                .setExpiration(expiration)
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Controller sonda solo para ejercitar el filtro en standalone. */
    @RestController
    static class ProbeController {
        static final ThreadLocal<SessionUserContext> lastUser = new ThreadLocal<>();

        @GetMapping("/v1/anything")
        public String anything(HttpServletRequest request) {
            lastUser.set((SessionUserContext) request.getAttribute(JwtAuthenticationFilter.USER_CONTEXT_ATTRIBUTE));
            return "ok";
        }

        @GetMapping({"/swagger-ui/index.html", "/v3/api-docs", "/actuator/health"})
        public String docs() {
            return "ok";
        }
    }
}
