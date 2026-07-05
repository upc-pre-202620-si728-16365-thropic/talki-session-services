package com.thropic.talki.livecoach.infrastructure.gemini;

/** Se lanza cuando Gemini rechaza o falla la creación del token efímero. */
public class GeminiTokenException extends RuntimeException {
    public GeminiTokenException(String message) {
        super(message);
    }
}
