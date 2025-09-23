package com.ledgerx.query.projections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ProjectionListener {
  private final JdbcTemplate jdbc; private final ObjectMapper om;
  public ProjectionListener(JdbcTemplate j, ObjectMapper o){ this.jdbc=j; this.om=o; }

  @KafkaListener(topics = {"ledgerx.accounts.events.v2"})
  public void onAccount(String value){
    try {
      JsonNode root = om.readTree(value);
      String type = root.get("eventType").asText();
      String agg  = root.get("aggregateId").asText();
      long ver    = root.get("version").asLong();
      JsonNode p  = root.get("payload");

      // idempotency
      int inserted = jdbc.update("""
        INSERT INTO applied_events(aggregate_id, version) VALUES (?, ?)
        ON CONFLICT (aggregate_id, version) DO NOTHING
      """, agg, ver);
      if (inserted == 0) return;

      if ("AccountOpened".equals(type)) {
        jdbc.update("""
          INSERT INTO account_balance(account_id, currency, balance_cents)
          VALUES (?, ?, 0) ON CONFLICT (account_id) DO NOTHING
        """, agg, p.get("currency").asText());
      } else if ("FundsCredited".equals(type)) {
        jdbc.update("UPDATE account_balance SET balance_cents = balance_cents + ?, updated_at=NOW() WHERE account_id=?",
          p.get("amountCents").asLong(), agg);
      } else if ("FundsDebited".equals(type)) {
        jdbc.update("UPDATE account_balance SET balance_cents = balance_cents - ?, updated_at=NOW() WHERE account_id=?",
          p.get("amountCents").asLong(), agg);
      }
    } catch(Exception e){ throw new RuntimeException(e); }
  }
}