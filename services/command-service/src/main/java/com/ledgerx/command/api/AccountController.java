package com.ledgerx.command.api;

import com.ledgerx.command.app.AccountService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

record OpenReq(String accountId, String currency) {}
record MoneyReq(long amountCents, String currency, String idempotencyKey) {}

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
  private final AccountService svc;
  public AccountController(AccountService svc){ this.svc = svc; }

  @PostMapping
  public Map<String,Object> open(@RequestBody OpenReq req){
    return svc.open(req.accountId(), req.currency());
  }

  @PostMapping("/{id}/credit")
  public Map<String,Object> credit(@PathVariable String id, @RequestBody MoneyReq req){
    return svc.credit(id, req.amountCents(), req.currency(), req.idempotencyKey());
  }

  @PostMapping("/{id}/debit")
  public Map<String,Object> debit(@PathVariable String id, @RequestBody MoneyReq req){
    return svc.debit(id, req.amountCents(), req.currency(), req.idempotencyKey());
  }
}