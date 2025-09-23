package com.ledgerx.command.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class EventStoreRepository {
  private final JdbcTemplate jdbc; private final ObjectMapper om;
  public EventStoreRepository(JdbcTemplate jdbc, ObjectMapper om){ this.jdbc=jdbc; this.om=om; }

  public void append(String aggType, String aggId, long version, String eventType,
                     Object payload, Map<String,Object> metadata){
    try {
      jdbc.update("""
        INSERT INTO event_store(aggregate_type,aggregate_id,version,event_type,payload,metadata)
        VALUES (?,?,?,?,?::jsonb,?::jsonb)
      """, aggType, aggId, version, eventType,
          om.writeValueAsString(payload), om.writeValueAsString(metadata));
    } catch (Exception e){ throw new RuntimeException(e); }
  }

  /** Loads events and parses JSONB payloads into Maps so callers don't see PGobject. */
  public List<Map<String,Object>> load(String aggType, String aggId){
    return jdbc.query("""
        SELECT event_type, payload::text AS payload
        FROM event_store
        WHERE aggregate_type=? AND aggregate_id=?
        ORDER BY version
      """,
      (rs, i) -> {
        String eventType = rs.getString("event_type");
        String payloadJson = rs.getString("payload"); // JSON as text
        Map<String,Object> payload;
        try {
          payload = om.readValue(payloadJson, new TypeReference<Map<String,Object>>() {});
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        Map<String,Object> row = new HashMap<>();
        row.put("event_type", eventType);
        row.put("payload", payload);
        return row;
      },
      aggType, aggId
    );
  }
}