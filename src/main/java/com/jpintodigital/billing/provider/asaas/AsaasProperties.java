package com.jpintodigital.billing.provider.asaas;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jp.billing.asaas")
public class AsaasProperties {

    /** Sandbox por padrão. Produção: https://api.asaas.com/v3 */
    private String baseUrl = "https://api-sandbox.asaas.com/v3";

    /** API key da conta Asaas (header {@code access_token}). */
    private String apiKey = "";

    /** Token que a gente configurou no webhook do Asaas; chega no header {@code asaas-access-token}. */
    private String webhookToken = "";

    /** Ciclo da assinatura no Asaas. */
    private String cycle = "MONTHLY";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getWebhookToken() {
        return webhookToken;
    }

    public void setWebhookToken(String webhookToken) {
        this.webhookToken = webhookToken;
    }

    public String getCycle() {
        return cycle;
    }

    public void setCycle(String cycle) {
        this.cycle = cycle;
    }
}
