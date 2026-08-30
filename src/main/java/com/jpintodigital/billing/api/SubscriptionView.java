package com.jpintodigital.billing.api;

import java.time.Instant;
import java.util.UUID;

/** Visão somente-leitura da assinatura de um tenant. */
public record SubscriptionView(
        UUID tenantId,
        String planCode,
        SubscriptionStatus status,
        Instant currentPeriodEnd,
        Instant trialEnd,
        String providerSubscriptionId) {

    public boolean grantsAccess() {
        return status.grantsAccess();
    }
}
