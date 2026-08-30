package com.jpintodigital.billing.internal;

import com.jpintodigital.billing.spi.PaymentProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST <jp.billing.webhook-base-path>/{provider}} — o provedor se autentica
 * sozinho (token no header). O host precisa liberar esse path no seu SecurityFilterChain.
 */
@RestController
class BillingWebhookController {

    private final WebhookProcessor processor;
    private final PaymentProvider paymentProvider;

    BillingWebhookController(WebhookProcessor processor, PaymentProvider paymentProvider) {
        this.processor = processor;
        this.paymentProvider = paymentProvider;
    }

    @PostMapping("${jp.billing.webhook-base-path:/webhooks/billing}/{provider}")
    ResponseEntity<Void> receive(
            @PathVariable String provider,
            @RequestBody(required = false) String body,
            HttpServletRequest request) {

        if (!paymentProvider.name().equalsIgnoreCase(provider)) {
            return ResponseEntity.notFound().build();
        }
        var result = processor.ingest(body == null ? "" : body, headers(request));
        return switch (result) {
            case OK, IGNORED -> ResponseEntity.ok().build();
            case UNAUTHORIZED -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        };
    }

    private static Map<String, String> headers(HttpServletRequest request) {
        var out = new LinkedHashMap<String, String>();
        for (var name : Collections.list(request.getHeaderNames())) {
            out.put(name.toLowerCase(), request.getHeader(name));
        }
        return out;
    }
}
