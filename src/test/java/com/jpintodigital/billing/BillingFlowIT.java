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
    void subscribeWithRawCardAttachesProviderAndConfirmsViaWebhook() throws Exception {
        var tenant = UUID.randomUUID();
        billing.startTrial(tenant, "standard");

        var subd = billing.subscribeWithCard(tenant, new com.jpintodigital.billing.api.BillingApi.CardInput(
                "4444444444444444", "Ada", "12", "2030", "123",
                "ada@example.com", "12345678900", "01310000", "100", "1130000000", "8.8.8.8"));

        // cartão em arquivo; a 1ª cobrança só no fim do trial, então segue TRIALING
        assertThat(subd.providerSubscriptionId()).startsWith("sub_");
        assertThat(billing.statusOf(tenant).orElseThrow().status()).isEqualTo(SubscriptionStatus.TRIALING);

        webhook("evt-raw|PAYMENT_CONFIRMED|" + subd.providerSubscriptionId() + "|pay-raw");
        assertThat(billing.statusOf(tenant).orElseThrow().status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void upsertPlanCreatesThenUpdatesTheSameCode() throws Exception {
        var codigo = "bridge:beauty:t-" + UUID.randomUUID();
        billing.upsertPlan(codigo, "Standard — beauty", 7500L, 0, 0);

        var tenant = UUID.randomUUID();
        billing.startTrial(tenant, codigo);
        assertThat(billing.statusOf(tenant).orElseThrow().planCode()).isEqualTo(codigo);
        billing.subscribe(tenant, new CardToken("tok_bridge_1", "Ada", "ada@example.com", "12345678900"));
        assertThat(provider.lastCreatedSubscriptionAmountCents).isEqualTo(7500L);

        // Repreçar o mesmo code (o "update" do upsert) não cria linha nova nem quebra quem já
        // estava nele -- só reescreve nome/valor/trial/limite para a próxima assinatura que
        // usar esse code.
        billing.upsertPlan(codigo, "Standard — beauty", 8000L, 0, 0);
        var outroTenant = UUID.randomUUID();
        billing.startTrial(outroTenant, codigo);
        billing.subscribe(outroTenant, new CardToken("tok_bridge_2", "Ada", "ada@example.com", "12345678900"));
        assertThat(provider.lastCreatedSubscriptionAmountCents).isEqualTo(8000L);
    }

    @Test
    void endTrialNowLetsTheFirstChargeStartTodayInsteadOfTheOldFarTrialEnd() throws Exception {
        var tenant = UUID.randomUUID();
        billing.startTrial(tenant, "free"); // trial-days: 36500 -- o marcador "sem prazo real"
        var antes = billing.statusOf(tenant).orElseThrow();
        assertThat(antes.trialEnd()).isAfter(java.time.Instant.now().plus(java.time.Duration.ofDays(365)));

        // Escolheu uma faixa paga: troca o plano local (sem assinatura no provedor ainda) e
        // termina o trial antigo, do mesmo jeito que a ponte de troca de plano do host faria.
        billing.changePlan(tenant, "premium");
        var trocado = billing.endTrialNow(tenant);

        assertThat(trocado.trialEnd()).isBeforeOrEqualTo(java.time.Instant.now());
        assertThat(trocado.status()).isEqualTo(SubscriptionStatus.TRIALING); // endTrialNow não muda o status

        // A cobrança real agora nasce hoje, não daqui a 100 anos: `doSubscribe` só marca ACTIVE
        // (e abre o período) quando `firstDueDate` não está no futuro -- é exatamente o que o
        // bug faria dar errado sem `endTrialNow` (firstDueDate cairia ~100 anos no futuro, e a
        // assinatura ficaria TRIALING para sempre em vez de cobrar).
        var subd = billing.subscribeWithCard(tenant, new com.jpintodigital.billing.api.BillingApi.CardInput(
                "4444444444444444", "Ada", "12", "2030", "123",
                "ada@example.com", "12345678900", "01310000", "100", "1130000000", "8.8.8.8"));
        assertThat(subd.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subd.currentPeriodEnd()).isNotNull();
    }

    @Test
    void endTrialNowDoesNothingWhenThereIsAlreadyARealSubscription() throws Exception {
        var tenant = UUID.randomUUID();
        billing.startTrial(tenant, "standard");
        billing.subscribe(tenant, new CardToken("tok_789", "Ada", "ada@example.com", "12345678900"));
        var antes = billing.statusOf(tenant).orElseThrow();

        var depois = billing.endTrialNow(tenant);

        assertThat(depois.trialEnd()).isEqualTo(antes.trialEnd());
    }

    @Test
    void changePlanUpdatesValueAtProviderWithoutCancelingOrTouchingCard() throws Exception {
        int cancelCallsAntes = provider.cancelCalls;
        int updateCallsAntes = provider.updateValueCalls;
        var tenant = UUID.randomUUID();
        billing.startTrial(tenant, "standard");
        var subd = billing.subscribe(tenant, new CardToken("tok_123", "Ada", "ada@example.com", "12345678900"));
        webhook("evt-plan-1|PAYMENT_CONFIRMED|" + subd.providerSubscriptionId() + "|pay-plan-1");
        assertThat(billing.statusOf(tenant).orElseThrow().status()).isEqualTo(SubscriptionStatus.ACTIVE);

        var trocado = billing.changePlan(tenant, "premium");

        assertThat(trocado.planCode()).isEqualTo("premium");
        // mesma assinatura no provedor — nunca cancelou pra recriar, o cartão continua o mesmo
        assertThat(trocado.providerSubscriptionId()).isEqualTo(subd.providerSubscriptionId());
        // deltas, não valor absoluto: `provider` é bean singleton reaproveitado por todos os
        // métodos desta classe, na mesma execução do Spring — outro teste já pode ter chamado
        // cancelar/trocar plano antes deste, na ordem que o JUnit decidir rodar.
        assertThat(provider.cancelCalls).isEqualTo(cancelCallsAntes);
        assertThat(provider.updateValueCalls).isEqualTo(updateCallsAntes + 1);
        assertThat(provider.lastUpdatedValueCents).isEqualTo(19900L);
        // status não muda por trocar de plano — não é reação a pagamento
        assertThat(trocado.status()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void changePlanForTheSamePlanIsIdempotentAndDoesNotCallProvider() throws Exception {
        int updateCallsAntes = provider.updateValueCalls;
        var tenant = UUID.randomUUID();
        billing.startTrial(tenant, "standard");
        billing.subscribe(tenant, new CardToken("tok_456", "Ada", "ada@example.com", "12345678900"));

        var mesmo = billing.changePlan(tenant, "standard");

        assertThat(mesmo.planCode()).isEqualTo("standard");
        assertThat(provider.updateValueCalls).isEqualTo(updateCallsAntes);
    }

    @Test
    void changePlanDuringTrialWithoutCardYetOnlySwitchesLocalPlan() {
        int updateCallsAntes = provider.updateValueCalls;
        var tenant = UUID.randomUUID();
        billing.startTrial(tenant, "standard");

        var trocado = billing.changePlan(tenant, "premium");

        assertThat(trocado.planCode()).isEqualTo("premium");
        assertThat(trocado.status()).isEqualTo(SubscriptionStatus.TRIALING);
        // sem assinatura no provedor ainda — nada para atualizar lá
        assertThat(provider.updateValueCalls).isEqualTo(updateCallsAntes);
    }

    @Test
    void changePlanRejectsCanceledSubscription() {
        var tenant = UUID.randomUUID();
        billing.startTrial(tenant, "standard");
        billing.cancel(tenant);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> billing.changePlan(tenant, "premium"));
    }

    @Test
    void changePlanRejectsUnknownPlanCode() {
        var tenant = UUID.randomUUID();
        billing.startTrial(tenant, "standard");

        org.junit.jupiter.api.Assertions.assertThrows(java.util.NoSuchElementException.class,
                () -> billing.changePlan(tenant, "inexistente"));
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
