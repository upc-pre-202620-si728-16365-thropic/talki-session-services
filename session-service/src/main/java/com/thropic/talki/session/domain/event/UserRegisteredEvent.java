package com.thropic.talki.session.domain.event;

/**
 * Contrato local del evento {@code user.registered} emitido por identity-service.
 * Se declara dentro de este bounded context (no se comparte la clase upstream)
 * para preservar la autonomía del servicio: el Anti-Corruption Layer traduce
 * este payload a la proyección local {@code AppUser}.
 */
public class UserRegisteredEvent {

    private Long userId;
    private String email;
    private String username;
    private String academicSegment;

    public UserRegisteredEvent() {
    }

    public UserRegisteredEvent(Long userId, String email, String username, String academicSegment) {
        this.userId = userId;
        this.email = email;
        this.username = username;
        this.academicSegment = academicSegment;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAcademicSegment() { return academicSegment; }
    public void setAcademicSegment(String academicSegment) { this.academicSegment = academicSegment; }
}
