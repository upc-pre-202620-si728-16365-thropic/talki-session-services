package com.thropic.talki.session.application.services;

import com.thropic.talki.session.application.exception.SessionAccessDeniedException;
import com.thropic.talki.session.application.exception.SessionNotFoundException;
import com.thropic.talki.session.domain.model.entities.Session;
import com.thropic.talki.session.infrastructure.persistence.jpa.repositories.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de propiedad de sesión (SB1-02-B): solo el dueño puede leer,
 * finalizar o añadir feedback. Punto único de decisión antes de delegar
 * a los services (no cambia sus firmas).
 */
@Service
public class SessionAccessService {

    private final SessionRepository sessionRepository;

    public SessionAccessService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * Verifica que la sesión exista y pertenezca a ownerId; lanza
     * SessionNotFoundException (404) o SessionAccessDeniedException (403).
     */
    @Transactional(readOnly = true)
    public Session assertOwner(Long sessionId, Long ownerId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (!session.getUser().getId().equals(ownerId)) {
            throw new SessionAccessDeniedException(sessionId, ownerId);
        }
        return session;
    }
}
