package com.jpintodigital.billing.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("jp.billing")
public class BillingProperties {

    /**
     * Catálogo de planos que a lib semeia na tabela {@code billing_plans} no
     * startup (insere o que faltar; não atualiza plano já existente). O host
     * declara o próprio catálogo — a lib não tem opinião sobre preço.
     */
    private List<PlanDef> plans = new ArrayList<>();

    /** Liga a biblioteca. */
    private boolean enabled = true;

    /** Provedor ativo. Hoje só {@code asaas}. */
    private String provider = "asaas";

    /** Prefixo do endpoint de webhook: {@code <base>/<provider>}. */
    private String webhookBasePath = "/webhooks/billing";

    /** Carência depois que a cobrança fica PAST_DUE antes de virar EXPIRED. */
    private Duration graceAfterPastDue = Duration.ofDays(7);

    /** Roda o job de reconciliação. */
    private boolean reconciliationEnabled = true;

    /** Cron do job de reconciliação (default: 03:17 todo dia). */
    private String reconciliationCron = "0 17 3 * * *";

    /**
     * A lib roda as próprias migrações ({@code classpath:billing/migration}) num
     * Flyway dedicado com histórico próprio. Desligue se o host gerencia o
     * schema {@code billing_*} por fora.
     */
    private boolean manageSchema = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getWebhookBasePath() {
        return webhookBasePath;
    }

    public void setWebhookBasePath(String webhookBasePath) {
        this.webhookBasePath = webhookBasePath;
    }

    public Duration getGraceAfterPastDue() {
        return graceAfterPastDue;
    }

    public void setGraceAfterPastDue(Duration graceAfterPastDue) {
        this.graceAfterPastDue = graceAfterPastDue;
    }

    public boolean isReconciliationEnabled() {
        return reconciliationEnabled;
    }

    public void setReconciliationEnabled(boolean reconciliationEnabled) {
        this.reconciliationEnabled = reconciliationEnabled;
    }

    public String getReconciliationCron() {
        return reconciliationCron;
    }

    public void setReconciliationCron(String reconciliationCron) {
        this.reconciliationCron = reconciliationCron;
    }

    public boolean isManageSchema() {
        return manageSchema;
    }

    public void setManageSchema(boolean manageSchema) {
        this.manageSchema = manageSchema;
    }

    public List<PlanDef> getPlans() {
        return plans;
    }

    public void setPlans(List<PlanDef> plans) {
        this.plans = plans;
    }

    /** Definição de um plano a semear. */
    public static class PlanDef {

        private String code;
        private String name;
        private long amountCents;
        private int trialDays = 14;
        private int maxUnits;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public long getAmountCents() {
            return amountCents;
        }

        public void setAmountCents(long amountCents) {
            this.amountCents = amountCents;
        }

        public int getTrialDays() {
            return trialDays;
        }

        public void setTrialDays(int trialDays) {
            this.trialDays = trialDays;
        }

        public int getMaxUnits() {
            return maxUnits;
        }

        public void setMaxUnits(int maxUnits) {
            this.maxUnits = maxUnits;
        }
    }
}
