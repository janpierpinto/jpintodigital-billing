package com.jpintodigital.billing.provider.asaas;

import com.jpintodigital.billing.spi.PaymentProvider;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador Asaas — só cartão de crédito recorrente. Fala com a API v3 via
 * {@code access_token}; o webhook é autenticado pelo header {@code asaas-access-token}.
 */
public class AsaasPaymentProvider implements PaymentProvider {

    private final RestClient http;
    private final AsaasProperties properties;
    private final ObjectMapper json;

    public AsaasPaymentProvider(RestClient.Builder builder, AsaasProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.http = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("access_token", properties.getApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String name() {
        return "asaas";
    }

    @Override
    public ProviderCustomer ensureCustomer(CustomerRequest request) {
        var body = Map.of(
                "name", nz(request.name()),
                "email", nz(request.email()),
                "cpfCnpj", nz(request.cpfCnpj()));
        var node = post("/customers", body);
        return new ProviderCustomer(node.path("id").asString());
    }

    @Override
    public ProviderSubscription createSubscription(SubscriptionRequest request) {
        var body = new LinkedHashMap<String, Object>();
        body.put("customer", request.customer().id());
        body.put("billingType", "CREDIT_CARD");
        body.put("value", BigDecimal.valueOf(request.amountCents(), 2));
        body.put("nextDueDate", request.firstDueDate().toString());
        body.put("cycle", properties.getCycle());
        body.put("creditCardToken", request.cardToken());
        body.put("externalReference", request.externalReference());
        var node = post("/subscriptions", body);
        return toSubscription(node);
    }

    @Override
    public void cancelSubscription(String providerSubscriptionId) {
        http.delete().uri("/subscriptions/{id}", providerSubscriptionId).retrieve().toBodilessEntity();
    }

    @Override
    public ProviderSubscription fetchSubscription(String providerSubscriptionId) {
        var node = get("/subscriptions/{id}", providerSubscriptionId);
        return toSubscription(node);
    }

    @Override
    public List<ProviderPayment> recentPayments(String providerSubscriptionId) {
        var node = get("/subscriptions/{id}/payments?limit=20&order=desc", providerSubscriptionId);
        var out = new ArrayList<ProviderPayment>();
        for (JsonNode p : node.path("data")) {
            out.add(new ProviderPayment(
                    p.path("id").asString(),
                    paymentStatus(p.path("status").asString("")),
                    cents(p.path("value")),
                    parseDate(p.path("dueDate").asString(null)),
                    parseInstant(p.path("paymentDate").asString(null))));
        }
        return out;
    }

    @Override
    public boolean verifyWebhook(Map<String, String> headers, String body) {
        var expected = properties.getWebhookToken();
        if (expected == null || expected.isBlank()) {
            return false; // sem token configurado, nada entra
        }
        var got = headers.getOrDefault("asaas-access-token", "");
        return MessageDigest.isEqual(
                got.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Optional<ProviderEvent> parseWebhook(String body) {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        var event = root.path("event").asString("");
        var payment = root.path("payment");
        var subscriptionId = payment.path("subscription").asString(null);
        var paymentId = payment.path("id").asString(null);
        var eventId = root.path("id").asString(null);
        if (eventId == null || eventId.isBlank()) {
            eventId = event + ":" + paymentId + ":" + payment.path("status").asString("");
        }
        return Optional.of(new ProviderEvent(eventId, eventType(event), subscriptionId, paymentId));
    }

    // --- helpers ---

    private static ProviderEventType eventType(String asaasEvent) {
        return switch (asaasEvent) {
            case "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED", "PAYMENT_RECEIVED_IN_CASH" -> ProviderEventType.PAYMENT_CONFIRMED;
            case "PAYMENT_OVERDUE" -> ProviderEventType.PAYMENT_OVERDUE;
            case "PAYMENT_REFUNDED", "PAYMENT_CHARGEBACK_REQUESTED", "PAYMENT_CHARGEBACK_DISPUTE",
                    "PAYMENT_PARTIALLY_REFUNDED" -> ProviderEventType.PAYMENT_REFUNDED_OR_CHARGEBACK;
            case "PAYMENT_DELETED", "SUBSCRIPTION_DELETED" -> ProviderEventType.SUBSCRIPTION_CANCELED;
            default -> ProviderEventType.OTHER;
        };
    }

    private static ProviderSubscriptionStatus subscriptionStatus(String asaas) {
        return switch (asaas) {
            case "ACTIVE" -> ProviderSubscriptionStatus.ACTIVE;
            case "OVERDUE", "EXPIRED" -> ProviderSubscriptionStatus.OVERDUE;
            case "INACTIVE" -> ProviderSubscriptionStatus.INACTIVE;
            default -> ProviderSubscriptionStatus.UNKNOWN;
        };
    }

    private static ProviderPaymentStatus paymentStatus(String asaas) {
        return switch (asaas) {
            case "CONFIRMED" -> ProviderPaymentStatus.CONFIRMED;
            case "RECEIVED", "RECEIVED_IN_CASH" -> ProviderPaymentStatus.RECEIVED;
            case "PENDING", "AWAITING_RISK_ANALYSIS" -> ProviderPaymentStatus.PENDING;
            case "OVERDUE" -> ProviderPaymentStatus.OVERDUE;
            case "REFUNDED", "REFUND_REQUESTED", "PARTIALLY_REFUNDED" -> ProviderPaymentStatus.REFUNDED;
            case "CHARGEBACK_REQUESTED", "CHARGEBACK_DISPUTE", "AWAITING_CHARGEBACK_REVERSAL" -> ProviderPaymentStatus.CHARGEBACK;
            case "DELETED" -> ProviderPaymentStatus.DELETED;
            default -> ProviderPaymentStatus.UNKNOWN;
        };
    }

    private ProviderSubscription toSubscription(JsonNode node) {
        return new ProviderSubscription(
                node.path("id").asString(),
                subscriptionStatus(node.path("status").asString("")),
                null,
                parseDate(node.path("nextDueDate").asString(null)));
    }

    private JsonNode post(String path, Object body) {
        return http.post().uri(path).body(body).retrieve().body(JsonNode.class);
    }

    private JsonNode get(String pathTemplate, Object... args) {
        return http.get().uri(pathTemplate, args).retrieve().body(JsonNode.class);
    }

    private static long cents(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return 0L;
        }
        return value.decimalValue().movePointRight(2).longValueExact();
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.substring(0, Math.min(10, raw.length())));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank() || "null".equals(raw)) {
            return null;
        }
        try {
            if (raw.length() <= 10) {
                return LocalDate.parse(raw).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            }
            return OffsetDateTime.parse(raw.replace(" ", "T")).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
