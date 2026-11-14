package com.thropic.talki.session.domain.model.entities;

import com.thropic.talki.session.domain.exceptions.InvalidSessionTransitionException;
import com.thropic.talki.session.domain.model.valueobjects.SessionStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sessions")
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "session_type")
    private String sessionType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    protected Session() {}

    public Session(String title, String sessionType, AppUser user) {
        this.title = title;
        this.sessionType = sessionType;
        this.user = user;
        this.status = SessionStatus.DRAFT;
        this.createdAt = LocalDateTime.now();
    }

    public void start() {
        if (this.status != SessionStatus.DRAFT)
            throw new InvalidSessionTransitionException("start", this.status, "Session must be DRAFT to start.");
        this.status = SessionStatus.RECORDING;
    }

    public void finalizeSession() {
        if (this.status != SessionStatus.RECORDING)
            throw new InvalidSessionTransitionException("finalize", this.status, "Session must be RECORDING to finalize.");
        this.status = SessionStatus.PROCESSING;
        this.finalizedAt = LocalDateTime.now();
    }

    public void markAsCompleted() { this.status = SessionStatus.COMPLETED; }
    public void markAsAnalysisPending() { this.status = SessionStatus.ANALYSIS_PENDING; }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public SessionStatus getStatus() { return status; }
    public String getSessionType() { return sessionType; }
    public AppUser getUser() { return user; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getFinalizedAt() { return finalizedAt; }
}
