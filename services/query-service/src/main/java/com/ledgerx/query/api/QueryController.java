package com.ledgerx.query.api;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;

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
    @RequestParam(defaultValue = "50") int limit,
    @RequestParam(required = false) String status,
    @RequestParam(required = false) String account,
    @RequestParam(required = false) String since,
    @RequestParam(required = false) String until,
    @RequestParam(required = false) String afterUpdatedAt,
    @RequestParam(required = false) String afterId
) {
  StringBuilder sql = new StringBuilder(
    "SELECT transfer_id, from_account, to_account, amount_cents, currency, status, updated_at " +
    "FROM transfers WHERE 1=1 "
  );
  List<Object> args = new ArrayList<>();

  if (status != null && !status.isBlank()) {
    sql.append("AND status = ? ");
    args.add(status);
  }
  if (account != null && !account.isBlank()) {
    sql.append("AND (from_account = ? OR to_account = ?) ");
    args.add(account);
    args.add(account);
  }
  if (since != null && !since.isBlank()) {
    sql.append("AND updated_at >= ? ");
    args.add(Timestamp.from(Instant.parse(since)));
  }
  if (until != null && !until.isBlank()) {
    sql.append("AND updated_at < ? ");
    args.add(Timestamp.from(Instant.parse(until)));
  }
  if (afterUpdatedAt != null && afterId != null) {
    Timestamp ts = Timestamp.from(Instant.parse(afterUpdatedAt));
    sql.append("AND (updated_at < ? OR (updated_at = ? AND transfer_id < ?)) ");
    args.add(ts);
    args.add(ts);
    args.add(afterId);
  }

  sql.append("ORDER BY updated_at DESC, transfer_id DESC ");
  sql.append("LIMIT ? ");
  args.add(Math.max(1, Math.min(500, limit)));

  return jdbc.queryForList(sql.toString(), args.toArray());
}
}