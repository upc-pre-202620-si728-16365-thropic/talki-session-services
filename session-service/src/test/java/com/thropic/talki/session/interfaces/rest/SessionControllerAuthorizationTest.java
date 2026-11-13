package com.thropic.talki.session.interfaces.rest;

import com.thropic.talki.session.application.services.SessionCommandService;
import com.thropic.talki.session.domain.model.entities.AppUser;
import com.thropic.talki.session.domain.model.entities.Session;
import com.thropic.talki.session.infrastructure.persistence.jpa.repositories.AppUserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SB1-02-B — el usuario B no puede leer, listar, finalizar ni añadir
 * feedback a la sesión de A. La identidad del token prevalece sobre
 * cualquier userId enviado por el cliente. Tokens firmados con un
 * secreto de test (mismo formato HS256 + claim userId que identity-service).
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=test-secret-para-sb1-02b-de-32-bytes-minimo!!")
@Transactional
class SessionControllerAuthorizationTest {

    private static final SecretKey KEY = Keys.hmacShaKeyFor(
            "test-secret-para-sb1-02b-de-32-bytes-minimo!!".getBytes(StandardCharsets.UTF_8));
    private static final SecretKey OTHER_KEY = Keys.hmacShaKeyFor(
            "secreto-distinto-de-test-sb1-02b-32-bytes!!".getBytes(StandardCharsets.UTF_8));

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;
    private static final String TITLE_A = "Pitch secreto de A";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private SessionCommandService commandService;

    private Long sessionAId;

    @BeforeEach
    void seedUsersAndSessionOfA() {
        appUserRepository.save(new AppUser(USER_A, "a@talki.pe", "userA", "ciclos_6_10"));
        appUserRepository.save(new AppUser(USER_B, "b@talki.pe", "userB", "ciclos_6_10"));
        Session sessionOfA = commandService.create(USER_A, TITLE_A, "PITCH");
        sessionAId = sessionOfA.getId();
    }

    // ---- B no puede acceder a la sesión de A (IDOR cerrado) ----

    @Test
    void bCannotReadSessionOfA() throws Exception {
        mockMvc.perform(get("/v1/sessions/" + sessionAId).header("Authorization", bearer(USER_B)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                // sin exponer título, estado ni feedback
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    org.assertj.core.api.Assertions.assertThat(body)
                            .doesNotContain(TITLE_A)
                            .doesNotContain("DRAFT")
                            .doesNotContain("feedback");
                });
    }

    @Test
    void bListDoesNotContainSessionOfA() throws Exception {
        mockMvc.perform(get("/v1/sessions").header("Authorization", bearer(USER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(sessionAId.intValue()))));
    }

    @Test
    void bCannotFinalizeSessionOfA() throws Exception {
        mockMvc.perform(post("/v1/sessions/" + sessionAId + "/finalize").header("Authorization", bearer(USER_B)))
                .andExpect(status().isForbidden());
    }

    @Test
    void bCannotAddFeedbackToSessionOfA() throws Exception {
        mockMvc.perform(post("/v1/sessions/" + sessionAId + "/feedbacks")
                        .header("Authorization", bearer(USER_B))
                        .contentType("application/json")
                        .content("{\"feedbackType\":\"general\",\"content\":\"comentario intruso\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void bCannotReadFeedbacksOfSessionOfA() throws Exception {
        mockMvc.perform(get("/v1/sessions/" + sessionAId + "/feedbacks").header("Authorization", bearer(USER_B)))
                .andExpect(status().isForbidden());
    }

    // ---- El token manda sobre el userId del cliente ----

    @Test
    void createWithUserIdAInBodyButTokenB_createsOwnedByB() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/sessions")
                        .header("Authorization", bearer(USER_B))
                        .contentType("application/json")
                        .content("{\"title\":\"Trampa\",\"sessionType\":\"PITCH\",\"userId\":" + USER_A + "}"))
                .andExpect(status().isCreated())
                .andReturn();

        Long createdId = ((Number) com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
        mockMvc.perform(get("/v1/sessions/" + createdId).header("Authorization", bearer(USER_B)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(USER_B.intValue()))
                .andExpect(jsonPath("$.title").value("Trampa"));

        // el userId del body no creó una sesión para A
        mockMvc.perform(get("/v1/sessions").header("Authorization", bearer(USER_A)))
                .andExpect(jsonPath("$[*].id", not(hasItem(createdId.intValue()))));
    }

    // ---- Autenticación ----

    @Test
    void withoutToken_shouldReturn401OnAllSessionEndpoints() throws Exception {
        mockMvc.perform(get("/v1/sessions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/sessions/" + sessionAId)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/sessions").contentType("application/json").content("{\"title\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/sessions/" + sessionAId + "/finalize")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/sessions/" + sessionAId + "/feedbacks")).andExpect(status().isUnauthorized());
    }

    @Test
    void withExpiredToken_shouldReturn401() throws Exception {
        String expired = Jwts.builder()
                .setSubject("b@talki.pe")
                .claim("userId", USER_B.toString())
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
        mockMvc.perform(get("/v1/sessions").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void withBadSignatureToken_shouldReturn401() throws Exception {
        String forged = Jwts.builder()
                .setSubject("b@talki.pe")
                .claim("userId", USER_B.toString())
                .setExpiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(OTHER_KEY, SignatureAlgorithm.HS256)
                .compact();
        mockMvc.perform(get("/v1/sessions").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    // ---- 404 ----

    @Test
    void missingSession_shouldReturn404() throws Exception {
        mockMvc.perform(get("/v1/sessions/99999").header("Authorization", bearer(USER_A)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ---- Happy path mínimo del propietario ----

    @Test
    void ownerACanReadOwnSession() throws Exception {
        mockMvc.perform(get("/v1/sessions/" + sessionAId).header("Authorization", bearer(USER_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(TITLE_A))
                .andExpect(jsonPath("$.user.id").value(USER_A.intValue()));
    }

    /**
     * PENDIENTE (SB1-03, Alejandro): el segundo finalize lanza IllegalState →
     * hoy responde 500. Cuando la máquina de estados devuelva 409 (o el código
     * que defina SB1-03), se activa: la sesión debe seguir accesible por A y
     * el repetido NO debe mutar estado ni reponer feedback.
     */
    @Test
    @Disabled("SB1-03 (Alejandro): finalize inválido debe ser 409; mientras tanto IllegalState → 500")
    void repeatedFinalize_shouldNotMutateStateAgain() throws Exception {
        Session session = commandService.create(USER_A, "Para finalizar dos veces", "PITCH");
        session.start();
        mockMvc.perform(post("/v1/sessions/" + session.getId() + "/finalize").header("Authorization", bearer(USER_A)))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/v1/sessions/" + session.getId() + "/finalize").header("Authorization", bearer(USER_A)))
                .andExpect(status().isConflict());
    }

    private static String bearer(Long userId) {
        return "Bearer " + Jwts.builder()
                .setSubject(userId == USER_A ? "a@talki.pe" : "b@talki.pe")
                .claim("userId", userId.toString())
                .setExpiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }
}
