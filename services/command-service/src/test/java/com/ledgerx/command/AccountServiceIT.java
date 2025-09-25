package com.ledgerx.command;

import com.ledgerx.command.app.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

@Testcontainers
@SpringBootTest(classes = com.ledgerx.command.CommandServiceApplication.class, properties = {"spring.task.scheduling.enabled=false", "ledgerx.outbox.enabled=false"})
class AccountServiceIT {

  @Container static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
      .withDatabaseName("ledgerx").withUsername("ledgerx").withPassword("ledgerx");

  @Container static RedpandaContainer KAFKA = new RedpandaContainer("docker.redpanda.com/redpandadata/redpanda:v24.1.7");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r){
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
    r.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    // isolate Flyway
    r.add("spring.flyway.table", () -> "flyway_history_command_test");
    r.add("spring.flyway.baseline-on-migrate", () -> true);
  }

  @Autowired AccountService svc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void credit_is_idempotent() {
    var acc = "acc_it_" + System.currentTimeMillis();
    // open
    var open = svc.open(acc, "USD");
    Assertions.assertEquals("ok", open.get("status"));

    // credit once
    Map<String,Object> r1 = svc.credit(acc, 5000L, "USD", "idem-1");
    // credit again with same key
    Map<String,Object> r2 = svc.credit(acc, 5000L, "USD", "idem-1");
    Assertions.assertEquals(r1, r2, "same idempotency key should return same response");

    // event_store should have exactly 2 events: AccountOpened + FundsCredited
    Integer count = jdbc.queryForObject(
        "SELECT COUNT(*) FROM event_store WHERE aggregate_type='Account' AND aggregate_id=?", Integer.class, acc);
    Assertions.assertEquals(2, count);
  }
}