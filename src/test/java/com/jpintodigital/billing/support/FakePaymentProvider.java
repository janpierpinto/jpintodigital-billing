package com.jpintodigital.billing.support;

import com.jpintodigital.billing.spi.PaymentProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Provedor de brinquedo para os testes da lib — controlável pelo teste. */
public class FakePaymentProvider implements PaymentProvider {

    private final AtomicInteger seq = new AtomicInteger();
    public volatile ProviderSubscriptionStatus subscriptionStatus = ProviderSubscriptionStatus.ACTIVE;
    public final List<ProviderPayment> payments = new ArrayList<>();
    public volatile boolean webhookAuthOk = true;
    public volatile int cancelCalls = 0;

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public ProviderCustomer ensureCustomer(CustomerRequest request) {
        return new ProviderCustomer("cus_" + seq.incrementAndGet());
    }

    @Override
    public ProviderSubscription createSubscription(SubscriptionRequest request) {
        return new ProviderSubscription("sub_" + seq.incrementAndGet(), ProviderSubscriptionStatus.ACTIVE,
                null, request.firstDueDate());
    }

    @Override
    public void cancelSubscription(String providerSubscriptionId) {
        cancelCalls++;
    }

    @Override
    public ProviderSubscription fetchSubscription(String providerSubscriptionId) {
        return new ProviderSubscription(providerSubscriptionId, subscriptionStatus, null,
                LocalDate.now().plusMonths(1));
    }

    @Override
    public List<ProviderPayment> recentPayments(String providerSubscriptionId) {
        return List.copyOf(payments);
    }

    @Override
    public boolean verifyWebhook(Map<String, String> headers, String body) {
        return webhookAuthOk;
    }

    @Override
    public Optional<ProviderEvent> parseWebhook(String body) {
        // corpo de teste: "eventId|TYPE|subId|payId"
        var parts = body.split("\\|", -1);
        if (parts.length < 3) {
            return Optional.empty();
        }
        return Optional.of(new ProviderEvent(
                parts[0], ProviderEventType.valueOf(parts[1]), parts[2],
                parts.length > 3 ? parts[3] : null));
    }

    public void addPayment(String id, ProviderPaymentStatus status, long cents) {
        payments.add(new ProviderPayment(id, status, cents, LocalDate.now(), Instant.now()));
    }
}
