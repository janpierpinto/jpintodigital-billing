package com.jpintodigital.billing.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentView(
        UUID id,
        String providerPaymentId,
        BigDecimal amount,
        String status,
        Instant dueDate,
        Instant paidAt) {
}
