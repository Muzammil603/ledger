package com.ledgerx.command.app;

import com.ledgerx.command.idempotency.IdempotencyRepository;
import com.ledgerx.command.store.EventStoreRepository;
import com.ledgerx.command.store.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@Service
public class AccountService {
  private final EventStoreRepository store;
  private final OutboxRepository outbox;
  private final IdempotencyRepository idem;
  private static final String TOPIC = "ledgerx.accounts.events.v2";

  public AccountService(EventStoreRepository store, OutboxRepository outbox, IdempotencyRepository idem){
    this.store = store; this.outbox = outbox; this.idem = idem;
  }

  @Transactional
  public Map<String,Object> open(String id, String currency){
    List<Map<String,Object>> hist = store.load("Account", id);
    if (!hist.isEmpty()) throw new IllegalStateException("Account already opened");
    long version = 1L;
    Map<String,Object> payload = Map.<String,Object>of("accountId", id, "currency", currency);
    append("AccountOpened", id, version, payload, Map.<String,Object>of());
    Map<String,Object> resp = Map.<String,Object>of("status","ok","accountId", id, "version", version);
    return resp;
  }

  @Transactional
  public Map<String,Object> credit(String id, long amountCents, String currency, String idemKey){
    Map<String,Object> body = Map.<String,Object>of("op","credit","id",id,"amountCents",amountCents,"currency",currency);
    String hash = sha256(body.toString());
    var existing = idem.beginOrGet(idemKey, hash);
    if (existing.isPresent()) return existing.get(); // already processed or in-flight

    long version = store.load("Account", id).size() + 1L;
    Map<String,Object> payload = Map.<String,Object>of("accountId", id, "amountCents", amountCents, "currency", currency);
    append("FundsCredited", id, version, payload, Map.<String,Object>of("idempotencyKey", idemKey));

    Map<String,Object> resp = Map.<String,Object>of("status","ok","accountId", id, "version", version, "newBalanceDelta", amountCents);
    idem.complete(idemKey, resp);
    return resp;
  }

  @Transactional
  public Map<String,Object> debit(String id, long amountCents, String currency, String idemKey){
    Map<String,Object> body = Map.<String,Object>of("op","debit","id",id,"amountCents",amountCents,"currency",currency);
    String hash = sha256(body.toString());
    var existing = idem.beginOrGet(idemKey, hash);
    if (existing.isPresent()) return existing.get();

    var hist = store.load("Account", id);
    if (hist.isEmpty()) throw new IllegalStateException("Account not opened");
    long balance = computeBalance(hist);
    if (amountCents > balance) throw new IllegalStateException("Insufficient funds");

    long version = hist.size() + 1L;
    Map<String,Object> payload = Map.<String,Object>of("accountId", id, "amountCents", amountCents, "currency", currency);
    append("FundsDebited", id, version, payload, Map.<String,Object>of("idempotencyKey", idemKey));

    Map<String,Object> resp = Map.<String,Object>of("status","ok","accountId", id, "version", version, "newBalanceDelta", -amountCents);
    idem.complete(idemKey, resp);
    return resp;
  }

  private void append(String type, String aggId, long version, Map<String,Object> payload, Map<String,Object> meta){
    store.append("Account", aggId, version, type, payload, meta);
    Map<String,Object> value = Map.<String,Object>of("eventType", type, "aggregateId", aggId, "version", version,
                       "payload", payload, "metadata", meta);
    outbox.stage(TOPIC, aggId, value, Map.<String,Object>of());
  }

  private static long computeBalance(List<Map<String,Object>> events){
    long cents = 0;
    for (var e: events){
      String t = (String)e.get("event_type");
      Map<?,?> p = (Map<?,?>) e.get("payload");
      if ("FundsCredited".equals(t)) cents += ((Number)p.get("amountCents")).longValue();
      else if ("FundsDebited".equals(t)) cents -= ((Number)p.get("amountCents")).longValue();
    }
    return cents;
  }

  private static String sha256(String s){
    try {
      var md = MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : d) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e){ throw new RuntimeException(e); }
  }
}