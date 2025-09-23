package com.ledgerx.command.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class BackfillController {
  private final JdbcTemplate jdbc;
  private final KafkaTemplate<String,String> kafka;
  private final ObjectMapper om;

  public BackfillController(JdbcTemplate jdbc, KafkaTemplate<String,String> kafka, ObjectMapper om){
    this.jdbc = jdbc; this.kafka = kafka; this.om = om;
  }

  @PostMapping("/backfill/accounts")
  public Map<String,Object> backfill() {
    List<Map<String,Object>> rows = jdbc.queryForList("""
      SELECT aggregate_id, version, event_type, payload::text AS payload, metadata::text AS metadata
      FROM event_store
      WHERE aggregate_type = 'Account'
      ORDER BY aggregate_id, version
    """);
    int published = 0;
    for (var r : rows){
      var value = Map.of(
          "eventType", r.get("event_type"),
          "aggregateId", r.get("aggregate_id"),
          "version", ((Number) r.get("version")).longValue(),
          "payload", fromJson((String) r.get("payload")),
          "metadata", fromJson((String) r.get("metadata"))
      );
      try {
        kafka.send("ledgerx.accounts.events.v2",
            (String) r.get("aggregate_id"),
            om.writeValueAsString(value)).get();
        published++;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    return Map.of("published", published);
  }

  private Object fromJson(String json){
    try { return om.readTree(json); } catch (Exception e){ throw new RuntimeException(e); }
  }
}