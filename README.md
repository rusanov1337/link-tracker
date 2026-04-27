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

## PostgreSQL, Kafka, Bot и Scrapper

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
```

### Локальный запуск инфраструктуры

```bash
docker compose up -d postgres kafka-1 kafka-2 kafka-3 kafka-init schema-registry
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

- сначала поднимите `postgres`, `kafka-*`, `kafka-init` и `schema-registry` через `docker compose`
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

### Переключение доступа к БД

- `SCRAPPER_DB_ACCESS_TYPE=SQL`
- `SCRAPPER_DB_ACCESS_TYPE=ORM`

### Настройки планировщика

- `SCRAPPER_SCHEDULER_BATCH_SIZE` — размер батча ссылок
- `SCRAPPER_SCHEDULER_PARALLELISM` — количество потоков для обработки батча

### Локальный тест для проверки интеграции Scrapper -> Kafka -> Bot

Тест, который можно запускать локально для проверки полного пути сообщения:

```bash
./mvnw -pl build-report-aggregate -am -Dtest=BotScrapperContainerE2ETest -Dsurefire.failIfNoSpecifiedTests=false test
```

Для интеграционных тестов нужен запущенный Docker.

Полезную для разработки проекта информацию вы можете найти в файле [HELP.md](./HELP.md).
