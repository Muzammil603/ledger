#!/usr/bin/env bash
set -e
echo "==> Starting/ensuring infra..."
cat > docker-compose.yml <<'YML'
version: "3.9"
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_USER: ledgerx
      POSTGRES_PASSWORD: ledgerx
      POSTGRES_DB: ledgerx
    ports: ["5432:5432"]
    volumes: ["pgdata:/var/lib/postgresql/data"]
  redpanda:
    image: docker.redpanda.com/redpandadata/redpanda:v24.1.11
    command: ["redpanda","start","--overprovisioned","--smp","1","--memory","1G","--reserve-memory","0M","--node-id","0","--check=false","--set","redpanda.enable_idempotence=true"]
    ports: ["9092:9092","9644:9644"]
volumes: { pgdata: {} }
YML

docker compose up -d
sleep 5
echo "==> Containers:"
docker ps --format '  - {{.Names}}\t{{.Status}}'

echo "==> Postgres ping:"
PGPASSWORD=ledgerx psql -h localhost -U ledgerx -d ledgerx -c "select now() as pg_ok;" || true

echo "==> Kafka metadata:"
kcat -b localhost:9092 -L | sed -n '1,80p' || true

echo "==> Kafka produce/consume test:"
TOPIC=ledgerx.test.$RANDOM
printf 'hello-ledgerx\n' | kcat -P -b localhost:9092 -t "$TOPIC" -k key1 && \
kcat -C -b localhost:9092 -t "$TOPIC" -o -1 -e -q && echo "  consumed last message ✅" || echo "  consume failed ❌"
