package com.jpintodigital.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro de idempotência dos webhooks. {@code event_id} UNIQUE — o INSERT que
 * conflita é um replay e responde 200 na hora.
 */
@Entity
@Table(name = "billing_webhook_events")
public class WebhookEvent {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "event_id", nullable = false, unique = true, length = 120)
    private String eventId;

    @Column(name = "event_type", length = 60)
    private String eventType;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "processing_error", length = 500)
    private String processingError;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    protected WebhookEvent() {
    }

    public WebhookEvent(String provider, String eventId, String eventType, String payload) {
        this.provider = provider;
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public void markProcessed() {
        this.processedAt = Instant.now();
        this.processingError = null;
    }

    public void markFailed(String error) {
        this.processingError = error == null ? "erro" : error.substring(0, Math.min(error.length(), 500));
    }

    public UUID getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public boolean isProcessed() {
        return processedAt != null;
    }

    public String getPayload() {
        return payload;
    }
}
