package com.jpintodigital.billing.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Superfície que o app host chama. Uma assinatura por tenant. O host cuida de
 * autenticação/autorização e passa o {@code tenantId} já resolvido.
 */
public interface BillingApi {

    /**
     * Cria o plano se {@code code} não existir, ou atualiza nome/valor/trial/limite se já
     * existir — idempotente por {@code code}, nunca desativa. Existe para o host materializar
     * um preço que vem de outro lugar (ex.: catálogo por vertical e override por negócio) como
     * um plano estável que esta lib entende, sem que a lib precise saber de onde o preço veio.
     */
    void upsertPlan(String code, String name, long amountCents, int trialDays, int maxUnits);

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

    /**
     * Troca o plano da assinatura sem pedir cartão de novo. Se já existe assinatura de verdade
     * no provedor (cartão em arquivo, mesmo em trial), atualiza o valor lá — o cartão
     * tokenizado continua sendo o mesmo, porque a chamada ao provedor é de atualização, nunca
     * de cancelar-e-recriar. Sem assinatura no provedor ainda (trial sem cartão), só troca o
     * plano local; a primeira cobrança continua exigindo cartão, como sempre exigiu. Mesmo
     * plano de novo é idempotente. Assinatura cancelada/expirada não troca de plano.
     *
     * <p>Sem proração: o ciclo já cobrado não é ajustado, só os próximos (ver o javadoc de
     * {@code PaymentProvider#updateSubscriptionValue}).
     */
    SubscriptionView changePlan(UUID tenantId, String newPlanCode);

    /**
     * Termina o trial agora. Existe para quando o tenant estava numa faixa sem cobrança de
     * verdade (ex.: plano gratuito com trial de 100 anos, convenção do host para nunca deixar
     * a reconciliação expirar sozinha) e escolheu uma faixa paga: sem isto, a primeira cobrança
     * nasceria com a data de vencimento do trial antigo (daqui a décadas), não hoje. Não faz
     * nada se já existe assinatura de verdade no provedor, ou se o status não é TRIALING —
     * seguro de chamar sempre, sem o chamador verificar o estado antes.
     */
    SubscriptionView endTrialNow(UUID tenantId);

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
