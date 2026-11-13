package com.thropic.talki.session.infrastructure.security;

import com.thropic.talki.session.application.acl.SessionContextFacade;
import com.thropic.talki.session.domain.model.valueobjects.SessionUserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Valida localmente el JWT emitido por identity-service (mismo JWT_SECRET,
 * HS256): el API Gateway no existe todavía, así que la verificación de firma,
 * expiración y presencia del claim de usuario vive aquí. Sin token, con token
 * inválido o vencido responde 401 con cuerpo JSON controlado (QAS-SEC-01).
 *
 * El userId NO se acepta ya desde el cliente: la identidad del token
 * prevalece sobre cualquier userId enviado en body o query (SB1-02-B, IDOR).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Atributo de request donde queda el usuario autenticado para los controllers. */
    public static final String USER_CONTEXT_ATTRIBUTE = SessionUserContext.class.getName();

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final SessionContextFacade contextFacade;
    private final SecurityAuditService auditService;
    private final ObjectMapper objectMapper;
    private final Key signingKey;

    public JwtAuthenticationFilter(SessionContextFacade contextFacade,
                                   SecurityAuditService auditService,
                                   ObjectMapper objectMapper,
                                   @Value("${jwt.secret}") String secret) {
        this.contextFacade = contextFacade;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Rutas de documentación y monitoreo públicas: no exigen token. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs") || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            reject(request, response);
            return;
        }
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(header.substring("Bearer ".length()))
                    .getBody();

            Object rawUserId = claims.get("userId");
            if (rawUserId == null) {
                reject(request, response);
                return;
            }

            // ACL: el token upstream trae el userId en el claim "userId" (sub = email);
            // SessionContextFacade.fromJwtClaims espera el userId en "sub", se traduce aquí.
            Map<String, Object> aclClaims = new HashMap<>(claims);
            aclClaims.put("sub", rawUserId.toString());
            SessionUserContext user = contextFacade.fromJwtClaims(aclClaims);

            request.setAttribute(USER_CONTEXT_ATTRIBUTE, user);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT rechazado para {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            reject(request, response);
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        auditService.denied(null, request.getRequestURI(), request.getMethod(), 401);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", 401);
        body.put("error", "Unauthorized");
        body.put("path", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
