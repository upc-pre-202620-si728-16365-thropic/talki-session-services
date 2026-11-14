package com.thropic.talki.session.interfaces.rest;

import com.thropic.talki.session.domain.model.entities.AppUser;
import com.thropic.talki.session.infrastructure.persistence.jpa.repositories.AppUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SB1-03 — ciclo de vida de la sesión por HTTP: crear (DRAFT), iniciar
 * (RECORDING), finalizar (PROCESSING) y rechazo controlado (409) de las
 * transiciones inválidas, sin alterar el estado vigente.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "jwt.secret=test-secret-para-sb1-03-de-32-bytes-minimo!!!")
@Transactional
class SessionLifecycleControllerTest {

    private static final SecretKey KEY = Keys.hmacShaKeyFor(
            "test-secret-para-sb1-03-de-32-bytes-minimo!!!".getBytes(StandardCharsets.UTF_8));
    private static final Long OWNER = 10L;
    private static final Long OTHER = 11L;

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository appUserRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void seedUsers() {
        appUserRepository.save(new AppUser(OWNER, "owner@talki.pe", "owner", "ciclos_1_5"));
        appUserRepository.save(new AppUser(OTHER, "other@talki.pe", "other", "ciclos_6_10"));
    }

    @Test
    void fullLifecycle_draftToRecordingToProcessing() throws Exception {
        Long id = createSession();

        mockMvc.perform(post("/v1/sessions/" + id + "/start").header("Authorization", bearer(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECORDING"));

        mockMvc.perform(post("/v1/sessions/" + id + "/finalize").header("Authorization", bearer(OWNER)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.finalizedAt").exists());
    }

    @Test
    void finalizeFromDraft_returns409AndKeepsDraft() throws Exception {
        Long id = createSession();

        mockMvc.perform(post("/v1/sessions/" + id + "/finalize").header("Authorization", bearer(OWNER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_SESSION_TRANSITION"))
                .andExpect(jsonPath("$.action").value("finalize"))
                .andExpect(jsonPath("$.currentStatus").value("DRAFT"));

        mockMvc.perform(get("/v1/sessions/" + id).header("Authorization", bearer(OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void startTwice_returns409() throws Exception {
        Long id = createSession();
        mockMvc.perform(post("/v1/sessions/" + id + "/start").header("Authorization", bearer(OWNER)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/sessions/" + id + "/start").header("Authorization", bearer(OWNER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.action").value("start"))
                .andExpect(jsonPath("$.currentStatus").value("RECORDING"));
    }

    @Test
    void otherUserCannotStartSession() throws Exception {
        Long id = createSession();
        mockMvc.perform(post("/v1/sessions/" + id + "/start").header("Authorization", bearer(OTHER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/v1/sessions/" + id).header("Authorization", bearer(OWNER)))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void startWithoutToken_returns401() throws Exception {
        Long id = createSession();
        mockMvc.perform(post("/v1/sessions/" + id + "/start")).andExpect(status().isUnauthorized());
    }

    private Long createSession() throws Exception {
        String body = mockMvc.perform(post("/v1/sessions")
                        .header("Authorization", bearer(OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ensayo de sustentación\",\"sessionType\":\"PITCH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = mapper.readTree(body);
        return node.get("id").asLong();
    }

    private static String bearer(Long userId) {
        return "Bearer " + Jwts.builder()
                .setSubject(userId + "@talki.pe")
                .claim("userId", userId.toString())
                .setExpiration(new Date(System.currentTimeMillis() + 600_000))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }
}
