package com.jpintodigital.billing.internal;

import com.jpintodigital.billing.api.SubscriptionStatus;
import com.jpintodigital.billing.domain.WebhookEvent;
import com.jpintodigital.billing.spi.PaymentProvider;
import com.jpintodigital.billing.spi.PaymentProvider.ProviderEvent;
import com.jpintodigital.billing.spi.SubscriptionListener;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;


class WebhookProcessor {

    private static final Logger log = LoggerFactory.getLogger(WebhookProcessor.class);

    enum Result {
        OK,
        UNAUTHORIZED,
        IGNORED
    }

    private final PaymentProvider provider;
    private final SubscriptionRepository subscriptions;
    private final WebhookEventRepository events;
    private final SubscriptionListener listener;
    private final Clock clock;
    private final TransactionTemplate newTx;
    private final TransactionTemplate tx;

    WebhookProcessor(
            PaymentProvider provider,
            SubscriptionRepository subscriptions,
            WebhookEventRepository events,
            SubscriptionListener listener,
            Clock clock,
            PlatformTransactionManager txManager) {
        this.provider = provider;
        this.subscriptions = subscriptions;
        this.events = events;
        this.listener = listener;
        this.clock = clock;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tx = new TransactionTemplate(txManager);
    }

    Result ingest(String body, Map<String, String> headers) {
        if (!provider.verifyWebhook(headers, body)) {
            return Result.UNAUTHORIZED;
        }
        var parsed = provider.parseWebhook(body).orElse(null);
        if (parsed == null || parsed.eventId() == null || parsed.eventId().isBlank()) {
            return Result.IGNORED;
        }

        var existing = events.findByEventId(parsed.eventId());
        if (existing.isPresent()) {
            return Result.OK; // replay
        }

        UUID rowId;
        try {
            rowId = newTx.execute(status -> events.save(
                    new WebhookEvent(provider.name(), parsed.eventId(), parsed.type().name(), body)).getId());
        } catch (DataIntegrityViolationException concurrentReplay) {
            return Result.OK;
        }

        tx.executeWithoutResult(status -> {
            var row = events.findById(rowId).orElseThrow();
            try {
                apply(parsed);
                row.markProcessed();
            } catch (RuntimeException e) {
                // 200 mesmo assim; a reconciliação conserta
                row.markFailed(e.getMessage());
                log.warn("Falha ao processar webhook {}: {} — reconciliação vai reprocessar",
                        parsed.eventId(), e.getMessage());
            }
        });
        return Result.OK;
    }

    private void apply(ProviderEvent event) {
        var sub = subscriptions.findByProviderSubscriptionId(event.subscriptionId()).orElse(null);
        if (sub == null) {
            return;
        }
        var target = SubscriptionStateMachine.onProviderEvent(sub.getStatus(), event.type());
        if (target == null || target == sub.getStatus()) {
            return;
        }
        var previous = sub.getStatus();
        var periodEnd = target == SubscriptionStatus.ACTIVE
                ? Instant.now(clock).plus(31, ChronoUnit.DAYS)
                : null;
        sub.transitionTo(target, periodEnd);
        listener.onChanged(BillingService.toView(sub), previous);
    }
}
