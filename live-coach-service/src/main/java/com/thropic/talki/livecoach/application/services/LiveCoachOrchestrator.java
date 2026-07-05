package com.thropic.talki.livecoach.application.services;

import com.thropic.talki.livecoach.domain.event.SessionLiveFinalizedEvent;
import com.thropic.talki.livecoach.domain.model.SessionModeStrategy;
import com.thropic.talki.livecoach.domain.model.SessionModeStrategyFactory;
import com.thropic.talki.livecoach.infrastructure.gemini.EphemeralToken;
import com.thropic.talki.livecoach.infrastructure.gemini.GeminiTokenService;
import com.thropic.talki.livecoach.infrastructure.messaging.producer.SessionFinalizedPublisher;
import com.thropic.talki.livecoach.interfaces.rest.dto.LiveTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LiveCoachOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(LiveCoachOrchestrator.class);

    private final SessionModeStrategyFactory strategyFactory;
    private final SessionFinalizedPublisher publisher;
    private final GeminiTokenService geminiTokenService;

    public LiveCoachOrchestrator(SessionModeStrategyFactory strategyFactory,
                                  SessionFinalizedPublisher publisher,
                                  GeminiTokenService geminiTokenService) {
        this.strategyFactory = strategyFactory;
        this.publisher = publisher;
        this.geminiTokenService = geminiTokenService;
    }

    public String prepareSystemPrompt(String mode, String scenarioId) {
        SessionModeStrategy strategy = strategyFactory.create(mode);
        String prompt = strategy.buildSystemPrompt(scenarioId);
        log.info("[live-coach-service] System prompt prepared for mode={}", mode);
        return prompt;
    }

    /**
     * Construye el system prompt del modo, emite un token efímero de Gemini Live
     * ligado a ese prompt y devuelve al navegador todo lo necesario para conectar.
     */
    public LiveTokenResponse createLiveSession(String mode, String scenarioId) {
        SessionModeStrategy strategy = strategyFactory.create(mode);
        String prompt = strategy.buildSystemPrompt(scenarioId);
        EphemeralToken token = geminiTokenService.mint(prompt);
        log.info("[live-coach-service] Live session lista para mode={} (expira {})", mode, token.expireTime());
        return new LiveTokenResponse(
                token.token(),
                token.model(),
                token.expireTime().toString(),
                mode,
                strategy.maxDurationMinutes()
        );
    }

    public SessionLiveFinalizedEvent finalizeAndPublish(String sessionId, String userId,
                                                         String mode, String scenarioId,
                                                         String transcriptGemini,
                                                         int wordsPerMinute,
                                                         double silenceRatio,
                                                         double volumeRmsAvg,
                                                         int durationSeconds,
                                                         String academicSegment) {
        SessionLiveFinalizedEvent event = new SessionLiveFinalizedEvent(
                sessionId, userId, mode, scenarioId,
                transcriptGemini, wordsPerMinute, silenceRatio, volumeRmsAvg,
                durationSeconds, academicSegment
        );
        publisher.publish(event);
        return event;
    }
}
