package com.thropic.talki.livecoach.infrastructure.gemini;

/** Se lanza cuando falta GEMINI_API_KEY y por tanto no se puede hablar con Gemini. */
public class GeminiNotConfiguredException extends RuntimeException {
    public GeminiNotConfiguredException() {
        super("GEMINI_API_KEY no está configurada en live-coach-service");
    }
}
