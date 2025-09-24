package com.ledgerx.command.api;

import com.ledgerx.command.app.TransferService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

record TransferReq(String transferId, String fromAccount, String toAccount,
                   long amountCents, String currency, String idempotencyKey) {}

@RestController
@RequestMapping("/api/transfers")
public class TransferController {
  private final TransferService transfers;
  public TransferController(TransferService transfers){ this.transfers = transfers; }

  @PostMapping
  public Map<String,Object> start(@RequestBody TransferReq req){
    return transfers.transfer(
        req.transferId(),
        req.fromAccount(),
        req.toAccount(),
        req.amountCents(),
        req.currency(),
        req.idempotencyKey()
    );
  }
}