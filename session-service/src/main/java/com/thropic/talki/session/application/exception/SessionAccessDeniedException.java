package com.thropic.talki.session.application.exception;

/**
 * El usuario autenticado no es propietario de la sesión (HTTP 403).
 * El mensaje no revela datos de la sesión (título, estado, feedback).
 */
public class SessionAccessDeniedException extends RuntimeException {

    private final Long requesterId;

    public SessionAccessDeniedException(Long sessionId, Long requesterId) {
        super("Access denied to session " + sessionId + " for user " + requesterId);
        this.requesterId = requesterId;
    }

    /** userId del token, para la auditoría de seguridad (QAS-SEC-01). */
    public Long getRequesterId() {
        return requesterId;
    }
}
