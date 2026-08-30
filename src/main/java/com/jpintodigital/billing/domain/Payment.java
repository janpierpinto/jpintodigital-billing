package com.jpintodigital.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Espelho local de uma cobrança do provedor. */
@Entity
@Table(name = "billing_payments")
public class Payment {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_payment_id", nullable = false, unique = true, length = 80)
    private String providerPaymentId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Payment() {
    }

    public Payment(UUID subscriptionId, UUID tenantId, String providerPaymentId, long amountCents,
            String status, LocalDate dueDate, Instant paidAt) {
        this.subscriptionId = subscriptionId;
        this.tenantId = tenantId;
        this.providerPaymentId = providerPaymentId;
        this.amountCents = amountCents;
        this.status = status;
        this.dueDate = dueDate;
        this.paidAt = paidAt;
    }

    public void update(String status, Instant paidAt) {
        this.status = status;
        if (paidAt != null) {
            this.paidAt = paidAt;
        }
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
