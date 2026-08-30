package com.jpintodigital.billing.api;

/** Ciclo de vida da assinatura. Estado absoluto — computado do provedor, nunca por delta. */
public enum SubscriptionStatus {

    /** Período de teste, sem cartão. */
    TRIALING,
    /** Pagamento em dia. */
    ACTIVE,
    /** Cobrança recusada/atrasada; ainda dá acesso durante o dunning. */
    PAST_DUE,
    /** Cancelada pelo cliente ou pelo provedor. */
    CANCELED,
    /** Trial terminou sem conversão, ou dunning esgotou. */
    EXPIRED;

    public boolean grantsAccess() {
        return this == TRIALING || this == ACTIVE || this == PAST_DUE;
    }

    public boolean isTerminal() {
        return this == CANCELED || this == EXPIRED;
    }
}
