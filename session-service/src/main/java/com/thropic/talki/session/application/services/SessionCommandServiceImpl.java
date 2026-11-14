package com.thropic.talki.session.application.services;

import com.thropic.talki.session.application.acl.SessionContextFacade;
import com.thropic.talki.session.domain.model.entities.AppUser;
import com.thropic.talki.session.domain.model.entities.Feedback;
import com.thropic.talki.session.domain.model.entities.Session;
import com.thropic.talki.session.domain.model.valueobjects.SessionUserContext;
import com.thropic.talki.session.infrastructure.persistence.jpa.repositories.AppUserRepository;
import com.thropic.talki.session.infrastructure.persistence.jpa.repositories.FeedbackRepository;
import com.thropic.talki.session.infrastructure.persistence.jpa.repositories.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionCommandServiceImpl implements SessionCommandService {

    private final SessionRepository sessionRepository;
    private final AppUserRepository appUserRepository;
    private final FeedbackRepository feedbackRepository;
    private final SessionContextFacade sessionContextFacade;

    public SessionCommandServiceImpl(SessionRepository sessionRepository,
                                      AppUserRepository appUserRepository,
                                      FeedbackRepository feedbackRepository,
                                      SessionContextFacade sessionContextFacade) {
        this.sessionRepository = sessionRepository;
        this.appUserRepository = appUserRepository;
        this.feedbackRepository = feedbackRepository;
        this.sessionContextFacade = sessionContextFacade;
    }

    @Override
    @Transactional
    public Session create(Long userId, String title, String sessionType) {
        SessionUserContext context = sessionContextFacade.fromLocalUserId(userId);
        AppUser user = appUserRepository.findById(context.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "AppUser disappeared mid-transaction: " + context.userId()));
        Session session = new Session(title, sessionType, user);
        return sessionRepository.save(session);
    }

    @Override
    @Transactional
    public Session start(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.start();
        return sessionRepository.save(session);
    }

    @Override
    @Transactional
    public Session finalize(Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        session.finalizeSession();
        return sessionRepository.save(session);
    }

    @Override
    @Transactional
    public Feedback addFeedback(Long sessionId, String feedbackType, String content) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        Feedback feedback = new Feedback(session, feedbackType, content);
        return feedbackRepository.save(feedback);
    }
}
