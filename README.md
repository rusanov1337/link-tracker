# LinkTracker

LinkTracker – Telegram-бот, который отслеживает изменения на веб-страницах и оперативно информирует пользователя о них.

Это шаблон проекта, который вам необходимо взять за основу для разработки своей системы.

## Telegram API

### Требования

- JDK 25
- Maven Wrapper (`mvnw`)

### Подготовка токена

Получите токен у `@BotFather` и задайте его через переменную окружения `TELEGRAM_TOKEN`.

PowerShell (текущая сессия):

```powershell
$env:TELEGRAM_TOKEN="telegram_bot_token"
```

PowerShell (сохранить для пользователя Windows):

```powershell
setx TELEGRAM_TOKEN "telegram_bot_token"
```

После `setx` откройте новое окно терминала.

Bash (Linux/macOS, текущая сессия):

```bash
export TELEGRAM_TOKEN="telegram_bot_token"
```

### Локальная проверка бота

Сборка и тесты:

```powershell
.\mvnw.cmd clean verify
```

```bash
./mvnw clean verify
```

Запуск bot-модуля:

```powershell
java -jar .\bot\target\bot-0.0.1.jar --app.telegram.polling-enabled=true
```

```bash
java -jar ./bot/target/bot-0.0.1.jar --app.telegram.polling-enabled=true
```

Проверка в Telegram:

- `/start`
- `/help`
- любая неизвестная команда, например `/abc`

## PostgreSQL, Kafka, Valkey, Bot и Scrapper

### Дополнительно требуется

- Docker
- `docker compose`

### Переменные окружения

Для локального запуска `bot` и `scrapper`:

```bash
export TELEGRAM_TOKEN="telegram_bot_token"
export SCRAPPER_BASE_URL="http://localhost:8081"
export SCRAPPER_DB_URL="jdbc:postgresql://localhost:5432/link_tracker"
export SCRAPPER_DB_USERNAME="postgres"
export SCRAPPER_DB_PASSWORD="postgres"
export SCRAPPER_DB_ACCESS_TYPE="SQL" # или ORM
export BOT_BASE_URL="http://localhost:8080"
export BOT_TRANSPORT="kafka"
export KAFKA_BOOTSTRAP_SERVERS="localhost:19092"
export KAFKA_SCHEMA_REGISTRY_URL="http://localhost:8085"
export VALKEY_HOST="localhost"
export VALKEY_PORT="6379"
export VALKEY_CLUSTER_ENABLED="true"
export VALKEY_CLUSTER_NODES="localhost:6379,localhost:6380,localhost:6381"
```

При необходимости можно также задать:

```bash
export GITHUB_TOKEN=""
export STACKOVERFLOW_KEY=""
export STACKOVERFLOW_ACCESS_TOKEN=""
export SCRAPPER_SCHEDULER_BATCH_SIZE="500"
export SCRAPPER_SCHEDULER_PARALLELISM="1"
export KAFKA_CONSUMER_MAX_ATTEMPTS="3"
export KAFKA_CONSUMER_RETRY_BACKOFF="1s"
export KAFKA_AUTO_REGISTER_SCHEMAS="true"
export TRACKED_LINKS_CACHE_ENABLED="true"
export TRACKED_LINKS_CACHE_TTL="10m"
export TRACKED_LINKS_CACHE_KEY_PREFIX="tracked-links"
export TRACKED_LINKS_CLIENT_SIDE_CACHE_ENABLED="false"
export TRACKED_LINKS_CLIENT_SIDE_CACHE_MAX_SIZE="1024"
export VALKEY_CLUSTER_MAX_REDIRECTS="3"
export HTTP_RETRY_MAX_ATTEMPTS="3"
export HTTP_RETRY_BACKOFF="500ms"
export HTTP_RETRYABLE_STATUSES="500,502,503,504"
export HTTP_CIRCUIT_BREAKER_SLIDING_WINDOW_SIZE="10"
export HTTP_CIRCUIT_BREAKER_MINIMUM_NUMBER_OF_CALLS="5"
export HTTP_CIRCUIT_BREAKER_FAILURE_RATE_THRESHOLD="50"
export HTTP_CIRCUIT_BREAKER_HALF_OPEN_CALLS="5"
export HTTP_CIRCUIT_BREAKER_OPEN_WAIT="5s"
export RATE_LIMITING_ENABLED="true"
export RATE_LIMITING_LIMIT_FOR_PERIOD="1000"
export RATE_LIMITING_LIMIT_REFRESH_PERIOD="1m"
export RATE_LIMITING_TIMEOUT_DURATION="0ms"
```

### Локальный запуск инфраструктуры

```bash
docker compose up -d postgres kafka-1 kafka-2 kafka-3 kafka-init schema-registry valkey-1 valkey-2 valkey-3 valkey-init
```

### Применение миграций отдельным контейнером

```bash
docker compose run --rm migrations
```

### Локальный запуск сервисов

Для локального запуска нужно запускать оба сервиса:

```bash
java -jar ./bot/target/bot-0.0.1.jar --app.telegram.polling-enabled=true
```

```bash
java -jar ./scrapper/target/scrapper-0.0.1.jar
```

Если запускаете из IDE:

- сначала поднимите `postgres`, `kafka-*`, `kafka-init`, `schema-registry`, `valkey-*` и `valkey-init` через `docker compose`
- затем запустите `bot`
- затем запустите `scrapper`

По умолчанию `scrapper` отправляет уведомления в `bot` через Kafka. Для возврата к синхронному режиму можно явно задать:

```bash
export BOT_TRANSPORT="http"
```

или

```bash
export BOT_TRANSPORT="grpc"
```

### Локальный запуск только scrapper

```bash
java -jar ./scrapper/target/scrapper-0.0.1.jar
```

При запуске `scrapper` из IDE миграции применяются автоматически.

### Асинхронная доставка уведомлений

- асинхронная доставка уведомлений `scrapper -> bot` через Kafka
- Avro-сообщения через Schema Registry
- retry и DLQ на стороне `bot`
- `Transactional Outbox` на стороне `scrapper`
- 3-брокерный Kafka-кластер в `docker-compose`

### Надежность HTTP-взаимодействий

Для исходящих HTTP-запросов `bot` и `scrapper` используются timeout, retry и circuit breaker. Retry выполняется с constant backoff только для HTTP-статусов из `HTTP_RETRYABLE_STATUSES`.

Настройки:

- `HTTP_RETRY_MAX_ATTEMPTS`
- `HTTP_RETRY_BACKOFF`
- `HTTP_RETRYABLE_STATUSES`
- `HTTP_CIRCUIT_BREAKER_SLIDING_WINDOW_SIZE`
- `HTTP_CIRCUIT_BREAKER_MINIMUM_NUMBER_OF_CALLS`
- `HTTP_CIRCUIT_BREAKER_FAILURE_RATE_THRESHOLD`
- `HTTP_CIRCUIT_BREAKER_HALF_OPEN_CALLS`
- `HTTP_CIRCUIT_BREAKER_OPEN_WAIT`

Для отправки уведомлений можно включить fallback: если основной HTTP-транспорт `scrapper -> bot` недоступен, уведомление отправляется через Kafka.

```bash
export BOT_TRANSPORT="http"
export BOT_FALLBACK_KAFKA_ENABLED="true"
```

Для входящих HTTP-запросов включен rate limiting по IP-адресу. При превышении лимита сервис возвращает `HTTP 429`.

Настройки:

- `RATE_LIMITING_ENABLED`
- `RATE_LIMITING_LIMIT_FOR_PERIOD`
- `RATE_LIMITING_LIMIT_REFRESH_PERIOD`
- `RATE_LIMITING_TIMEOUT_DURATION`

### Кэширование списка ссылок

`scrapper` кэширует ответ `GET /links` в Valkey. Ключ строится из заголовка `Tg-Chat-Id`, значение хранится как JSON с телом ответа.

Кэш автоматически инвалидируется при добавлении и удалении отслеживаемых ссылок. TTL, префикс ключа и параметры подключения задаются через конфигурацию:

- `TRACKED_LINKS_CACHE_ENABLED`
- `TRACKED_LINKS_CACHE_TTL`
- `TRACKED_LINKS_CACHE_KEY_PREFIX`
- `TRACKED_LINKS_CLIENT_SIDE_CACHE_ENABLED`
- `TRACKED_LINKS_CLIENT_SIDE_CACHE_MAX_SIZE`
- `VALKEY_HOST`
- `VALKEY_PORT`
- `VALKEY_CLUSTER_ENABLED`
- `VALKEY_CLUSTER_NODES`
- `VALKEY_CLUSTER_MAX_REDIRECTS`

Valkey-кластер в `docker-compose` состоит из трех master-нод без реплик. Слоты распределяются между master-нодами, данные сохраняются в named volumes.
Ограничение этой конфигурации: при падении одной ноды часть hash-слотов становится недоступна; для полноценной отказоустойчивости нужен кластер из шести нод `3 master + 3 replica`.
Client-side cache включается отдельно и работает для standalone-подключения к Valkey; при включенном `VALKEY_CLUSTER_ENABLED` используется только общий кэш в Valkey.

### Нагрузочная проверка кэширования

Сценарий находится в `load-tests/tracked-links-cache`. Он заполняет БД 1000 чатами и 100000 подписками, затем запускает k6 с профилем:

- 24 VU: `2 * 12` ядер локальной машины
- ramp up: 1 минута
- stage: 5 минут
- ramp down: 30 секунд
- пауза между итерациями: 50 мс
- 99% операций: `GET /links`
- 1% итераций: `POST /links` и `DELETE /links` для проверки инвалидации

Запуск подготовки данных:

```bash
docker compose up -d postgres valkey-1 valkey-2 valkey-3 valkey-init
docker compose run --rm migrations
docker compose exec -T postgres psql -U postgres -d link_tracker < load-tests/tracked-links-cache/seed.sql
```

Запуск k6:

```bash
docker run --rm --network host \
  -e BASE_URL="http://localhost:8081" \
  -e VUS="24" \
  -e RAMP_UP_DURATION="1m" \
  -e STAGE_DURATION="5m" \
  -e RAMP_DOWN_DURATION="30s" \
  -e WRITE_RATIO="0.01" \
  -v "$PWD/load-tests/tracked-links-cache:/scripts:ro" \
  -v "$PWD/target/load-test-results:/results" \
  grafana/k6:0.55.0 run --summary-export=/results/result.json /scripts/tracked-links-cache.js
```

Результаты локального прогона:

|           Режим            |     Запрос      |    RPS | Среднее, мс | p50, мс | p99, мс |    200 | 502/504 | 500 | Частые ошибки |
|----------------------------|-----------------|-------:|------------:|--------:|--------:|-------:|--------:|----:|---------------|
| Без кэша                   | `GET /links`    | 376.99 |        5.06 |    5.04 |    7.04 | 147041 |       0 |   0 | Нет           |
| Без кэша                   | `POST /links`   |   3.79 |       10.88 |   14.28 |   18.43 |   1477 |       0 |   0 | Нет           |
| Без кэша                   | `DELETE /links` |   3.79 |        2.52 |    2.21 |    6.58 |   1477 |       0 |   0 | Нет           |
| Valkey cluster             | `GET /links`    | 397.73 |        2.23 |    1.05 |    6.95 | 155130 |       0 |   0 | Нет           |
| Valkey cluster             | `POST /links`   |   4.13 |        8.10 |    4.74 |   17.55 |   1611 |       0 |   0 | Нет           |
| Valkey cluster             | `DELETE /links` |   4.13 |        2.53 |    2.39 |    5.67 |   1611 |       0 |   0 | Нет           |
| Valkey + client-side cache | `GET /links`    | 399.20 |        1.96 |    1.91 |    5.41 | 155700 |       0 |   0 | Нет           |
| Valkey + client-side cache | `POST /links`   |   4.03 |       10.49 |   13.73 |   18.98 |   1572 |       0 |   0 | Нет           |
| Valkey + client-side cache | `DELETE /links` |   4.03 |        2.58 |    2.44 |    4.82 |   1572 |       0 |   0 | Нет           |

Вывод: кэширование в Valkey снижает среднее время `GET /links` с 5.06 мс до 2.23 мс и медиану с 5.04 мс до 1.05 мс. Client-side cache в standalone-режиме дополнительно снижает среднее время чтения до 1.96 мс и p99 до 5.41 мс. Мутации не ускоряются так же заметно, потому что они выполняют запись в БД и инвалидацию кэша.

### Локальная проверка

- запустите `bot` и `scrapper`
- в Telegram выполните `/start`
- добавьте ссылку через `/track`
- дождитесь запуска планировщика в `scrapper`
- проверьте, что уведомление приходит в `bot`

При желании можно проверить инфраструктуру отдельно:

```bash
docker compose ps
```

### Kafka topics

`kafka-init` создает следующие топики:

- `link-updates`
- `link-updates-dlq`
- `processing-failure-reports`
- `processing-failure-reports-dlq`

Выбранные настройки:

- `partitions=3` для базового параллелизма
- `replication-factor=3` для отказоустойчивости к падению одного брокера
- `min.insync.replicas=2` вместе с `acks=all` для более надежной записи

### Проверка Valkey-кластера

```bash
docker compose exec valkey-1 valkey-cli cluster info
```

В рабочем состоянии команда должна вернуть `cluster_state:ok`.

### Переключение доступа к БД

- `SCRAPPER_DB_ACCESS_TYPE=SQL`
- `SCRAPPER_DB_ACCESS_TYPE=ORM`

### Настройки планировщика

- `SCRAPPER_SCHEDULER_BATCH_SIZE` — размер батча ссылок
- `SCRAPPER_SCHEDULER_PARALLELISM` — количество потоков для обработки батча

### Локальный тест для проверки интеграции Scrapper → Kafka → Bot

Тест, который можно запускать локально для проверки полного пути сообщения:

```bash
./mvnw -Pwith-e2e -pl build-report-aggregate -am -Dtest=BotScrapperContainerE2ETest -Dsurefire.failIfNoSpecifiedTests=false test
```

Для интеграционных тестов нужен запущенный Docker.

### Локальный тест для проверки кэширования Scrapper → Valkey

```bash
./mvnw -pl scrapper -am -Dtest=TrackedLinksCacheIntegrationTest test
```

Тест поднимает Valkey через Testcontainers и проверяет запись JSON-ответа, cache hit, инвалидацию при добавлении/удалении ссылки, истечение TTL и invalidation-события для client-side cache.

### Локальные тесты для проверки надежности

```bash
./mvnw -pl scrapper -am -Dtest=HttpResilienceExecutorTest,GithubExternalLinkClientTest,HttpBotUpdatesClientTest,FallbackBotUpdatesClientTest,RateLimitingIntegrationTest test
```

```bash
./mvnw -pl bot -am -Dtest=HttpScrapperClientTest,RateLimitingIntegrationTest test
```

Эти тесты проверяют timeout, retry, retryable/non-retryable статусы, переходы circuit breaker, fallback HTTP -> Kafka и rate limiting.

Полезную для разработки проекта информацию вы можете найти в файле [HELP.md](./HELP.md).
