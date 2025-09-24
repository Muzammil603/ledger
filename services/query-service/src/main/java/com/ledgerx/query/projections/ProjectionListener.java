package com.ledgerx.query.projections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

@Component
public class ProjectionListener {
  private static final Logger log = LoggerFactory.getLogger(ProjectionListener.class);
  private final JdbcTemplate jdbc; private final ObjectMapper om;

  private final Timer processTimer;
  private final Counter opened, credited, debited;

  public ProjectionListener(JdbcTemplate j, ObjectMapper o, MeterRegistry meter){
    this.jdbc=j; this.om=o;
    this.processTimer = meter.timer("ledgerx.projection.account.latency");
    this.opened   = meter.counter("ledgerx.projection.account.opened");
    this.credited = meter.counter("ledgerx.projection.account.credited");
    this.debited  = meter.counter("ledgerx.projection.account.debited");
  }

  @KafkaListener(topics = {"ledgerx.accounts.events.v1","ledgerx.accounts.events.v2"})
  public void onAccount(String value){
    processTimer.record(() -> {
      try {
        JsonNode root = om.readTree(value);
        String type = root.path("eventType").asText(root.path("type").asText(""));
        String agg  = root.path("aggregateId").asText(root.path("aggId").asText(""));
        long ver    = root.path("version").asLong(root.path("v").asLong(0));
        JsonNode p  = root.has("payload") ? root.path("payload") : root.path("data");
        if (type.isEmpty() || agg.isEmpty()) {
          log.warn("Skipping account event with missing fields: {}", value);
          return;
        }

        int inserted = jdbc.update("""
          INSERT INTO applied_events(aggregate_id, version) VALUES (?, ?)
          ON CONFLICT (aggregate_id, version) DO NOTHING
        """, agg, ver);
        if (inserted == 0) return;

        switch (type.toUpperCase(Locale.ROOT)) {
          case "ACCOUNT_OPENED", "ACCOUNTOPENED", "ACCOUNT_CREATED", "ACCOUNTCREATED" -> {
            String currency = p.path("currency").asText(p.path("curr").asText(""));
            jdbc.update("""
              INSERT INTO account_balance(account_id, currency, balance_cents)
              VALUES (?, ?, 0) ON CONFLICT (account_id) DO NOTHING
            """, agg, currency);
            opened.increment();
          }
          case "FUNDS_CREDITED", "FUNDSCREDITED", "ACCOUNT_CREDITED", "ACCOUNTCREDITED", "CREDITED" -> {
            long amount = p.path("amountCents").asLong(p.path("amount").asLong(0));
            // Ensure the row exists even if ACCOUNT_OPENED was missed
            String currencyUp = p.path("currency").asText(p.path("curr").asText("USD"));
            jdbc.update("""
              INSERT INTO account_balance(account_id, currency, balance_cents)
              VALUES (?, ?, 0)
              ON CONFLICT (account_id) DO NOTHING
            """, agg, currencyUp);
            jdbc.update("UPDATE account_balance SET balance_cents=balance_cents+?, updated_at=NOW() WHERE account_id=?",
              amount, agg);
            credited.increment();
          }
          case "FUNDS_DEBITED", "FUNDSDEBITED", "ACCOUNT_DEBITED", "ACCOUNTDEBITED", "DEBITED" -> {
            long amount = p.path("amountCents").asLong(p.path("amount").asLong(0));
            // Ensure the row exists even if ACCOUNT_OPENED was missed
            String currencyUp = p.path("currency").asText(p.path("curr").asText("USD"));
            jdbc.update("""
              INSERT INTO account_balance(account_id, currency, balance_cents)
              VALUES (?, ?, 0)
              ON CONFLICT (account_id) DO NOTHING
            """, agg, currencyUp);
            jdbc.update("UPDATE account_balance SET balance_cents=balance_cents-?, updated_at=NOW() WHERE account_id=?",
              amount, agg);
            debited.increment();
          }
          default -> {
            log.warn("Skipping unknown account event type: {} payload={}", type, value);
          }
        }
      } catch(Exception e){ log.error("Failed to project account event: {}", value, e); }
    });
  }
}