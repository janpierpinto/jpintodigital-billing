package com.jpintodigital.billing.internal;

import com.jpintodigital.billing.api.SubscriptionStatus;
import com.jpintodigital.billing.api.SubscriptionView;
import com.jpintodigital.billing.spi.SubscriptionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Usado quando o host não registra um SubscriptionListener próprio. */
public class NoopSubscriptionListener implements SubscriptionListener {

    private static final Logger log = LoggerFactory.getLogger(NoopSubscriptionListener.class);

    @Override
    public void onChanged(SubscriptionView subscription, SubscriptionStatus previous) {
        log.info("Assinatura do tenant {} {} -> {} (nenhum SubscriptionListener registrado)",
                subscription.tenantId(), previous, subscription.status());
    }
}
