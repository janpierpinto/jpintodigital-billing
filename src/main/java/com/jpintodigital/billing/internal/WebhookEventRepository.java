package com.jpintodigital.billing.internal;

import com.jpintodigital.billing.domain.WebhookEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {

    Optional<WebhookEvent> findByEventId(String eventId);

    List<WebhookEvent> findByProcessedAtIsNullAndReceivedAtBefore(Instant cutoff);
}
