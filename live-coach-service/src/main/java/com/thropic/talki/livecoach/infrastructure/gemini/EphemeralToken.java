package com.thropic.talki.livecoach.infrastructure.gemini;

import java.time.Instant;

/**
 * Token efímero de un solo uso emitido por la Gemini Live API. El navegador lo
 * usa como credencial para abrir el WebSocket bidireccional con Gemini, de modo
 * que la GEMINI_API_KEY real nunca sale del servidor.
 */
public record EphemeralToken(String token, String model, Instant expireTime) {}
