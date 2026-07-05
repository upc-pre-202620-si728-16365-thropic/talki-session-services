package com.thropic.talki.livecoach.infrastructure.gemini;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Emite tokens efímeros de la Gemini Live API (endpoint {@code v1alpha/auth_tokens}).
 *
 * <p>El token se restringe con {@code bidiGenerateContentSetup}: el modelo, el
 * system prompt (según el modo de sesión) y las transcripciones de entrada/salida
 * quedan fijados del lado servidor, de modo que el navegador no puede alterarlos.
 * Así la GEMINI_API_KEY nunca viaja al cliente y el prompt no es manipulable.
 */
@Service
public class GeminiTokenService {

    private static final Logger log = LoggerFactory.getLogger(GeminiTokenService.class);

    private final RestClient rest;
    private final String apiKey;
    private final String model;
    private final int tokenTtlSeconds;
    private final int sessionTtlSeconds;

    public GeminiTokenService(
            @Value("${gemini.api-key:}") String apiKey,
            @Value("${gemini.model:gemini-2.5-flash-native-audio-latest}") String model,
            @Value("${gemini.token-ttl-seconds:1800}") int tokenTtlSeconds,
            @Value("${gemini.session-ttl-seconds:120}") int sessionTtlSeconds,
            @Value("${gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        // El SDK de navegador (@google/genai) y la Live API usan el id sin el prefijo "models/".
        this.model = model.startsWith("models/") ? model.substring("models/".length()) : model;
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.sessionTtlSeconds = sessionTtlSeconds;
        this.rest = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** {@code true} si hay GEMINI_API_KEY y por tanto se puede emitir tokens. */
    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /**
     * Crea un token efímero de un solo uso ligado al system prompt indicado.
     *
     * @throws GeminiNotConfiguredException si falta la API key
     * @throws GeminiTokenException         si Gemini rechaza o falla la petición
     */
    public EphemeralToken mint(String systemPrompt) {
        if (!isConfigured()) {
            throw new GeminiNotConfiguredException();
        }

        Instant now = Instant.now();
        Instant tokenExpire = now.plusSeconds(tokenTtlSeconds);
        Instant newSessionExpire = now.plusSeconds(sessionTtlSeconds);

        // El token queda restringido al setup de la sesión Live: modelo, system
        // prompt (según el modo) y transcripciones quedan fijados del lado servidor.
        // Nota: el modelo aquí lleva el prefijo "models/".
        Map<String, Object> setup = Map.of(
                "model", "models/" + model,
                "generationConfig", Map.of("responseModalities", List.of("AUDIO")),
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemPrompt))
                ),
                "inputAudioTranscription", Map.of(),
                "outputAudioTranscription", Map.of()
        );

        Map<String, Object> body = Map.of(
                "uses", 1,
                "expireTime", tokenExpire.toString(),
                "newSessionExpireTime", newSessionExpire.toString(),
                "bidiGenerateContentSetup", setup
        );

        try {
            Map<?, ?> resp = rest.post()
                    .uri(uri -> uri.path("/v1alpha/auth_tokens").queryParam("key", apiKey).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            Object name = resp == null ? null : resp.get("name");
            if (name == null) {
                throw new GeminiTokenException("Gemini devolvió una respuesta sin campo 'name'");
            }
            log.info("[live-coach-service] Ephemeral token emitido (model={}, ttl={}s)", model, tokenTtlSeconds);
            return new EphemeralToken(name.toString(), model, tokenExpire);

        } catch (RestClientResponseException e) {
            log.error("[live-coach-service] Gemini rechazó el token ({}): {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new GeminiTokenException("Gemini rechazó la creación del token: " + e.getStatusText());
        } catch (RestClientException e) {
            log.error("[live-coach-service] Error de red contra Gemini: {}", e.getMessage());
            throw new GeminiTokenException("No se pudo contactar la Gemini API: " + e.getMessage());
        }
    }
}
