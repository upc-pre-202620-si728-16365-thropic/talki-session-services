package com.thropic.talki.session.application.services;

import com.thropic.talki.session.application.exception.SessionAccessDeniedException;
import com.thropic.talki.session.application.exception.SessionNotFoundException;
import com.thropic.talki.session.domain.model.entities.AppUser;
import com.thropic.talki.session.domain.model.entities.Session;
import com.thropic.talki.session.infrastructure.persistence.jpa.repositories.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionAccessServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @InjectMocks
    private SessionAccessService accessService;

    private Session sessionOfA;

    @BeforeEach
    void setUp() {
        AppUser userA = new AppUser(1L, "a@talki.pe", "userA", "ciclos_6_10");
        sessionOfA = new Session("Sesión de A", "PITCH", userA);
    }

    @Test
    void whenOwnerMatches_shouldReturnSession() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(sessionOfA));
        assertThat(accessService.assertOwner(1L, sessionOfA.getUser().getId())).isSameAs(sessionOfA);
    }

    @Test
    void whenSessionMissing_shouldThrowNotFound() {
        when(sessionRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> accessService.assertOwner(99L, 1L))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void whenOtherOwner_shouldThrowAccessDenied() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(sessionOfA));
        assertThatThrownBy(() -> accessService.assertOwner(1L, 2L))
                .isInstanceOf(SessionAccessDeniedException.class);
    }
}
