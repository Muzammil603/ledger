package com.ledgerx.query.api;

import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
  private final JdbcTemplate jdbc;

  public AdminController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @GetMapping("/stats")
  public Map<String, Object> stats() {
    Map<String, Object> out = new HashMap<>();
    Long accounts = jdbc.queryForObject("SELECT COUNT(*) FROM account_balance", Long.class);
    Long transfers = jdbc.queryForObject("SELECT COUNT(*) FROM transfers", Long.class);
    OffsetDateTime accLast = jdbc.queryForObject("SELECT MAX(updated_at) FROM account_balance", OffsetDateTime.class);
    OffsetDateTime trnLast = jdbc.queryForObject("SELECT MAX(updated_at) FROM transfers", OffsetDateTime.class);

    out.put("accounts_count", accounts == null ? 0L : accounts);
    out.put("transfers_count", transfers == null ? 0L : transfers);
    out.put("last_account_update", accLast);
    out.put("last_transfer_update", trnLast);
    return out;
  }
}