package com.jpintodigital.billing.internal;

import com.jpintodigital.billing.config.BillingProperties;
import com.jpintodigital.billing.domain.Plan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.Transactional;

/**
 * Semeia {@code billing_plans} a partir de {@code jp.billing.plans[*]} no
 * startup. Insere o que faltar; não mexe em plano que já existe (mudança de
 * preço é migração explícita do host).
 */
class PlanSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlanSeeder.class);

    private final PlanRepository plans;
    private final BillingProperties properties;

    PlanSeeder(PlanRepository plans, BillingProperties properties) {
        this.plans = plans;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (var def : properties.getPlans()) {
            if (def.getCode() == null || def.getCode().isBlank()) {
                throw new IllegalStateException("jp.billing.plans[*].code é obrigatório");
            }
            if (plans.existsById(def.getCode())) {
                continue;
            }
            plans.save(new Plan(def.getCode(), def.getName(), def.getAmountCents(), def.getTrialDays(), def.getMaxUnits()));
            log.info("plano '{}' semeado ({} centavos, trial {}d)", def.getCode(), def.getAmountCents(), def.getTrialDays());
        }
    }
}
