package com.jpintodigital.billing.provider.asaas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jpintodigital.billing.spi.PaymentProvider.CustomerRequest;
import com.jpintodigital.billing.spi.PaymentProvider.ProviderPaymentStatus;
import com.jpintodigital.billing.spi.PaymentProvider.ProviderSubscription;
import com.jpintodigital.billing.spi.PaymentProvider.ProviderSubscriptionStatus;
import com.jpintodigital.billing.spi.PaymentProvider.SubscriptionRequest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class AsaasHttpTest {

    private static final String BASE = "https://api-sandbox.asaas.com/v3";

    private AsaasPaymentProvider asaas;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        var props = new AsaasProperties();
        props.setBaseUrl(BASE);
        props.setApiKey("k-123");
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        asaas = new AsaasPaymentProvider(builder, props, new ObjectMapper());
    }

    @Test
    void ensureCustomerPostsAndReadsId() {
        server.expect(requestTo(BASE + "/customers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access_token", "k-123"))
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andRespond(withSuccess("{\"id\":\"cus_42\"}", MediaType.APPLICATION_JSON));

        var customer = asaas.ensureCustomer(new CustomerRequest("Ada", "ada@example.com", "12345678900"));

        assertThat(customer.id()).isEqualTo("cus_42");
        server.verify();
    }

    @Test
    void createSubscriptionSendsCreditCardFields() {
        server.expect(requestTo(BASE + "/subscriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.billingType").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.customer").value("cus_42"))
                .andExpect(jsonPath("$.creditCardToken").value("tok_9"))
                .andExpect(jsonPath("$.value").value(99.90))
                .andExpect(jsonPath("$.nextDueDate").value("2026-09-15"))
                .andRespond(withSuccess(
                        "{\"id\":\"sub_7\",\"status\":\"ACTIVE\",\"nextDueDate\":\"2026-09-15\"}",
                        MediaType.APPLICATION_JSON));

        ProviderSubscription sub = asaas.createSubscription(new SubscriptionRequest(
                new com.jpintodigital.billing.spi.PaymentProvider.ProviderCustomer("cus_42"),
                "tok_9", null, 9990L, "BRL", LocalDate.of(2026, 9, 15), "tenant:x"));

        assertThat(sub.id()).isEqualTo("sub_7");
        assertThat(sub.status()).isEqualTo(ProviderSubscriptionStatus.ACTIVE);
        server.verify();
    }

    @Test
    void createSubscriptionSendsRawCardWhenNoToken() {
        server.expect(requestTo(BASE + "/subscriptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.billingType").value("CREDIT_CARD"))
                .andExpect(jsonPath("$.creditCardToken").doesNotExist())
                .andExpect(jsonPath("$.creditCard.number").value("4444444444444444"))
                .andExpect(jsonPath("$.creditCardHolderInfo.cpfCnpj").value("11144477735"))
                .andExpect(jsonPath("$.remoteIp").value("8.8.8.8"))
                .andRespond(withSuccess(
                        "{\"id\":\"sub_8\",\"status\":\"ACTIVE\",\"nextDueDate\":\"2026-09-15\"}",
                        MediaType.APPLICATION_JSON));

        var card = new com.jpintodigital.billing.spi.PaymentProvider.CardData(
                "4444444444444444", "Ada", "12", "2030", "123",
                "ada@example.com", "11144477735", "01310000", "100", "1130000000", "8.8.8.8");
        ProviderSubscription sub = asaas.createSubscription(new SubscriptionRequest(
                new com.jpintodigital.billing.spi.PaymentProvider.ProviderCustomer("cus_42"),
                null, card, 9990L, "BRL", LocalDate.of(2026, 9, 15), "tenant:x"));

        assertThat(sub.id()).isEqualTo("sub_8");
        server.verify();
    }

    @Test
    void fetchSubscriptionMapsOverdue() {
        server.expect(requestTo(BASE + "/subscriptions/sub_7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"id\":\"sub_7\",\"status\":\"OVERDUE\"}", MediaType.APPLICATION_JSON));

        assertThat(asaas.fetchSubscription("sub_7").status()).isEqualTo(ProviderSubscriptionStatus.OVERDUE);
        server.verify();
    }

    @Test
    void recentPaymentsParsesList() {
        server.expect(requestTo(BASE + "/subscriptions/sub_7/payments?limit=20&order=desc"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"data":[
                          {"id":"pay_1","status":"CONFIRMED","value":99.90,"dueDate":"2026-09-15","paymentDate":"2026-09-15"},
                          {"id":"pay_2","status":"OVERDUE","value":99.90,"dueDate":"2026-10-15"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var payments = asaas.recentPayments("sub_7");

        assertThat(payments).hasSize(2);
        assertThat(payments.get(0).id()).isEqualTo("pay_1");
        assertThat(payments.get(0).status()).isEqualTo(ProviderPaymentStatus.CONFIRMED);
        assertThat(payments.get(0).amountCents()).isEqualTo(9990L);
        assertThat(payments.get(1).status()).isEqualTo(ProviderPaymentStatus.OVERDUE);
        server.verify();
    }

    @Test
    void cancelSubscriptionCallsDelete() {
        server.expect(requestTo(BASE + "/subscriptions/sub_7"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess("{\"deleted\":true}", MediaType.APPLICATION_JSON));

        asaas.cancelSubscription("sub_7");
        server.verify();
    }
}
