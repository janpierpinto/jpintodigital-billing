package com.jpintodigital.billing.internal;

import com.jpintodigital.billing.domain.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    List<Payment> findByTenantIdOrderByDueDateDesc(UUID tenantId);
}
