package com.jpintodigital.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Plano — seed global do host (sem tenant). */
@Entity
@Table(name = "billing_plans")
public class Plan {

    @Id
    @Column(length = 60)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency = "BRL";

    @Column(name = "trial_days", nullable = false)
    private int trialDays = 14;

    /** Limite genérico do plano (nº de unidades cobráveis — o host interpreta). 0 = sem limite. */
    @Column(name = "max_units", nullable = false)
    private int maxUnits;

    @Column(nullable = false)
    private boolean active = true;

    protected Plan() {
    }

    public Plan(String code, String name, long amountCents, int trialDays, int maxUnits) {
        this.code = code;
        this.name = name;
        this.amountCents = amountCents;
        this.trialDays = trialDays;
        this.maxUnits = maxUnits;
    }

    /**
     * Atualiza o plano existente — usado pelo upsert do host (ver
     * {@code BillingApi#upsertPlan}). {@code active} não muda aqui: quem chama nunca desativa
     * pelo upsert, só cria ou reprecifica.
     */
    public void update(String name, long amountCents, int trialDays, int maxUnits) {
        this.name = name;
        this.amountCents = amountCents;
        this.trialDays = trialDays;
        this.maxUnits = maxUnits;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public int getTrialDays() {
        return trialDays;
    }

    public int getMaxUnits() {
        return maxUnits;
    }

    public boolean isActive() {
        return active;
    }
}
