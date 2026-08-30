package com.jpintodigital.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jpintodigital.billing.api.BillingApi;
import com.jpintodigital.billing.api.BillingApi.CardToken;
import com.jpintodigital.billing.api.SubscriptionStatus;
import com.jpintodigital.billing.spi.PaymentProvider;
import com.jpintodigital.billing.spi.SubscriptionListener;
import com.jpintodigital.billing.support.FakePaymentProvider;
import com.jpintodigital.billing.support.RecordingSubscriptionListener;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(BillingFlowIT.Beans.class)
class BillingFlowIT {

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
    }

    @Autowired
    private BillingApi billing;

    @Autowired
    private FakePaymentProvider provider;

    @Autowired
    private RecordingSubscriptionListener listener;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void trialSubscribeWebhookCancelFlow() throws Exception {
        var tenant = UUID.randomUUID();

        var trial = billing.startTrial(tenant, "standard");
        assertThat(trial.status()).isEqualTo(SubscriptionStatus.TRIALING);
        assertThat(trial.grantsAccess()).isTrue();
        // startTrial idempotente
        assertThat(billing.startTrial(tenant, "standard").status()).isEqualTo(SubscriptionStatus.TRIALING);

        var subd = billing.subscribe(tenant, new CardToken("tok_123", "Ada", "ada@example.com", "12345678900"));
        assertThat(subd.providerSubscriptionId()).startsWith("sub_");
        var providerSubId = subd.providerSubscriptionId();

        // webhook: pagamento confirmado -> ACTIVE
        webhook("evt-1|PAYMENT_CONFIRMED|" + providerSubId + "|pay-1");
        assertThat(billing.statusOf(tenant).orElseThrow().status()).isEqualTo(SubscriptionStatus.ACTIVE);

        // replay do mesmo evento -> idempotente, nada muda
        int before = listener.changes.size();
        webhook("evt-1|PAYMENT_CONFIRMED|" + providerSubId + "|pay-1");
        assertThat(listener.changes).hasSize(before);

        // webhook: vencido -> PAST_DUE (ainda dá acesso)
        webhook("evt-2|PAYMENT_OVERDUE|" + providerSubId + "|pay-2");
        var pastDue = billing.statusOf(tenant).orElseThrow();
        assertThat(pastDue.status()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(pastDue.grantsAccess()).isTrue();

        // cancelar
        billing.cancel(tenant);
        assertThat(billing.statusOf(tenant).orElseThrow().status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(provider.cancelCalls).isEqualTo(1);

        assertThat(listener.lastStatus()).isEqualTo(SubscriptionStatus.CANCELED);
    }

    @Test
    void webhookWithBadTokenIsRejected() throws Exception {
        provider.webhookAuthOk = false;
        try {
            mockMvc.perform(post("/webhooks/billing/fake").content("evt-x|PAYMENT_CONFIRMED|sub_x|pay-x"))
                    .andExpect(status().isUnauthorized());
        } finally {
            provider.webhookAuthOk = true;
        }
    }

    @Test
    void webhookForUnknownProviderIs404() throws Exception {
        mockMvc.perform(post("/webhooks/billing/stripe").content("evt-y|PAYMENT_CONFIRMED|sub_y|pay-y"))
                .andExpect(status().isNotFound());
    }

    private void webhook(String body) throws Exception {
        mockMvc.perform(post("/webhooks/billing/fake").content(body)).andExpect(status().isOk());
    }

    // garante que a lib injeta o PaymentProvider e o SubscriptionListener de teste
    @Autowired
    void checkWiring(PaymentProvider p, SubscriptionListener l) {
        assertThat(p).isInstanceOf(FakePaymentProvider.class);
        assertThat(l).isInstanceOf(RecordingSubscriptionListener.class);
    }
}
