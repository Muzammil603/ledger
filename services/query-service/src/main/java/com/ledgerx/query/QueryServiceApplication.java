package com.ledgerx.query;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication
public class QueryServiceApplication {
  public static void main(String[] args) {
    SpringApplication.run(QueryServiceApplication.class, args);
  }
}