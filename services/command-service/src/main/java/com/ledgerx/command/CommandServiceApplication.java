package com.ledgerx.command;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CommandServiceApplication {
  public static void main(String[] args){ SpringApplication.run(CommandServiceApplication.class, args); }
}