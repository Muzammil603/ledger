package com.ledgerx.command.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

  @Bean
  public NewTopic accountsTopicV2() {
    return TopicBuilder.name("ledgerx.accounts.events.v2")
        .partitions(1)
        .replicas(1)
        .build();
  }

  @Bean
  public NewTopic transfersTopicV1() {
    return TopicBuilder.name("ledgerx.transfers.events.v1")
        .partitions(1)
        .replicas(1)
        .build();
  }
}