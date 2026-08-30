package com.jpintodigital.billing.provider.asaas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.jpintodigital.billing.spi.PaymentProvider.CustomerRequest;
import com.jpintodigital.billing.spi.PaymentProvider.ProviderCustomer;
import com.jpintodigital.billing.spi.PaymentProvider.ProviderSubscription;
import com.jpintodigital.billing.spi.PaymentProvider.ProviderSubscriptionStatus;
import com.jpintodigital.billing.spi.PaymentProvider.SubscriptionRequest;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Smoke ao vivo contra o <b>sandbox</b> do Asaas — exercita o
 * {@link AsaasPaymentProvider} de verdade (HTTP real, sem mock), fechando o
 * ciclo cliente → tokenização → assinatura de cartão → consulta → cancelamento.
 *
 * <p>Roda só quando {@code ASAAS_API_KEY} está no ambiente (chave {@code $aact_hmlg_...}).
 * Sem a variável o teste é ignorado — o {@code verify} normal e o CI não tocam a rede.
 * Localmente: {@code set -a && . backend/.asaas.env.local && set +a} no dbintegrated,
 * ou exportar {@code ASAAS_API_KEY} antes do {@code ./mvnw -pl . verify -Dit.test=AsaasSandboxSmokeIT}.
 *
 * <p>A tokenização não faz parte do SPI (em produção é client-side), então o teste
 * chama {@code POST /creditCard/tokenizeCreditCard} direto com um cartão de teste
 * do sandbox (aprovação: {@code 4444 4444 4444 4444}).
 */
@EnabledIfEnvironmentVariable(named = "ASAAS_API_KEY", matches = ".+")
class AsaasSandboxSmokeIT {

    private static final String APPROVAL_CARD = "4444444444444444";
    // CPF de teste com dígitos verificadores válidos (não é de ninguém).
    private static final String TEST_CPF = "11144477735";

    private final String baseUrl =
            System.getenv().getOrDefault("ASAAS_BASE_URL", "https://api-sandbox.asaas.com/v3");
    private final String apiKey = System.getenv("ASAAS_API_KEY");

    private final AsaasProperties props = props();
    private final AsaasPaymentProvider provider =
            new AsaasPaymentProvider(RestClient.builder(), props, new ObjectMapper());
    private final RestClient raw = RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("access_token", apiKey)
            .build();

    private String customerId;

    private AsaasProperties props() {
        var p = new AsaasProperties();
        p.setBaseUrl(baseUrl);
        p.setApiKey(apiKey);
        return p;
    }

    @AfterEach
    void cleanup() {
        if (customerId != null) {
            // Apaga o cliente do sandbox — cascateia a assinatura/cobrança de teste.
            try {
                raw.delete().uri("/customers/{id}", customerId).retrieve().toBodilessEntity();
            } catch (RuntimeException ignored) {
                // sandbox — melhor esforço
            }
        }
    }

    @Test
    void fullCreditCardSubscriptionLifecycle() {
        var suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. cliente
        ProviderCustomer customer = provider.ensureCustomer(
                new CustomerRequest("jp-billing smoke " + suffix, "smoke+" + suffix + "@jpintodigital.com", TEST_CPF));
        assertThat(customer.id()).startsWith("cus_");
        customerId = customer.id();

        // 2. tokeniza um cartão de teste (fora do SPI, como seria no cliente)
        String cardToken = tokenizeApprovalCard(customer.id());
        assertThat(cardToken).isNotBlank();

        // 3. assinatura recorrente de cartão
        ProviderSubscription sub = provider.createSubscription(new SubscriptionRequest(
                customer, cardToken, 9990L, "BRL", LocalDate.now().plusDays(1), "smoke:" + suffix));
        assertThat(sub.id()).startsWith("sub_");
        assertThat(sub.status()).isEqualTo(ProviderSubscriptionStatus.ACTIVE);

        // 4. consulta — fonte da verdade da reconciliação
        assertThat(provider.fetchSubscription(sub.id()).status())
                .isEqualTo(ProviderSubscriptionStatus.ACTIVE);

        // 5. a assinatura já gerou a 1ª cobrança
        var payments = provider.recentPayments(sub.id());
        assertThat(payments).isNotEmpty();
        assertThat(payments.get(0).amountCents()).isEqualTo(9990L);

        // 6. cancelamento
        assertThatCode(() -> provider.cancelSubscription(sub.id())).doesNotThrowAnyException();
    }

    private String tokenizeApprovalCard(String customer) {
        var body = Map.of(
                "customer", customer,
                "creditCard", Map.of(
                        "holderName", "Smoke Test",
                        "number", APPROVAL_CARD,
                        "expiryMonth", "12",
                        "expiryYear", "2030",
                        "ccv", "123"),
                "creditCardHolderInfo", Map.of(
                        "name", "Smoke Test",
                        "email", "smoke@jpintodigital.com",
                        "cpfCnpj", TEST_CPF,
                        "postalCode", "01310000",
                        "addressNumber", "100",
                        "phone", "1130000000"),
                "remoteIp", "8.8.8.8");
        JsonNode node = raw.post()
                .uri("/creditCard/tokenizeCreditCard")
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        return node.path("creditCardToken").asString();
    }
}
