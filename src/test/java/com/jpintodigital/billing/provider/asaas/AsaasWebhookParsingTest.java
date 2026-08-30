package com.jpintodigital.billing.provider.asaas;

import static org.assertj.core.api.Assertions.assertThat;

import com.jpintodigital.billing.spi.PaymentProvider.ProviderEventType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class AsaasWebhookParsingTest {

    private final AsaasProperties props = props();
    private final AsaasPaymentProvider asaas =
            new AsaasPaymentProvider(RestClient.builder(), props, new ObjectMapper());

    private static AsaasProperties props() {
        var p = new AsaasProperties();
        p.setApiKey("k");
        p.setWebhookToken("segredo-do-webhook");
        return p;
    }

    @Test
    void verifyWebhookIsConstantTimeAndTokenGated() {
        assertThat(asaas.verifyWebhook(Map.of("asaas-access-token", "segredo-do-webhook"), "{}")).isTrue();
        assertThat(asaas.verifyWebhook(Map.of("asaas-access-token", "errado"), "{}")).isFalse();
        assertThat(asaas.verifyWebhook(Map.of(), "{}")).isFalse();

        var noToken = new AsaasProperties();
        noToken.setWebhookToken("");
        var bare = new AsaasPaymentProvider(RestClient.builder(), noToken, new ObjectMapper());
        assertThat(bare.verifyWebhook(Map.of("asaas-access-token", ""), "{}")).isFalse();
    }

    @Test
    void parsesPaymentConfirmed() {
        var body = """
                {"id":"evt_abc","event":"PAYMENT_CONFIRMED",
                 "payment":{"id":"pay_1","subscription":"sub_9","status":"CONFIRMED"}}
                """;
        var event = asaas.parseWebhook(body).orElseThrow();
        assertThat(event.eventId()).isEqualTo("evt_abc");
        assertThat(event.type()).isEqualTo(ProviderEventType.PAYMENT_CONFIRMED);
        assertThat(event.subscriptionId()).isEqualTo("sub_9");
        assertThat(event.paymentId()).isEqualTo("pay_1");
    }

    @Test
    void synthesizesEventIdWhenMissing() {
        var body = """
                {"event":"PAYMENT_OVERDUE","payment":{"id":"pay_2","subscription":"sub_9","status":"OVERDUE"}}
                """;
        var event = asaas.parseWebhook(body).orElseThrow();
        assertThat(event.eventId()).isEqualTo("PAYMENT_OVERDUE:pay_2:OVERDUE");
        assertThat(event.type()).isEqualTo(ProviderEventType.PAYMENT_OVERDUE);
    }

    @Test
    void mapsChargebackAndDeletion() {
        assertThat(asaas.parseWebhook("{\"event\":\"PAYMENT_CHARGEBACK_REQUESTED\",\"payment\":{\"id\":\"p\",\"subscription\":\"s\"}}")
                .orElseThrow().type()).isEqualTo(ProviderEventType.PAYMENT_REFUNDED_OR_CHARGEBACK);
        assertThat(asaas.parseWebhook("{\"event\":\"SUBSCRIPTION_DELETED\",\"payment\":{\"id\":\"p\",\"subscription\":\"s\"}}")
                .orElseThrow().type()).isEqualTo(ProviderEventType.SUBSCRIPTION_CANCELED);
    }
}
