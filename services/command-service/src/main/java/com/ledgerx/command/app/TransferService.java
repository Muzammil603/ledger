package com.ledgerx.command.app;

import com.ledgerx.command.idempotency.IdempotencyRepository;
import com.ledgerx.command.store.EventStoreRepository;
import com.ledgerx.command.store.OutboxRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TransferService {
  private final EventStoreRepository store;
  private final OutboxRepository outbox;
  private final IdempotencyRepository idem;
  private final AccountService accounts;

  private static final String TRANSFER_TOPIC = "ledgerx.transfers.events.v1";

  public TransferService(EventStoreRepository store,
                         OutboxRepository outbox,
                         IdempotencyRepository idem,
                         AccountService accounts) {
    this.store = store; this.outbox = outbox; this.idem = idem; this.accounts = accounts;
  }

  public Map<String,Object> transfer(String transferId, String from, String to, long amountCents,
                                     String currency, String idemKey) {
    if (from.equals(to)) throw new IllegalStateException("from == to");
    if (amountCents <= 0) throw new IllegalStateException("amount must be > 0");

    Map<String,Object> body = Map.<String,Object>of("op","transfer","transferId",transferId,"from",from,"to",to,"amountCents",amountCents,"currency",currency);
    var existing = idem.beginOrGet(idemKey, sha256(body.toString()));
    if (existing.isPresent()) return existing.get();

    try {
      // 1) Request
      long v1 = nextVersion(transferId);
      Map<String,Object> reqPayload = Map.<String,Object>of("transferId", transferId, "from", from, "to", to, "amountCents", amountCents, "currency", currency);
      appendTransfer("TransferRequested", transferId, v1, reqPayload, Map.<String,Object>of("idempotencyKey", idemKey));

      // 2) Debit source
      try {
        accounts.debit(from, amountCents, currency, transferId + ":debit"); // idempotent
      } catch (IllegalStateException ex) {
        // insufficient funds or not opened → fail
        long vFail = nextVersion(transferId);
        appendTransfer("TransferFailed", transferId, vFail, Map.<String,Object>of("reason", ex.getMessage()), Map.<String,Object>of());
        Map<String,Object> resp = Map.<String,Object>of("status","failed","transferId",transferId,"reason",ex.getMessage());
        idem.complete(idemKey, resp);
        return resp;
      }

      long v2 = nextVersion(transferId);
      appendTransfer("SourceDebited", transferId, v2, Map.<String,Object>of("from", from, "amountCents", amountCents), Map.<String,Object>of());

      // 3) Credit destination
      try {
        accounts.credit(to, amountCents, currency, transferId + ":credit");
      } catch (Exception ex) {
        // 3b) Compensate: credit source back
        try { accounts.credit(from, amountCents, currency, transferId + ":comp"); }
        catch (Exception ignore) { /* best-effort; real system would alert */ }

        long vFail = nextVersion(transferId);
        appendTransfer("TransferFailed", transferId, vFail, Map.<String,Object>of("reason", "credit_failed:" + ex.getMessage()), Map.<String,Object>of("compensated", true));
        Map<String,Object> resp = Map.<String,Object>of("status","failed","transferId",transferId,"reason","credit_failed","compensated", true);
        idem.complete(idemKey, resp);
        return resp;
      }

      long v3 = nextVersion(transferId);
      appendTransfer("DestinationCredited", transferId, v3, Map.<String,Object>of("to", to, "amountCents", amountCents), Map.<String,Object>of());

      long v4 = nextVersion(transferId);
      appendTransfer("TransferCompleted", transferId, v4, Map.<String,Object>of(), Map.<String,Object>of());

      Map<String,Object> resp = Map.<String,Object>of("status","ok","transferId",transferId);
      idem.complete(idemKey, resp);
      return resp;

    } catch (RuntimeException e){
      // leave idempotency row without response → client can safely retry with same key
      throw e;
    }
  }

  private long nextVersion(String transferId){
    List<Map<String,Object>> hist = store.load("Transfer", transferId);
    return hist.size() + 1L;
  }

  private void appendTransfer(String type, String transferId, long version,
                              Map<String,Object> payload, Map<String,Object> meta){
    store.append("Transfer", transferId, version, type, payload, meta);
    Map<String,Object> value = Map.<String,Object>of("eventType", type, "aggregateId", transferId, "version", version,
        "payload", payload, "metadata", meta);
    outbox.stage(TRANSFER_TOPIC, transferId, value, Map.<String,Object>of());
  }

  private static String sha256(String input){
    try {
      var md = java.security.MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(hash.length * 2);
      for (byte b : hash) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) { throw new RuntimeException(e); }
  }
}