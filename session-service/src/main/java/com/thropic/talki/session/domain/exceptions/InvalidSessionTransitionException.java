package com.thropic.talki.session.domain.exceptions;

import com.thropic.talki.session.domain.model.valueobjects.SessionStatus;

/**
 * SB1-03: transición no permitida en el ciclo de vida de la sesión
 * (DRAFT → RECORDING → PROCESSING). Extiende IllegalStateException para
 * conservar el contrato del dominio; la capa REST la traduce a 409.
 */
public class InvalidSessionTransitionException extends IllegalStateException {

    private final SessionStatus currentStatus;
    private final String action;

    public InvalidSessionTransitionException(String action, SessionStatus currentStatus, String message) {
        super(message);
        this.action = action;
        this.currentStatus = currentStatus;
    }

    public SessionStatus getCurrentStatus() { return currentStatus; }

    public String getAction() { return action; }
}
