package com.thropic.talki.livecoach.interfaces.rest.dto;

/**
 * Respuesta de {@code POST /v1/coach/live-token}: todo lo que el navegador
 * necesita para abrir la sesión de Gemini Live con {@code @google/genai}.
 *
 * @param token             token efímero de un solo uso (se usa como apiKey en el cliente)
 * @param model             modelo Live al que conectar
 * @param expiresAt         instante ISO-8601 en que el token deja de ser válido
 * @param mode              modo de sesión solicitado
 * @param maxDurationMinutes duración máxima sugerida para el modo
 */
public record LiveTokenResponse(
        String token,
        String model,
        String expiresAt,
        String mode,
        int maxDurationMinutes
) {}
