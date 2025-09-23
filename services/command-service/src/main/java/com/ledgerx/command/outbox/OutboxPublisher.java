package com.ledgerx.command.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List; import java.util.Map; import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private final JdbcTemplate jdbc; private final KafkaTemplate<String,String> kafka;
  public OutboxPublisher(JdbcTemplate j, KafkaTemplate<String,String> k){ this.jdbc=j; this.kafka=k; }

  @Scheduled(fixedDelay = 500)
  @Transactional
  public void publishBatch(){
    List<Map<String,Object>> rows = jdbc.queryForList("""
      SELECT id, topic, key, value::text AS value
      FROM outbox WHERE sent_at IS NULL ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 200
    """);
    for (var r: rows){
      Long id = ((Number)r.get("id")).longValue();
      String topic = (String) r.get("topic");
      String key = (String) r.get("key");
      String value = (String) r.get("value");
      try {
        kafka.send(new ProducerRecord<>(topic, key, value)).get(10, TimeUnit.SECONDS);
        jdbc.update("UPDATE outbox SET sent_at=NOW(), attempts=attempts+1 WHERE id=?", id);
      } catch (Exception e){
        jdbc.update("UPDATE outbox SET attempts=attempts+1 WHERE id=?", id);
        log.warn("Outbox publish failed id={} topic={} key={}: {}", id, topic, key, e.toString());
      }
    }
  }
}