package com.ledgerx.command;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@EnableScheduling
@SpringBootApplication
public class CommandServiceApplication {
  public static void main(String[] args){ SpringApplication.run(CommandServiceApplication.class, args); }
  @Bean
@ConditionalOnMissingBean(MeterRegistry.class)
public MeterRegistry meterRegistry() {
  return new SimpleMeterRegistry();
}
}