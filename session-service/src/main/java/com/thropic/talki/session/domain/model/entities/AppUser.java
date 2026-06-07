package com.thropic.talki.session.domain.model.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

/**
 * Proyección local (read-model) del usuario dentro del bounded context
 * Practice Session. NO es la fuente de verdad: el dueño del usuario es
 * identity-service. Esta replica se mantiene de forma eventual-consistente
 * consumiendo el evento {@code user.registered} (Saga por coreografía), por
 * lo que su identificador (id) es el MISMO userId emitido por identity-service
 * y no se autogenera localmente — así {@code findById(userId)} en la ACL
 * resuelve el usuario sin acoplarse al esquema upstream (database-per-service).
 */
@Entity
@Table(name = "users")
// Permite serializar la proyección aunque venga como proxy lazy de Hibernate
// (Session.user es @ManyToOne(LAZY)); evita el error de Jackson al serializar
// el ByteBuddyInterceptor del proxy sin perder el lazy loading.
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class AppUser {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String username;

    @Column(name = "academic_segment")
    private String academicSegment;

    protected AppUser() {}

    /**
     * Constructor de proyección: el id proviene del evento user.registered
     * (userId de identity-service), garantizando un identificador compartido
     * entre el bounded context upstream y esta replica local.
     */
    public AppUser(Long id, String email, String username, String academicSegment) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.academicSegment = academicSegment;
    }

    public AppUser(String email, String username, String academicSegment) {
        this.email = email;
        this.username = username;
        this.academicSegment = academicSegment;
    }

    /** Actualiza los datos proyectados (idempotencia del consumidor at-least-once). */
    public void updateProfile(String email, String username, String academicSegment) {
        this.email = email;
        this.username = username;
        this.academicSegment = academicSegment;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public String getAcademicSegment() { return academicSegment; }
}
