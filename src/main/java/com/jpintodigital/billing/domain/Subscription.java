package com.jpintodigital.billing.domain;

import com.jpintodigital.billing.api.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Uma assinatura por tenant. */
@Entity
@Table(name = "billing_subscriptions")
public class Subscription {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "plan_code", nullable = false, length = 60)
    private String planCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SubscriptionStatus status;

    @Column(nullable = false, length = 20)
    private String provider = "none";

    @Column(name = "provider_customer_id", length = 80)
    private String providerCustomerId;

    @Column(name = "provider_subscription_id", length = 80)
    private String providerSubscriptionId;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "trial_end")
    private Instant trialEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Version
    private long version;

    protected Subscription() {
    }

    public Subscription(UUID tenantId, String planCode, SubscriptionStatus status, Instant trialEnd) {
        this.tenantId = tenantId;
        this.planCode = planCode;
        this.status = status;
        this.trialEnd = trialEnd;
    }

    public void transitionTo(SubscriptionStatus target, Instant currentPeriodEnd) {
        this.status = target;
        if (currentPeriodEnd != null) {
            this.currentPeriodEnd = currentPeriodEnd;
        }
        if (target == SubscriptionStatus.CANCELED && canceledAt == null) {
            this.canceledAt = Instant.now();
        }
        this.updatedAt = Instant.now();
    }

    public void attachProvider(String provider, String customerId, String subscriptionId) {
        this.provider = provider;
        this.providerCustomerId = customerId;
        this.providerSubscriptionId = subscriptionId;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getPlanCode() {
        return planCode;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderCustomerId() {
        return providerCustomerId;
    }

    public String getProviderSubscriptionId() {
        return providerSubscriptionId;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public Instant getTrialEnd() {
        return trialEnd;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }
}
