package com.jpintodigital.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jpintodigital.billing.api.BillingApi;
import com.jpintodigital.billing.api.BillingApi.CardToken;
import com.jpintodigital.billing.api.SubscriptionStatus;
import com.jpintodigital.billing.spi.PaymentProvider.ProviderPaymentStatus;
import com.jpintodigital.billing.spi.PaymentProvider.ProviderSubscriptionStatus;
import com.jpintodigital.billing.support.FakePaymentProvider;
import com.jpintodigital.billing.support.MutableClock;
import com.jpintodigital.billing.support.RecordingSubscriptionListener;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Reconciliação: rede de segurança para webhook perdido / trial estourado / dunning. */
@Testcontainers
@SpringBootTest
@Import(ReconciliationFlowIT.Beans.class)
class ReconciliationFlowIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @TestConfiguration(proxyBeanMethods = false)
    static class Beans {
        @Bean
        FakePaymentProvider fakePaymentProvider() {
            return new FakePaymentProvider();
        }

        @Bean
        RecordingSubscriptionListener recordingListener() {
            return new RecordingSubscriptionListener();
        }

        @Bean
        Clock billingClock() {
            return new MutableClock();
        }
    }

    @Autowired
    private BillingApi billing;

    @Autowired
    private FakePaymentProvider provider;

    @Autowired
    private RecordingSubscriptionListener listener;

    @Autowired
    private SubscriptionReconciler reconciler;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private MutableClock clock;

    private UUID tenant;

    @BeforeEach
    void reset() {
        tenant = UUID.randomUUID();
        provider.subscriptionStatus = ProviderSubscriptionStatus.ACTIVE;
        provider.payments.clear();
        listener.changes.clear();
    }

    private UUID subId() {
        return subscriptions.findByTenantId(tenant).orElseThrow().getId();
    }

    @Test
    void trialExpiresWithoutConversion() {
        billing.startTrial(tenant, "standard"); // trialEnd = now + 14d
        clock.advance(Duration.ofDays(15));

        reconciler.reconcile(subId());

        assertThat(billing.statusOf(tenant).orElseThrow().status()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(listener.lastStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void overdueWithinGraceGoesPastDue() {
        billing.startTrial(tenant, "standard");
        billing.subscribe(tenant, new CardToken("tok", "Ada", "ada@x.com", "1"));
        provider.subscriptionStatus = ProviderSubscriptionStatus.OVERDUE;

        reconciler.reconcile(subId());

        assertThat(billing.statusOf(tenant).orElseThrow().status()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(billing.statusOf(tenant).orElseThrow().grantsAccess()).isTrue();
    }

    @Test
    void overduePastGraceExpires() {
        billing.startTrial(tenant, "standard");
        clock.advance(Duration.ofDays(20)); // trial já venceu
        billing.subscribe(tenant, new CardToken("tok", "Ada", "ada@x.com", "1")); // firstDueDate hoje -> ACTIVE, period = +31d
        provider.subscriptionStatus = ProviderSubscriptionStatus.OVERDUE;
        clock.advance(Duration.ofDays(40)); // além de period_end (+31d) + carência (7d)

        reconciler.reconcile(subId());

        assertThat(billing.statusOf(tenant).orElseThrow().status()).isEqualTo(SubscriptionStatus.EXPIRED);
    }

    @Test
    void recoversWhenWebhookWasMissed() {
        billing.startTrial(tenant, "standard");
        clock.advance(Duration.ofDays(20));
        billing.subscribe(tenant, new CardToken("tok", "Ada", "ada@x.com", "1"));
        // simula webhook OVERDUE que chegou
        provider.subscriptionStatus = ProviderSubscriptionStatus.OVERDUE;
        reconciler.reconcile(subId());
        assertThat(billing.statusOf(tenant).orElseThrow().status()).isEqualTo(SubscriptionStatus.PAST_DUE);

        // pagou de novo mas o webhook PAYMENT_CONFIRMED se perdeu — provedor já está ACTIVE
        provider.subscriptionStatus = ProviderSubscriptionStatus.ACTIVE;
        reconciler.reconcile(subId());
        assertThat(billing.statusOf(tenant).orElseThrow().status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void syncsPaymentsFromProvider() {
        billing.startTrial(tenant, "standard");
        clock.advance(Duration.ofDays(20));
        billing.subscribe(tenant, new CardToken("tok", "Ada", "ada@x.com", "1"));
        provider.addPayment("pay_1", ProviderPaymentStatus.CONFIRMED, 9900);
        provider.addPayment("pay_2", ProviderPaymentStatus.PENDING, 9900);

        reconciler.reconcile(subId());

        var payments = billing.payments(tenant);
        assertThat(payments).hasSize(2);
        assertThat(payments).anyMatch(p -> p.providerPaymentId().equals("pay_1") && p.status().equals("CONFIRMED"));

        // status muda no provedor -> reconciliação atualiza, não duplica
        provider.payments.clear();
        provider.addPayment("pay_2", ProviderPaymentStatus.CONFIRMED, 9900);
        reconciler.reconcile(subId());
        assertThat(billing.payments(tenant)).hasSize(2);
        assertThat(billing.payments(tenant)).anyMatch(p -> p.providerPaymentId().equals("pay_2") && p.status().equals("CONFIRMED"));
    }
}
