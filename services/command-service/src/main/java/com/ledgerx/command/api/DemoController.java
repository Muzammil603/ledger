package com.ledgerx.command.api;

import com.ledgerx.command.store.EventStoreRepository;
import com.ledgerx.command.store.OutboxRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/demo")
public class DemoController {
  private final EventStoreRepository store;
  private final OutboxRepository outbox;

  public DemoController(EventStoreRepository store, OutboxRepository outbox) {
    this.store = store;
    this.outbox = outbox;
  }

  @PostMapping("/event")
  @Transactional
  public Map<String, Object> writeDummyEvent(@RequestParam(defaultValue = "acc_001") String aggregateId) {
    // Use next version (prevents unique-constraint errors on re-post)
    long version = store.load("Account", aggregateId).size() + 1L;

    Map<String, Object> payload = Map.of(
        "accountId", aggregateId,
        "amountCents", 500L,
        "currency", "USD"
    );
    Map<String, Object> meta = Map.of("correlationId", UUID.randomUUID().toString());

    // Append to event store
    store.append("Account", aggregateId, version, "FundsCredited", payload, meta);

    // Stage for outbox -> Kafka (include version for idempotent projections)
    Map<String, Object> value = Map.of(
        "eventType", "FundsCredited",
        "aggregateId", aggregateId,
        "version", version,
        "payload", payload,
        "metadata", meta
    );
    outbox.stage("ledgerx.accounts.events.v2", aggregateId, value, Map.<String, Object>of());

    return Map.<String, Object>of("status", "ok", "aggregateId", aggregateId, "version", version);
  }
}