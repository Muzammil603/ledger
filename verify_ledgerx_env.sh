#!/usr/bin/env bash
set -e
ok(){ printf "\033[32m[OK]\033[0m  %s\n" "$*"; }
bad(){ printf "\033[31m[ERR]\033[0m %s\n" "$*"; }

# Java
if command -v java >/dev/null; then
  JV=$(java -version 2>&1 | head -n1)
  echo "$JV" | grep -q '"21' && ok "$JV" || bad "Need Java 21 (Temurin@21). Got: $JV"
else bad "Java not found"; fi

# Maven
command -v mvn >/dev/null && ok "$(mvn -v | head -n1)" || bad "Maven not found"

# Git
command -v git >/dev/null && ok "$(git --version)" || bad "Git not found"

# Docker
if command -v docker >/dev/null; then
  docker info >/dev/null 2>&1 && ok "$(docker --version)" || bad "Docker engine not reachable (open Docker Desktop)"
else bad "Docker not found"; fi

# psql
command -v psql >/dev/null && ok "$(psql --version)" || bad "psql not found"

# kcat
command -v kcat >/dev/null && ok "$(kcat -V | head -n1)" || bad "kcat not found"

# k6
command -v k6 >/dev/null && ok "$(k6 version)" || bad "k6 not found"
