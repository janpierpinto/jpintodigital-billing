package com.jpintodigital.billing.internal;

import com.jpintodigital.billing.api.SubscriptionStatus;
import com.jpintodigital.billing.config.BillingProperties;
import com.jpintodigital.billing.domain.Payment;
import com.jpintodigital.billing.domain.Subscription;
import com.jpintodigital.billing.spi.PaymentProvider;
import com.jpintodigital.billing.spi.SubscriptionListener;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/** Reconcilia UMA assinatura contra o estado absoluto do provedor. */

class SubscriptionReconciler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionReconciler.class);

    private final SubscriptionRepository subscriptions;
    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final SubscriptionListener listener;
    private final BillingProperties properties;
    private final Clock clock;

    SubscriptionReconciler(
            SubscriptionRepository subscriptions,
            PaymentRepository payments,
            PaymentProvider provider,
            SubscriptionListener listener,
            BillingProperties properties,
            Clock clock) {
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.provider = provider;
        this.listener = listener;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void reconcile(UUID subscriptionId) {
        var sub = subscriptions.findById(subscriptionId).orElse(null);
        if (sub == null || sub.getStatus().isTerminal()) {
            return;
        }

        boolean trialEnded = sub.getTrialEnd() != null && sub.getTrialEnd().isBefore(Instant.now(clock));
        if (SubscriptionStateMachine.trialExpired(
                sub.getStatus(), sub.getProviderSubscriptionId() != null, trialEnded)) {
            transition(sub, SubscriptionStatus.EXPIRED, null);
            return;
        }
        if (sub.getProviderSubscriptionId() == null) {
            return; // trial em curso
        }

        var providerSub = provider.fetchSubscription(sub.getProviderSubscriptionId());
        syncPayments(sub);

        var target = SubscriptionStateMachine.fromProviderState(providerSub.status(), pastDueWithinGrace(sub));
        if (target != null && target != sub.getStatus()) {
            var periodEnd = target == SubscriptionStatus.ACTIVE
                    ? (providerSub.currentPeriodEnd() != null
                            ? providerSub.currentPeriodEnd()
                            : Instant.now(clock).plus(31, ChronoUnit.DAYS))
                    : null;
            transition(sub, target, periodEnd);
        }
    }

    private void syncPayments(Subscription sub) {
        for (var pp : provider.recentPayments(sub.getProviderSubscriptionId())) {
            payments.findByProviderPaymentId(pp.id()).ifPresentOrElse(
                    existing -> existing.update(pp.status().name(), pp.paidAt()),
                    () -> payments.save(new Payment(
                            sub.getId(), sub.getTenantId(), pp.id(), pp.amountCents(),
                            pp.status().name(), pp.dueDate(), pp.paidAt())));
        }
    }

    private boolean pastDueWithinGrace(Subscription sub) {
        var since = sub.getCurrentPeriodEnd() != null ? sub.getCurrentPeriodEnd() : Instant.now(clock);
        return Instant.now(clock).isBefore(since.plus(properties.getGraceAfterPastDue()));
    }

    private void transition(Subscription sub, SubscriptionStatus target, Instant periodEnd) {
        var previous = sub.getStatus();
        sub.transitionTo(target, periodEnd);
        listener.onChanged(BillingService.toView(sub), previous);
        log.info("Reconciliação: assinatura {} {} -> {}", sub.getId(), previous, target);
    }
}
