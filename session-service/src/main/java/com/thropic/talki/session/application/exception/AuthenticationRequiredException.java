package com.thropic.talki.session.application.exception;

/**
 * No hay identidad autenticada en la request (HTTP 401). El filtro
 * JwtAuthenticationFilter ya garantiza 401 aguas arriba; esto es la
 * defensa del controller ante una configuración rota.
 */
public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException() {
        super("Authentication required");
    }
}
