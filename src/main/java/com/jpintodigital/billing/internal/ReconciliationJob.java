package com.jpintodigital.billing.internal;

import com.jpintodigital.billing.api.SubscriptionStatus;
import com.jpintodigital.billing.config.BillingProperties;
import com.jpintodigital.billing.domain.Subscription;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Dispara a reconciliação de cada assinatura não-terminal (cada uma na sua transação). */

class ReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationJob.class);
    private static final List<SubscriptionStatus> NON_TERMINAL = List.of(
            SubscriptionStatus.TRIALING, SubscriptionStatus.ACTIVE, SubscriptionStatus.PAST_DUE);

    private final SubscriptionRepository subscriptions;
    private final SubscriptionReconciler reconciler;
    private final BillingProperties properties;

    ReconciliationJob(
            SubscriptionRepository subscriptions,
            SubscriptionReconciler reconciler,
            BillingProperties properties) {
        this.subscriptions = subscriptions;
        this.reconciler = reconciler;
        this.properties = properties;
    }

    @Scheduled(cron = "${jp.billing.reconciliation-cron:0 17 3 * * *}")
    public void run() {
        if (!properties.isReconciliationEnabled()) {
            return;
        }
        var ids = subscriptions.findByStatusIn(NON_TERMINAL).stream().map(Subscription::getId).toList();
        for (UUID id : ids) {
            try {
                reconciler.reconcile(id);
            } catch (RuntimeException e) {
                log.warn("Reconciliação da assinatura {} falhou: {}", id, e.getMessage());
            }
        }
    }
}
