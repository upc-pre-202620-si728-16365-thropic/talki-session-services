package com.thropic.talki.session.application.exception;

/** La sesión no existe para el ownerId solicitado (HTTP 404). */
public class SessionNotFoundException extends RuntimeException {

    public SessionNotFoundException(Long sessionId) {
        super("Session not found: " + sessionId);
    }
}
