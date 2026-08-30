package com.jpintodigital.billing.internal;

import com.jpintodigital.billing.api.BillingApi;
import com.jpintodigital.billing.api.PaymentView;
import com.jpintodigital.billing.api.SubscriptionStatus;
import com.jpintodigital.billing.api.SubscriptionView;
import com.jpintodigital.billing.domain.Plan;
import com.jpintodigital.billing.domain.Subscription;
import com.jpintodigital.billing.spi.PaymentProvider;
import com.jpintodigital.billing.spi.PaymentProvider.CustomerRequest;
import com.jpintodigital.billing.spi.PaymentProvider.SubscriptionRequest;
import com.jpintodigital.billing.spi.SubscriptionListener;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;


class BillingService implements BillingApi {

    private final SubscriptionRepository subscriptions;
    private final PaymentRepository payments;
    private final PlanRepository plans;
    private final PaymentProvider provider;
    private final SubscriptionListener listener;
    private final Clock clock;

    BillingService(
            SubscriptionRepository subscriptions,
            PaymentRepository payments,
            PlanRepository plans,
            PaymentProvider provider,
            SubscriptionListener listener,
            Clock clock) {
        this.subscriptions = subscriptions;
        this.payments = payments;
        this.plans = plans;
        this.provider = provider;
        this.listener = listener;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SubscriptionView startTrial(UUID tenantId, String planCode) {
        var existing = subscriptions.findByTenantId(tenantId);
        if (existing.isPresent()) {
            return toView(existing.get());
        }
        var plan = plan(planCode);
        var trialEnd = plan.getTrialDays() <= 0
                ? Instant.now(clock)
                : Instant.now(clock).plus(plan.getTrialDays(), ChronoUnit.DAYS);
        var sub = subscriptions.save(new Subscription(tenantId, planCode, SubscriptionStatus.TRIALING, trialEnd));
        dispatch(sub, null);
        return toView(sub);
    }

    @Override
    @Transactional
    public SubscriptionView subscribe(UUID tenantId, CardToken card) {
        return doSubscribe(tenantId, card.holderName(), card.holderEmail(), card.holderCpfCnpj(),
                card.token(), null);
    }

    @Override
    @Transactional
    public SubscriptionView subscribeWithCard(UUID tenantId, CardInput card) {
        var data = new PaymentProvider.CardData(
                card.number(), card.holderName(), card.expiryMonth(), card.expiryYear(), card.ccv(),
                card.holderEmail(), card.holderCpfCnpj(), card.holderPostalCode(),
                card.holderAddressNumber(), card.holderPhone(), card.remoteIp());
        return doSubscribe(tenantId, card.holderName(), card.holderEmail(), card.holderCpfCnpj(), null, data);
    }

    private SubscriptionView doSubscribe(
            UUID tenantId, String holderName, String holderEmail, String holderCpfCnpj,
            String cardToken, PaymentProvider.CardData card) {
        var sub = require(tenantId);
        var plan = plan(sub.getPlanCode());

        var customer = provider.ensureCustomer(new CustomerRequest(holderName, holderEmail, holderCpfCnpj));
        var today = LocalDate.now(clock);
        var trialEndDate = sub.getTrialEnd() == null
                ? today
                : sub.getTrialEnd().atZone(ZoneOffset.UTC).toLocalDate();
        var firstDueDate = trialEndDate.isAfter(today) ? trialEndDate : today;

        var providerSub = provider.createSubscription(new SubscriptionRequest(
                customer, cardToken, card, plan.getAmountCents(), plan.getCurrency(),
                firstDueDate, "tenant:" + tenantId));
        sub.attachProvider(provider.name(), customer.id(), providerSub.id());

        var previous = sub.getStatus();
        // Cobrança já hoje -> acesso imediato (otimista); webhook/reconciliação corrige se recusar.
        if (!firstDueDate.isAfter(today)) {
            sub.transitionTo(SubscriptionStatus.ACTIVE, Instant.now(clock).plus(31, ChronoUnit.DAYS));
        }
        if (sub.getStatus() != previous) {
            dispatch(sub, previous);
        }
        return toView(sub);
    }

    @Override
    @Transactional
    public SubscriptionView cancel(UUID tenantId) {
        var sub = require(tenantId);
        if (sub.getProviderSubscriptionId() != null) {
            provider.cancelSubscription(sub.getProviderSubscriptionId());
        }
        var previous = sub.getStatus();
        sub.transitionTo(SubscriptionStatus.CANCELED, null);
        dispatch(sub, previous);
        return toView(sub);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SubscriptionView> statusOf(UUID tenantId) {
        return subscriptions.findByTenantId(tenantId).map(BillingService::toView);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentView> payments(UUID tenantId) {
        return payments.findByTenantIdOrderByDueDateDesc(tenantId).stream()
                .map(p -> new PaymentView(
                        p.getId(), p.getProviderPaymentId(),
                        BigDecimal.valueOf(p.getAmountCents(), 2), p.getStatus(),
                        p.getDueDate() == null ? null : p.getDueDate().atStartOfDay(ZoneOffset.UTC).toInstant(),
                        p.getPaidAt()))
                .toList();
    }

    private void dispatch(Subscription sub, SubscriptionStatus previous) {
        listener.onChanged(toView(sub), previous);
    }

    private Subscription require(UUID tenantId) {
        return subscriptions.findByTenantId(tenantId)
                .orElseThrow(() -> new NoSuchElementException("sem assinatura para o tenant " + tenantId));
    }

    private Plan plan(String code) {
        return plans.findById(code)
                .filter(Plan::isActive)
                .orElseThrow(() -> new NoSuchElementException("plano inexistente ou inativo: " + code));
    }

    static SubscriptionView toView(Subscription s) {
        return new SubscriptionView(
                s.getTenantId(), s.getPlanCode(), s.getStatus(),
                s.getCurrentPeriodEnd(), s.getTrialEnd(), s.getProviderSubscriptionId());
    }
}
