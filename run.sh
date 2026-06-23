#!/usr/bin/env bash
set -e

# Fix filename if needed
[ -f docker.compose.yml ] && [ ! -f docker-compose.yml ] && \
  mv docker.compose.yml docker-compose.yml

echo "▶ Starting PostgreSQL..."
docker compose up postgres -d

echo "▶ Waiting for DB to be ready..."
until docker exec chatapp-postgres pg_isready -U chatapp -q 2>/dev/null; do
  printf '.'
  sleep 1
done
echo -e "\n✓ PostgreSQL ready"

echo "▶ Starting Spring Boot app..."
mvn spring-boot:run