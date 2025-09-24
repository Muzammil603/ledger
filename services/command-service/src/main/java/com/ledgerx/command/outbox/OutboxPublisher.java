package com.ledgerx.command.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.List; import java.util.Map; import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class OutboxPublisher {
  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private final JdbcTemplate jdbc; private final KafkaTemplate<String,String> kafka;

  private final Timer publishTimer;
  
  
  private final MeterRegistry registry;
  private final AtomicInteger unsentGauge;

  public OutboxPublisher(JdbcTemplate j, KafkaTemplate<String,String> k, MeterRegistry meter){
    this.jdbc=j; this.kafka=k;
    this.registry = meter; 
    this.publishTimer = meter.timer("ledgerx.outbox.publish.latency");
    this.unsentGauge = meter.gauge("ledgerx.outbox.unsent", new AtomicInteger(0));
  }

  @Scheduled(fixedDelay = 500)
  @Transactional
  public void publishBatch(){
    // refresh gauge
    try {
      Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM outbox WHERE sent_at IS NULL", Integer.class);
      if (c != null) unsentGauge.set(c);
    } catch (Exception ignore){}

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
        AtomicInteger part = new AtomicInteger(-1);
        publishTimer.record(() -> {
          try {
            var result = kafka.send(new ProducerRecord<>(topic, key, value)).get(10, TimeUnit.SECONDS);
            var meta = result.getRecordMetadata();
            if (meta != null) part.set(meta.partition());
          } catch (Exception e) { throw new RuntimeException(e); }
        });
        jdbc.update("UPDATE outbox SET sent_at=NOW(), attempts=attempts+1 WHERE id=?", id);
        String partitionLabel = part.get() >= 0 ? String.valueOf(part.get()) : "na";
        registry.counter("ledgerx.outbox.publish.total",
        "topic", topic,
        "partition", partitionLabel,
        "result", "success",
        "error", "none").increment();
      } catch (Exception e){
        jdbc.update("UPDATE outbox SET attempts=attempts+1 WHERE id=?", id);
        registry.counter("ledgerx.outbox.publish.total",
        "topic", topic,
        "partition", "na",
        "result", "failure",
        "error", e.getClass().getSimpleName()).increment();
        log.warn("Outbox publish failed id={} topic={} key={}: {}", id, topic, key, e.toString());
      }
    }
  }
}