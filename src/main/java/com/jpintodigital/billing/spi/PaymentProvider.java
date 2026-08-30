package com.jpintodigital.billing.spi;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptador para um gateway de pagamento (Asaas hoje). Toda a lib fala com o
 * gateway só por esta interface; trocar de provedor ou plugar Pix Automático
 * depois é adicionar uma implementação.
 */
public interface PaymentProvider {

    /** Identificador estável do provedor, gravado em {@code billing_*.provider}. */
    String name();

    /** Cria (ou reusa) o customer no provedor. */
    ProviderCustomer ensureCustomer(CustomerRequest request);

    /** Cria a assinatura recorrente de cartão de crédito. */
    ProviderSubscription createSubscription(SubscriptionRequest request);

    void cancelSubscription(String providerSubscriptionId);

    /** Estado atual da assinatura no provedor — fonte da verdade para a reconciliação. */
    ProviderSubscription fetchSubscription(String providerSubscriptionId);

    List<ProviderPayment> recentPayments(String providerSubscriptionId);

    /** Confere autenticidade do webhook (token/assinatura). Comparação constant-time. */
    boolean verifyWebhook(Map<String, String> headers, String body);

    /** Traduz o corpo do webhook num evento neutro; vazio = evento que a lib ignora. */
    Optional<ProviderEvent> parseWebhook(String body);

    // --- contratos neutros ---

    record CustomerRequest(String name, String email, String cpfCnpj) {
    }

    record ProviderCustomer(String id) {
    }

    /**
     * {@code cardToken} (pré-tokenizado, p/ um futuro SDK client-side) OU
     * {@code card} (cartão em claro — o provedor tokeniza no ato). Exatamente um
     * dos dois. O PAN nunca é persistido pela lib.
     */
    record SubscriptionRequest(
            ProviderCustomer customer,
            String cardToken,
            CardData card,
            long amountCents,
            String currency,
            java.time.LocalDate firstDueDate,
            String externalReference) {
    }

    /** Dados de cartão em claro — só trafegam até o provedor, nunca são gravados. */
    record CardData(
            String number,
            String holderName,
            String expiryMonth,
            String expiryYear,
            String ccv,
            String holderEmail,
            String holderCpfCnpj,
            String holderPostalCode,
            String holderAddressNumber,
            String holderPhone,
            String remoteIp) {
    }

    /** {@code currentPeriodEnd}/{@code nextDueDate} podem ser nulos conforme o provedor. */
    record ProviderSubscription(
            String id,
            ProviderSubscriptionStatus status,
            java.time.Instant currentPeriodEnd,
            java.time.LocalDate nextDueDate) {
    }

    enum ProviderSubscriptionStatus {
        ACTIVE,
        OVERDUE,
        INACTIVE,
        CANCELED,
        UNKNOWN
    }

    record ProviderPayment(
            String id,
            ProviderPaymentStatus status,
            long amountCents,
            java.time.LocalDate dueDate,
            java.time.Instant paidAt) {
    }

    enum ProviderPaymentStatus {
        PENDING,
        CONFIRMED,
        RECEIVED,
        OVERDUE,
        REFUNDED,
        CHARGEBACK,
        DELETED,
        UNKNOWN
    }

    /** Evento neutro extraído do webhook. */
    record ProviderEvent(
            String eventId,
            ProviderEventType type,
            String subscriptionId,
            String paymentId) {
    }

    enum ProviderEventType {
        PAYMENT_CONFIRMED,
        PAYMENT_OVERDUE,
        PAYMENT_REFUNDED_OR_CHARGEBACK,
        SUBSCRIPTION_CANCELED,
        OTHER
    }
}
