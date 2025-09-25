package com.ledgerx.command.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.List;

@Repository
public class IdempotencyRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper om;

  public IdempotencyRepository(JdbcTemplate jdbc, ObjectMapper om){
    this.jdbc = jdbc; this.om = om;
  }

  /**
   * Reserve idem_key for request_hash if new.
   * If key exists:
   *   - if stored response is not null: return it (idempotent replay)
   *   - if stored response is null: allow retry (return empty so caller can process again)
   *     This handles previous crashes between reserve and complete.
   */
  public Optional<Map<String,Object>> beginOrGet(String idemKey, String requestHash){
    try {
      // Reserve without throwing on duplicates; creates the row if new.
      jdbc.update("""
        INSERT INTO idempotency_keys(idem_key, request_hash, response)
        VALUES (?,?, NULL)
        ON CONFLICT (idem_key) DO NOTHING
      """, idemKey, requestHash);

      // Load current state for this key
      List<Map<String,Object>> rows = jdbc.queryForList(
        "SELECT request_hash, response::text AS response FROM idempotency_keys WHERE idem_key=?", idemKey);
      if (rows.isEmpty()) return Optional.empty(); // should not happen

      Map<String,Object> row = rows.get(0);
      String storedHash = (String) row.get("request_hash");
      if (!requestHash.equals(storedHash)) {
        throw new IllegalStateException("Idempotency key reuse with different payload");
      }
      String respJson = (String) row.get("response");
      if (respJson == null) {
        // Previous attempt didn't finish; allow retry to proceed
        return Optional.empty();
      }
      Map<String,Object> parsed = om.readValue(respJson, new TypeReference<Map<String,Object>>() {});
      // Normalize numeric fields so Map.equals works across stored vs live responses
      Object v = parsed.get("version");
      if (v instanceof Number num && !(num instanceof Long)) {
        parsed.put("version", num.longValue());
      }
      Object d = parsed.get("newBalanceDelta");
      if (d instanceof Number num2 && !(num2 instanceof Long)) {
        parsed.put("newBalanceDelta", num2.longValue());
      }
      return Optional.of(parsed);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void complete(String idemKey, Map<String,Object> response){
    try {
      jdbc.update("""
        UPDATE idempotency_keys
        SET response = ?::jsonb
        WHERE idem_key = ? AND response IS NULL
      """, om.writeValueAsString(response), idemKey);
    } catch (Exception e){
      throw new RuntimeException(e);
    }
  }
}