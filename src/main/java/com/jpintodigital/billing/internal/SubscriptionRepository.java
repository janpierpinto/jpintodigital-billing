package com.jpintodigital.billing.internal;

import com.jpintodigital.billing.api.SubscriptionStatus;
import com.jpintodigital.billing.domain.Subscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    Optional<Subscription> findByTenantId(UUID tenantId);

    Optional<Subscription> findByProviderSubscriptionId(String providerSubscriptionId);

    List<Subscription> findByStatusInAndTrialEndBefore(List<SubscriptionStatus> statuses, java.time.Instant cutoff);

    List<Subscription> findByStatusIn(List<SubscriptionStatus> statuses);
}
