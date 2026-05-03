# Нагрузочный тест кэширования списка ссылок

Сценарий проверяет `GET /links` на базе из 1000 чатов и 100000 подписок. На каждый чат приходится 100 ссылок.

Операции распределяются так:

- 99% запросов: `GET /links`
- 1% итераций: `POST /links` и последующий `DELETE /links` для проверки инвалидации кэша

## Подготовка данных

```bash
docker compose up -d postgres
docker compose run --rm migrations
docker compose exec -T postgres psql -U postgres -d link_tracker < load-tests/tracked-links-cache/seed.sql
```

## Запуск k6

Перед запуском поднимите `scrapper` в нужном режиме.

Без кэша:

```bash
TRACKED_LINKS_CACHE_ENABLED=false java -jar ./scrapper/target/scrapper-0.0.1.jar
```

Valkey cluster:

```bash
TRACKED_LINKS_CACHE_ENABLED=true \
VALKEY_CLUSTER_ENABLED=true \
VALKEY_CLUSTER_NODES=localhost:6379,localhost:6380,localhost:6381 \
java -jar ./scrapper/target/scrapper-0.0.1.jar
```

Client-side cache проверяется со standalone Valkey:

```bash
docker run -d --name link-tracker-valkey-standalone -p 6389:6379 valkey/valkey:8-alpine
TRACKED_LINKS_CACHE_ENABLED=true \
TRACKED_LINKS_CLIENT_SIDE_CACHE_ENABLED=true \
VALKEY_CLUSTER_ENABLED=false \
VALKEY_HOST=localhost \
VALKEY_PORT=6389 \
java -jar ./scrapper/target/scrapper-0.0.1.jar
```

Linux:

```bash
docker run --rm --network host \
  -v "$PWD/load-tests/tracked-links-cache:/scripts:ro" \
  grafana/k6:0.55.0 run /scripts/tracked-links-cache.js
```

Параметры можно переопределить через переменные:

```bash
docker run --rm --network host \
  -e BASE_URL="http://localhost:8081" \
  -e VUS="16" \
  -e RAMP_UP_DURATION="1m" \
  -e STAGE_DURATION="5m" \
  -e RAMP_DOWN_DURATION="30s" \
  -e WRITE_RATIO="0.01" \
  -v "$PWD/load-tests/tracked-links-cache:/scripts:ro" \
  -v "$PWD/target/load-test-results:/results" \
  grafana/k6:0.55.0 run --summary-export=/results/result.json /scripts/tracked-links-cache.js
```
