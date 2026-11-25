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

    /**
     * Reconciliação: computa do estado absoluto do provedor. {@code null} = não mexe.
     *
     * <p>{@code trialRunning}: assinatura em teste com o fim do teste ainda no futuro. No Asaas,
     * ACTIVE só diz que a assinatura existe e não foi cancelada — ela nasce ACTIVE já no cadastro do
     * cartão, com a primeira cobrança marcada para o fim do teste. Ler isso como "pagou" encerrava o
     * teste grátis na primeira reconciliação (bug de 2026-11-23). Durante o teste, só um pagamento
     * confirmado (webhook) ou o cancelamento mudam o estado.
     */
    static SubscriptionStatus fromProviderState(
            ProviderSubscriptionStatus providerStatus, boolean pastDueWithinGrace, boolean trialRunning) {
        return switch (providerStatus) {
            case ACTIVE -> trialRunning ? null : SubscriptionStatus.ACTIVE;
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
