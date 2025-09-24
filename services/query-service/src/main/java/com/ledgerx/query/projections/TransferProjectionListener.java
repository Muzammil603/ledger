package com.ledgerx.query.projections;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransferProjectionListener {
  private final JdbcTemplate jdbc; private final ObjectMapper om;
  public TransferProjectionListener(JdbcTemplate j, ObjectMapper o){ this.jdbc=j; this.om=o; }

  @KafkaListener(topics = {"ledgerx.transfers.events.v1"})
  public void onTransfer(String value){
    try {
      JsonNode root = om.readTree(value);
      String type = root.get("eventType").asText();
      String id   = root.get("aggregateId").asText();
      long ver    = root.get("version").asLong();
      JsonNode p  = root.get("payload");

      // idempotency for projections (share the same applied_events table)
      int inserted = jdbc.update("""
        INSERT INTO applied_events(aggregate_id, version) VALUES (?, ?)
        ON CONFLICT (aggregate_id, version) DO NOTHING
      """, "transfer:"+id, ver); // namespace with prefix to avoid collision with account ids
      if (inserted == 0) return;

      switch (type) {
        case "TransferRequested" -> {
          jdbc.update("""
            INSERT INTO transfers(transfer_id, from_account, to_account, amount_cents, currency, status)
            VALUES (?,?,?,?,?,'INITIATED')
            ON CONFLICT (transfer_id) DO UPDATE SET
              from_account=EXCLUDED.from_account,
              to_account=EXCLUDED.to_account,
              amount_cents=EXCLUDED.amount_cents,
              currency=EXCLUDED.currency,
              status='INITIATED',
              updated_at=NOW()
          """, id, p.get("from").asText(), p.get("to").asText(),
                p.get("amountCents").asLong(), p.get("currency").asText());
        }
        case "SourceDebited" -> jdbc.update(
          "UPDATE transfers SET status='DEBITED', updated_at=NOW() WHERE transfer_id=?", id);
        case "DestinationCredited" -> jdbc.update(
          "UPDATE transfers SET status='CREDITED', updated_at=NOW() WHERE transfer_id=?", id);
        case "TransferCompleted" -> jdbc.update(
          "UPDATE transfers SET status='COMPLETED', updated_at=NOW() WHERE transfer_id=?", id);
        case "TransferFailed" -> {
          String status = (root.path("metadata").path("compensated").asBoolean(false))
              ? "COMPENSATED" : "FAILED";
          jdbc.update("UPDATE transfers SET status=?, updated_at=NOW() WHERE transfer_id=?", status, id);
        }
      }
    } catch(Exception e){ throw new RuntimeException(e); }
  }
}