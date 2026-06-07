package com.thropic.talki.session.infrastructure.messaging.consumer;

import com.thropic.talki.session.domain.event.UserRegisteredEvent;
import com.thropic.talki.session.domain.model.entities.AppUser;
import com.thropic.talki.session.infrastructure.persistence.jpa.repositories.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegisteredConsumerTest {

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private UserRegisteredConsumer consumer;

    @Test
    void handle_whenUserIsNew_shouldCreateLocalProjectionWithSameId() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.empty());
        UserRegisteredEvent event =
                new UserRegisteredEvent(1L, "demo@upc.edu.pe", "demoUser", "ciclos_1_5");

        consumer.handle(event);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(captor.capture());
        AppUser saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getEmail()).isEqualTo("demo@upc.edu.pe");
        assertThat(saved.getUsername()).isEqualTo("demoUser");
        assertThat(saved.getAcademicSegment()).isEqualTo("ciclos_1_5");
    }

    @Test
    void handle_whenUserAlreadyProjected_shouldUpdateInPlace_idempotent() {
        AppUser existing = new AppUser(1L, "old@upc.edu.pe", "old", "ciclos_6_10");
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(existing));
        UserRegisteredEvent event =
                new UserRegisteredEvent(1L, "new@upc.edu.pe", "newName", "ciclos_1_5");

        consumer.handle(event);

        assertThat(existing.getEmail()).isEqualTo("new@upc.edu.pe");
        assertThat(existing.getUsername()).isEqualTo("newName");
        assertThat(existing.getAcademicSegment()).isEqualTo("ciclos_1_5");
        verify(appUserRepository).save(existing);
    }

    @Test
    void handle_whenUserIdIsNull_shouldSkipWithoutPersisting() {
        consumer.handle(new UserRegisteredEvent(null, "x@upc.edu.pe", "x", "ciclos_1_5"));

        verify(appUserRepository, never()).save(any());
        verify(appUserRepository, never()).findById(any());
    }
}
