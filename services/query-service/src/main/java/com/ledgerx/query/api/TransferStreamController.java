package com.ledgerx.query.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/stream")
public class TransferStreamController {
  private final JdbcTemplate jdbc;
  private final ExecutorService pool = Executors.newCachedThreadPool();

  public TransferStreamController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  private static OffsetDateTime toOdt(Object ts) {
    if (ts instanceof OffsetDateTime odt) return odt;
    if (ts instanceof java.sql.Timestamp t) return t.toInstant().atOffset(ZoneOffset.UTC);
    if (ts instanceof LocalDateTime ldt) return ldt.atOffset(ZoneOffset.UTC);
    throw new IllegalArgumentException("Unsupported timestamp type: " + ts);
  }

  @GetMapping(path = "/transfers", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamTransfers(
      @RequestParam(value = "since", required = false) String sinceIso,
      @RequestParam(value = "intervalMs", defaultValue = "1000") long intervalMs) {
    @SuppressWarnings("ConstantConditions")
    OffsetDateTime initial = (sinceIso != null)
        ? OffsetDateTime.parse(sinceIso)
        : OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(2);

    // Clamp poll interval between 250ms and 5000ms
    long pollMs = Math.max(250L, Math.min(intervalMs, 5000L));

    final SseEmitter emitter = new SseEmitter(0L); // no timeout

    pool.submit(() -> {
      OffsetDateTime lastTs = initial;
      String lastId = ""; // tie-breaker for equal timestamps

      try {
        while (true) {
          // Fetch strictly-after (ts,id) using tuple ordering to avoid skips/dupes
          List<Map<String, Object>> rows = jdbc.queryForList(
              "SELECT transfer_id, from_account, to_account, amount_cents, currency, status, updated_at " +
              "FROM transfers " +
              "WHERE (updated_at > ?) OR (updated_at = ? AND transfer_id > ?) " +
              "ORDER BY updated_at ASC, transfer_id ASC " +
              "LIMIT 200",
              Timestamp.from(lastTs.toInstant()),
              Timestamp.from(lastTs.toInstant()),
              lastId
          );

          for (Map<String, Object> row : rows) {
            try {
              emitter.send(SseEmitter.event().name("row").data(row));
            } catch (IOException ignore) {
              emitter.complete();
              return;
            }
            // Advance watermark using robust timestamp conversion
            lastTs = toOdt(row.get("updated_at"));
            Object idObj = row.get("transfer_id");
            lastId = (idObj instanceof String s) ? s : String.valueOf(idObj);
          }

          // Heartbeat to keep intermediaries from closing the connection
          try { emitter.send(SseEmitter.event().name("heartbeat").data("ok")); } catch (IOException ignored) {}

          Thread.sleep(pollMs);
        }
      } catch (Exception e) {
        try { emitter.completeWithError(e); } catch (Exception ignored) {}
      }
    });

    emitter.onTimeout(emitter::complete);
    emitter.onCompletion(() -> {});
    return emitter;
  }
}