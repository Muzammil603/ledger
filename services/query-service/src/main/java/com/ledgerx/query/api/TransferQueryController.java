package com.ledgerx.query.api;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TransferQueryController {
  private final JdbcTemplate jdbc;
  public TransferQueryController(JdbcTemplate jdbc){ this.jdbc=jdbc; }

  @GetMapping("/transfers/{id}")
  public Map<String,Object> get(@PathVariable String id){
    List<Map<String,Object>> rows = jdbc.queryForList(
        "SELECT transfer_id, from_account, to_account, amount_cents, currency, status, updated_at FROM transfers WHERE transfer_id=?",
        id);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "transfer not found");
    return rows.get(0);
  }
}