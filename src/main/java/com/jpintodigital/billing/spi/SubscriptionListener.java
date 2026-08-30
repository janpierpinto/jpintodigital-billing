package com.jpintodigital.billing.spi;

import com.jpintodigital.billing.api.SubscriptionStatus;
import com.jpintodigital.billing.api.SubscriptionView;

/**
 * O app host implementa isto para reagir a mudanças de assinatura — tipicamente
 * liberar/bloquear acesso. Chamado dentro da transação que persiste a mudança;
 * lançar exceção aqui faz rollback da transição (a reconciliação reprocessa depois).
 *
 * <p>Se o host não registrar um bean, a lib usa um listener no-op.
 */
public interface SubscriptionListener {

    /**
     * @param subscription estado novo (já persistido nesta transação)
     * @param previous     estado anterior ({@code null} se a assinatura acabou de ser criada)
     */
    void onChanged(SubscriptionView subscription, SubscriptionStatus previous);
}
