# jp-billing

Biblioteca de **cobrança recorrente por cartão de crédito** (provedor Asaas) para os produtos
JP Into Digital. Spring Boot auto-configuration — o app host adiciona a dependência, configura
as properties, implementa 1 SPI, e billing funciona.

> **Sem Pix, sem boleto.** Assinatura recorrente = cobrança automática mês a mês; só cartão de
> crédito tem esse trilho de verdade. `PaymentProvider` fica pronto para plugar Pix Automático depois.

## Uso no host

**1. Dependência**

```xml
<dependency>
  <groupId>com.jpintodigital</groupId>
  <artifactId>jp-billing</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

**2. Migrações** — inclua o caminho do schema da lib no Flyway:

```yaml
spring:
  flyway:
    locations: classpath:db/migration,classpath:billing/migration
```

Tabelas: `billing_plans` (seed pelo host), `billing_subscriptions`, `billing_payments`,
`billing_webhook_events`.

**3. Configuração**

```yaml
jp:
  billing:
    provider: asaas
    webhook-base-path: /webhooks/billing        # POST <base>/asaas
    grace-after-past-due: 7d
  asaas:
    base-url: https://api-sandbox.asaas.com/v3  # produção: https://api.asaas.com/v3
    api-key: ${ASAAS_API_KEY}
    webhook-token: ${ASAAS_WEBHOOK_TOKEN}       # o que você configurou no webhook do Asaas
```

**4. SPI de acesso** — reaja a mudanças de assinatura (liberar/bloquear o produto):

```java
@Component
class MyEntitlement implements SubscriptionListener {
    public void onChanged(SubscriptionView sub, SubscriptionStatus previous) {
        // sub.grantsAccess() -> libera; senão bloqueia
    }
}
```

Sem um `SubscriptionListener` registrado, a lib só loga.

**5. Segurança** — o path do webhook se autentica sozinho (token do provedor). Libere-o no seu
`SecurityFilterChain` (`permitAll`).

## API

`BillingApi`: `startTrial(tenantId, planCode)` · `subscribe(tenantId, CardToken)` ·
`cancel(tenantId)` · `statusOf(tenantId)` · `payments(tenantId)`.

O `CardToken` é o token da tokenização do provedor (ex: Asaas `creditCardToken`) — o cartão nunca
passa em claro pela lib nem pelo host (fora de escopo PCI).

## Como funciona

- **Trial** sem cartão (14 dias default). Na conversão (`subscribe`), cria customer + subscription
  `CREDIT_CARD` no Asaas.
- **Webhook** → estado-alvo computado do provedor (nunca delta), idempotente por `event_id`,
  dispara o `SubscriptionListener`. Erro de processamento responde 200 e deixa a reconciliação consertar.
- **Reconciliação** noturna (`@Scheduled`): para cada assinatura não-terminal, recomputa status +
  período + pagamentos do estado absoluto do Asaas. Pega webhook perdido/fora de ordem, trial que estourou.

## Status

`0.1.0` — lógica completa + testes com provedor fake e parsing do webhook do Asaas.
**Falta:** teste de fumaça ao vivo contra o sandbox do Asaas (precisa de credenciais).
