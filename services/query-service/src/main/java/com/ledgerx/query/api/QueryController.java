package com.ledgerx.query.api;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class QueryController {
  private final JdbcTemplate jdbc;
  public QueryController(JdbcTemplate jdbc){ this.jdbc = jdbc; }

  @GetMapping("/accounts/{id}")
  public Map<String,Object> balance(@PathVariable String id){
    List<Map<String,Object>> rows = jdbc.queryForList(
      "SELECT account_id,currency,balance_cents,updated_at FROM account_balance WHERE account_id=?", id);
    if (rows.isEmpty()){
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "account not found");
    }
    return rows.get(0);
  }

  @GetMapping("/accounts")
  public List<Map<String,Object>> listAccounts(@RequestParam(value = "q", required = false) String q) {
    if (q != null && !q.isBlank()) {
      return jdbc.queryForList(
          "SELECT account_id,currency,balance_cents,updated_at " +
          "FROM account_balance " +
          "WHERE LOWER(account_id) LIKE LOWER(?) " +
          "ORDER BY updated_at DESC",
          "%" + q + "%"
      );
    }
    return jdbc.queryForList(
        "SELECT account_id,currency,balance_cents,updated_at " +
        "FROM account_balance " +
        "ORDER BY updated_at DESC"
    );
  }

// Existing imports + class omitted for brevity

@GetMapping("/transfers")
public List<Map<String,Object>> listTransfers(
    @RequestParam(value = "limit", defaultValue = "50") int limit,
    @RequestParam(value = "account", required = false) String account) {

  if (account != null && !account.isBlank()) {
    return jdbc.queryForList(
        "SELECT transfer_id,from_account,to_account,amount_cents,currency,status,updated_at " +
        "FROM transfers " +
        "WHERE from_account = ? OR to_account = ? " +
        "ORDER BY updated_at DESC LIMIT ?",
        account, account, limit
    );
  }

  return jdbc.queryForList(
      "SELECT transfer_id,from_account,to_account,amount_cents,currency,status,updated_at " +
      "FROM transfers ORDER BY updated_at DESC LIMIT ?",
      limit
  );
}
}