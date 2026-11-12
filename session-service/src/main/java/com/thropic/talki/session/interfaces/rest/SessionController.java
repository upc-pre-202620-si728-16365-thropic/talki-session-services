package com.thropic.talki.session.interfaces.rest;

import com.thropic.talki.session.application.exception.AuthenticationRequiredException;
import com.thropic.talki.session.application.services.SessionAccessService;
import com.thropic.talki.session.application.services.SessionCommandService;
import com.thropic.talki.session.application.services.SessionQueryService;
import com.thropic.talki.session.domain.model.entities.Feedback;
import com.thropic.talki.session.domain.model.entities.Session;
import com.thropic.talki.session.domain.model.valueobjects.SessionUserContext;
import com.thropic.talki.session.infrastructure.security.JwtAuthenticationFilter;
import com.thropic.talki.session.interfaces.rest.dto.CreateSessionRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/sessions")
public class SessionController {

    private final SessionCommandService commandService;
    private final SessionQueryService queryService;
    private final SessionAccessService accessService;

    public SessionController(SessionCommandService commandService,
                              SessionQueryService queryService,
                              SessionAccessService accessService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.accessService = accessService;
    }

    /**
     * SB1-02-B: el propietario es el usuario del token; cualquier userId
     * del body se ignora (la identidad del token prevalece).
     */
    @PostMapping
    public ResponseEntity<Session> create(@RequestBody CreateSessionRequest req,
                                          HttpServletRequest request) {
        Session session = commandService.create(currentUser(request).userId(), req.title(), req.sessionType());
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Session> getById(@PathVariable Long id, HttpServletRequest request) {
        accessService.assertOwner(id, currentUser(request).userId());
        return queryService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** SB1-02-B: lista acotada al usuario del token (sin @RequestParam userId). */
    @GetMapping
    public ResponseEntity<List<Session>> getByUser(HttpServletRequest request) {
        return ResponseEntity.ok(queryService.findByUserId(currentUser(request).userId()));
    }

    @PostMapping("/{id}/finalize")
    public ResponseEntity<Session> finalize(@PathVariable Long id, HttpServletRequest request) {
        accessService.assertOwner(id, currentUser(request).userId());
        return ResponseEntity.accepted().body(commandService.finalize(id));
    }

    @PostMapping("/{id}/feedbacks")
    public ResponseEntity<Feedback> addFeedback(@PathVariable Long id,
                                                 @RequestBody Map<String, String> body,
                                                 HttpServletRequest request) {
        accessService.assertOwner(id, currentUser(request).userId());
        Feedback feedback = commandService.addFeedback(
                id,
                body.getOrDefault("feedbackType", "general"),
                body.getOrDefault("content", "")
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(feedback);
    }

    @GetMapping("/{id}/feedbacks")
    public ResponseEntity<List<Feedback>> listFeedbacks(@PathVariable Long id, HttpServletRequest request) {
        accessService.assertOwner(id, currentUser(request).userId());
        return ResponseEntity.ok(queryService.findFeedbacksBySessionId(id));
    }

    /** El filtro garantiza el contexto en toda ruta protegida; defensa ante config rota. */
    private static SessionUserContext currentUser(HttpServletRequest request) {
        SessionUserContext user = (SessionUserContext)
                request.getAttribute(JwtAuthenticationFilter.USER_CONTEXT_ATTRIBUTE);
        if (user == null) {
            throw new AuthenticationRequiredException();
        }
        return user;
    }
}
