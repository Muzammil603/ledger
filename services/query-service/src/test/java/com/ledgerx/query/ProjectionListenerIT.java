package com.ledgerx.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.apache.kafka.common.serialization.StringSerializer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Testcontainers
@SpringBootTest
class ProjectionListenerIT {

  @SuppressWarnings("resource") @Container static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
      .withDatabaseName("ledgerx").withUsername("ledgerx").withPassword("ledgerx");

  @SuppressWarnings("resource") @Container static RedpandaContainer KAFKA = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.1.7");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r){
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
    r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    // isolate Flyway
    r.add("spring.flyway.table", () -> "flyway_history_query_test");
    r.add("spring.flyway.baseline-on-migrate", () -> true);
    // use a fresh group so consumer reads from beginning
    r.add("spring.kafka.consumer.group-id", () -> "ledgerx-projections-test");
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper om;

  KafkaTemplate<String, String> kafka;
  ProducerFactory<String,String> pf;

  @BeforeEach
  void mkProducer(){
    Map<String,Object> cfg = new HashMap<>();
    cfg.put("bootstrap.servers", KAFKA.getBootstrapServers());
    cfg.put("key.serializer", StringSerializer.class);
    cfg.put("value.serializer", StringSerializer.class);
    pf = new DefaultKafkaProducerFactory<>(cfg);
    kafka = new KafkaTemplate<>(pf);
  }

  @AfterEach
  void closeProducer(){
    if (kafka != null) {
      try { kafka.destroy(); } catch (Exception ignore) {}
    }
    if (pf instanceof DefaultKafkaProducerFactory<?,?> dpf) {
      try { dpf.destroy(); } catch (Exception ignore) {}
    }
  }

  @Test
  void projection_applies_account_open_and_credit() throws Exception {
    String topic = "ledgerx.accounts.events.v2";
    String acc = "acc_proj";

    // publish AccountOpened v1
    var opened = Map.of(
        "eventType","AccountOpened",
        "aggregateId", acc,
        "version", 1,
        "payload", Map.of("accountId", acc, "currency","USD"),
        "metadata", Map.of()
    );
    kafka.send(topic, acc, om.writeValueAsString(opened)).get();

    // publish FundsCredited v2
    var credited = Map.of(
        "eventType","FundsCredited",
        "aggregateId", acc,
        "version", 2,
        "payload", Map.of("accountId", acc, "amountCents", 1500, "currency","USD"),
        "metadata", Map.of()
    );
    kafka.send(topic, acc, om.writeValueAsString(credited)).get();

    // Await projection
    Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
      Integer c = jdbc.queryForObject(
          "SELECT balance_cents FROM account_balance WHERE account_id=?", Integer.class, acc);
      Assertions.assertEquals(1500, c);
    });
  }
}