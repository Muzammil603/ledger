package com.ledgerx.command.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
public class OutboxRepository {
  private final JdbcTemplate jdbc; private final ObjectMapper om;
  public OutboxRepository(JdbcTemplate jdbc, ObjectMapper om){ this.jdbc=jdbc; this.om=om; }

  public void stage(String topic, String key, Object value, Map<String,Object> headers){
    try {
      jdbc.update("""
        INSERT INTO outbox(topic,key,value,headers)
        VALUES (?,?,?::jsonb,?::jsonb)
      """, topic, key, om.writeValueAsString(value), om.writeValueAsString(headers));
    } catch(Exception e){ throw new RuntimeException(e); }
  }
}