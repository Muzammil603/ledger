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
}