package org.institutonata.suprihub.notification.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Registro de cada notificação tentada (sem o conteúdo, para não duplicar dados pessoais). */
@Entity
@Table(name = "notification")
public class NotificationRecord {

    public enum Status { SENT, FAILED, SKIPPED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id")
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageChannel.ChannelType channel;

    @Column(nullable = false)
    private String template;

    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    private String error;

    @Column(name = "source_event")
    private UUID sourceEvent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected NotificationRecord() {
    }

    public NotificationRecord(Long recipientId, MessageChannel.ChannelType channel, String template, String subject,
            Status status, String error, UUID sourceEvent, Instant createdAt) {
        this.recipientId = recipientId;
        this.channel = channel;
        this.template = template;
        this.subject = subject;
        this.status = status;
        this.error = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        this.sourceEvent = sourceEvent;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getRecipientId() {
        return recipientId;
    }

    public MessageChannel.ChannelType getChannel() {
        return channel;
    }

    public String getTemplate() {
        return template;
    }

    public String getSubject() {
        return subject;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
