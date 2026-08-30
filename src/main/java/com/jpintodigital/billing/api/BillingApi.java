package com.jpintodigital.billing.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Superfície que o app host chama. Uma assinatura por tenant. O host cuida de
 * autenticação/autorização e passa o {@code tenantId} já resolvido.
 */
public interface BillingApi {

    /** Inicia o trial (sem cartão). Idempotente: se já existe assinatura, devolve a atual. */
    SubscriptionView startTrial(UUID tenantId, String planCode);

    /**
     * Converte o trial (ou reativa) criando a assinatura recorrente no provedor
     * com o cartão já tokenizado (ex.: por um SDK client-side).
     */
    SubscriptionView subscribe(UUID tenantId, CardToken card);

    /**
     * Igual a {@link #subscribe}, mas recebe o cartão em claro — o provedor
     * tokeniza no ato. O PAN só trafega até o gateway; a lib e o host não gravam.
     * Use enquanto não há tokenização client-side (o Asaas não expõe SDK de browser).
     */
    SubscriptionView subscribeWithCard(UUID tenantId, CardInput card);

    /** Cancela no provedor e marca CANCELED. Acesso segue até o fim do período pago. */
    SubscriptionView cancel(UUID tenantId);

    Optional<SubscriptionView> statusOf(UUID tenantId);

    List<PaymentView> payments(UUID tenantId);

    /** Token de cartão emitido pela tokenização do provedor (ex: Asaas creditCardToken). */
    record CardToken(String token, String holderName, String holderEmail, String holderCpfCnpj) {
    }

    /** Cartão em claro para {@link #subscribeWithCard}. Não é persistido em lugar nenhum. */
    record CardInput(
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
}
