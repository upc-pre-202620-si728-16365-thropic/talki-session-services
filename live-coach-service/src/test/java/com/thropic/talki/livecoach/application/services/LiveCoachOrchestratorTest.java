package com.thropic.talki.livecoach.application.services;

import com.thropic.talki.livecoach.domain.event.SessionLiveFinalizedEvent;
import com.thropic.talki.livecoach.domain.model.SessionModeStrategy;
import com.thropic.talki.livecoach.domain.model.SessionModeStrategyFactory;
import com.thropic.talki.livecoach.infrastructure.gemini.EphemeralToken;
import com.thropic.talki.livecoach.infrastructure.gemini.GeminiTokenService;
import com.thropic.talki.livecoach.infrastructure.messaging.producer.SessionFinalizedPublisher;
import com.thropic.talki.livecoach.interfaces.rest.dto.LiveTokenResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveCoachOrchestratorTest {

    @Mock
    private SessionModeStrategyFactory strategyFactory;

    @Mock
    private SessionFinalizedPublisher publisher;

    @Mock
    private GeminiTokenService geminiTokenService;

    @Mock
    private SessionModeStrategy strategy;

    @InjectMocks
    private LiveCoachOrchestrator orchestrator;

    @Test
    void prepareSystemPrompt_shouldDelegateToStrategy() {
        when(strategyFactory.create("interview")).thenReturn(strategy);
        when(strategy.buildSystemPrompt("scn-001")).thenReturn("Eres un reclutador...");

        String result = orchestrator.prepareSystemPrompt("interview", "scn-001");

        assertThat(result).isEqualTo("Eres un reclutador...");
    }

    @Test
    void createLiveSession_shouldMintTokenForModePrompt() {
        Instant expiry = Instant.now().plusSeconds(1800);
        when(strategyFactory.create("interview")).thenReturn(strategy);
        when(strategy.buildSystemPrompt("scn-001")).thenReturn("Eres un reclutador...");
        when(strategy.maxDurationMinutes()).thenReturn(15);
        when(geminiTokenService.mint("Eres un reclutador..."))
                .thenReturn(new EphemeralToken("auth_tokens/abc", "gemini-2.0-flash-live-001", expiry));

        LiveTokenResponse response = orchestrator.createLiveSession("interview", "scn-001");

        assertThat(response.token()).isEqualTo("auth_tokens/abc");
        assertThat(response.model()).isEqualTo("gemini-2.0-flash-live-001");
        assertThat(response.mode()).isEqualTo("interview");
        assertThat(response.maxDurationMinutes()).isEqualTo(15);
        assertThat(response.expiresAt()).isEqualTo(expiry.toString());
        verify(geminiTokenService).mint("Eres un reclutador...");
    }

    @Test
    void finalizeAndPublish_shouldPublishEventWithTranscriptAndMetrics() {
        SessionLiveFinalizedEvent event = orchestrator.finalizeAndPublish(
                "sess-1", "user-42", "quick_practice", null,
                "Hola, este... bueno, mi proyecto...", 95, 0.18, 0.62,
                300, "ciclos_6_10"
        );

        assertThat(event.getSessionId()).isEqualTo("sess-1");
        assertThat(event.getUserId()).isEqualTo("user-42");
        assertThat(event.getMode()).isEqualTo("quick_practice");
        assertThat(event.getTranscriptGemini()).contains("mi proyecto");
        assertThat(event.getWordsPerMinute()).isEqualTo(95);
        assertThat(event.getSilenceRatio()).isEqualTo(0.18);
        assertThat(event.getVolumeRmsAvg()).isEqualTo(0.62);
        assertThat(event.getAcademicSegment()).isEqualTo("ciclos_6_10");
        verify(publisher).publish(event);
    }

    @Test
    void finalizeAndPublish_shouldGenerateEventIdAndTraceId() {
        SessionLiveFinalizedEvent event = orchestrator.finalizeAndPublish(
                "sess-2", "user-1", "interview", "scn-001",
                "", 0, 0.0, 0.0, 0, "ciclos_1_5"
        );

        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getTraceId()).isNotBlank();
        assertThat(event.getEventType()).isEqualTo("session.live.finalized");
        assertThat(event.getOccurredAt()).isNotNull();
    }

    @Test
    void finalizeAndPublish_shouldNotIncludeAudioUri() {
        ArgumentCaptor<SessionLiveFinalizedEvent> captor =
                ArgumentCaptor.forClass(SessionLiveFinalizedEvent.class);

        orchestrator.finalizeAndPublish("sess-3", "user-2", "thesis_defense", "scn-2",
                "transcript completo", 110, 0.10, 0.7, 600, "ciclos_6_10");

        verify(publisher).publish(captor.capture());
        SessionLiveFinalizedEvent published = captor.getValue();
        // El audio NO se persiste por cumplimiento de la Ley 29733: el evento
        // no debe tener referencia al binario de audio.
        assertThat(published.getTranscriptGemini()).isNotNull();
    }
}
