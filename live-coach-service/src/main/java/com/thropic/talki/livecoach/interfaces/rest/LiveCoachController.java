package com.thropic.talki.livecoach.interfaces.rest;

import com.thropic.talki.livecoach.application.services.LiveCoachOrchestrator;
import com.thropic.talki.livecoach.domain.event.SessionLiveFinalizedEvent;
import com.thropic.talki.livecoach.infrastructure.gemini.GeminiNotConfiguredException;
import com.thropic.talki.livecoach.infrastructure.gemini.GeminiTokenException;
import com.thropic.talki.livecoach.infrastructure.security.JwtAuthenticationFilter;
import com.thropic.talki.livecoach.interfaces.rest.dto.LiveTokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/v1/coach")
public class LiveCoachController {

    private final LiveCoachOrchestrator orchestrator;

    public LiveCoachController(LiveCoachOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping("/modes")
    public ResponseEntity<Map<String, Object>> getModes() {
        return ResponseEntity.ok(Map.of(
                "modes", java.util.List.of("quick_practice", "interview", "thesis_defense", "scenario"),
                "description", "Modos disponibles para sesiones con Gemini Live"
        ));
    }

    /**
     * Emite un token efímero de Gemini Live para el modo indicado. El navegador
     * lo usa como credencial de un solo uso para abrir el WebSocket con Gemini.
     */
    @PostMapping("/live-token")
    public ResponseEntity<?> liveToken(
            @RequestParam String mode,
            @RequestParam(required = false) String scenarioId) {
        try {
            LiveTokenResponse response = orchestrator.createLiveSession(mode, scenarioId);
            return ResponseEntity.ok(response);
        } catch (GeminiNotConfiguredException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", e.getMessage()));
        } catch (GeminiTokenException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * SB1-02-B: el userId viene SIEMPRE del token (el query param se eliminó);
     * la identidad del cliente ya no se acepta para atribuir la finalización.
     *
     * DEPENDENCIA (SB1-03, Alejandro): la verificación de PERTENENCIA de la
     * sesión ({sessionId} es de A) queda a cargo del GET
     * /internal/v1/sessions/{id}/access de session-service; aquí no se
     * implementa (el servicio no tiene acceso al schema sessions).
     */
    @PostMapping("/{sessionId}/finalize")
    public ResponseEntity<SessionLiveFinalizedEvent> finalize(
            @PathVariable String sessionId,
            HttpServletRequest request,
            @RequestParam String mode,
            @RequestParam(required = false) String scenarioId,
            @RequestParam(required = false, defaultValue = "") String transcriptGemini,
            @RequestParam(defaultValue = "0") int wordsPerMinute,
            @RequestParam(defaultValue = "0.0") double silenceRatio,
            @RequestParam(defaultValue = "0.0") double volumeRmsAvg,
            @RequestParam(defaultValue = "300") int durationSeconds,
            @RequestParam(defaultValue = "ciclos_6_10") String academicSegment) {

        SessionLiveFinalizedEvent event = orchestrator.finalizeAndPublish(
                sessionId, tokenUserId(request), mode, scenarioId,
                transcriptGemini, wordsPerMinute, silenceRatio, volumeRmsAvg,
                durationSeconds, academicSegment
        );

        return ResponseEntity.accepted().body(event);
    }

    /** El filtro garantiza el userId en toda ruta protegida; defensa ante config rota. */
    private static String tokenUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtAuthenticationFilter.USER_ID_ATTRIBUTE);
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return userId.toString();
    }
}
