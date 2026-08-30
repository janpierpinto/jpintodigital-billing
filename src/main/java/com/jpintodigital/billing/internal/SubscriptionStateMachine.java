package com.jpintodigital.billing.internal;

import com.jpintodigital.billing.api.SubscriptionStatus;
import com.jpintodigital.billing.spi.PaymentProvider.ProviderEventType;
import com.jpintodigital.billing.spi.PaymentProvider.ProviderSubscriptionStatus;

/**
 * Coração da lib: estado-alvo da assinatura. Nunca aplica delta — a partir de um
 * evento ou do estado absoluto do provedor, decide para onde a assinatura vai.
 * Pura, sem I/O.
 */
final class SubscriptionStateMachine {

    private SubscriptionStateMachine() {
    }

    /** Reação a um webhook. {@code null} = não mexe. */
    static SubscriptionStatus onProviderEvent(SubscriptionStatus current, ProviderEventType type) {
        return switch (type) {
            case PAYMENT_CONFIRMED -> SubscriptionStatus.ACTIVE;
            case PAYMENT_OVERDUE -> current.isTerminal() ? null : SubscriptionStatus.PAST_DUE;
            case PAYMENT_REFUNDED_OR_CHARGEBACK, SUBSCRIPTION_CANCELED -> SubscriptionStatus.CANCELED;
            case OTHER -> null;
        };
    }

    /** Reconciliação: computa do estado absoluto do provedor. {@code null} = não mexe. */
    static SubscriptionStatus fromProviderState(ProviderSubscriptionStatus providerStatus, boolean pastDueWithinGrace) {
        return switch (providerStatus) {
            case ACTIVE -> SubscriptionStatus.ACTIVE;
            case OVERDUE -> pastDueWithinGrace ? SubscriptionStatus.PAST_DUE : SubscriptionStatus.EXPIRED;
            case CANCELED -> SubscriptionStatus.CANCELED;
            case INACTIVE -> SubscriptionStatus.EXPIRED;
            case UNKNOWN -> null;
        };
    }

    /** Trial que estourou sem provider anexado. */
    static boolean trialExpired(SubscriptionStatus current, boolean hasProvider, boolean trialEnded) {
        return current == SubscriptionStatus.TRIALING && !hasProvider && trialEnded;
    }
}
