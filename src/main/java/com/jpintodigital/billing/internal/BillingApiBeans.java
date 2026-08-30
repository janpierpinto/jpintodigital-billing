package com.jpintodigital.billing.internal;

import com.jpintodigital.billing.api.BillingApi;
import com.jpintodigital.billing.config.BillingProperties;
import com.jpintodigital.billing.spi.PaymentProvider;
import com.jpintodigital.billing.spi.SubscriptionListener;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Fábrica dos beans internos (mantém as classes package-private). */
@Configuration(proxyBeanMethods = false)
public class BillingApiBeans {

    @Bean
    BillingApi billingApi(
            SubscriptionRepository subscriptions, PaymentRepository payments, PlanRepository plans,
            PaymentProvider provider, SubscriptionListener listener, Clock clock) {
        return new BillingService(subscriptions, payments, plans, provider, listener, clock);
    }

    @Bean
    SubscriptionReconciler subscriptionReconciler(
            SubscriptionRepository subscriptions, PaymentRepository payments, PaymentProvider provider,
            SubscriptionListener listener, BillingProperties properties, Clock clock) {
        return new SubscriptionReconciler(subscriptions, payments, provider, listener, properties, clock);
    }

    @Bean
    ReconciliationJob reconciliationJob(
            SubscriptionRepository subscriptions, SubscriptionReconciler reconciler, BillingProperties properties) {
        return new ReconciliationJob(subscriptions, reconciler, properties);
    }

    @Bean
    WebhookProcessor webhookProcessor(
            PaymentProvider provider, SubscriptionRepository subscriptions, WebhookEventRepository events,
            SubscriptionListener listener, Clock clock, PlatformTransactionManager txManager) {
        return new WebhookProcessor(provider, subscriptions, events, listener, clock, txManager);
    }

    @Bean
    @ConditionalOnWebApplication
    BillingWebhookController billingWebhookController(WebhookProcessor processor) {
        return new BillingWebhookController(processor);
    }
}
