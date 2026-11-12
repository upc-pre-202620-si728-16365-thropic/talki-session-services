package com.thropic.talki.livecoach.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Copia mínima del filtro de session-service (SB1-02-B): valida el JWT de
 * identity-service (JWT_SECRET compartido, HS256) sin gateway. Aquí no hay
 * ACL de usuario; solo se necesita el userId del claim para trazabilidad de
 * /finalize. Los modos y el live-token exigen token válido pero no se ligan
 * a usuario.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Atributo de request con el userId del token (null nunca llega al controller). */
    public static final String USER_ID_ATTRIBUTE = "security.userId";

    private final SecurityAuditService auditService;
    private final ObjectMapper objectMapper;
    private final Key signingKey;

    public JwtAuthenticationFilter(SecurityAuditService auditService,
                                   ObjectMapper objectMapper,
                                   @Value("${jwt.secret}") String secret) {
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
            request.setAttribute(USER_ID_ATTRIBUTE, Long.valueOf(rawUserId.toString()));
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException e) {
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
