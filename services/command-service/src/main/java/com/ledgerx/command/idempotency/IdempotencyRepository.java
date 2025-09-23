package com.ledgerx.command.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.dao.DuplicateKeyException;
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
      jdbc.update("INSERT INTO idempotency_keys(idem_key, request_hash) VALUES (?,?)", idemKey, requestHash);
      return Optional.empty(); // new reservation
    } catch (DuplicateKeyException dup) {
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
      try {
        Map<String,Object> parsed = om.readValue(respJson, new TypeReference<Map<String,Object>>() {});
        return Optional.of(parsed);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  public void complete(String idemKey, Map<String,Object> response){
    try {
      jdbc.update("UPDATE idempotency_keys SET response=?::jsonb WHERE idem_key=?",
          om.writeValueAsString(response), idemKey);
    } catch (Exception e){ throw new RuntimeException(e); }
  }
}