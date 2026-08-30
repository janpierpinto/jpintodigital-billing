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
     * com o cartão tokenizado. O cartão nunca passa em claro pela lib nem pelo host.
     */
    SubscriptionView subscribe(UUID tenantId, CardToken card);

    /** Cancela no provedor e marca CANCELED. Acesso segue até o fim do período pago. */
    SubscriptionView cancel(UUID tenantId);

    Optional<SubscriptionView> statusOf(UUID tenantId);

    List<PaymentView> payments(UUID tenantId);

    /** Token de cartão emitido pela tokenização do provedor (ex: Asaas creditCardToken). */
    record CardToken(String token, String holderName, String holderEmail, String holderCpfCnpj) {
    }
}
