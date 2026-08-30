package com.jpintodigital.billing;

import com.jpintodigital.billing.config.BillingProperties;
import com.jpintodigital.billing.internal.BillingApiBeans;
import com.jpintodigital.billing.provider.asaas.AsaasPaymentProvider;
import com.jpintodigital.billing.provider.asaas.AsaasProperties;
import com.jpintodigital.billing.spi.PaymentProvider;
import com.jpintodigital.billing.spi.SubscriptionListener;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Auto-configuração da lib de cobrança. O host adiciona a dependência, define
 * {@code jp.billing.*} + {@code jp.billing.asaas.*} e (opcional) registra um
 * {@link SubscriptionListener}.
 *
 * <p>{@code @AutoConfigurationPackage} faz o Boot varrer {@code com.jpintodigital.billing}
 * também para {@code @Entity} e repositórios — aditivo ao pacote do host.
 */
@AutoConfiguration
@AutoConfigurationPackage
@ConditionalOnProperty(prefix = "jp.billing", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties({BillingProperties.class, AsaasProperties.class})
@EnableScheduling
@Import(BillingApiBeans.class)
public class BillingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    Clock billingClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(SubscriptionListener.class)
    SubscriptionListener noopSubscriptionListener() {
        return new com.jpintodigital.billing.internal.NoopSubscriptionListener();
    }

    @Bean
    @ConditionalOnMissingBean(PaymentProvider.class)
    @ConditionalOnProperty(prefix = "jp.billing", name = "provider", havingValue = "asaas", matchIfMissing = true)
    PaymentProvider asaasPaymentProvider(
            ObjectProvider<RestClient.Builder> builders, AsaasProperties props, ObjectMapper mapper) {
        return new AsaasPaymentProvider(builders.getIfAvailable(RestClient::builder), props, mapper);
    }
}
