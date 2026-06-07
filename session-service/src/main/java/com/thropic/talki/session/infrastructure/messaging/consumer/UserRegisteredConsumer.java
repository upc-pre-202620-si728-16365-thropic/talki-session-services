package com.thropic.talki.session.infrastructure.messaging.consumer;

import com.thropic.talki.session.domain.event.UserRegisteredEvent;
import com.thropic.talki.session.domain.model.entities.AppUser;
import com.thropic.talki.session.infrastructure.messaging.config.RabbitMQConfig;
import com.thropic.talki.session.infrastructure.persistence.jpa.repositories.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumidor de {@code user.registered}: mantiene la proyección local
 * {@link AppUser} dentro del bounded context Practice Session, habilitando que
 * {@code SessionContextFacade.fromLocalUserId} resuelva al usuario sin llamadas
 * síncronas a identity-service (database-per-service + Saga por coreografía).
 *
 * Idempotente: ante una entrega at-least-once duplicada, actualiza la
 * proyección existente en lugar de insertar un registro nuevo.
 */
@Component
public class UserRegisteredConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserRegisteredConsumer.class);

    private final AppUserRepository appUserRepository;

    public UserRegisteredConsumer(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    @Transactional
    public void handle(UserRegisteredEvent event) {
        if (event.getUserId() == null) {
            log.warn("[session-service] user.registered sin userId — descartando");
            return;
        }
        appUserRepository.findById(event.getUserId())
                .ifPresentOrElse(
                        existing -> {
                            existing.updateProfile(event.getEmail(), event.getUsername(), event.getAcademicSegment());
                            appUserRepository.save(existing);
                            log.info("[session-service] Proyección AppUser actualizada — userId={}", event.getUserId());
                        },
                        () -> {
                            appUserRepository.save(new AppUser(
                                    event.getUserId(),
                                    event.getEmail(),
                                    event.getUsername(),
                                    event.getAcademicSegment()));
                            log.info("[session-service] Proyección AppUser creada — userId={} email={}",
                                    event.getUserId(), event.getEmail());
                        });
    }
}
