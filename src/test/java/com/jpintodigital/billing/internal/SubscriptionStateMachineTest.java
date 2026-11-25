package com.jpintodigital.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jpintodigital.billing.api.SubscriptionStatus;
import com.jpintodigital.billing.spi.PaymentProvider.ProviderEventType;
import com.jpintodigital.billing.spi.PaymentProvider.ProviderSubscriptionStatus;
import org.junit.jupiter.api.Test;

class SubscriptionStateMachineTest {

    @Test
    void confirmedPaymentActivates() {
        assertThat(SubscriptionStateMachine.onProviderEvent(SubscriptionStatus.TRIALING, ProviderEventType.PAYMENT_CONFIRMED))
                .isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(SubscriptionStateMachine.onProviderEvent(SubscriptionStatus.PAST_DUE, ProviderEventType.PAYMENT_CONFIRMED))
                .isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void overdueGoesPastDueButNotFromTerminal() {
        assertThat(SubscriptionStateMachine.onProviderEvent(SubscriptionStatus.ACTIVE, ProviderEventType.PAYMENT_OVERDUE))
                .isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(SubscriptionStateMachine.onProviderEvent(SubscriptionStatus.CANCELED, ProviderEventType.PAYMENT_OVERDUE))
                .isNull();
    }

    @Test
    void chargebackAndCancelAreTerminal() {
        assertThat(SubscriptionStateMachine.onProviderEvent(
                SubscriptionStatus.ACTIVE, ProviderEventType.PAYMENT_REFUNDED_OR_CHARGEBACK))
                .isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(SubscriptionStateMachine.onProviderEvent(
                SubscriptionStatus.ACTIVE, ProviderEventType.SUBSCRIPTION_CANCELED))
                .isEqualTo(SubscriptionStatus.CANCELED);
    }

    @Test
    void reconciliationFromProviderState() {
        assertThat(SubscriptionStateMachine.fromProviderState(ProviderSubscriptionStatus.ACTIVE, false, false))
                .isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(SubscriptionStateMachine.fromProviderState(ProviderSubscriptionStatus.OVERDUE, true, false))
                .isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(SubscriptionStateMachine.fromProviderState(ProviderSubscriptionStatus.OVERDUE, false, false))
                .isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(SubscriptionStateMachine.fromProviderState(ProviderSubscriptionStatus.UNKNOWN, false, false))
                .isNull();
    }

    /** No Asaas a assinatura é ACTIVE desde o cadastro do cartão: isso não encerra o teste grátis. */
    @Test
    void providerActiveDoesNotEndARunningTrial() {
        assertThat(SubscriptionStateMachine.fromProviderState(ProviderSubscriptionStatus.ACTIVE, false, true))
                .isNull();
        // Cancelamento no provedor continua valendo durante o teste.
        assertThat(SubscriptionStateMachine.fromProviderState(ProviderSubscriptionStatus.CANCELED, false, true))
                .isEqualTo(SubscriptionStatus.CANCELED);
    }

    @Test
    void trialExpiredOnlyWithoutProvider() {
        assertThat(SubscriptionStateMachine.trialExpired(SubscriptionStatus.TRIALING, false, true)).isTrue();
        assertThat(SubscriptionStateMachine.trialExpired(SubscriptionStatus.TRIALING, true, true)).isFalse();
        assertThat(SubscriptionStateMachine.trialExpired(SubscriptionStatus.ACTIVE, false, true)).isFalse();
    }
}
